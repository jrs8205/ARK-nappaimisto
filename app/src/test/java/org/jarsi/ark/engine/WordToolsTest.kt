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
}
