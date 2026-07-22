package org.jarsi.ark.dictation

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.jarsi.ark.R
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Sanelu omalla Whisper-mallilla kokonaan laitteella. Mikrofoni pysyy
 * auki koko istunnon eikä kuuntelu katkea melussa: puhe pätkitään
 * [SpeechSegmenter]illä ja pätkät tunnistetaan taustalla, joten teksti
 * ilmestyy pieninä erinä puheen perässä. Istunto päättyy vain omaan
 * hiljaisuusrajaan tai pysäytykseen. Malli ladataan muistiin
 * tunnistusjonon kärjessä nauhoituksen jo käydessä, ja se pidetään
 * hetki latauskustannuksen takia lämpimänä peräkkäisiä saneluja varten.
 * Kutsut päälangalta; jakaa [DictationController.Listener]-rajapinnan.
 */
class WhisperDictation(
    private val context: Context,
    private val listener: DictationController.Listener,
) {

    var isActive = false
        private set

    /** Hiljaisuus, jonka jälkeen sanelu päättyy itsestään. */
    var silenceLimitMs = 5_000L

    /** Käytettävä malli; asetetaan ennen [start]-kutsua. */
    var model: WhisperModel = WhisperModel.BASE

    private val mainHandler = Handler(Looper.getMainLooper())

    // Mallin elinkaari ja tunnistus elävät samassa säikeessä, joten
    // lataus, käyttö ja vapautus eivät voi ajautua päällekkäin.
    private val transcribeExecutor = Executors.newSingleThreadExecutor()
    private var engine: WhisperEngine? = null
    private var engineModel: WhisperModel? = null

    private var captureThread: Thread? = null

    @Volatile
    private var stopRequested = false
    private var session = 0
    private var promptContext = ""

    private val releaseEngine = Runnable {
        transcribeExecutor.execute {
            engine?.close()
            engine = null
            engineModel = null
        }
    }

    fun start() {
        if (isActive) return
        isActive = true
        stopRequested = false
        promptContext = ""
        mainHandler.removeCallbacks(releaseEngine)
        listener.onDictationStateChanged(true)
        val mySession = ++session
        val wanted = model
        transcribeExecutor.execute { ensureEngine(wanted) }
        captureThread = Thread({ runCapture(mySession) }, "whisper-sanelu")
            .also { it.start() }
    }

    /**
     * Pysäyttää kuuntelun; jonossa olevat pätkät tunnistetaan loppuun
     * ja niiden teksti viedään vielä kenttään.
     */
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
        mainHandler.removeCallbacks(releaseEngine)
        mainHandler.postDelayed(releaseEngine, ENGINE_KEEP_MS)
    }

    fun destroy() {
        stopRequested = true
        session++
        mainHandler.removeCallbacks(releaseEngine)
        transcribeExecutor.execute {
            engine?.close()
            engine = null
            engineModel = null
        }
        transcribeExecutor.shutdown()
    }

    private fun ensureEngine(wanted: WhisperModel) {
        if (engineModel == wanted && engine != null) return
        engine?.close()
        // Epäonnistuminen ei saa jäädä hiljaiseksi: käyttäjä saa virheen
        // ja sanelu pysähtyy sen sijaan, että mikrofoni kuuntelisi tyhjää.
        engine = try {
            WhisperEngine.load(WhisperModels.file(context, wanted).absolutePath)
        } catch (e: Throwable) {
            null
        }
        engineModel = if (engine != null) wanted else null
        debugLog("malli ${wanted.prefValue}: ${if (engine != null) "ladattu" else "EPÄONNISTUI"}")
        if (engine == null) {
            mainHandler.post {
                listener.onDictationError(R.string.whisper_malli_virhe)
            }
            stopRequested = true
        }
    }

    // RECORD_AUDIO tarkistetaan kutsupolulla ennen sanelun käynnistystä.
    @android.annotation.SuppressLint("MissingPermission")
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
            var logCountdown = 0
            while (!stopRequested && mySession == session) {
                val read = record.read(shorts, 0, shorts.size)
                if (read <= 0) continue
                val floats = FloatArray(read) { shorts[it] / 32768f }
                val event = segmenter.feed(floats)
                if (debuggable && --logCountdown <= 0) {
                    logCountdown = 10
                    debugLog("taso=%.4f".format(segmenter.currentLevel))
                }
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
        debugLog("segmentti: ${samples.size / (SAMPLE_RATE / 1000)} ms")
        runOnTranscribe {
            if (mySession != session) return@runOnTranscribe
            val text = engine?.transcribe(
                samples, promptContext.takeLast(PROMPT_CHARS), THREADS
            ).orEmpty()
            debugLog("tulos: ${text.length} merkkiä")
            if (text.isBlank()) return@runOnTranscribe
            promptContext = "$promptContext $text".takeLast(PROMPT_KEEP_CHARS)
            // Teksti viedään, vaikka istunto olisi jo päättynyt: puhe
            // sanottiin sen aikana, ja hitaalla laitteella tunnistus voi
            // valmistua vasta hiljaisuusrajan jälkeen.
            mainHandler.post {
                if (mySession == session) listener.onFinalText(text)
            }
        }
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
                // Malli pidetään hetki muistissa: heti perään alkava uusi
                // sanelu käynnistyy ilman latausviivettä.
                mainHandler.removeCallbacks(releaseEngine)
                mainHandler.postDelayed(releaseEngine, ENGINE_KEEP_MS)
            }
        }
    }

    private fun runOnTranscribe(task: Runnable) {
        try {
            transcribeExecutor.execute(task)
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // Palvelu on tuhottu ja jono suljettu; siivous on jo tehty.
        }
    }

    // Kehityslokit vianetsintään; julkaisuversiossa hiljaa.
    private val debuggable =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun debugLog(message: String) {
        if (debuggable) Log.d("ArkWhisper", message)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val ENGINE_KEEP_MS = 60_000L
        const val PROMPT_CHARS = 200
        const val PROMPT_KEEP_CHARS = 500
        val THREADS = min(4, Runtime.getRuntime().availableProcessors())
    }
}
