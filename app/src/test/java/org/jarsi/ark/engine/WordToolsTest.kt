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
        assertEquals("", WordTools.currentWord("sana."))

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
    fun `numero katkaisee sanan`() =
        assertEquals("", WordTools.currentWord("v123"))
}
