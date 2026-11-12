package app.margin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Keeps listening across natural pauses until stop() is called.
 * All calls must happen on the main thread.
 */
class VoiceCapture(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onSegment: (String) -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition isn't available on this device.")
            onDone()
            return
        }
        active = true
        listen()
    }

    fun stop() {
        active = false
        recognizer?.stopListening()
    }

    fun release() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        r.startListening(intent)
    }

    private fun relisten() {
        handler.postDelayed({ if (active) listen() }, 150)
    }

    private fun firstText(bundle: Bundle?): String {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull() ?: ""
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = firstText(partialResults)
            if (t.isNotEmpty()) onPartial(t)
        }

        override fun onResults(results: Bundle?) {
            val t = firstText(results)
            if (t.isNotEmpty()) onSegment(t)
            if (active) relisten() else onDone()
        }

        override fun onError(error: Int) {
            val quiet = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (active && quiet) {
                relisten()
                return
            }
            val wasActive = active
            active = false
            if (wasActive && error != SpeechRecognizer.ERROR_CLIENT) {
                this@VoiceCapture.onError("Couldn't keep listening (code " + error + ").")
            }
            onDone()
        }
    }
}
