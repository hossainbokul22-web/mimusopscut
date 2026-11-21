package io.github.artcraftandroid.capcraft.engine

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writing by any OpenAI-style chat endpoint the user names (their own FreeLLMAPI, Groq, Gemini's
 * compatible address, OpenRouter...): an address ending in /v1, a key and a model. Settings stay in
 * this app's private settings. https is required, except for a machine on the phone's own network
 * (localhost, 10.x, 192.168.x, 172.16-31.x), where plain http is allowed.
 */
object TextLlm {
    private const val PREFS = "capcraft-ai"

    fun url(context: Context): String = prefs(context).getString("llm_url", "").orEmpty()
    fun key(context: Context): String = prefs(context).getString("llm_key", "").orEmpty()
    fun model(context: Context): String = prefs(context).getString("llm_model", "").orEmpty()

    /** Whether an address and a model are set (a key may be left empty for a local server). */
    fun configured(context: Context): Boolean = url(context).isNotBlank() && model(context).isNotBlank()

    fun save(context: Context, url: String, key: String, model: String) {
        prefs(context).edit().putString("llm_url", url.trim().trimEnd('/')).putString("llm_key", key.trim()).putString("llm_model", model.trim()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun allowed(address: String): Boolean {
        val u = runCatching { URL(address) }.getOrNull() ?: return false
        if (u.protocol == "https") return true
        if (u.protocol != "http") return false
        val h = u.host
        if (h == "localhost" || h.startsWith("10.") || h.startsWith("192.168.")) return true
        val second = h.removePrefix("172.").substringBefore('.').toIntOrNull()
        return h.startsWith("172.") && second != null && second in 16..31
    }

    /** One question, one answer: the reply's text. Throws with a message fit to show. */
    suspend fun ask(context: Context, prompt: String, maxTokens: Int = 700): String = withContext(Dispatchers.IO) {
        if (!configured(context)) throw IllegalStateException("Set the writing service in AI Studio first.")
        val address = url(context) + "/chat/completions"
        if (!allowed(address)) throw IllegalStateException("The address must start with https:// (or be on your own network).")
        val body = JSONObject().put("model", model(context)).put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .toString().toByteArray()
        val c = URL(address).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.setRequestProperty("Content-Type", "application/json")
            key(context).takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            c.doOutput = true
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) {
                val why = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                throw IllegalStateException(
                    when (code) {
                        401, 403 -> "The writing service did not accept the key."
                        429 -> "The writing service is busy or out of free quota; try again later."
                        else -> "The writing service said $code: " + why.ifBlank { text.take(160) }
                    },
                )
            }
            JSONObject(text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
                .ifBlank { throw IllegalStateException("The writing service sent no text.") }
        } finally {
            c.disconnect()
        }
    }
}
