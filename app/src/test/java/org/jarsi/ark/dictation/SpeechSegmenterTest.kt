package org.jarsi.ark.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSegmenterTest {

    private val rate = 16_000

    /** Palat syötetään 100 ms erissä kuten äänitysluupissa. */
    private fun chunk(amplitude: Float): FloatArray =
        FloatArray(rate / 10) { amplitude }

    private fun feedMs(
        segmenter: SpeechSegmenter,
        amplitude: Float,
        ms: Int,
        collect: MutableList<SpeechSegmenter.Event>,
    ) {
        repeat(ms / 100) {
            segmenter.feed(chunk(amplitude))?.let { collect += it }
        }
    }

    private fun events(
        vararg steps: Pair<Float, Int>,
        sessionSilenceMs: Long = 5_000,
    ): List<SpeechSegmenter.Event> {
        val segmenter = SpeechSegmenter(rate, sessionSilenceMs)
        val collected = mutableListOf<SpeechSegmenter.Event>()
        steps.forEach { (amplitude, ms) ->
            feedMs(segmenter, amplitude, ms, collected)
        }
        return collected
    }

    @Test
    fun `puhe ja hiljaisuus tuottaa segmentin`() {
        val result = events(
            0.001f to 500, // kalibrointi ja taustaa
            0.2f to 2_000, // puhetta
            0.001f to 1_000, // hiljaisuus päättää segmentin
        )
        assertEquals(1, result.filterIsInstance<SpeechSegmenter.Event.Segment>().size)
        val segment = result.filterIsInstance<SpeechSegmenter.Event.Segment>().first()
        // Vähintään puheen verran näytteitä, korkeintaan koko syöte.
        assertTrue(segment.samples.size >= 2 * rate)
        assertTrue(segment.samples.size <= 4 * rate)
    }

    @Test
    fun `pitka puhe katkeaa enimmaispituuteen`() {
        val result = events(
            0.001f to 500,
            0.2f to 12_000, // puhetta yli 10 s rajan
        )
        assertTrue(result.filterIsInstance<SpeechSegmenter.Event.Segment>().isNotEmpty())
    }

    @Test
    fun `pelkka hiljaisuus paattaa istunnon`() {
        val result = events(
            0.001f to 6_000,
            sessionSilenceMs = 5_000,
        )
        assertEquals(1, result.filterIsInstance<SpeechSegmenter.Event.SessionTimeout>().size)
    }

    @Test
    fun `puhe nollaa istunnon hiljaisuuslaskurin`() {
        val result = events(
            0.001f to 500,
            0.2f to 1_000,
            0.001f to 3_000, // alle 5 s rajan
            0.2f to 1_000,
            0.001f to 6_000, // nyt raja ylittyy
            sessionSilenceMs = 5_000,
        )
        assertEquals(2, result.filterIsInstance<SpeechSegmenter.Event.Segment>().size)
        assertEquals(1, result.filterIsInstance<SpeechSegmenter.Event.SessionTimeout>().size)
        assertTrue(result.last() is SpeechSegmenter.Event.SessionTimeout)
    }

    @Test
    fun `lyhyt piikki ei tuota segmenttia`() {
        val result = events(
            0.001f to 500,
            0.2f to 100, // vain 100 ms "puhetta" — kolahdus tms.
            0.001f to 2_000,
        )
        assertTrue(result.filterIsInstance<SpeechSegmenter.Event.Segment>().isEmpty())
    }

    @Test
    fun `aikakatkaisu tulee vain kerran`() {
        val segmenter = SpeechSegmenter(rate, 2_000)
        val collected = mutableListOf<SpeechSegmenter.Event>()
        feedMs(segmenter, 0.001f, 10_000, collected)
        assertEquals(1, collected.filterIsInstance<SpeechSegmenter.Event.SessionTimeout>().size)
    }

    @Test
    fun `taso seuraa puhetta`() {
        val segmenter = SpeechSegmenter(rate, 5_000)
        segmenter.feed(chunk(0.001f))
        val quiet = segmenter.currentLevel
        repeat(5) { segmenter.feed(chunk(0.5f)) }
        assertTrue(segmenter.currentLevel > quiet)
        assertTrue(segmenter.currentLevel <= 1f)
    }

    @Test
    fun `tyhja syote ei kaada`() {
        val segmenter = SpeechSegmenter(rate, 5_000)
        assertNull(segmenter.feed(FloatArray(0)))
    }
}
