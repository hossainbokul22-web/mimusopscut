package io.github.artcraftandroid.capcraft.ui.editor.panels

import android.net.Uri
import io.github.artcraftandroid.capcraft.core.RowKind
import io.github.artcraftandroid.capcraft.engine.Media
import io.github.artcraftandroid.capcraft.engine.Threads
import io.github.artcraftandroid.capcraft.engine.userMessage
import io.github.artcraftandroid.capcraft.ui.editor.EditorViewModel
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** The slow moves the pictures take in turn, so no two scenes in a row move the same way. */
private val SCENE_MOTIONS = listOf("zoom-in", "pan-left", "zoom-out", "pan-right", "pan-up", "pan-down")

/** The most pictures a script video takes; they go round again when the script has more lines. */
const val SCENE_PICTURES = 20

/**
 * Text to video, the second half: the captions of a script are on the timeline already, and each
 * gets a picture for as long as it is up. The pictures go in order and round again when there are
 * fewer than lines. On an empty main track they become it, one after another, with a short
 * dissolve at each cut; over footage already there, each goes on an overlay row at its caption.
 * Every picture is given a slow zoom or pan.
 *
 * Returns nothing; the timeline shows the result, or [EditorViewModel.message] says what failed.
 */
suspend fun EditorViewModel.layScenes(uris: List<Uri>, from: Long) {
    val s = session ?: return
    if (uris.isEmpty()) return
    busy = "Adding the pictures"
    val result = withContext(Threads.commands) {
        runCatching {
            var view = timeline ?: error("the timeline is not ready")
            // the captions this script made, in the order they are read
            val captions = view.rows.flatMap { it.clips }
                .filter { it.kind == "text" && it.startUs >= from - 1_000L }
                .sortedBy { it.startUs }
            if (captions.isEmpty()) error("there are no captions to put pictures behind")
            val media = uris.take(SCENE_PICTURES).map { uri ->
                s.import(Media.importUri(getApplication(), path, uri).absolutePath).id
            }
            val scenes = captions.indices.map { media[it % media.size] }
            val mainEmpty = view.rows.firstOrNull { it.kind == RowKind.MAIN }?.clips.isNullOrEmpty()
            val placed = ArrayList<String>()
            if (mainEmpty) {
                view = s.addToMain(scenes, from)
                val mains = view.rows.first { it.kind == RowKind.MAIN }.clips.sortedBy { it.startUs }
                mains.forEachIndexed { i, clip ->
                    placed += clip.id
                    val caption = captions.getOrNull(i) ?: return@forEachIndexed
                    // as long as its caption, so the picture changes as the line does
                    val delta = caption.durationUs - clip.durationUs
                    if (delta != 0L) runCatching { view = s.trim(clip.id, false, delta) }
                }
                val cuts = JSONArray()
                view.rows.first { it.kind == RowKind.MAIN }.clips.sortedBy { it.startUs }.drop(1).forEach { c ->
                    cuts.put(
                        JSONObject().put("op", "updateClip").put("clipId", c.id)
                            .put("patch", JSONObject().put("transitionIn", JSONObject().put("id", "concat.dissolve").put("duration", 0.4))),
                    )
                }
                if (cuts.length() > 0) runCatching { view = s.applyBatch(cuts.toString()) }
            } else {
                captions.forEachIndexed { i, caption ->
                    view = s.addOverlay(scenes[i], caption.startUs)
                    val id = view.createdId
                    placed += id
                    val clip = view.rows.flatMap { it.clips }.firstOrNull { it.id == id } ?: return@forEachIndexed
                    val delta = caption.durationUs - clip.durationUs
                    if (delta != 0L) runCatching { view = s.trim(id, false, delta) }
                }
            }
            placed.forEachIndexed { i, id -> runCatching { view = s.photoMotion(id, SCENE_MOTIONS[i % SCENE_MOTIONS.size]) } }
            view
        }
    }
    busy = null
    result.onSuccess {
        applyView(it)
        message = "Your video is ready - change anything you like"
    }.onFailure { message = it.userMessage }
}
