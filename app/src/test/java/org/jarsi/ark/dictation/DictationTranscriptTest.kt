package org.jarsi.ark.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationTranscriptTest {

    @Test
    fun `deltat kertyvat ja alun valilyonti siivotaan`() {
        val transcript = DictationTranscript()
        assertEquals("Hei", transcript.onDelta("a", " Hei"))
        assertEquals("Hei maailma", transcript.onDelta("a", " maailma"))
        assertEquals("Hei maailma", transcript.partial)
    }

    @Test
    fun `valmis teksti tyhjentaa keskeneraisen`() {
        val transcript = DictationTranscript()
        transcript.onDelta("a", " Hei")
        assertEquals("Hei maailma.", transcript.onCompleted("a", " Hei maailma. "))
        assertFalse(transcript.hasPartial)
        assertEquals("", transcript.partial)
    }

    @Test
    fun `uusi lausuma alkaa puhtaalta poydalta`() {
        val transcript = DictationTranscript()
        transcript.onDelta("a", " Eka")
        transcript.onCompleted("a", "Eka.")
        assertEquals("Toka", transcript.onDelta("b", " Toka"))
    }

    @Test
    fun `alussa ei ole keskeneraista tekstia`() {
        assertFalse(DictationTranscript().hasPartial)
    }

    @Test
    fun `pelkka valilyontidelta ei ole keskeneraista tekstia`() {
        val transcript = DictationTranscript()
        transcript.onDelta("a", " ")
        assertFalse(transcript.hasPartial)
        assertTrue(transcript.onDelta("a", "Moi").isNotEmpty())
    }

    @Test
    fun `lausuman valmistuminen ei havita toisen keskeneraista`() {
        val transcript = DictationTranscript()
        transcript.onDelta("a", "Eka lause.")
        transcript.onDelta("b", " Toka")
        assertEquals("Eka lause.", transcript.onCompleted("a", "Eka lause."))
        assertEquals("Toka", transcript.partial)
    }

    @Test
    fun `myohempi lausuma odottaa aiemman valmistumista`() {
        val transcript = DictationTranscript()
        transcript.onDelta("a", "Eka")
        transcript.onDelta("b", " Toka")
        assertEquals("", transcript.onCompleted("b", "Toka."))
        assertEquals("Eka. Toka.", transcript.onCompleted("a", "Eka."))
        assertFalse(transcript.hasPartial)
    }

    @Test
    fun `keskeneraiset nakyvat lausumajarjestyksessa`() {
        val transcript = DictationTranscript()
        transcript.onDelta("a", "Eka")
        transcript.onDelta("b", "Toka")
        transcript.onDelta("a", " jatkuu")
        assertEquals("Eka jatkuu Toka", transcript.partial)
    }

    @Test
    fun `flush palauttaa loput jarjestyksessa ja tyhjentaa`() {
        val transcript = DictationTranscript()
        transcript.onDelta("a", "Eka")
        transcript.onDelta("b", "Toka")
        transcript.onCompleted("b", "Toka.")
        assertEquals("Eka Toka.", transcript.flush())
        assertFalse(transcript.hasPartial)
    }

    @Test
    fun `myohainen delta valmistuneelle lausumalle ohitetaan`() {
        val transcript = DictationTranscript()
        transcript.onDelta("a", "Eka")
        transcript.onCompleted("a", "Eka.")
        assertEquals("", transcript.onDelta("a", " haamu"))
        assertFalse(transcript.hasPartial)
    }

    @Test
    fun `tyhja transkriptio ei tuota tekstia mutta vapauttaa jonon`() {
        val transcript = DictationTranscript()
        transcript.onDelta("a", " ")
        transcript.onDelta("b", "Toka")
        assertEquals("", transcript.onCompleted("a", ""))
        assertEquals("Toka.", transcript.onCompleted("b", "Toka."))
    }
}
