package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineChipsTest {

    @Test
    fun `chipit korvaavat ehdotusrivin kun niita on`() {
        val state = InlineChips.rowState(suggestionsEnabled = true, chipCount = 2)
        assertTrue(state.chipsVisible)
        assertFalse(state.suggestionsVisible)
    }

    @Test
    fun `ilman chippeja ehdotusrivi seuraa omaa tilaansa`() {
        assertTrue(InlineChips.rowState(suggestionsEnabled = true, chipCount = 0).suggestionsVisible)
        assertFalse(InlineChips.rowState(suggestionsEnabled = false, chipCount = 0).suggestionsVisible)
        assertFalse(InlineChips.rowState(suggestionsEnabled = false, chipCount = 0).chipsVisible)
    }

    @Test
    fun `salasanakentassa chipit nakyvat vaikka ehdotukset ovat pois`() {
        val state = InlineChips.rowState(suggestionsEnabled = false, chipCount = 1)
        assertTrue(state.chipsVisible)
        assertFalse(state.suggestionsVisible)
    }

    @Test
    fun `chipin korkeus on rivi ilman reunatayteita ja leveys 48 dp asti nayton leveyteen`() {
        val density = 2.625f
        val bounds = InlineChips.specBounds(barHeightPx = 150, screenWidthPx = 1080, density = density)
        assertEquals(150 - 2 * Math.round(InlineChips.CHIP_PADDING_DP * density), bounds.height)
        assertEquals(Math.round(InlineChips.MIN_WIDTH_DP * density), bounds.minWidth)
        assertEquals(1080, bounds.maxWidth)
        assertTrue(bounds.minWidth <= bounds.maxWidth)
    }

    @Test
    fun `chipin mitat otetaan alustan antamista arvoista`() {
        // InlineSuggestion.inflate antaa näkymälle alustan mittaaman koon
        // LayoutParams-oliona; WRAP_CONTENT-leveys mittautuisi nollaksi.
        val size = InlineChips.chipSize(givenWidth = 723, givenHeight = 148, rowChipHeight = 148)
        assertEquals(723, size.width)
        assertEquals(148, size.height)
    }

    @Test
    fun `ilman alustan mittoja kaytetaan rivin chipkorkeutta ja sisallon leveytta`() {
        val size = InlineChips.chipSize(givenWidth = -2, givenHeight = -2, rowChipHeight = 148)
        assertEquals(InlineChips.WRAP_CONTENT, size.width)
        assertEquals(148, size.height)
    }

    @Test
    fun `kapea naytto ei kaanna minimia ja maksimia`() {
        val bounds = InlineChips.specBounds(barHeightPx = 10, screenWidthPx = 50, density = 3f)
        assertTrue(bounds.minWidth <= bounds.maxWidth)
        assertTrue(bounds.height >= 1)
    }
}
