package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslatePrepTest {

    @Test
    fun `iso alkukirjain ja loppupiste`() {
        val prep = TranslatePrep.prepare("moi kaikille")
        assertEquals("Moi kaikille.", prep.text)
        assertEquals(true, prep.addedStop)
    }

    @Test
    fun `lauseiden alut kapitalisoidaan`() =
        assertEquals(
            "Moi. Mitä kuuluu.",
            TranslatePrep.prepare("moi. mitä kuuluu").text,
        )

    @Test
    fun `valmis valimerkki sailyy eika pistetta lisata`() {
        val prep = TranslatePrep.prepare("mitä kuuluu?")
        assertEquals("Mitä kuuluu?", prep.text)
        assertEquals(false, prep.addedStop)
    }

    @Test
    fun `osoitteen sisainen piste ei aloita lausetta`() =
        assertEquals(
            "Käy jarsi.org.",
            TranslatePrep.prepare("käy jarsi.org").text,
        )

    @Test
    fun `tyhjatila siivotaan`() =
        assertEquals("Moi kaikki.", TranslatePrep.prepare("  moi   kaikki ").text)

    @Test
    fun `numero lopussa saa pisteen`() {
        val prep = TranslatePrep.prepare("jako 20")
        assertEquals("Jako 20.", prep.text)
        assertEquals(true, prep.addedStop)
    }

    @Test
    fun `pilkkuun paattyva ei saa pistetta`() {
        val prep = TranslatePrep.prepare("moi,")
        assertEquals("Moi,", prep.text)
        assertEquals(false, prep.addedStop)
    }

    @Test
    fun `aakkonen kapitalisoituu`() =
        assertEquals("Äiti tulee.", TranslatePrep.prepare("äiti tulee").text)

    @Test
    fun `tyhja pysyy tyhjana`() {
        val prep = TranslatePrep.prepare("   ")
        assertEquals("", prep.text)
        assertEquals(false, prep.addedStop)
    }

    @Test
    fun `clean poistaa lisatyn loppupisteen`() {
        val prep = TranslatePrep.prepare("moi kaikille")
        assertEquals("Hi everyone", TranslatePrep.clean("Hi everyone.", prep))
    }

    @Test
    fun `clean ei koske kayttajan omaan valimerkkiin`() {
        val prep = TranslatePrep.prepare("mitä kuuluu?")
        assertEquals("How are you?", TranslatePrep.clean("How are you?", prep))
    }

    @Test
    fun `clean ei poista muuta kuin pisteen`() {
        val prep = TranslatePrep.prepare("moi kaikille")
        assertEquals("Hi everyone!", TranslatePrep.clean("Hi everyone!", prep))
    }
}
