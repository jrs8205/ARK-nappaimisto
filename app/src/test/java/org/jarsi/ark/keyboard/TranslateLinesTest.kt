package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslateLinesTest {

    @Test
    fun `yksirivinen teksti kulkee lapi sellaisenaan`() {
        val lines = TranslateLines.split("hei maailma")
        assertEquals(listOf("hei maailma"), TranslateLines.translatable(lines))
        assertEquals("hello world", TranslateLines.merge(lines, listOf("hello world")))
    }

    @Test
    fun `rivinvaihdot sailyvat kaannoksessa`() {
        val lines = TranslateLines.split("eka rivi\ntoka rivi")
        assertEquals(listOf("eka rivi", "toka rivi"), TranslateLines.translatable(lines))
        assertEquals(
            "first line\nsecond line",
            TranslateLines.merge(lines, listOf("first line", "second line")),
        )
    }

    @Test
    fun `tyhjat rivit sailyvat paikoillaan`() {
        val lines = TranslateLines.split("eka\n\ntoka")
        assertEquals(listOf("eka", "toka"), TranslateLines.translatable(lines))
        assertEquals("one\n\ntwo", TranslateLines.merge(lines, listOf("one", "two")))
    }

    @Test
    fun `valilyontirivi sailyy sellaisenaan`() {
        val lines = TranslateLines.split("eka\n  \ntoka")
        assertEquals(listOf("eka", "toka"), TranslateLines.translatable(lines))
        assertEquals("one\n  \ntwo", TranslateLines.merge(lines, listOf("one", "two")))
    }

    @Test
    fun `puuttuva kaannos jattaa alkuperaisen rivin`() {
        val lines = TranslateLines.split("eka\ntoka")
        assertEquals("one\ntoka", TranslateLines.merge(lines, listOf("one")))
    }
}
