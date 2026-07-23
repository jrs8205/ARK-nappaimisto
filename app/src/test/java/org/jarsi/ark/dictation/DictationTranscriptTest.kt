package org.jarsi.ark.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationTranscriptTest {

    @Test
    fun `deltat kertyvat ja alun valilyonti siivotaan`() {
        val transcript = DictationTranscript()
        assertEquals("Hei", transcript.onDelta(" Hei"))
        assertEquals("Hei maailma", transcript.onDelta(" maailma"))
        assertEquals("Hei maailma", transcript.partial)
    }

    @Test
    fun `valmis teksti tyhjentaa keskeneraisen`() {
        val transcript = DictationTranscript()
        transcript.onDelta(" Hei")
        assertEquals("Hei maailma.", transcript.onCompleted(" Hei maailma. "))
        assertFalse(transcript.hasPartial)
        assertEquals("", transcript.partial)
    }

    @Test
    fun `uusi lausuma alkaa puhtaalta poydalta`() {
        val transcript = DictationTranscript()
        transcript.onDelta(" Eka")
        transcript.onCompleted("Eka.")
        assertEquals("Toka", transcript.onDelta(" Toka"))
    }

    @Test
    fun `alussa ei ole keskeneraista tekstia`() {
        val transcript = DictationTranscript()
        assertFalse(transcript.hasPartial)
    }

    @Test
    fun `pelkka valilyontidelta ei ole keskeneraista tekstia`() {
        val transcript = DictationTranscript()
        transcript.onDelta(" ")
        assertFalse(transcript.hasPartial)
        assertTrue(transcript.onDelta("Moi").isNotEmpty())
    }
}
