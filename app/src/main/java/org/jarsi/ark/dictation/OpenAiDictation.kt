package org.jarsi.ark.dictation

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.jarsi.ark.R
import org.jarsi.ark.data.ApiKeyStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.max

/**
 * Sanelu OpenAI:n puheentunnistuksella käyttäjän omalla API-avaimella.
 * Mikrofoni pysyy auki koko istunnon eikä kuuntelu katkea melussa: puhe
 * pätkitään [SpeechSegmenter]illä ja pätkät tunnistetaan verkossa
 * järjestyksessä, joten teksti ilmestyy lausepätkinä pienellä viiveellä.
 * Istunto päättyy vain omaan hiljaisuusrajaan tai pysäytykseen; sulussa
 * jonossa olevat pätkät tunnistetaan loppuun. Kutsut päälangalta; jakaa
 * [DictationController.Listener]-rajapinnan.
 */
class OpenAiDictation(
    private val prefs: SharedPreferences,
    private val listener: DictationController.Listener,
) {

    var isActive = false
        private set

    /** Hiljaisuus, jonka jälkeen sanelu päättyy itsestään. */
    var silenceLimitMs = 5_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    // Yksi tunnistusjono pitää pätkien tekstit oikeassa järjestyksessä.
    private val transcribeExecutor = Executors.newSingleThreadExecutor()
    private var captureThread: Thread? = null

    @Volatile
    private var stopRequested = false
    private var session = 0
    private var promptContext = ""

    fun start() {
        if (isActive) return
        isActive = true
        stopRequested = false
        promptContext = ""
        listener.onDictationStateChanged(true)
        val mySession = ++session
        captureThread = Thread({ runCapture(mySession) }, "openai-sanelu")
            .also { it.start() }
    }

    /** Pysäyttää kuuntelun; jonossa olevat pätkät tunnistetaan loppuun. */
    fun stop() {
        if (!isActive) return
        stopRequested = true
    }

    /**
     * Keskeyttää heti ja hylkää jonossa olevat tulokset — käytetään kun
     * kenttä vaihtuu eikä vanha puhe saa valua uuteen kenttään.
     */
    fun cancel() {
        stopRequested = true
        session++
        if (isActive) {
            isActive = false
            listener.onDictationStateChanged(false)
        }
    }

    fun destroy() {
        cancel()
        transcribeExecutor.shutdown()
    }

    // RECORD_AUDIO tarkistetaan kutsupolulla ennen sanelun käynnistystä.
    @SuppressLint("MissingPermission")
    private fun runCapture(mySession: Int) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuffer, SAMPLE_RATE * 2),
            )
        } catch (e: Exception) {
            null
        }
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            mainHandler.post { listener.onDictationError(R.string.sanelu_virhe) }
            finishSession(mySession)
            return
        }
        try {
            record.startRecording()
            val segmenter = SpeechSegmenter(SAMPLE_RATE, silenceLimitMs)
            val shorts = ShortArray(SAMPLE_RATE / 10)
            while (!stopRequested && mySession == session) {
                val read = record.read(shorts, 0, shorts.size)
                if (read <= 0) continue
                val floats = FloatArray(read) { shorts[it] / 32768f }
                val event = segmenter.feed(floats)
                mainHandler.post {
                    if (mySession == session && isActive) {
                        listener.onSpeechLevel(segmenter.currentLevel)
                    }
                }
                when (event) {
                    is SpeechSegmenter.Event.Segment ->
                        queueTranscription(event.samples, mySession)
                    is SpeechSegmenter.Event.SessionTimeout -> break
                    null -> Unit
                }
            }
        } catch (e: SecurityException) {
            mainHandler.post { listener.onDictationError(R.string.sanelu_virhe) }
        } finally {
            try {
                record.stop()
            } catch (e: IllegalStateException) {
                // Nauhoitus ei ehtinyt käyntiin; vapautus riittää.
            }
            record.release()
        }
        finishSession(mySession)
    }

    private fun queueTranscription(samples: FloatArray, mySession: Int) {
        runOnTranscribe {
            if (mySession != session) return@runOnTranscribe
            val apiKey = ApiKeyStore.read(prefs, ApiKeyStore.Slot.OPENAI).orEmpty()
            if (apiKey.isEmpty()) return@runOnTranscribe
            val text = try {
                transcribe(DictationWav.encode(samples), apiKey)
            } catch (e: Exception) {
                // Yksittäisen pätkän virhe ei kaada istuntoa, mutta syy
                // näytetään, ettei sanelu vaikuta mykältä.
                mainHandler.post {
                    if (mySession == session) {
                        listener.onDictationErrorMessage(e.message.orEmpty())
                    }
                }
                return@runOnTranscribe
            }
            if (text.isBlank()) return@runOnTranscribe
            promptContext = "$promptContext $text".takeLast(PROMPT_KEEP_CHARS)
            // Teksti viedään, vaikka istunto olisi jo päättynyt: puhe
            // sanottiin sen aikana ja tunnistus vain valmistui myöhemmin.
            mainHandler.post {
                if (mySession == session) listener.onFinalText(text.trim())
            }
        }
    }

    /** Lähettää pätkän OpenAI:n tunnistukseen ja palauttaa tekstin. */
    private fun transcribe(wav: ByteArray, apiKey: String): String {
        val boundary = "ark-sanelu-${System.nanoTime()}"
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty(
                "Content-Type", "multipart/form-data; boundary=$boundary"
            )
            connection.outputStream.use { output ->
                fun field(name: String, value: String) {
                    output.write(
                        (
                            "--$boundary\r\n" +
                                "Content-Disposition: form-data; name=\"$name\"\r\n\r\n" +
                                "$value\r\n"
                            ).toByteArray()
                    )
                }
                field(
                    "model",
                    prefs.getString(PREF_MODEL, null)?.takeIf { it.isNotBlank() }
                        ?: DEFAULT_MODEL,
                )
                field("language", "fi")
                if (promptContext.isNotBlank()) {
                    field("prompt", promptContext.takeLast(PROMPT_CHARS))
                }
                output.write(
                    (
                        "--$boundary\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; " +
                            "filename=\"puhe.wav\"\r\n" +
                            "Content-Type: audio/wav\r\n\r\n"
                        ).toByteArray()
                )
                output.write(wav)
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw IllegalStateException(shortError(code, error))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body).optString("text")
        } finally {
            connection.disconnect()
        }
    }

    /** Tiivistää virhevastauksen ilmoitukseen sopivaksi syyksi. */
    private fun shortError(code: Int, body: String?): String {
        val message = body?.let {
            try {
                JSONObject(it).optJSONObject("error")?.optString("message")
            } catch (e: Exception) {
                null
            }
        }
        return if (message.isNullOrBlank()) "HTTP $code" else message.take(120)
    }

    private fun finishSession(mySession: Int) {
        // Sulku ajetaan tunnistusjonon hännästä: jonossa olevat pätkät
        // valmistuvat ensin, joten viimeinenkin lause ehtii kenttään.
        runOnTranscribe {
            mainHandler.post {
                if (mySession != session) return@post
                if (isActive) {
                    isActive = false
                    listener.onDictationStateChanged(false)
                }
            }
        }
    }

    private fun runOnTranscribe(task: Runnable) {
        try {
            transcribeExecutor.execute(task)
        } catch (e: RejectedExecutionException) {
            // Palvelu on tuhottu ja jono suljettu; siivous on jo tehty.
        }
    }

    companion object {
        /**
         * Oletuksena tarkin eräpohjainen tunnistusmalli. Puhemallit ovat
         * oma perheensä eivätkä seuraa Paranna teksti -mallivalintaa;
         * valinnan lista haetaan livenä [TextImprover.parseTranscribeModels].
         */
        const val DEFAULT_MODEL = "gpt-4o-transcribe"
        const val PREF_MODEL = "sanelu_malli"

        private const val SAMPLE_RATE = 16_000
        private const val ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
        private const val PROMPT_CHARS = 200
        private const val PROMPT_KEEP_CHARS = 500
    }
}
