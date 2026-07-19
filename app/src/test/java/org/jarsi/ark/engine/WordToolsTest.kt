package org.jarsi.ark.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WordToolsTest {

    @Test
    fun `poimii viimeisen sanan`() =
        assertEquals("hei", WordTools.currentWord("moi hei"))

    @Test
    fun `valilyonnin jalkeen tyhja`() =
        assertEquals("", WordTools.currentWord("moi hei "))

    @Test
    fun `valimerkin jalkeen tyhja`() =
        assertEquals("", WordTools.currentWord("sana!"))

    @Test
    fun `yhdysmerkki kuuluu sanaan`() =
        assertEquals("linja-auto", WordTools.currentWord("iso linja-auto"))

    @Test
    fun `alun yhdysmerkki siivotaan`() =
        assertEquals("abc", WordTools.currentWord("x -abc"))

    @Test
    fun `aakkoset toimivat`() =
        assertEquals("päivää", WordTools.currentWord("hyvää päivää"))

    @Test
    fun `tyhja teksti`() =
        assertEquals("", WordTools.currentWord(""))

    @Test
    fun `numerot kuuluvat sanaan`() =
        assertEquals("prx4", WordTools.currentWord("koodi prx4"))

    @Test
    fun `pelkka numero on sana`() =
        assertEquals("20", WordTools.currentWord("Jako 20"))

    @Test
    fun `sisainen piste kuuluu sanaan`() =
        assertEquals("jarsi.org", WordTools.currentWord("käy jarsi.org"))

    @Test
    fun `keskeneraisen osoitteen piste`() =
        assertEquals("jarsi.o", WordTools.currentWord("jarsi.o"))

    @Test
    fun `loppupiste ei kuulu sanaan`() =
        assertEquals("", WordTools.currentWord("sana."))

    @Test
    fun `piste ilman jatkoa ei kuulu sanaan`() =
        assertEquals("", WordTools.currentWord("jarsi."))

    @Test
    fun `edellinen sana rivinvaihdon yli`() =
        assertEquals(listOf("prx4"), WordTools.previousWords("prx4\n"))

    @Test
    fun `kaksi edellista sanaa`() =
        assertEquals(listOf("Jako", "20"), WordTools.previousWords("Jako 20 "))

    @Test
    fun `keskeneraista sanaa ei lasketa edeltaviin`() =
        assertEquals(listOf("Jako", "20"), WordTools.previousWords("prx4\nJako 20 nouto"))

    @Test
    fun `lauseen loppupiste ohitetaan`() =
        assertEquals(listOf("sana"), WordTools.previousWords("sana. ", 1))

    @Test
    fun `tyhja teksti antaa tyhjat edeltavat`() =
        assertEquals(emptyList<String>(), WordTools.previousWords(""))

    @Test
    fun `count rajaa edeltavien maaran`() =
        assertEquals(listOf("c"), WordTools.previousWords("a b c ", 1))

    @Test
    fun `words pilkkoo tekstin sanoiksi`() =
        assertEquals(listOf("hei", "maailma"), WordTools.words("hei maailma"))

    @Test
    fun `words sailyttaa sisaiset pisteet ja siivoaa reunat`() =
        assertEquals(listOf("käy", "jarsi.org"), WordTools.words("käy jarsi.org."))

    @Test
    fun `words tyhjasta tyhja`() =
        assertEquals(emptyList<String>(), WordTools.words("  "))

    @Test
    fun `words pitaa numerot ja koodit`() =
        assertEquals(listOf("prx4", "Jako", "20"), WordTools.words("prx4, Jako 20!"))

    @Test
    fun `withTypedWord lisaa puuttuvan sanan karkeen`() =
        assertEquals(
            listOf("chrome", "chromea", "chromen"),
            WordTools.withTypedWord("chrome", listOf("chromea", "chromen")),
        )

    @Test
    fun `withTypedWord ei tuplaa listalla olevaa sanaa`() =
        assertEquals(
            listOf("auto", "autolla"),
            WordTools.withTypedWord("auto", listOf("auto", "autolla")),
        )

    @Test
    fun `withTypedWord tyhja sana ei muuta listaa`() =
        assertEquals(listOf("moi"), WordTools.withTypedWord("", listOf("moi")))

    @Test
    fun `withTypedWord kirjainkoko erottaa asut`() =
        assertEquals(
            listOf("CHROME", "Chrome"),
            WordTools.withTypedWord("CHROME", listOf("Chrome")),
        )

    @Test
    fun `editDistance tunnistaa lisayksen poiston ja vaihdon`() {
        assertEquals(0, WordTools.editDistanceAtMost("koira", "koira", 2))
        assertEquals(1, WordTools.editDistanceAtMost("koira", "koiraa", 2))
        assertEquals(1, WordTools.editDistanceAtMost("koira", "kira", 2))
        assertEquals(1, WordTools.editDistanceAtMost("koira", "koirq", 2))
    }

    @Test
    fun `editDistance laskee viereisen vaihdoksen yhdeksi`() =
        assertEquals(1, WordTools.editDistanceAtMost("koira", "kiora", 2))

    @Test
    fun `editDistance katkeaa rajaan`() {
        assertEquals(3, WordTools.editDistanceAtMost("koira", "talo", 2))
        assertEquals(1, WordTools.editDistanceAtMost("ab", "ba", 1))
    }

    @Test
    fun `continuationAfter poimii sanan jatkon kursorista`() {
        assertEquals("ira", WordTools.continuationAfter("ira jatkuu"))
        assertEquals("", WordTools.continuationAfter(" heti"))
        assertEquals("ra", WordTools.continuationAfter("ra. Sitten"))
        assertEquals(".org", WordTools.continuationAfter(".org käy"))
        assertEquals("", WordTools.continuationAfter(""))
        assertEquals("ra-auto", WordTools.continuationAfter("ra-auto x"))
    }
}
