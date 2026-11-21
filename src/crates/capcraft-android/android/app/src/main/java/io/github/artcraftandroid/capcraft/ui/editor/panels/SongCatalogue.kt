package io.github.artcraftandroid.capcraft.ui.editor.panels

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * One song of the online catalogue: what its row shows, where its file is, and the licence it was
 * published under. Nothing here is bundled in the app; a song is streamed to play and kept on the
 * phone only once it is put on a timeline.
 */
class OnlineSong(
    val id: String,
    val title: String,
    val artist: String,
    /** "bn", "hi", "en" or "" for none of those. */
    val language: String,
    /** A shelf of the catalogue's own, such as Folk or Romantic; empty for none. */
    val collection: String,
    val url: String,
    val durationMs: Long,
    val bytes: Long,
    val license: String,
    val author: String,
    /** Lowercase hex, or empty when the catalogue gives none; checked after a download when given. */
    val sha256: String,
) {
    /** The credit a row shows: who made it and under what licence, which CC-BY asks for. */
    val credit: String get() = listOf(artist, author, license).filter { it.isNotBlank() }.distinct().joinToString(" · ")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("artist", artist).put("language", language)
        .put("collection", collection).put("url", url).put("duration", durationMs / 1000.0)
        .put("bytes", bytes).put("license", license).put("author", author).put("sha256", sha256)

    companion object {
        /** The song `o` describes, or null when it cannot be offered (see [SongCatalogue.LICENCES]). */
        fun fromJson(o: JSONObject): OnlineSong? {
            val id = o.optString("id")
            val title = o.optString("title")
            val url = o.optString("url")
            val license = o.optString("license").trim().uppercase()
            val sha = o.optString("sha256").trim().lowercase()
            if (!Regex("[a-z0-9-]{1,80}").matches(id) || title.isBlank()) return null
            if (!url.startsWith("https://")) return null
            if (license !in SongCatalogue.LICENCES) return null
            if (sha.isNotEmpty() && !Regex("[0-9a-f]{64}").matches(sha)) return null
            return OnlineSong(
                id = id,
                title = title,
                artist = o.optString("artist"),
                language = o.optString("language").lowercase().takeIf { it in SongCatalogue.LANGUAGES } ?: "",
                collection = o.optString("collection").trim(),
                url = url,
                durationMs = (o.optDouble("duration", 0.0) * 1000).toLong(),
                bytes = o.optLong("bytes", 0),
                license = license,
                author = o.optString("author"),
                sha256 = sha,
            )
        }
    }
}

/** The catalogue as read, and a note for the page when it is not the whole story. */
class Catalogue(val songs: List<OnlineSong>, val note: String)

/**
 * The online song catalogue: a JSON list (see docs/SONG_CATALOGUE.md) read from [MANIFEST_URL],
 * kept on the phone for the times there is no network, and the songs fetched one at a time.
 *
 * Only songs with a licence that lets anyone redistribute them are offered, and every song must be
 * served over https. Blocking: call from a worker.
 */
object SongCatalogue {
    /**
     * Where the list lives. Change this to the raw address of your own manifest.json, for example
     * https://raw.githubusercontent.com/YOUR-NAME/capcraft-songs/main/manifest.json. Until the
     * address answers, the page says there are no songs yet.
     */
    const val MANIFEST_URL = "https://raw.githubusercontent.com/YOUR-GITHUB-NAME/capcraft-songs/main/manifest.json"

    /** The licences the catalogue accepts, as written in the manifest, upper-case. */
    val LICENCES = setOf(
        "CC0", "CC0-1.0", "PUBLIC-DOMAIN", "PDM-1.0",
        "CC-BY-4.0", "CC-BY-3.0", "CC-BY-SA-4.0", "CC-BY-SA-3.0",
    )

    /** The languages the page filters by. */
    val LANGUAGES = setOf("bn", "hi", "en")

    private const val MAX_MANIFEST = 4 * 1024 * 1024

    private fun dir(context: Context) = File(context.filesDir, "songs").apply { mkdirs() }

    private fun parse(bytes: ByteArray): List<OnlineSong> {
        val list = JSONObject(String(bytes, Charsets.UTF_8)).optJSONArray("songs") ?: JSONArray()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let(OnlineSong::fromJson) }
    }

    /** The list as last fetched, when it has been. */
    fun cached(context: Context): List<OnlineSong>? =
        runCatching { parse(File(dir(context), "manifest.json").readBytes()) }.getOrNull()

    /**
     * The catalogue: fetched fresh when the network answers, else the copy from last time, else
     * empty with a note that says why.
     */
    fun load(context: Context): Catalogue {
        val failure = try {
            val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            try {
                when (val code = connection.responseCode) {
                    200 -> {
                        val bytes = connection.inputStream.use { it.readBytes() }
                        if (bytes.size > MAX_MANIFEST) throw IllegalStateException("The song list is too large.")
                        val songs = parse(bytes)
                        File(dir(context), "manifest.json").writeBytes(bytes)
                        return Catalogue(songs, if (songs.isEmpty()) "No songs here yet." else "")
                    }
                    404 -> "No songs here yet."
                    else -> "The song list did not answer ($code)."
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            "No connection: showing the songs saved on this phone."
        }
        val kept = cached(context)
        return if (kept != null) Catalogue(kept, failure) else Catalogue(emptyList(), failure)
    }

    /** Where `song` is kept once downloaded. */
    fun file(context: Context, song: OnlineSong): File {
        val extension = song.url.substringBefore('?').substringAfterLast('.', "mp3").lowercase()
            .takeIf { it in setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac") } ?: "mp3"
        return File(dir(context), "${song.id}-${song.sha256.take(12)}.$extension")
    }

    /**
     * Downloads `song` unless it is already here, checks it against its digest when the manifest
     * has one, and returns the file. [progress] gets 0 to 1; a file that does not match is deleted
     * and an error is thrown. Blocking.
     */
    fun download(context: Context, song: OnlineSong, progress: (Float) -> Unit): File {
        val target = file(context, song)
        if (target.isFile && target.length() > 0) return target
        val partial = File(target.path + ".partial")
        val connection = URL(song.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != 200) throw IllegalStateException("The song did not download (${connection.responseCode}).")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: song.bytes
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val got = digest.digest().joinToString("") { "%02x".format(it) }
            if (song.sha256.isNotEmpty() && got != song.sha256) {
                throw IllegalStateException("The song did not match its checksum and was not kept.")
            }
            if (!partial.renameTo(target)) throw IllegalStateException("The song could not be saved.")
            return target
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }

    /** The language a title or artist is written in, going by its script; null when unsure. */
    fun detectLanguage(text: String): String? {
        for (ch in text) {
            when (ch.code) {
                in 0x0980..0x09FF -> return "bn"
                in 0x0900..0x097F -> return "hi"
            }
        }
        return null
    }

    /** What the page's language chips and tags say, by language code. */
    fun languageName(code: String): String = when (code) {
        "bn" -> "বাংলা"
        "hi" -> "हिन्दी"
        "en" -> "English"
        else -> "Auto"
    }
}
