package com.apex.ai.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import com.apex.ai.core.ApexLog
import java.util.Locale

/**
 * On-device text to speech via the platform engine (Google TTS / eSpeak NG on
 * most ROMs). No network, no paid API. Speaks the tail of a reply with natural
 * pauses because sentence punctuation is passed through untouched.
 */
class SpeechEngine(context: Context) {

    private var tts: TextToSpeech? = null
    private var initialised = false
    private var pending: String? = null

    val isReady: Boolean get() = initialised

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            initialised = status == TextToSpeech.SUCCESS
            if (initialised) {
                configureLanguage()
                pending?.let { speak(it) }
                pending = null
            } else {
                ApexLog.w("Speech", "TTS engine unavailable on this device")
            }
        }
    }

    private fun configureLanguage() {
        val engine = tts ?: return
        runCatching {
            val locale = Locale.getDefault()
            val availability = engine.isLanguageAvailable(locale)
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = locale
            } else {
                engine.language = Locale.US
            }
            engine.setSpeechRate(1.02f)
            engine.setPitch(1.0f)
        }.onFailure { ApexLog.w("Speech", "language setup failed: ${it.message}") }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!initialised) {
            pending = clean
            return
        }
        val engine = tts ?: return
        configureLanguage()
        engine.stop()
        engine.speak(clean.take(3000), TextToSpeech.QUEUE_FLUSH, null, "apex-${System.currentTimeMillis()}")
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        pending = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        initialised = false
    }
}
