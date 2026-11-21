package io.github.artcraftandroid.capcraft.ui.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import io.github.artcraftandroid.capcraft.ui.Palette
import io.github.artcraftandroid.capcraft.ui.Type
import io.github.artcraftandroid.capcraft.ui.editor.EditorViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A language the captions can be translated between: ML Kit's code and the name in its own script. */
private class Lang(val code: String, val label: String)

private val LANGS = listOf(
    Lang("bn", "বাংলা"), Lang("hi", "हिन्दी"), Lang("en", "English"), Lang("ur", "اردو"),
    Lang("ar", "العربية"), Lang("es", "Español"), Lang("fr", "Français"), Lang("de", "Deutsch"),
    Lang("ru", "Русский"), Lang("id", "Indonesia"), Lang("tr", "Türkçe"), Lang("ja", "日本語"),
    Lang("ko", "한국어"), Lang("zh", "中文"),
)

/** Waits for a Google Play services task without blocking the thread. */
private suspend fun <T> Task<T>.result(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

/**
 * Translate captions: every caption on the timeline is put into another language, on the phone
 * (Google ML Kit; each language's model, about 30 MB, is downloaded once). Each caption keeps its
 * look and its time; only the words change, and one Undo puts them all back.
 */
@Composable
fun TranslateCaptionsPanel(vm: EditorViewModel) {
    val scope = rememberCoroutineScope()
    val ids = vm.captions.toList()
    // the language the captions are in, guessed from the first one's script; English otherwise
    var from by remember {
        mutableStateOf(
            ids.firstNotNullOfOrNull { vm.clipJson(it)?.optJSONObject("text")?.optString("content") }
                ?.let { SongCatalogue.detectLanguage(it) } ?: "en",
        )
    }
    var to by remember { mutableStateOf(if (from == "bn") "en" else "bn") }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun go() {
        if (ids.isEmpty() || working || from == to) return
        working = true
        status = "Getting the languages ready (the first time needs the internet)…"
        scope.launch {
            val translator = Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build(),
            )
            try {
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).result()
                val batch = JSONArray()
                ids.forEachIndexed { i, id ->
                    status = "Translating ${i + 1} of ${ids.size}…"
                    val style = vm.clipJson(id)?.optJSONObject("text") ?: return@forEachIndexed
                    val words = style.optString("content")
                    if (words.isBlank()) return@forEachIndexed
                    val out = translator.translate(words).result()
                    val look = JSONObject(style.toString()).put("content", out)
                    batch.put(JSONObject().put("op", "updateClip").put("clipId", id).put("patch", JSONObject().put("text", look)))
                }
                if (batch.length() > 0) vm.command(JSONObject().put("op", "batch").put("commands", batch))
                status = "Translated ${batch.length()} captions"
            } catch (e: Exception) {
                status = "Could not translate: ${e.message ?: "no connection"}"
            } finally {
                translator.close()
                working = false
            }
        }
    }

    PanelFrame("Translate captions", vm, height = 290.dp) {
        if (ids.isEmpty()) {
            Spacer(Modifier.height(30.dp))
            Text("Add captions first (Auto captions or Enter captions).", style = Type.Small.copy(color = Palette.TextSecondary, fontSize = 14.sp), modifier = Modifier.padding(horizontal = 20.dp))
            return@PanelFrame
        }
        Spacer(Modifier.height(10.dp))
        Text("From", style = Type.Small.copy(color = Palette.TextSecondary), modifier = Modifier.padding(horizontal = 20.dp))
        LanguageChips(from) { from = it }
        Spacer(Modifier.height(8.dp))
        Text("To", style = Type.Small.copy(color = Palette.TextSecondary), modifier = Modifier.padding(horizontal = 20.dp))
        LanguageChips(to) { to = it }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            val ready = !working && from != to
            Text(
                if (working) "Working…" else "Translate ${ids.size} captions",
                style = Type.BodyStrong.copy(color = Color.Black, fontSize = 15.sp),
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (ready) Palette.Accent else Palette.Accent.copy(alpha = 0.45f))
                    .clickable(enabled = ready) { go() }.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(status, style = Type.Small.copy(color = Palette.DockText, fontSize = 13.sp), modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}

@Composable
private fun LanguageChips(chosen: String, onChoose: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
        LANGS.forEach { l ->
            val on = l.code == chosen
            Text(
                l.label,
                style = Type.Small.copy(color = if (on) Color.Black else Palette.DockText, fontSize = 14.sp),
                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) Palette.Accent else Palette.DockText.copy(alpha = 0.12f))
                    .clickable { onChoose(l.code) }.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}
