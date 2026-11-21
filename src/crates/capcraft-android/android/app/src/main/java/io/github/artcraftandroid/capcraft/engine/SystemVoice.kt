package io.github.artcraftandroid.capcraft.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * The phone's own text to speech (Google's or the maker's engine) written to a sound file: free, on
 * the phone, no key, and no cloning. It reads any language the phone has a voice for, which for
 * Bengali usually means installing the voice in Settings > Languages > Text-to-speech.
 */
object SystemVoice {
    /** Saves `text` read in `locale` to `dest` (a .wav). Throws with a message fit to show. */
    suspend fun speak(context: Context, text: String, locale: Locale, dest: File): File {
        val engine = initEngine(context)
        try {
            when (engine.isLanguageAvailable(locale)) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED ->
                    throw IllegalStateException(
                        "This phone has no ${locale.displayLanguage} voice. Install it in Settings > System > Languages > Text-to-speech output, then try again.",
                    )
            }
            engine.language = locale
            val id = UUID.randomUUID().toString()
            val done = withTimeout(120_000) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(true) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(false) }
                    })
                    val queued = engine.synthesizeToFile(text, Bundle(), dest, id)
                    if (queued != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
                }
            }
            if (!done || !dest.isFile || dest.length() < 100) throw IllegalStateException("The phone's voice could not read that.")
            return dest
        } finally {
            engine.shutdown()
        }
    }

    private suspend fun initEngine(context: Context): TextToSpeech = withTimeout(20_000) {
        suspendCancellableCoroutine { cont ->
            var made: TextToSpeech? = null
            made = TextToSpeech(context.applicationContext) { status ->
                if (!cont.isActive) { made?.shutdown(); return@TextToSpeech }
                if (status == TextToSpeech.SUCCESS) cont.resume(made!!) else { made?.shutdown(); cont.resumeWith(Result.failure(IllegalStateException("The phone's text to speech did not start."))) }
            }
            cont.invokeOnCancellation { made?.shutdown() }
        }
    }
}
