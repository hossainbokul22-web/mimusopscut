package io.github.artcraftandroid.capcraft.ui.editor.panels

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.capcraft.core.RowKind
import io.github.artcraftandroid.capcraft.engine.clock
import io.github.artcraftandroid.capcraft.ui.Palette
import io.github.artcraftandroid.capcraft.ui.Type
import io.github.artcraftandroid.capcraft.ui.editor.EditorViewModel
import io.github.artcraftandroid.capcraft.ui.editor.Thumbs

/**
 * Storyboard: the clips of the main track as a row of scenes in order, each with its frame and
 * length. A scene can be moved earlier or later, or taken out; a touch on its picture puts the
 * playhead there. Every change is the timeline's own, so undo works as it does elsewhere.
 */
@Composable
fun StoryboardPanel(vm: EditorViewModel) {
    // redraw when a frame arrives
    @Suppress("UNUSED_VARIABLE") val tick = Thumbs.version.intValue
    val scenes = vm.timeline?.rows?.firstOrNull { it.kind == RowKind.MAIN }?.clips.orEmpty().sortedBy { it.startUs }
    PanelFrame("Storyboard", vm, height = 250.dp) {
        if (scenes.isEmpty()) {
            Text("Add clips first.", style = Type.Small.copy(color = Palette.DockText, fontSize = 14.sp), modifier = Modifier.padding(20.dp))
            return@PanelFrame
        }
        LazyRow(Modifier.padding(top = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
            itemsIndexed(scenes, key = { _, c -> c.id }) { i, c ->
                val frame = cutFrame(c, end = false)
                Column(Modifier.padding(end = 12.dp).width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.width(132.dp).height(80.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))
                            .clickable { vm.stop(); vm.scrub(c.startUs) },
                    ) {
                        if (frame != null) {
                            Image(frame.asImageBitmap(), null, Modifier.width(132.dp).height(80.dp), contentScale = ContentScale.Crop)
                        }
                        Text(
                            "${i + 1}", color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x99000000)).padding(horizontal = 5.dp),
                        )
                    }
                    Text(clock(c.durationUs), style = Type.Small.copy(color = Palette.DockText, fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("◀", color = if (i > 0) Color.White else Color(0xFF555555), fontSize = 18.sp,
                            modifier = Modifier.clickable(enabled = i > 0) { vm.edit { reorderMain(c.id, (i - 1).toUInt()) } }.padding(10.dp))
                        Text("✕", color = Color(0xFFFF6B6B), fontSize = 16.sp,
                            modifier = Modifier.clickable { vm.edit { delete(listOf(c.id)) } }.padding(10.dp))
                        Text("▶", color = if (i < scenes.size - 1) Color.White else Color(0xFF555555), fontSize = 18.sp,
                            modifier = Modifier.clickable(enabled = i < scenes.size - 1) { vm.edit { reorderMain(c.id, (i + 1).toUInt()) } }.padding(10.dp))
                    }
                }
            }
        }
    }
}
