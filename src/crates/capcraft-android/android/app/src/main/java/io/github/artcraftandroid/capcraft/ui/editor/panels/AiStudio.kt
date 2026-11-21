package io.github.artcraftandroid.capcraft.ui.editor.panels

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import io.github.artcraftandroid.capcraft.ui.editor.Thumbs
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.artcraftandroid.capcraft.engine.Replicate
import io.github.artcraftandroid.capcraft.core.still
import io.github.artcraftandroid.capcraft.engine.FreeImage
import io.github.artcraftandroid.capcraft.engine.SystemVoice
import io.github.artcraftandroid.capcraft.engine.TextLlm
import io.github.artcraftandroid.capcraft.engine.Threads
import io.github.artcraftandroid.capcraft.engine.toBitmap
import io.github.artcraftandroid.capcraft.ui.Glyph
import io.github.artcraftandroid.capcraft.ui.LucideIcons
import io.github.artcraftandroid.capcraft.ui.Palette
import io.github.artcraftandroid.capcraft.ui.editor.EditorViewModel
import io.github.artcraftandroid.capcraft.ui.editor.Panel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What AI Studio can make. */
private enum class AiTool(val title: String, val line: String, val icon: ImageVector) {
    TEXT_IMAGE("Text to image", "Describe a picture and get it.", LucideIcons.Image),
    EDIT("AI photo edit", "Change a photo with words: remove an object, extend the background, restyle it.", LucideIcons.WandSparkles),
    IMAGE_VIDEO("Image to video", "Bring a photo to life as a short clip.", LucideIcons.ImagePlay),
    TEXT_VIDEO("Text to video", "Describe a scene and get a short clip.", LucideIcons.Clapperboard),
    EXTEND("Extend video", "Continue a clip from its last frame.", LucideIcons.Film),
    ANIME("Video to anime", "Turn a video into an anime look.", LucideIcons.Sparkles),
    FACE("Face swap", "Put a face you have permission to use into a video.", LucideIcons.Smile),
    STORY("Story to video", "Write a script from an idea, then make it a video with pictures.", LucideIcons.Quote),
    REMOVE("Remove from video", "Draw a box over what to remove; it is filled in across the clip.", LucideIcons.Eraser),
    VOICE("Clone a voice", "Speak a line in a voice cloned from a recording of yours.", LucideIcons.MicVocal),
    DUB("Dub captions", "Speak every caption aloud, in a cloned or the default voice.", LucideIcons.Speech),
}

/** The models behind the tools, each by its Replicate name; change one here when a better one appears. */
private const val MODEL_IMAGE = "black-forest-labs/flux-schnell"
private const val MODEL_EDIT = "black-forest-labs/flux-kontext-pro"
private const val MODEL_VIDEO = "wan-video/wan-2.2-i2v-fast"
private const val MODEL_ANIME = "philz1337x/video-style-transfer"
private const val MODEL_FACE = "ddvinh1/tool-faceswap-video"
private const val MODEL_TEXT = "meta/meta-llama-3-8b-instruct"
private const val MODEL_REMOVE = "jd7h/propainter"
private const val MODEL_VOICE = "resemble-ai/chatterbox-multilingual"

/** The languages offered for a cloned voice (Bengali is not among the model's). */
private val VOICE_LANGS = listOf(
    "bn" to "বাংলা", "en" to "English", "hi" to "हिन्दी", "es" to "Español", "fr" to "Français", "de" to "Deutsch", "it" to "Italiano",
    "pt" to "Português", "ru" to "Русский", "ja" to "日本語", "ko" to "한국어", "zh" to "中文", "ar" to "العربية", "tr" to "Türkçe",
)

/**
 * AI Studio: pictures, videos and scripts made by models on Replicate, with the user's own API key
 * (kept on this phone; each run is billed to their Replicate account, and what is sent leaves the
 * phone). What comes back is put on the end of the timeline.
 */
@Composable
fun AiStudioPanel(vm: EditorViewModel) {
    val context = LocalContext.current
    var key by remember { mutableStateOf(Replicate.key(context)) }
    var tool by remember { mutableStateOf<AiTool?>(null) }
    var writer by remember { mutableStateOf(TextLlm.configured(context)) }
    Dialog(
        onDismissRequest = { if (tool != null) tool = null else vm.panel = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xFF161616)).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { if (tool != null) tool = null else vm.panel = null }, contentAlignment = Alignment.Center) {
                    Glyph(if (tool != null) LucideIcons.ChevronLeft else LucideIcons.X, Color.White, size = 24.dp, description = "Back")
                }
                Text(tool?.title ?: "AI Studio", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp))
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                val t = tool
                if (t == null) {
                    KeySection(key) {
                        Replicate.setKey(context, it)
                        key = it
                    }
                    Spacer(Modifier.height(12.dp))
                    WriterSection { writer = it }
                    Spacer(Modifier.height(18.dp))
                    AiTool.values().forEach { entry ->
                        // the story's script can come from the writing service alone, and pictures from the free service, with no Replicate key
                        val usable = key.isNotBlank() || entry == AiTool.TEXT_IMAGE || entry == AiTool.VOICE || entry == AiTool.DUB || (entry == AiTool.STORY && writer)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF232323))
                                .clickable(enabled = usable) { tool = entry }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Glyph(entry.icon, if (usable) Palette.Accent else Color(0xFF666666), size = 26.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(entry.title, color = if (usable) Color.White else Color(0xFF888888), fontSize = 16.sp)
                                Text(entry.line, color = Color(0xFFA3A3A3), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                } else {
                    AiToolScreen(vm, t)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun KeySection(saved: String, onSave: (String) -> Unit) {
    val context = LocalContext.current
    var typed by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF232323)).padding(14.dp)) {
        Text("Replicate API key", color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "AI Studio runs models on Replicate with your own key. Each run is billed to your Replicate account, and the pictures and videos you use are sent to Replicate. The key stays on this phone.",
            color = Color(0xFFA3A3A3), fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        if (saved.isNotBlank()) {
            Text("Saved (…${saved.takeLast(4)})", color = Palette.Accent, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF2E2E2E)).padding(12.dp)) {
            if (typed.isEmpty()) Text(if (saved.isBlank()) "Paste your key (r8_…)" else "Paste a new key to replace it", color = Color(0xFF888888), fontSize = 14.sp)
            BasicTextField(
                typed, { typed = it.trim() }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Palette.Accent),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("Save", typed.isNotBlank()) {
                onSave(typed)
                typed = ""
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "Get a key",
                color = Palette.Accent, fontSize = 14.sp,
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://replicate.com/account/api-tokens"))) }
                }.padding(8.dp),
            )
        }
    }
}

/** The writing service: any OpenAI-style address, key and model, for the story's script. */
@Composable
private fun WriterSection(onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf(TextLlm.url(context)) }
    var wkey by remember { mutableStateOf(TextLlm.key(context)) }
    var model by remember { mutableStateOf(TextLlm.model(context)) }
    var saved by remember { mutableStateOf(TextLlm.configured(context)) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF232323)).padding(14.dp)) {
        Text("Writing service (optional, can be free)", color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "For story scripts. Any OpenAI-style service works: your own FreeLLMAPI, Groq, Gemini's compatible address, OpenRouter. " +
                "The address ends in /v1, for example https://api.groq.com/openai/v1. When it is set, scripts do not use Replicate.",
            color = Color(0xFFA3A3A3), fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Field("Address", address, minHeight = 46) { address = it.replace("\n", "") }
        Field("Key (leave empty for a server on your own network)", wkey, minHeight = 46) { wkey = it.replace("\n", "") }
        Field("Model name (FreeLLMAPI: auto)", model, minHeight = 46) { model = it.replace("\n", "") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("Save", address.isNotBlank() && model.isNotBlank()) {
                TextLlm.save(context, address, wkey, model)
                saved = true
                onChange(true)
            }
            if (saved) {
                Spacer(Modifier.width(14.dp))
                Text("Saved", color = Palette.Accent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Pill(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = Color.Black, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (enabled) Palette.Accent else Palette.Accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp, vertical = 10.dp),
    )
}

@Composable
private fun Field(label: String, value: String, minHeight: Int = 90, onChange: (String) -> Unit) {
    Text(label, color = Color(0xFFA3A3A3), fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(minHeight.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2E2E2E)).padding(12.dp)) {
        BasicTextField(
            value, onChange,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Palette.Accent),
            modifier = Modifier.fillMaxSize(),
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Chips(options: List<Pair<String, String>>, chosen: String, onChoose: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        options.forEach { (key, label) ->
            val on = key == chosen
            Text(
                label, color = if (on) Color.Black else Color.White, fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) Palette.Accent else Color(0xFF2E2E2E))
                    .clickable { onChoose(key) }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

/** A frame with a box dragged on it: [rect] is left, top, right, bottom as fractions of the frame. */
@Composable
private fun BoxPicker(frame: android.graphics.Bitmap?, rect: List<Float>?, onRect: (List<Float>) -> Unit) {
    if (frame == null) {
        Text("Select a video clip first, then open this tool.", color = Color(0xFFA3A3A3), fontSize = 13.sp)
        return
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(frame.width.toFloat() / frame.height).onSizeChanged { size = it }
            .pointerInput(Unit) {
                var start = Offset.Zero
                detectDragGestures(onDragStart = { start = it }, onDrag = { change, _ ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val a = change.position
                    onRect(
                        listOf(
                            (minOf(start.x, a.x) / w).coerceIn(0f, 1f), (minOf(start.y, a.y) / h).coerceIn(0f, 1f),
                            (maxOf(start.x, a.x) / w).coerceIn(0f, 1f), (maxOf(start.y, a.y) / h).coerceIn(0f, 1f),
                        ),
                    )
                })
            },
    ) {
        Image(frame.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Canvas(Modifier.fillMaxSize()) {
            rect?.let { r ->
                val topLeft = Offset(r[0] * this.size.width, r[1] * this.size.height)
                val boxSize = Size((r[2] - r[0]) * this.size.width, (r[3] - r[1]) * this.size.height)
                drawRect(Color(0x66FF3B30), topLeft, boxSize)
                drawRect(Color.Red, topLeft, boxSize, style = Stroke(3f))
            }
        }
    }
}

@Composable
private fun PickRow(label: String, picked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, if (picked) Palette.Accent else Color(0xFF555555), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (picked) "✓ $label" else label, color = if (picked) Palette.Accent else Color.White, fontSize = 14.sp)
    }
    Spacer(Modifier.height(12.dp))
}

private fun withPicturesNote(context: android.content.Context) = Replicate.key(context).isBlank()

private val ASPECTS = listOf("1:1" to "Square", "16:9" to "Landscape", "9:16" to "Portrait", "4:5" to "4:5")
private val VIDEO_ASPECTS = listOf("16:9" to "Landscape", "9:16" to "Portrait")
private val EDIT_PRESETS = listOf(
    "Remove an object" to "Remove the ",
    "Extend the background" to "Extend the background outward on all sides, keeping the subject unchanged.",
    "Make it an illustration" to "Turn this photo into a painted illustration.",
    "Improve quality" to "Improve the quality: sharpen, fix the lighting and colours.",
)

private fun defaultPrompt(tool: AiTool) = when (tool) {
    AiTool.ANIME -> "best quality, perfect anime illustration, light"
    AiTool.EXTEND -> "Continue the scene smoothly."
    else -> ""
}

@Composable
private fun AiToolScreen(vm: EditorViewModel, tool: AiTool) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by remember(tool) { mutableStateOf(defaultPrompt(tool)) }
    var image by remember(tool) { mutableStateOf<Uri?>(null) }
    var video by remember(tool) { mutableStateOf<Uri?>(null) }
    var aspect by remember(tool) { mutableStateOf(if (tool == AiTool.TEXT_VIDEO) "16:9" else if (tool == AiTool.STORY) "9:16" else "1:1") }
    var consent by remember(tool) { mutableStateOf(false) }
    var language by remember(tool) { mutableStateOf("English") }
    var scenes by remember(tool) { mutableIntStateOf(6) }
    var script by remember(tool) { mutableStateOf("") }
    var audioRef by remember(tool) { mutableStateOf<Uri?>(null) }
    var voiceLang by remember(tool) { mutableStateOf("en") }
    var box by remember(tool) { mutableStateOf<List<Float>?>(null) }
    var status by remember(tool) { mutableStateOf("") }
    var working by remember(tool) { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) image = it }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) video = it }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) audioRef = it }
    val say: (String) -> Unit = { status = it }

    fun job(block: suspend () -> Unit) {
        if (working) return
        working = true
        status = "Starting…"
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "Something went wrong."
            } finally {
                working = false
            }
        }
    }

    fun placed(file: File, note: String = "Added to the end of your timeline") {
        vm.addToMain(listOf(Uri.fromFile(file)), atEnd = true) { vm.message = note }
        status = "Done: it is on your timeline."
    }

    fun cacheFile(extension: String) = File(context.cacheDir, "ai-${System.nanoTime()}.$extension")

    /** Makes a picture of `text` and returns where it was saved. */
    suspend fun picture(text: String, ratio: String): File {
        // without a Replicate key the free keyless service draws it
        if (Replicate.key(context).isBlank()) {
            val (w, h) = when (ratio) { "16:9" -> 1024 to 576; "9:16" -> 576 to 1024; "4:5" -> 816 to 1024; else -> 1024 to 1024 }
            return FreeImage.make(text, w, h, cacheFile("png"), say)
        }
        val out = Replicate.run(context, MODEL_IMAGE, JSONObject().put("prompt", text).put("aspect_ratio", ratio).put("output_format", "png"), say)
        val url = Replicate.urlsOf(out).firstOrNull() ?: error("No picture came back.")
        return Replicate.download(context, url, cacheFile("png"))
    }

    /** Animates the picture at `source` (an address or a data address) into a clip saved to the cache. */
    suspend fun animate(source: String, text: String, ratio: String?): File {
        val input = JSONObject().put("image", source).put("prompt", text).put("num_frames", 81)
        if (ratio != null) input.put("aspect_ratio", ratio)
        val out = Replicate.run(context, MODEL_VIDEO, input, say)
        val url = Replicate.urlsOf(out).firstOrNull() ?: error("No video came back.")
        status = "Downloading…"
        return Replicate.download(context, url, cacheFile("mp4"))
    }

    suspend fun uploadVideo(uri: Uri): String {
        val file = Replicate.cached(context, uri, "ai-in-${System.nanoTime()}.mp4")
        return try { Replicate.upload(context, file, "video/mp4") } finally { file.delete() }
    }

    suspend fun uploadImage(uri: Uri): String {
        val file = Replicate.cached(context, uri, "ai-in-${System.nanoTime()}.jpg")
        return try { Replicate.upload(context, file, "image/jpeg") } finally { file.delete() }
    }

    /** A voice saying `text`, in the cloned voice when [ref] (an uploaded address) is given; saved to the cache. */
    suspend fun speak(text: String, ref: String?): File {
        // Bengali is read by the phone's own voice: free, no key, no cloning
        if (voiceLang == "bn") {
            status = "Reading in Bengali…"
            return SystemVoice.speak(context, text.take(900), java.util.Locale("bn", "BD"), cacheFile("wav"))
        }
        val input = JSONObject().put("text", text.take(300)).put("language", voiceLang)
        if (ref != null) input.put("reference_audio", ref)
        val out = Replicate.run(context, MODEL_VOICE, input, say)
        val url = Replicate.urlsOf(out).firstOrNull() ?: error("No voice came back.")
        return Replicate.download(context, url, cacheFile("wav"))
    }

    suspend fun uploadRef(uri: Uri): String {
        val type = context.contentResolver.getType(uri) ?: "audio/mpeg"
        val file = Replicate.cached(context, uri, "ai-ref-${System.nanoTime()}")
        return try { Replicate.upload(context, file, type) } finally { file.delete() }
    }

    /** A black picture the video's size with the box in white: what to fill in. */
    suspend fun maskFile(w: Int, h: Int, r: List<Float>): File = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.BLACK)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
        canvas.drawRect(r[0] * w, r[1] * h, r[2] * w, r[3] * h, paint)
        val file = cacheFile("png")
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        file
    }

    fun removeClip() = vm.selectedClip?.takeIf { it.kind == "video" } ?: vm.mainClipAtPlayhead()?.takeIf { it.kind == "video" }

    fun scriptLines() = script.lines().map { it.trim() }.filter { it.isNotEmpty() }

    suspend fun writeScript() {
        val ask = "Write a short video script about: $prompt. Write it in $language. Exactly $scenes lines, one short " +
            "sentence on each line, no numbering, no title, no introduction and no extra text."
        // the writing service the user set (free ones work), else Replicate's model
        val written = if (TextLlm.configured(context)) {
            status = "Writing…"
            TextLlm.ask(context, ask)
        } else {
            Replicate.textOf(Replicate.run(context, MODEL_TEXT, JSONObject().put("prompt", ask).put("max_tokens", 700), say))
        }
        script = written.lines()
            .map { it.trim().replace(Regex("^[-*•\\d.)\\s]+"), "") }
            .filter { it.isNotEmpty() }.take(scenes).joinToString("\n")
        status = if (script.isEmpty()) "No script came back." else "Edit the script if you like, then make the video."
    }

    fun generate() {
        when (tool) {
            AiTool.TEXT_IMAGE -> job { placed(picture(prompt, aspect)) }
            AiTool.EDIT -> job {
                val data = Replicate.imageData(context, image ?: error("Pick a photo."))
                val out = Replicate.run(context, MODEL_EDIT, JSONObject().put("prompt", prompt).put("input_image", data).put("output_format", "png"), say)
                val url = Replicate.urlsOf(out).firstOrNull() ?: error("No picture came back.")
                status = "Downloading…"
                placed(Replicate.download(context, url, cacheFile("png")))
            }
            AiTool.IMAGE_VIDEO -> job { placed(animate(Replicate.imageData(context, image ?: error("Pick a photo.")), prompt, null)) }
            AiTool.TEXT_VIDEO -> job {
                val drawn = picture(prompt, aspect)
                status = "Animating the picture…"
                val data = Replicate.imageData(context, Uri.fromFile(drawn))
                placed(animate(data, prompt, aspect))
            }
            AiTool.EXTEND -> job {
                val clip = vm.selectedClip?.takeIf { it.kind == "video" } ?: vm.mainClipAtPlayhead()?.takeIf { it.kind == "video" }
                    ?: error("Select a video clip first.")
                val at = (clip.sourceStartUs + (clip.durationUs * clip.speed).toLong() - 150_000L).coerceAtLeast(0L)
                val frame = withContext(Threads.commands) { runCatching { still(clip.path, at, 1024u).toBitmap() }.getOrNull() } ?: error("The last frame could not be read.")
                placed(animate(Replicate.bitmapData(frame), prompt, null))
            }
            AiTool.ANIME -> job {
                status = "Uploading the video…"
                val url = uploadVideo(video ?: error("Pick a video."))
                val (w, h) = when (aspect) { "9:16" -> 576 to 1024; "16:9" -> 1024 to 576; else -> 1024 to 1024 }
                val input = JSONObject().put("video", url).put("prompt", prompt)
                    .put("input_width", w).put("input_height", h).put("output_width", w).put("output_height", h)
                val out = Replicate.run(context, MODEL_ANIME, input, say)
                val result = Replicate.urlsOf(out).firstOrNull() ?: error("No video came back.")
                status = "Downloading…"
                placed(Replicate.download(context, result, cacheFile("mp4")))
            }
            AiTool.FACE -> job {
                status = "Uploading…"
                val face = uploadImage(image ?: error("Pick the face."))
                val target = uploadVideo(video ?: error("Pick the video."))
                val out = Replicate.run(context, MODEL_FACE, JSONObject().put("source_image", face).put("target_video", target).put("mode", "single"), say)
                val result = Replicate.urlsOf(out).firstOrNull() ?: error("No video came back.")
                status = "Downloading…"
                placed(Replicate.download(context, result, cacheFile("mp4")))
            }
            AiTool.STORY -> job { writeScript() }
            AiTool.REMOVE -> job {
                val clip = removeClip() ?: error("Select a video clip first.")
                val region = box ?: error("Draw a box over what to remove.")
                val source = File(clip.path)
                if (source.length() > 95L * 1024 * 1024) error("This video is too large to send (over 95 MB). Trim or shrink it first.")
                val w = clip.mediaWidth.toInt().coerceAtLeast(2)
                val h = clip.mediaHeight.toInt().coerceAtLeast(2)
                status = "Uploading the video…"
                val videoUrl = Replicate.upload(context, source, "video/mp4")
                val mask = maskFile(w, h, region)
                val maskUrl = try { Replicate.upload(context, mask, "image/png") } finally { mask.delete() }
                val ratio = minOf(1.0, 720.0 / maxOf(w, h))
                val out = Replicate.run(
                    context, MODEL_REMOVE,
                    JSONObject().put("video", videoUrl).put("mask", maskUrl).put("resize_ratio", ratio).put("mask_dilation", 8),
                    say,
                )
                val urls = Replicate.urlsOf(out)
                val result = urls.firstOrNull { it.substringBefore('?').endsWith(".mp4") } ?: urls.firstOrNull() ?: error("No video came back.")
                status = "Downloading…"
                placed(Replicate.download(context, result, cacheFile("mp4")), "The cleaned video is at the end (the whole file, without sound)")
            }
            AiTool.VOICE -> job {
                val ref = audioRef?.takeIf { voiceLang != "bn" }?.let { status = "Uploading your voice…"; uploadRef(it) }
                val file = speak(prompt, ref)
                status = "Adding it to the timeline…"
                vm.addRecording(file, vm.playhead)
                status = "Done: the voice is on an audio row at the playhead."
            }
            AiTool.DUB -> job {
                val lines = vm.captions.mapNotNull { id -> vm.clip(id)?.let { c -> c.startUs to (vm.clipJson(id)?.optJSONObject("text")?.optString("content").orEmpty()) } }
                    .filter { it.second.isNotBlank() }.sortedBy { it.first }.take(40)
                if (lines.isEmpty()) error("There are no captions to dub. Add captions first.")
                val ref = audioRef?.takeIf { voiceLang != "bn" }?.let { status = "Uploading your voice…"; uploadRef(it) }
                lines.forEachIndexed { i, (at, text) ->
                    status = "Line ${i + 1} of ${lines.size}…"
                    val file = speak(text, ref)
                    vm.addRecording(file, at)
                }
                status = "Done: ${lines.size} lines are on the audio rows, each at its caption."
            }
        }
    }

    // Story to video: the script goes to Enter captions, with a picture made for each line if asked
    fun toCaptions(withPictures: Boolean) {
        val lines = scriptLines()
        if (lines.isEmpty()) return
        if (!withPictures) {
            vm.pendingScript = lines.joinToString("\n")
            vm.pendingPictures = emptyList()
            vm.panel = Panel.EnterCaptions
            return
        }
        job {
            val made = ArrayList<Uri>()
            lines.take(8).forEachIndexed { i, line ->
                status = "Picture ${i + 1} of ${minOf(lines.size, 8)}…"
                made += Uri.fromFile(picture("cinematic illustration: $line", aspect))
            }
            vm.pendingScript = lines.joinToString("\n")
            vm.pendingPictures = made
            vm.panel = Panel.EnterCaptions
        }
    }

    val needsImage = tool == AiTool.EDIT || tool == AiTool.IMAGE_VIDEO || tool == AiTool.FACE
    val needsVideo = tool == AiTool.ANIME || tool == AiTool.FACE
    val ready = !working && when (tool) {
        AiTool.TEXT_IMAGE, AiTool.TEXT_VIDEO, AiTool.STORY -> prompt.isNotBlank()
        AiTool.EDIT, AiTool.IMAGE_VIDEO -> image != null && prompt.isNotBlank()
        AiTool.EXTEND -> true
        AiTool.ANIME -> video != null
        AiTool.FACE -> image != null && video != null && consent
        AiTool.REMOVE -> box != null
        AiTool.VOICE -> prompt.isNotBlank()
        AiTool.DUB -> true
    }

    Text(tool.line, color = Color(0xFFA3A3A3), fontSize = 13.sp)
    Spacer(Modifier.height(14.dp))
    if (tool == AiTool.EDIT) {
        Chips(EDIT_PRESETS.map { it.second to it.first }, "") { prompt = it }
    }
    if (needsImage) PickRow(if (tool == AiTool.FACE) "Pick the face to use" else "Pick a photo", image != null) { pickImage.launch("image/*") }
    if (needsVideo) PickRow("Pick a video", video != null) { pickVideo.launch("video/*") }
    if (tool == AiTool.EXTEND) {
        Text("Uses the last frame of the selected video clip (or the main clip at the playhead).", color = Color(0xFFA3A3A3), fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
    }
    if (tool == AiTool.REMOVE) {
        val clipNow = removeClip()
        @Suppress("UNUSED_VARIABLE") val tick = Thumbs.version.intValue
        val frame = clipNow?.let { Thumbs.still(it.path, it.sourceStartUs, 540) }
        BoxPicker(frame, box) { box = it }
        Spacer(Modifier.height(8.dp))
        Text(
            "Drag over what to remove. The box stays in one place, so it works best for something that does not move much. The whole file is processed and the result has no sound; this can take several minutes and costs more than a picture.",
            color = Color(0xFFA3A3A3), fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
    }
    if (tool == AiTool.VOICE || tool == AiTool.DUB) {
        PickRow(if (tool == AiTool.VOICE) "Pick a recording of the voice to copy (6+ seconds)" else "Pick a recording of the voice to copy (optional)", audioRef != null) { pickAudio.launch("audio/*") }
        Text("Language", color = Color(0xFFA3A3A3), fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Chips(VOICE_LANGS, voiceLang) { voiceLang = it }
        Text(
            (if (voiceLang == "bn") "বাংলা is read by your phone's own voice: free, no key, but it cannot copy a voice. Install the Bengali voice in the phone's Text-to-speech settings if it is missing. "
            else "Other languages use a cloud voice (Replicate key). ") +
                "Only clone a voice you have permission to use. " +
                (if (tool == AiTool.DUB) "Translate the captions first (Captions > Translate) to dub into another language. Each line is one run and is cut at 300 letters; the speech may run longer than its caption." else "Up to 300 letters."),
            color = Color(0xFFA3A3A3), fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
    }
    if (tool != AiTool.FACE && tool != AiTool.REMOVE && tool != AiTool.DUB) {
        Field(
            when (tool) {
                AiTool.STORY -> "What is the story about?"
                AiTool.ANIME -> "Style"
                AiTool.EDIT -> "What should change?"
                AiTool.EXTEND -> "What happens next?"
                AiTool.VOICE -> "What should the voice say?"
                else -> "Describe it"
            },
            prompt,
        ) { prompt = it }
    }
    when (tool) {
        AiTool.TEXT_IMAGE -> Chips(ASPECTS, aspect) { aspect = it }
        AiTool.TEXT_VIDEO -> Chips(VIDEO_ASPECTS, aspect) { aspect = it }
        AiTool.ANIME -> Chips(listOf("1:1" to "Square", "9:16" to "Portrait", "16:9" to "Landscape"), aspect) { aspect = it }
        AiTool.STORY -> {
            Text("Language", color = Color(0xFFA3A3A3), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Chips(listOf("English" to "English", "Bengali" to "বাংলা", "Hindi" to "हिन्दी"), language) { language = it }
            Text("Lines", color = Color(0xFFA3A3A3), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Chips((3..10).map { it.toString() to it.toString() }, scenes.toString()) { scenes = it.toInt() }
            Text("Picture shape", color = Color(0xFFA3A3A3), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Chips(listOf("9:16" to "Portrait", "16:9" to "Landscape", "1:1" to "Square"), aspect) { aspect = it }
        }
        AiTool.FACE -> {
            Row(Modifier.fillMaxWidth().clickable { consent = !consent }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .then(if (consent) Modifier.background(Palette.Accent) else Modifier.border(1.5.dp, Color(0xFFA3A3A3), CircleShape)),
                    contentAlignment = Alignment.Center,
                ) { if (consent) Glyph(LucideIcons.Check, Color.Black, size = 14.dp) }
                Spacer(Modifier.width(10.dp))
                Text(
                    "I have the permission of the person whose face this is, and I will not use the result to mislead, impersonate or harm anyone.",
                    color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        else -> {}
    }
    Pill(
        when {
            working -> "Working…"
            tool == AiTool.STORY -> "Write the script"
            else -> "Generate"
        },
        ready,
    ) { generate() }
    if (status.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(status, color = Color.White, fontSize = 13.sp)
    }
    if (tool == AiTool.STORY && script.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Field("Script, one line for each scene", script, minHeight = 200) { script = it }
        Row {
            Pill("Use as script", !working) { toCaptions(false) }
            Spacer(Modifier.width(10.dp))
            Pill("Add AI pictures", !working) { toCaptions(true) }
        }
    }
    if (tool != AiTool.STORY && tool != AiTool.TEXT_IMAGE) {
        Spacer(Modifier.height(10.dp))
        Text("Each run costs a few cents on your Replicate account.", color = Color(0xFF888888), fontSize = 11.sp)
    }
    if ((tool == AiTool.STORY && withPicturesNote(context)) || (tool == AiTool.TEXT_IMAGE && Replicate.key(context).isBlank())) {
        Spacer(Modifier.height(10.dp))
        Text(
            "No Replicate key: pictures come from a free public service (image.pollinations.ai). Your description is sent to it, it allows about one picture every 15 seconds, and it may stop working.",
            color = Color(0xFF888888), fontSize = 11.sp,
        )
    }
}
