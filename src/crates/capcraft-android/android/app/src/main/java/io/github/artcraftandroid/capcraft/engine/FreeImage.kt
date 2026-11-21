package io.github.artcraftandroid.capcraft.engine

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A free, keyless picture source: Pollinations' public prompt address. No account or key, but it is
 * a third-party service that can slow, mark pictures with a logo or stop working, and it allows one
 * picture about every fifteen seconds. The prompt is sent to image.pollinations.ai.
 */
object FreeImage {
    private const val HOST = "https://image.pollinations.ai/prompt/"
    private const val GAP_MS = 16_000L
    private const val MAX_BYTES = 12 * 1024 * 1024

    private var last = 0L

    /** Saves a picture of [prompt], [width] by [height], to [dest]. Waits its turn; throws with a message fit to show. */
    suspend fun make(prompt: String, width: Int, height: Int, dest: File, status: (String) -> Unit): File {
        val wait = last + GAP_MS - System.currentTimeMillis()
        if (wait > 0) {
            status("Waiting for the free service's turn…")
            delay(wait)
        }
        status("Drawing…")
        last = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            val address = HOST + URLEncoder.encode(prompt.take(600), "UTF-8").replace("+", "%20") +
                "?width=$width&height=$height&nologo=true&model=flux&seed=${(System.nanoTime() % 1_000_000).toInt()}"
            val c = URL(address).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 20_000
                c.readTimeout = 120_000
                val code = c.responseCode
                if (code == 429) throw IllegalStateException("The free picture service is busy; try again in a minute.")
                if (code != 200) throw IllegalStateException("The free picture service said $code. It may have changed or stopped; use a Replicate key instead.")
                val type = c.contentType.orEmpty()
                if (!type.startsWith("image/")) throw IllegalStateException("The free picture service did not send a picture.")
                val bytes = c.inputStream.use { it.readBytes() }
                if (bytes.size > MAX_BYTES || bytes.isEmpty()) throw IllegalStateException("The picture was not usable.")
                dest.writeBytes(bytes)
                dest
            } finally {
                c.disconnect()
            }
        }
    }
}
