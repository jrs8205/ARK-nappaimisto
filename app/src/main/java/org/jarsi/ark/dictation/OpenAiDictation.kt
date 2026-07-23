package org.jarsi.ark.dictation

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.jarsi.ark.R
import org.jarsi.ark.data.ApiKeyStore
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Sanelu OpenAI:n realtime-puheentunnistuksella käyttäjän omalla
 * API-avaimella. Kaikki mikrofonin ääni virtaa WebSocketilla palvelulle
 * jatkuvana — laitteella ei pätkitä mitään, joten puhetta ei katoa
 * pätkärajoille eikä kuuntelu katkea melussa. Suoratoistomallilla
 * (gpt-realtime-*) teksti ilmestyy deltoina puheen tahdissa ja lopullinen
 * transkriptio valmistuu lopetuksen commitista; eräpohjaisilla malleilla
 * palvelimen VAD jakaa virran lausumiin ja tekstit valmistuvat pitkin
 * matkaa. Istunto päättyy vain omaan hiljaisuusrajaan tai pysäytykseen.
 * Kutsut päälangalta; jakaa [DictationController.Listener]-rajapinnan.
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

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private var webSocket: WebSocket? = null
    private var captureThread: Thread? = null
    private var transcript = DictationTranscript()

    @Volatile
    private var stopRequested = false
    private var session = 0
    private var finishing = false
    private var streamingModel = true

    @Volatile
    private var socketOpen = false

    @Volatile
    private var sentSamples = 0L

    // Mikrofoni käy heti käynnistyksestä: yhteyden avautumista odottavat
    // palat puskuroidaan, ettei puheen alku katoa kättelyn aikana.
    private val backlog = ArrayDeque<ByteArray>()

    private val finishRunnable = Runnable { finishNow(session) }

    fun start() {
        if (isActive) return
        val apiKey = ApiKeyStore.read(prefs, ApiKeyStore.Slot.OPENAI).orEmpty()
        if (apiKey.isEmpty()) {
            listener.onDictationError(R.string.sanelu_virhe)
            return
        }
        isActive = true
        stopRequested = false
        finishing = false
        socketOpen = false
        sentSamples = 0
        transcript = DictationTranscript()
        synchronized(backlog) { backlog.clear() }
        val model = prefs.getString(PREF_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MODEL
        streamingModel = model.startsWith("gpt-realtime")
        listener.onDictationStateChanged(true)
        val mySession = ++session
        openSocket(apiKey, model, mySession)
        captureThread = Thread({ runCapture(mySession) }, "openai-sanelu")
            .also { it.start() }
    }

    /** Pysäyttää kuuntelun; kesken olevat tunnistukset valmistuvat loppuun. */
    fun stop() {
        if (!isActive) return
        stopRequested = true
        // Jos mikrofoni kaatui jo, lopetus ei jää sen säikeen varaan.
        if (captureThread?.isAlive != true) beginFinish(session)
    }

    /**
     * Keskeyttää heti ja hylkää kesken olevat tulokset — käytetään kun
     * kenttä vaihtuu eikä vanha puhe saa valua uuteen kenttään.
     */
    fun cancel() {
        stopRequested = true
        session++
        mainHandler.removeCallbacks(finishRunnable)
        webSocket?.cancel()
        webSocket = null
        socketOpen = false
        if (isActive) {
            isActive = false
            listener.onDictationStateChanged(false)
        }
    }

    fun destroy() {
        cancel()
        httpClient.dispatcher.executorService.shutdown()
    }

    private fun openSocket(apiKey: String, model: String, mySession: Int) {
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .build()
        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    ws.send(RealtimeEvents.sessionUpdate(model, "fi"))
                    synchronized(backlog) {
                        socketOpen = true
                        while (backlog.isNotEmpty()) {
                            ws.send(RealtimeEvents.appendAudio(backlog.removeFirst()))
                        }
                    }
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    val event = RealtimeEvents.parse(text) ?: return
                    mainHandler.post { handleEvent(event, mySession) }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    mainHandler.post {
                        if (mySession != session) return@post
                        listener.onDictationErrorMessage(
                            t.message ?: t.javaClass.simpleName
                        )
                        // Kertyneet deltat pelastetaan kenttään finishNow'ssa.
                        stopRequested = true
                        finishNow(mySession)
                    }
                }
            },
        )
    }

    private fun handleEvent(event: RealtimeEvents.Incoming, mySession: Int) {
        if (mySession != session) return
        when (event) {
            is RealtimeEvents.Incoming.Delta -> {
                val shown = transcript.onDelta(event.text)
                if (shown.isNotEmpty() && isActive) listener.onPartialText(shown)
            }
            is RealtimeEvents.Incoming.Completed -> {
                val text = transcript.onCompleted(event.text)
                if (text.isNotEmpty()) listener.onFinalText(text)
                if (finishing) {
                    // Lopetus odottaa vain hetken lisää mahdollisia
                    // jälkitulevia lausumia, sitten istunto suljetaan.
                    mainHandler.removeCallbacks(finishRunnable)
                    mainHandler.postDelayed(finishRunnable, FINISH_EXTRA_MS)
                }
            }
            is RealtimeEvents.Incoming.Failure ->
                listener.onDictationErrorMessage(event.message)
        }
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
            mainHandler.post {
                if (mySession == session) {
                    listener.onDictationError(R.string.sanelu_virhe)
                    beginFinish(mySession)
                }
            }
            return
        }
        try {
            record.startRecording()
            val segmenter = SpeechSegmenter(SAMPLE_RATE, silenceLimitMs)
            val shorts = ShortArray(SAMPLE_RATE / 10)
            while (!stopRequested && mySession == session) {
                val read = record.read(shorts, 0, shorts.size)
                if (read <= 0) continue
                sendAudio(RealtimeEvents.pcmBytes(shorts, read))
                sentSamples += read
                val floats = FloatArray(read) { shorts[it] / 32768f }
                // Segmentterin pätkintää ei käytetä — se toimii vain
                // hiljaisuusvahtina ja mikrofonisykkeen mittarina.
                val event = segmenter.feed(floats)
                mainHandler.post {
                    if (mySession == session && isActive) {
                        listener.onSpeechLevel(segmenter.currentLevel)
                    }
                }
                if (event is SpeechSegmenter.Event.SessionTimeout) break
            }
        } catch (e: SecurityException) {
            mainHandler.post {
                if (mySession == session) listener.onDictationError(R.string.sanelu_virhe)
            }
        } finally {
            try {
                record.stop()
            } catch (e: IllegalStateException) {
                // Nauhoitus ei ehtinyt käyntiin; vapautus riittää.
            }
            record.release()
        }
        mainHandler.post { beginFinish(mySession) }
    }

    private fun sendAudio(pcm: ByteArray) {
        synchronized(backlog) {
            if (socketOpen) {
                webSocket?.send(RealtimeEvents.appendAudio(pcm))
            } else {
                backlog.addLast(pcm)
                if (backlog.size > BACKLOG_MAX_CHUNKS) backlog.removeFirst()
            }
        }
    }

    /**
     * Mikrofoni on pysäytetty: viimeistellään kesken oleva tunnistus.
     * Suoratoistomalli saa commitin, eräpohjaisille syötetään hetki
     * hiljaisuutta, jotta palvelimen VAD sulkee viimeisen lausuman.
     */
    private fun beginFinish(mySession: Int) {
        if (mySession != session || finishing) return
        finishing = true
        val ws = webSocket
        if (ws == null || !socketOpen || sentSamples < MIN_COMMIT_SAMPLES) {
            finishNow(mySession)
            return
        }
        if (streamingModel) {
            ws.send(RealtimeEvents.commit())
        } else {
            val silence = ByteArray(SAMPLE_RATE / 5 * 2)
            repeat(5) { ws.send(RealtimeEvents.appendAudio(silence)) }
        }
        mainHandler.postDelayed(finishRunnable, FINISH_WAIT_MS)
    }

    /** Sulkee istunnon; toimittamatta jääneet deltat pelastetaan kenttään. */
    private fun finishNow(mySession: Int) {
        if (mySession != session) return
        mainHandler.removeCallbacks(finishRunnable)
        if (transcript.hasPartial) {
            listener.onFinalText(transcript.onCompleted(transcript.partial))
        }
        webSocket?.close(1000, null)
        webSocket = null
        socketOpen = false
        session++
        if (isActive) {
            isActive = false
            listener.onDictationStateChanged(false)
        }
    }

    companion object {
        /**
         * Oletuksena suoratoistomalli: teksti ilmestyy puheen tahdissa.
         * Puhemallit ovat oma perheensä eivätkä seuraa Paranna teksti
         * -mallivalintaa; lista haetaan livenä
         * [TextImprover.parseTranscribeModels].
         */
        const val DEFAULT_MODEL = "gpt-realtime-whisper"
        const val PREF_MODEL = "sanelu_malli"

        private const val SAMPLE_RATE = RealtimeEvents.SAMPLE_RATE
        private const val ENDPOINT =
            "wss://api.openai.com/v1/realtime?intent=transcription"

        /** Lopetuksen odotus lopulliselle tekstille. */
        private const val FINISH_WAIT_MS = 4_000L

        /** Lisäodotus, jos lausumia valmistuu vielä lopetuksen aikana. */
        private const val FINISH_EXTRA_MS = 1_200L

        /** Alle 0,2 s ääntä ei commitoida (palvelu hylkäisi tyhjän). */
        private const val MIN_COMMIT_SAMPLES = SAMPLE_RATE / 5L

        /** Puskuri yhteyden avausta odottaville palasille (~30 s). */
        private const val BACKLOG_MAX_CHUNKS = 300
    }
}
