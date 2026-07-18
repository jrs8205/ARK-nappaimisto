package org.jarsi.ark.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningEngineTest {

    private var now = 1_000_000_000_000L

    private fun engine() = LearningEngine { now }.apply { load(emptyList(), emptyList()) }

    @Test
    fun `sana opitaan ja ehdotetaan heti`() {
        val e = engine()
        e.onWordCommitted("prx4")
        assertEquals(listOf("prx4"), e.suggest("pr"))
        assertTrue(e.isOwnWord("prx4"))
    }

    @Test
    fun `asu sailyy ja tasmays on kirjainkoosta riippumaton`() {
        val e = engine()
        e.onWordCommitted("Jako")
        assertEquals(listOf("Jako"), e.suggest("ja"))
        e.onWordCommitted("jako")
        assertEquals(listOf("Jako"), e.suggest("JA"))
    }

    @Test
    fun `pelkka numero ei mene sanastoon mutta ketjuun kylla`() {
        val e = engine()
        e.onWordCommitted("Jako")
        e.onWordCommitted("20")
        e.onWordCommitted("nouto")
        assertTrue(e.suggest("2").isEmpty())
        val dirty = e.drainDirty()
        assertTrue(dirty.bigrams.any { it.previous == "jako" && it.next == "20" })
        assertTrue(dirty.bigrams.any { it.previous == "20" && it.next == "nouto" })
    }

    @Test
    fun `resetContext katkaisee ketjun`() {
        val e = engine()
        e.onWordCommitted("eka")
        e.resetContext()
        e.onWordCommitted("toka")
        assertTrue(e.drainDirty().bigrams.none { it.previous == "eka" })
    }

    @Test
    fun `tuoreus painottaa`() {
        val e = engine()
        e.onWordCommitted("vanha")
        e.onWordCommitted("vanha")
        e.onWordCommitted("vanha")
        now += 40L * 24 * 60 * 60 * 1000
        e.onWordCommitted("vasta")
        e.onWordCommitted("vasta")
        assertEquals(listOf("vasta", "vanha"), e.suggest("va"))
    }

    @Test
    fun `estetty ei nay ja esto toimii vieraalle sanalle`() {
        val e = engine()
        e.onWordCommitted("moro")
        e.blockWord("moro")
        e.blockWord("auto")
        assertTrue(e.suggest("mo").isEmpty())
        assertTrue(e.isBlocked("moro"))
        assertTrue(e.isBlocked("Auto"))
    }

    @Test
    fun `poisto vie sanan ja kirjaa poiston`() {
        val e = engine()
        e.onWordCommitted("typo")
        e.drainDirty()
        e.removeWord("typo")
        assertTrue(e.suggest("ty").isEmpty())
        assertEquals(listOf("typo"), e.drainDirty().removedWords)
    }

    @Test
    fun `ehdotuksen valinta kasvattaa vain omaa sanaa mutta ketjuttaa aina`() {
        val e = engine()
        e.onWordCommitted("prx4")
        e.onSuggestionAccepted("auto")
        assertFalse(e.isOwnWord("auto"))
        assertTrue(e.drainDirty().bigrams.any { it.previous == "prx4" && it.next == "auto" })
    }

    @Test
    fun `kelpoisuus hylkaa liian lyhyet ja kirjaimettomat`() {
        val e = engine()
        e.onWordCommitted("a")
        e.onWordCommitted("20")
        assertTrue(e.suggest("a").isEmpty())
        assertTrue(e.suggest("2").isEmpty())
    }

    @Test
    fun `lataamaton moottori ei opi eika ehdota`() {
        val e = LearningEngine { now }
        e.onWordCommitted("sana")
        assertTrue(e.suggest("sa").isEmpty())
        assertEquals(0, e.dirtyCount)
    }
}
