package io.github.artcraftandroid.capcraft.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A small client for Replicate's HTTP API (https://replicate.com/docs/reference/http): one API key,
 * and any model on it is run with `run`. The key is the user's own, kept in this app's private
 * settings, and every run is billed to their Replicate account. Pictures and videos sent for a run
 * leave the phone for Replicate's servers.
 */
object Replicate {
    private const val API = "https://api.replicate.com/v1"
    private const val PREFS = "capcraft-ai"

    fun key(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("key", "").orEmpty()

    fun setKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("key", key.trim()).apply()
    }

    /** Whether `url` is one a key may be sent to: https, and Replicate's own. */
    private fun trusted(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val host = runCatching { URL(url).host }.getOrNull() ?: return false
        return host == "replicate.com" || host.endsWith(".replicate.com") || host == "replicate.delivery" || host.endsWith(".replicate.delivery")
    }

    private fun request(
        method: String,
        url: String,
        token: String,
        body: ByteArray? = null,
        type: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        if (!trusted(url)) throw IllegalStateException("Refusing to send the key to $url")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = 90_000
            c.setRequestProperty("Authorization", "Bearer $token")
            if (type != null) c.setRequestProperty("Content-Type", type)
            extra.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: "")
        } finally {
            c.disconnect()
        }
    }

    private fun describe(code: Int, text: String): String = when (code) {
        401 -> "The API key was not accepted. Check it in AI Studio."
        402 -> "Your Replicate account needs credit."
        429 -> "Too many requests; try again in a moment."
        else -> "Replicate said $code: " + (runCatching { JSONObject(text).optString("detail") }.getOrNull().orEmpty().ifBlank { text.take(200) })
    }

    /**
     * Runs `model` ("owner/name") on `input` and returns its output: a URL string, or an array of
     * them or of text. Waits for it, telling `status` how it goes. Throws with a message fit to show.
     */
    suspend fun run(context: Context, model: String, input: JSONObject, status: (String) -> Unit): Any? =
        withContext(Dispatchers.IO) {
            val token = key(context)
            if (token.isBlank()) throw IllegalStateException("Add your Replicate API key first.")
            status("Starting…")
            val wait = mapOf("Prefer" to "wait=60")
            val json = "application/json"
            var (code, text) = request("POST", "$API/models/$model/predictions", token, JSONObject().put("input", input).toString().toByteArray(), json, wait)
            if (code == 404) {
                // not an official model: its predictions are made by version
                val (c2, t2) = request("GET", "$API/models/$model", token)
                if (c2 != 200) throw IllegalStateException(describe(c2, t2))
                val version = JSONObject(t2).optJSONObject("latest_version")?.optString("id").orEmpty()
                if (version.isEmpty()) throw IllegalStateException("The model $model has no version to run.")
                val made = request("POST", "$API/predictions", token, JSONObject().put("version", version).put("input", input).toString().toByteArray(), json, wait)
                code = made.first
                text = made.second
            }
            if (code !in 200..201) throw IllegalStateException(describe(code, text))
            var prediction = JSONObject(text)
            val started = System.currentTimeMillis()
            while (true) {
                when (prediction.optString("status")) {
                    "succeeded" -> return@withContext prediction.opt("output")
                    "failed", "canceled" -> throw IllegalStateException(prediction.optString("error").ifBlank { "The job did not finish." })
                }
                val seconds = (System.currentTimeMillis() - started) / 1000
                if (seconds > 15 * 60) throw IllegalStateException("The job took too long.")
                status((if (prediction.optString("status") == "starting") "Starting…" else "Working…") + " ${seconds}s")
                delay(3000)
                val get = prediction.optJSONObject("urls")?.optString("get").orEmpty()
                val (c, t) = request("GET", get, token)
                if (c !in 200..299) throw IllegalStateException(describe(c, t))
                prediction = JSONObject(t)
            }
            // the loop leaves only by returning or throwing
            throw IllegalStateException("The job ended without a result.")
        }

    /** The URLs in a model's output, whether it gave one or a list. */
    fun urlsOf(output: Any?): List<String> = when (output) {
        is String -> listOf(output)
        is JSONArray -> (0 until output.length()).mapNotNull { output.optString(it).takeIf { s -> s.startsWith("http") } }
        else -> emptyList()
    }

    /** The text of a language model's output, which arrives in pieces. */
    fun textOf(output: Any?): String = when (output) {
        is String -> output
        is JSONArray -> (0 until output.length()).joinToString("") { output.optString(it) }
        else -> ""
    }

    /** Downloads `url` (a result) into `dest`. */
    suspend fun download(context: Context, url: String, dest: File): File = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) throw IllegalStateException("The result is not an https address.")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 90_000
            if (trusted(url)) c.setRequestProperty("Authorization", "Bearer ${key(context)}")
            if (c.responseCode !in 200..299) throw IllegalStateException("The result did not download (${c.responseCode}).")
            c.inputStream.use { input -> dest.outputStream().use { input.copyTo(it, 1 shl 16) } }
            dest
        } finally {
            c.disconnect()
        }
    }

    /** Uploads a file to Replicate and returns the address a model can read it at. */
    suspend fun upload(context: Context, file: File, mime: String): String = withContext(Dispatchers.IO) {
        val token = key(context)
        if (token.isBlank()) throw IllegalStateException("Add your Replicate API key first.")
        val boundary = "----mimusops${System.nanoTime()}"
        val c = URL("$API/files").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.doOutput = true
            c.setChunkedStreamingMode(1 shl 16)
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            c.outputStream.use { out ->
                fun part(s: String) = out.write(s.toByteArray())
                part("--$boundary\r\nContent-Disposition: form-data; name=\"content\"; filename=\"${file.name}\"\r\nContent-Type: $mime\r\n\r\n")
                file.inputStream().use { it.copyTo(out, 1 shl 16) }
                part("\r\n--$boundary\r\nContent-Disposition: form-data; name=\"filename\"\r\n\r\n${file.name}")
                part("\r\n--$boundary\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\n$mime\r\n--$boundary--\r\n")
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
            if (code !in 200..201) throw IllegalStateException(describe(code, text))
            JSONObject(text).optJSONObject("urls")?.optString("get")?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("The upload gave no address to use.")
        } finally {
            c.disconnect()
        }
    }

    /** Copies a picked file into the cache under `name` so it can be uploaded. */
    suspend fun cached(context: Context, uri: Uri, name: String): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it, 1 shl 16) } }
            ?: throw IllegalStateException("The file could not be read.")
        file
    }

    /** A picture as a data address a model takes as an input, shrunk to `maxSide` on its longer side. */
    suspend fun imageData(context: Context, uri: Uri, maxSide: Int = 1536): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IllegalStateException("The picture could not be read.")
        bitmapData(bitmap, maxSide)
    }

    fun bitmapData(bitmap: Bitmap, maxSide: Int = 1536): String {
        val scale = minOf(1f, maxSide / maxOf(bitmap.width, bitmap.height).toFloat())
        val shrunk = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        shrunk.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
