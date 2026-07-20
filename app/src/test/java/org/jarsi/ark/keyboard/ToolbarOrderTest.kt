package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarOrderTest {

    @Test
    fun `oletuksessa kaikki napit nakyvissa ja asetukset viimeisena`() {
        val config = ToolbarOrder.load(null)
        assertEquals(ToolbarTool.entries.toList(), config.order)
        assertTrue(config.hidden.isEmpty())
        assertEquals(ToolbarTool.SETTINGS, config.visible.last())
    }

    @Test
    fun `tallennus ja luku sailyttavat jarjestyksen ja piilotukset`() {
        val order = ToolbarOrder.default.reversed()
        val hidden = setOf(ToolbarTool.MIC, ToolbarTool.EMOJI)
        val config = ToolbarOrder.load(ToolbarOrder.serialize(order, hidden))
        assertEquals(order, config.order)
        assertEquals(hidden, config.hidden)
    }

    @Test
    fun `rikkinainen tallennus antaa oletuksen`() {
        val config = ToolbarOrder.load("rikki")
        assertEquals(ToolbarOrder.default, config.order)
        assertTrue(config.hidden.isEmpty())
    }

    @Test
    fun `tuntematon tunniste poistuu ja puuttuva palaa loppuun`() {
        val partial = listOf(ToolbarTool.SETTINGS, ToolbarTool.MIC)
        val saved = ToolbarOrder.serialize(partial, emptySet())
            .replace("mikrofoni", "tuntematon")
        val config = ToolbarOrder.load(saved)
        assertEquals(ToolbarTool.SETTINGS, config.order.first())
        assertEquals(ToolbarTool.entries.toSet(), config.order.toSet())
        assertFalse(config.hidden.contains(ToolbarTool.MIC))
    }

    @Test
    fun `asetusnappia ei voi piilottaa`() {
        val saved = ToolbarOrder.serialize(ToolbarOrder.default, setOf(ToolbarTool.SETTINGS))
        val config = ToolbarOrder.load(saved)
        assertTrue(config.hidden.isEmpty())
        assertTrue(ToolbarTool.SETTINGS in config.visible)
    }

    @Test
    fun `nakyvat suodattavat piilotetut jarjestyksessa`() {
        val hidden = setOf(ToolbarTool.MIC, ToolbarTool.TRANSLATE)
        val config = ToolbarOrder.load(ToolbarOrder.serialize(ToolbarOrder.default, hidden))
        assertEquals(ToolbarOrder.default.filter { it !in hidden }, config.visible)
    }

    @Test
    fun `kaksoiskappale siivotaan`() {
        val doubled = listOf(ToolbarTool.MIC) + ToolbarOrder.default
        val config = ToolbarOrder.load(ToolbarOrder.serialize(doubled, emptySet()))
        assertEquals(ToolbarTool.entries.size, config.order.size)
        assertEquals(ToolbarTool.MIC, config.order.first())
    }

    @Test
    fun `tunnisteet ovat yksilollisia`() {
        val ids = ToolbarTool.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ToolbarTool.MIC, ToolbarTool.byId("mikrofoni"))
        assertEquals(null, ToolbarTool.byId("tuntematon"))
    }
}
