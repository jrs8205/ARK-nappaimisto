package org.jarsi.ark.dictation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.jarsi.ark.R

/**
 * Jatkuva sanelu Androidin vakiotunnistimella. Tunnistin käynnistetään
 * jokaisen valmistuneen tuloksen ja tyhjän jakson jälkeen uudelleen, joten
 * miettimistauko ei katkaise sanelua; vasta [SILENCE_BUDGET_MS] ylittävä
 * yhtäjaksoinen hiljaisuus (tai [stop]) lopettaa. Kutsut päälangalta.
 */
class DictationController(
    private val context: Context,
    private val listener: Listener,
) {

    interface Listener {
        fun onPartialText(text: String)
        fun onFinalText(text: String)
        fun onDictationStateChanged(active: Boolean)
        fun onDictationError(messageResId: Int)

        /** Puheen voimakkuus 0–1 mikrofonin sykettä varten. */
        fun onSpeechLevel(level: Float)
    }

    var isActive = false
        private set

    private var recognizer: SpeechRecognizer? = null
    private var useOnDevice = false
    private var lastSpeechTime = 0L
    private var retried = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (!isActive) return
            listener.onSpeechLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = bestResult(partialResults) ?: return
            if (text.isNotBlank()) {
                lastSpeechTime = SystemClock.elapsedRealtime()
                listener.onPartialText(text)
            }
        }

        override fun onResults(results: Bundle?) {
            if (!isActive) return
            val text = bestResult(results)
            if (!text.isNullOrBlank()) {
                lastSpeechTime = SystemClock.elapsedRealtime()
                listener.onFinalText(text)
            }
            restartListening()
        }

        override fun onError(error: Int) {
            if (!isActive) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Tyhjä jakso: jatketaan kunnes hiljaisuusbudjetti täyttyy.
                    if (SystemClock.elapsedRealtime() - lastSpeechTime > SILENCE_BUDGET_MS) {
                        stop()
                    } else {
                        restartListening()
                    }
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stop()
                    listener.onDictationError(R.string.sanelu_virhe)
                }
                else -> {
                    // Laitetunnistin voi kaatua esim. kielitukeen: pudotaan
                    // kerran tavalliseen tunnistimeen ennen luovuttamista.
                    if (useOnDevice) {
                        useOnDevice = false
                        recreateRecognizer()
                        restartListening()
                    } else if (!retried) {
                        retried = true
                        restartListening()
                    } else {
                        stop()
                        listener.onDictationError(R.string.sanelu_virhe)
                    }
                }
            }
        }
    }

    fun start() {
        if (isActive) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onDictationError(R.string.sanelu_ei_saatavilla)
            return
        }
        isActive = true
        retried = false
        useOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        lastSpeechTime = SystemClock.elapsedRealtime()
        recreateRecognizer()
        listener.onDictationStateChanged(true)
        restartListening()
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        recognizer?.destroy()
        recognizer = null
        listener.onDictationStateChanged(false)
    }

    private fun recreateRecognizer() {
        recognizer?.destroy()
        recognizer = if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.also { it.setRecognitionListener(recognitionListener) }
    }

    private fun restartListening() {
        if (!isActive) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fi-FI")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }

    private fun bestResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private companion object {
        const val SILENCE_BUDGET_MS = 15_000L
    }
}
