package io.github.artcraftandroid.capcraft.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** A frame a design can start in: its name and size in pixels. */
private class Frame(val label: String, val width: Int, val height: Int)

private val FRAMES = listOf(
    Frame("Poster 4:5", 1080, 1350),
    Frame("Story 9:16", 1080, 1920),
    Frame("Square 1:1", 1080, 1080),
    Frame("Landscape 16:9", 1920, 1080),
)

/** A background: the colour at the top and at the bottom (the same twice for a flat one). */
private class Ground(val top: Int, val bottom: Int)

private val GROUNDS = listOf(
    Ground(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
    Ground(0xFF111111.toInt(), 0xFF111111.toInt()),
    Ground(0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
    Ground(0xFFFF512F.toInt(), 0xFFF09819.toInt()),
    Ground(0xFF2193B0.toInt(), 0xFF6DD5ED.toInt()),
    Ground(0xFF134E5E.toInt(), 0xFF71B280.toInt()),
    Ground(0xFF8E2DE2.toInt(), 0xFF4A00E0.toInt()),
    Ground(0xFFED4264.toInt(), 0xFFFFEDBC.toInt()),
    Ground(0xFF00B09B.toInt(), 0xFF96C93D.toInt()),
    Ground(0xFF232526.toInt(), 0xFF414345.toInt()),
)

/** Paints a background of [ground] at [width] by [height] into the cache and returns its file. */
private fun paint(cache: File, width: Int, height: Int, ground: Ground): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, height.toFloat(), ground.top, ground.bottom, Shader.TileMode.CLAMP)
    }
    Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    val file = File(cache, "design-${System.currentTimeMillis()}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}

/**
 * The start of a poster or a logo: a frame and a background to choose, then the editor opens on a
 * blank picture of that size with the text templates up, to which text, stickers and shapes are
 * added. [onStart] gets the frame's size and the background picture's file; the export is a photo.
 */
@Composable
fun DesignStarter(kind: String, onClose: () -> Unit, onStart: (width: Int, height: Int, picture: Uri) -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val logo = kind == "logo"
    var frame by remember { mutableIntStateOf(if (logo) 2 else 0) }
    var ground by remember { mutableIntStateOf(if (logo) 0 else 2) }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF121212)).statusBarsPadding().navigationBarsPadding()
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(if (logo) "Logo maker" else "Poster maker", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Text("Size", color = Color(0xFFA3A3A3), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                FRAMES.forEachIndexed { i, f ->
                    val on = i == frame
                    Text(
                        f.label,
                        color = if (on) Color.Black else Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 10.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (on) Color(0xFF00CAE0) else Color(0xFF2A2A2A))
                            .clickable { frame = i }.padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Background", color = Color(0xFFA3A3A3), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                GROUNDS.forEachIndexed { i, g ->
                    Box(
                        Modifier.padding(end = 12.dp).size(48.dp).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(g.top), Color(g.bottom))))
                            .border(if (i == ground) 3.dp else 1.dp, if (i == ground) Color(0xFF00CAE0) else Color(0xFF555555), CircleShape)
                            .clickable { ground = i },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Start designing",
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF00CAE0))
                    .clickable {
                        val f = FRAMES[frame]
                        runCatching { paint(context.cacheDir, f.width, f.height, GROUNDS[ground]) }
                            .onSuccess { onStart(f.width, f.height, Uri.fromFile(it)) }
                    }
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Cancel",
                color = Color(0xFFA3A3A3),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClose).padding(10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
