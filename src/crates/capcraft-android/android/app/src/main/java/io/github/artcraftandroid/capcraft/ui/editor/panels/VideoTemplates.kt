package io.github.artcraftandroid.capcraft.ui.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.capcraft.core.RowKind
import io.github.artcraftandroid.capcraft.engine.Threads
import io.github.artcraftandroid.capcraft.engine.userMessage
import io.github.artcraftandroid.capcraft.ui.Palette
import io.github.artcraftandroid.capcraft.ui.Type
import io.github.artcraftandroid.capcraft.ui.editor.EditorViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A video template: a recipe over the clips already on the main track. It sets how long each clip
 * plays, the cut between them, a colour look and, for photos, a slow move. Nothing is bundled; a
 * template only does what the editor's own tools do, so every part can be changed afterwards.
 */
class VideoTemplate(
    val id: String,
    val name: String,
    val line: String,
    /** How long each clip plays, in seconds; the lengths go round when there are more clips. */
    val lengths: List<Double>,
    /** The engine's transition names, going round, or empty for hard cuts. */
    val cuts: List<String>,
    val cutSeconds: Double,
    /** The colour look: knobs of Adjust (brightness, contrast, saturation, temperature), or empty. */
    val look: Map<String, Double>,
    /** The photo moves, going round, or empty for none. */
    val moves: List<String>,
    /** The frame as width and height, or null to leave it as it is. */
    val frame: Pair<Int, Int>?,
)

val VIDEO_TEMPLATES = listOf(
    VideoTemplate(
        "vlog", "Vlog", "Quick cuts with soft slides and a warm, bright look.",
        listOf(2.0, 1.6, 2.4), listOf("concat.slide", "concat.dissolve", "concat.push"), 0.35,
        mapOf("brightness" to 6.0, "saturation" to 12.0, "temperature" to 6900.0),
        listOf("zoom-in", "pan-left", "zoom-out"), null,
    ),
    VideoTemplate(
        "reel", "Reel punch", "Fast one-second beats in a tall frame, vivid colour.",
        listOf(1.0, 0.8, 1.2), listOf("concat.zoom-transition", "concat.blur-dissolve"), 0.2,
        mapOf("contrast" to 12.0, "saturation" to 22.0),
        listOf("zoom-in", "zoom-out"), 1080 to 1920,
    ),
    VideoTemplate(
        "cinema", "Cinematic", "Long, slow dissolves with deep contrast and muted colour.",
        listOf(3.5, 3.0), listOf("concat.dissolve"), 0.9,
        mapOf("contrast" to 18.0, "saturation" to -14.0, "temperature" to 6100.0),
        listOf("zoom-in", "pan-right"), 1920 to 1080,
    ),
    VideoTemplate(
        "beatpop", "Beat pop", "Half-second beats that pop with a zoom, vivid and tall.",
        listOf(0.6, 0.5, 0.7), listOf("concat.zoom-transition", "concat.blur-dissolve"), 0.15,
        mapOf("contrast" to 14.0, "saturation" to 20.0),
        listOf("zoom-in", "zoom-out"), 1080 to 1920,
    ),
    VideoTemplate(
        "glide", "Glide reel", "Sliding cuts and warm colour for a travel reel.",
        listOf(1.4, 1.2, 1.8), listOf("concat.push", "concat.slide"), 0.3,
        mapOf("contrast" to 8.0, "saturation" to 22.0, "temperature" to 6800.0),
        listOf("pan-left", "pan-right", "zoom-in"), 1080 to 1920,
    ),
    VideoTemplate(
        "moody", "Moody", "Slow dissolves, cool muted colour, tall frame.",
        listOf(2.2, 1.8), listOf("concat.dissolve"), 0.5,
        mapOf("brightness" to -4.0, "contrast" to 20.0, "saturation" to -22.0, "temperature" to 5900.0),
        listOf("zoom-in", "pan-up"), 1080 to 1920,
    ),
    VideoTemplate(
        "hardcut", "Hard cuts", "Quick plain cuts with no transitions, bright and clean.",
        listOf(1.2, 0.9, 1.5), emptyList(), 0.0,
        mapOf("brightness" to 5.0, "saturation" to 10.0),
        emptyList(), 1080 to 1920,
    ),
    VideoTemplate(
        "memories", "Memories", "A gentle photo slideshow: slow moves and warm light.",
        listOf(3.0), listOf("concat.dissolve"), 0.6,
        mapOf("brightness" to 4.0, "saturation" to 6.0, "temperature" to 7200.0),
        listOf("zoom-in", "pan-left", "zoom-out", "pan-right", "pan-up", "pan-down"), null,
    ),
    VideoTemplate(
        "travel", "Travel story", "Medium cuts that push across, rich colour.",
        listOf(2.5, 2.0), listOf("concat.push", "concat.slide", "concat.zoom-transition"), 0.4,
        mapOf("contrast" to 8.0, "saturation" to 18.0),
        listOf("pan-left", "zoom-in", "pan-right"), null,
    ),
)

/** Puts [t] on the clips of the main track; one undo step is not guaranteed, so it is done in one batch where it can be. */
suspend fun EditorViewModel.applyTemplate(t: VideoTemplate, title: String = "") {
    val s = session ?: return
    busy = "Applying ${t.name}"
    val result = withContext(Threads.commands) {
        runCatching {
            var view = timeline ?: error("the timeline is not ready")
            val order = view.rows.firstOrNull { it.kind == RowKind.MAIN }?.clips.orEmpty().sortedBy { it.startUs }.map { it.id }
            if (order.isEmpty()) error("Add some clips first")
            t.frame?.let { (w, h) -> view = s.setCanvas(w.toUInt(), h.toUInt()) }
            order.forEachIndexed { i, id ->
                val clip = view.rows.flatMap { it.clips }.firstOrNull { it.id == id } ?: return@forEachIndexed
                val wanted = (t.lengths[i % t.lengths.size] * 1_000_000).toLong()
                val spare = clip.durationUs - wanted
                // a longer clip is cut to its middle; a shorter one plays as it is
                if (spare > 100_000L) {
                    if (clip.kind != "image") view = s.trim(id, true, spare / 2)
                    val now = view.rows.flatMap { it.clips }.firstOrNull { it.id == id } ?: return@forEachIndexed
                    view = s.trim(id, false, -(now.durationUs - wanted))
                } else if (clip.kind == "image" && spare < -100_000L) {
                    view = s.trim(id, false, -spare)
                }
            }
            val batch = JSONArray()
            order.forEachIndexed { i, id ->
                val patch = JSONObject()
                // the cut into every clip after the first
                if (i > 0 && t.cuts.isNotEmpty()) {
                    patch.put("transitionIn", JSONObject().put("id", t.cuts[(i - 1) % t.cuts.size]).put("duration", t.cutSeconds))
                }
                if (t.look.isNotEmpty()) {
                    val chain = view.rows.flatMap { it.clips }.firstOrNull { it.id == id }?.let { clipJson(id) }
                        ?.optJSONArray("videoEffects") ?: JSONArray()
                    val next = JSONArray()
                    for (k in 0 until chain.length()) {
                        val link = chain.getJSONObject(k)
                        if (link.optString("id") != "concat.adjust") next.put(link)
                    }
                    val params = JSONObject()
                    t.look.forEach { (k, v) -> params.put(k, v) }
                    next.put(JSONObject().put("id", "concat.adjust").put("params", params))
                    patch.put("videoEffects", next)
                }
                if (patch.length() > 0) {
                    batch.put(JSONObject().put("op", "updateClip").put("clipId", id).put("patch", patch))
                }
            }
            if (batch.length() > 0) view = s.applyBatch(batch.toString())
            if (t.moves.isNotEmpty()) {
                var n = 0
                order.forEach { id ->
                    val c = view.rows.flatMap { it.clips }.firstOrNull { it.id == id }
                    if (c?.kind == "image") {
                        view = s.photoMotion(id, t.moves[n % t.moves.size])
                        n++
                    }
                }
            }
            // the title, over the first scene
            if (title.isNotBlank()) {
                val style = JSONObject().put("content", title.trim()).put("fontFamily", "Inter")
                    .put("fontSize", 0.07).put("fontWeight", 700).put("color", "#ffffff").put("shadow", true)
                runCatching { view = s.addText(0L, 3_000_000L, style.toString()) }
            }
            view
        }
    }
    busy = null
    result.onSuccess {
        applyView(it)
        message = "${t.name} applied - change anything you like"
    }.onFailure { message = it.userMessage }
}

/**
 * The rhythm of a reel: the shot lengths found in [uri] become a template's clip lengths, so the
 * clips on the main track are cut to the same beat. Only the timing is taken from the reel.
 */
suspend fun EditorViewModel.copyRhythm(context: android.content.Context, uri: android.net.Uri, title: String) {
    busy = "Reading the reel's cuts"
    val lengths = withContext(kotlinx.coroutines.Dispatchers.Default) {
        runCatching { io.github.artcraftandroid.capcraft.engine.CutDetect.shotLengths(context, uri) }.getOrDefault(emptyList())
    }
    busy = null
    if (lengths.size < 2) {
        message = "Could not find cuts in that video"
        return
    }
    val mains = timeline?.rows?.firstOrNull { it.kind == RowKind.MAIN }?.clips?.size ?: 0
    val recipe = VideoTemplate(
        "rhythm", "Reel rhythm", "", lengths, emptyList(), 0.0, emptyMap(), emptyList(), null,
    )
    applyTemplate(recipe, title)
    message = "Found ${lengths.size} shots in the reel; your $mains clips follow the first ${minOf(lengths.size, mains)} of them"
}

/** Templates: the choice of recipes for the clips on the main track. */
@Composable
fun TemplatesPanel(vm: EditorViewModel) {
    var title by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pickReel = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            vm.stop()
            vm.panel = null
            vm.viewModelScope.launch { vm.copyRhythm(context.applicationContext, uri, title) }
        }
    }
    PanelFrame("Templates", vm, height = 380.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
            // an optional title for the first seconds
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.DockText.copy(alpha = 0.10f)).padding(12.dp)) {
                if (title.isEmpty()) Text("Title for the start (optional)", style = Type.Small.copy(color = Palette.DockText, fontSize = 14.sp))
                BasicTextField(
                    title, { title = it.replace("\n", " ") }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Palette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Accent.copy(alpha = 0.22f))
                    .clickable { pickReel.launch("video/*") }.padding(14.dp),
            ) {
                Text("Copy the rhythm of a reel", style = Type.Small.copy(color = Color.White, fontSize = 16.sp))
                Text(
                    "Pick a video (a reel you saved yourself). Its cuts are found and your clips are cut to the same beat. Only the timing is taken.",
                    style = Type.Small.copy(color = Palette.DockText, fontSize = 12.sp),
                )
            }
            Spacer(Modifier.height(8.dp))
            VIDEO_TEMPLATES.forEach { t ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Palette.DockText.copy(alpha = 0.10f))
                        .clickable {
                            vm.stop()
                            vm.panel = null
                            vm.viewModelScope.launch { vm.applyTemplate(t, title) }
                        }.padding(14.dp),
                ) {
                    Text(t.name, style = Type.Small.copy(color = Color.White, fontSize = 16.sp))
                    Text(t.line, style = Type.Small.copy(color = Palette.DockText, fontSize = 12.sp))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
