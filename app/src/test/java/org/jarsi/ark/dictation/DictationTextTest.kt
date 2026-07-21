package org.jarsi.ark.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationTextTest {

    @Test
    fun `capitalize nostaa ensimmaisen kirjaimen isoksi`() {
        assertEquals("Moikka vaan", DictationText.capitalize("moikka vaan"))
        assertEquals("Äiti tuli", DictationText.capitalize("äiti tuli"))
    }

    @Test
    fun `capitalize sailyttaa jo ison alun`() {
        assertEquals("Moikka", DictationText.capitalize("Moikka"))
    }

    @Test
    fun `capitalize ei koske numeroalkuista tekstia`() {
        assertEquals("5 euroa", DictationText.capitalize("5 euroa"))
    }

    @Test
    fun `capitalize tyhjalla ja pelkalla valilla`() {
        assertEquals("", DictationText.capitalize(""))
        assertEquals(" ", DictationText.capitalize(" "))
    }

    @Test
    fun `endsSentence tunnistaa lauseen lopun`() {
        assertTrue(DictationText.endsSentence("valmista tuli."))
        assertTrue(DictationText.endsSentence("mitä kuuluu? "))
        assertTrue(DictationText.endsSentence("hienoa!"))
        assertTrue(DictationText.endsSentence("no niin…"))
    }

    @Test
    fun `endsSentence kesken jaava teksti`() {
        assertFalse(DictationText.endsSentence("mitä kuuluu"))
        assertFalse(DictationText.endsSentence("sana,"))
        assertFalse(DictationText.endsSentence(""))
    }
}
