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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.capcraft.ui.Palette
import io.github.artcraftandroid.capcraft.ui.Type
import io.github.artcraftandroid.capcraft.ui.editor.EditorViewModel

/** The moves a photo can be given, by the engine's name for each. */
private val MOTIONS = listOf(
    "none" to "None",
    "zoom-in" to "Zoom in",
    "zoom-out" to "Zoom out",
    "pan-left" to "Pan left",
    "pan-right" to "Pan right",
    "pan-up" to "Pan up",
    "pan-down" to "Pan down",
)

/**
 * Photo motion: a slow zoom or pan over a still, so a photo plays as a video. A touch on a move
 * puts it on the photo (or on every photo, when that is switched on) as keyframes the timeline
 * shows and Keyframe can edit; None takes them off.
 */
@Composable
fun PhotoMotionPanel(vm: EditorViewModel) {
    val photo = vm.selectedClip?.takeIf { it.kind == "image" } ?: run { vm.panel = null; return }
    var chosen by remember(photo.id) { mutableStateOf("") }
    var all by remember { mutableStateOf(false) }

    fun apply(key: String) {
        chosen = key
        vm.stop()
        vm.edit { if (all) photoMotionAll(key) else photoMotion(photo.id, key) }
    }

    PanelFrame("Photo motion", vm, height = 190.dp) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            MOTIONS.forEach { (key, label) ->
                val on = key == chosen
                Text(
                    label,
                    style = Type.Small.copy(color = if (on) Color.White else Palette.DockText, fontSize = 14.sp),
                    modifier = Modifier.padding(end = 10.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (on) Palette.Accent else Palette.DockText.copy(alpha = 0.12f))
                        .clickable { apply(key) }.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clickable { all = !all }.padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (all) "✓ Apply to all photos" else "Apply to all photos",
                style = Type.Small.copy(color = if (all) Palette.Accent else Palette.DockText, fontSize = 14.sp),
            )
        }
    }
}
