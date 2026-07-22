package org.jarsi.ark.dictation

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pätkii äänivirran puhesegmenteiksi Whisper-tunnistusta varten.
 * Taustataso kalibroidaan alusta ja se sopeutuu hiljaisiin paloihin,
 * joten kynnys toimii myös melussa. Segmentti alkaa esipuskurilla
 * (puheen alku ei leikkaudu), päättyy lyhyeen hiljaisuuteen tai
 * enimmäispituuteen, ja koko istunto päättyy vasta käyttäjän
 * valitseman hiljaisuusrajan täytyttyä — kuuntelu ei siis katkea
 * itsestään kesken puheen kuten järjestelmätunnistimilla.
 *
 * Aika lasketaan näytemääristä, joten luokka on deterministinen.
 */
class SpeechSegmenter(
    private val sampleRate: Int,
    private val sessionSilenceMs: Long,
) {

    sealed interface Event {
        /** Valmis puhepätkä tunnistettavaksi (16 kHz mono, -1..1). */
        class Segment(val samples: FloatArray) : Event

        /** Hiljaisuusraja täyttyi: sanelu päättyy. */
        object SessionTimeout : Event
    }

    /** Viimeisimmän palan voimakkuus 0–1 mikrofonin sykettä varten. */
    var currentLevel = 0f
        private set

    private var background = 0f
    private var calibrationSamples = 0
    private var speechActive = false
    private var finished = false

    private val segment = ArrayList<FloatArray>()
    private var segmentSamples = 0
    private var speechSamplesInSegment = 0
    private var silenceSamplesInSegment = 0
    private var silenceSamplesSinceSpeech = 0L

    // Esipuskuri: puheen alkua edeltävät palat otetaan segmenttiin
    // mukaan, ettei ensimmäinen tavu leikkaudu pois.
    private val preRoll = ArrayDeque<FloatArray>()
    private var preRollSamples = 0

    private fun msToSamples(ms: Long): Long = ms * sampleRate / 1000

    fun feed(chunk: FloatArray): Event? {
        if (finished || chunk.isEmpty()) return null

        var sum = 0.0
        for (sample in chunk) sum += sample * sample
        val rms = sqrt(sum / chunk.size).toFloat()
        currentLevel = min(1f, rms / LEVEL_FULL_SCALE)

        // Taustataso: kalibrointi ottaa alun hiljaisimman palan (puhe voi
        // olla käynnissä heti), ja jatkossa taso laskee nopeasti mutta
        // nousee vain hitaasti — oma puhe ei saa vetää kynnystä ylös.
        if (calibrationSamples < msToSamples(CALIBRATION_MS)) {
            background = if (calibrationSamples == 0) rms else min(background, rms)
            calibrationSamples += chunk.size
        }
        // Kynnyksellä on katto: tätä kovempi ääni on aina puhetta, vaikka
        // kalibrointi olisi osunut puheen päälle.
        val threshold = max(
            min(background * THRESHOLD_FACTOR, THRESHOLD_CAP),
            MIN_THRESHOLD,
        )
        val speech = rms >= threshold
        if (!speech) {
            background = if (rms < background) {
                background * FAST_DROP + rms * (1f - FAST_DROP)
            } else {
                background * SLOW_RISE + rms * (1f - SLOW_RISE)
            }
        }

        if (speech) {
            silenceSamplesSinceSpeech = 0
            if (!speechActive) {
                speechActive = true
                segment.addAll(preRoll)
                segmentSamples += preRollSamples
                preRoll.clear()
                preRollSamples = 0
            }
            segment += chunk
            segmentSamples += chunk.size
            speechSamplesInSegment += chunk.size
            silenceSamplesInSegment = 0
            if (segmentSamples >= msToSamples(MAX_SEGMENT_MS)) {
                return finishSegment()
            }
            return null
        }

        silenceSamplesSinceSpeech += chunk.size
        if (speechActive) {
            segment += chunk
            segmentSamples += chunk.size
            silenceSamplesInSegment += chunk.size
            if (silenceSamplesInSegment >= msToSamples(SEGMENT_SILENCE_MS)) {
                return finishSegment() ?: timeoutIfDue()
            }
        } else {
            preRoll += chunk
            preRollSamples += chunk.size
            while (preRollSamples > msToSamples(PRE_ROLL_MS)) {
                preRollSamples -= preRoll.removeFirst().size
            }
        }
        return timeoutIfDue()
    }

    private fun timeoutIfDue(): Event? {
        if (speechActive) return null
        if (silenceSamplesSinceSpeech >= msToSamples(sessionSilenceMs)) {
            finished = true
            return Event.SessionTimeout
        }
        return null
    }

    /** Kokoaa segmentin; alle vähimmäispituuden jäänyt puhe hylätään. */
    private fun finishSegment(): Event.Segment? {
        val hadSpeech = speechSamplesInSegment >= msToSamples(MIN_SPEECH_MS)
        val samples = if (hadSpeech) {
            val merged = FloatArray(segmentSamples)
            var offset = 0
            for (part in segment) {
                part.copyInto(merged, offset)
                offset += part.size
            }
            merged
        } else {
            null
        }
        segment.clear()
        segmentSamples = 0
        speechSamplesInSegment = 0
        silenceSamplesInSegment = 0
        speechActive = false
        return samples?.let { Event.Segment(it) }
    }

    private companion object {
        const val CALIBRATION_MS = 300L
        const val PRE_ROLL_MS = 300L
        const val SEGMENT_SILENCE_MS = 700L
        const val MAX_SEGMENT_MS = 10_000L
        const val MIN_SPEECH_MS = 300L
        const val THRESHOLD_FACTOR = 2.5f
        const val MIN_THRESHOLD = 0.006f
        const val THRESHOLD_CAP = 0.04f
        const val FAST_DROP = 0.7f
        const val SLOW_RISE = 0.99f
        const val LEVEL_FULL_SCALE = 0.25f
    }
}
