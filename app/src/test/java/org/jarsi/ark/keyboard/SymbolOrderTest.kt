package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolOrderTest {

    @Test
    fun `oletusjarjestyksessa on 52 eri merkkia`() {
        assertEquals(52, SymbolOrder.default.size)
        assertEquals(52, SymbolOrder.default.toSet().size)
        assertTrue(SymbolOrder.PAGE1_COUNT < SymbolOrder.default.size)
    }

    @Test
    fun `tallennus ja luku sailyttavat jarjestyksen`() {
        val order = SymbolOrder.default.reversed()
        assertEquals(order, SymbolOrder.load(SymbolOrder.serialize(order)))
    }

    @Test
    fun `puuttuva tai rikkinainen tallennus antaa oletuksen`() {
        assertEquals(SymbolOrder.default, SymbolOrder.load(null))
        assertEquals(SymbolOrder.default, SymbolOrder.load("rikki"))
    }

    @Test
    fun `tuntematon merkki poistuu ja puuttuva palaa listan loppuun`() {
        val muokattu = SymbolOrder.default.toMutableList().apply {
            remove("€")
            add(0, "☃")
        }
        val loaded = SymbolOrder.load(SymbolOrder.serialize(muokattu))
        assertEquals(SymbolOrder.default.toSet(), loaded.toSet())
        assertEquals("€", loaded.last())
        assertFalse(loaded.contains("☃"))
    }

    @Test
    fun `kaksoiskappale siivotaan`() {
        val tupla = listOf("@", "@") + SymbolOrder.default.filter { it != "@" }
        val loaded = SymbolOrder.load(SymbolOrder.serialize(tupla))
        assertEquals(52, loaded.size)
        assertEquals(SymbolOrder.default.toSet(), loaded.toSet())
    }

    @Test
    fun `symbolisivut rakentuvat jarjestyksesta ja lisamerkit seuraavat`() {
        val layout1 = Layouts.symbols1(SymbolOrder.default)
        assertEquals("@", layout1.rows[1].first().label)
        val euro = layout1.rows[1].first { it.label == "€" }
        assertEquals(listOf("$", "£", "¥"), euro.longPress)
        val layout2 = Layouts.symbols2(SymbolOrder.default)
        assertEquals("¹", layout2.rows[0].first().label)
        assertEquals("™", layout2.rows[2][7].label)
    }

    @Test
    fun `vaara mittainen jarjestys korvautuu oletuksella`() {
        val layout = Layouts.symbols1(listOf("@", "#"))
        assertEquals("@", layout.rows[1].first().label)
        assertEquals(10, layout.rows[1].size)
    }
}
