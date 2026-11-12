package com.apex.ai.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.apex.ai.core.ApexLog
import java.util.Locale

/**
 * Dictation through the platform recogniser — the one that ships with Android
 * and costs nothing. Offline where the device supports it, network-backed
 * otherwise. No third-party SDK, no upload of its own.
 */
class VoiceInput(private val context: Context) {

    interface Listener {
        fun onState(listening: Boolean)
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null

    var isListening: Boolean = false
        private set

    fun isAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun start(target: Listener) {
        listener = target
        if (!isAvailable()) {
            target.onError("No speech recogniser is installed on this device")
            return
        }
        releaseRecognizer()
        val engine = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (engine == null) {
            target.onError("Could not start the speech recogniser")
            return
        }
        recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                target.onState(true)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                isListening = false
                target.onState(false)
            }

            override fun onError(error: Int) {
                isListening = false
                target.onState(false)
                target.onError(describe(error))
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                target.onState(false)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isBlank()) target.onError("Nothing was recognised") else target.onFinal(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) target.onPartial(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        runCatching { engine.startListening(intent) }.onFailure {
            ApexLog.e("VoiceInput", "startListening failed", it)
            target.onError(it.message ?: "Could not start listening")
        }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        isListening = false
    }

    fun cancel() {
        runCatching { recognizer?.cancel() }
        isListening = false
    }

    fun destroy() {
        listener = null
        releaseRecognizer()
    }

    private fun releaseRecognizer() {
        val engine = recognizer ?: return
        runCatching {
            engine.cancel()
            engine.destroy()
        }
        recognizer = null
        isListening = false
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Recogniser client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Recognition timed out"
        SpeechRecognizer.ERROR_NO_MATCH -> "Nothing matched — try again"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser is busy"
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Recognition failed (code $error)"
    }
}
