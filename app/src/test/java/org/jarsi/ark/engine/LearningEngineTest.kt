package org.jarsi.ark.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningEngineTest {

    private var now = 1_000_000_000_000L

    private fun engine() =
        LearningEngine { now }.apply { load(emptyList(), emptyList(), emptyList()) }

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

    @Test
    fun `trigram voittaa bigramin`() {
        val e = engine()
        listOf("alfa", "beeta", "cee").forEach { e.onWordCommitted(it) }
        e.resetContext()
        listOf("xoo", "beeta", "dee").forEach { e.onWordCommitted(it) }
        assertEquals("cee", e.predictNext(listOf("alfa", "beeta")).first())
    }

    @Test
    fun `bigram taydentaa kun trigramia ei ole`() {
        val e = engine()
        listOf("eka", "toka").forEach { e.onWordCommitted(it) }
        assertEquals(listOf("toka"), e.predictNext(listOf("eka")))
    }

    @Test
    fun `ennustus sailyttaa asun ja numerot`() {
        val e = engine()
        listOf("prx4", "Jako", "20", "nouto").forEach { e.onWordCommitted(it) }
        assertEquals(listOf("Jako"), e.predictNext(listOf("prx4")))
        assertEquals(listOf("20"), e.predictNext(listOf("prx4", "Jako")).take(1))
        assertEquals(listOf("nouto"), e.predictNext(listOf("Jako", "20")).take(1))
    }

    @Test
    fun `estetty ei ennustu`() {
        val e = engine()
        listOf("eka", "paha").forEach { e.onWordCommitted(it) }
        e.blockWord("paha")
        assertTrue(e.predictNext(listOf("eka")).isEmpty())
    }

    @Test
    fun `tyhja konteksti antaa tyhjan ennustuksen`() {
        assertTrue(engine().predictNext(emptyList()).isEmpty())
    }

    @Test
    fun `trigramit paatyvat draineen`() {
        val e = engine()
        listOf("a1", "b2", "c3").forEach { e.onWordCommitted(it) }
        val dirty = e.drainDirty()
        assertTrue(dirty.trigrams.any { it.first == "a1" && it.second == "b2" && it.next == "c3" })
    }

    @Test
    fun `hyvaksynta kirjautuu ja nollaa ohitukset`() {
        val e = engine()
        e.onWordCommitted("sana")
        e.onSuggestionsIgnored(listOf("sana"), "muu")
        e.onSuggestionsIgnored(listOf("sana"), "muu")
        assertEquals(2, e.signals("sana")!!.ignoredCount)
        e.onSuggestionAccepted("sana")
        assertEquals(1, e.signals("sana")!!.acceptedCount)
        assertEquals(0, e.signals("sana")!!.ignoredCount)
    }

    @Test
    fun `yleissana saa palautetilan muttei tule omaksi`() {
        val e = engine()
        e.onSuggestionAccepted("auto")
        assertFalse(e.isOwnWord("auto"))
        assertEquals(1, e.signals("auto")!!.acceptedCount)
    }

    @Test
    fun `lopullinen sana ei saa ohitusta`() {
        val e = engine()
        e.onWordCommitted("sana")
        e.onSuggestionsIgnored(listOf("sana", "toinen"), "Sana")
        assertEquals(0, e.signals("sana")!!.ignoredCount)
    }

    @Test
    fun `kiinnitys ja irrotus`() {
        val e = engine()
        e.setPinned("prx4", true)
        assertTrue(e.isPinned("PRX4"))
        e.setPinned("prx4", false)
        assertFalse(e.isPinned("prx4"))
    }

    @Test
    fun `contextMatches erottelee bigramin ja trigramin`() {
        val e = engine()
        listOf("alfa", "beeta", "cee").forEach { e.onWordCommitted(it) }
        val matches = e.contextMatches(listOf("alfa", "beeta"))
        assertTrue(matches.getValue("cee").bigram > 0f)
        assertTrue(matches.getValue("cee").trigram > 0f)
    }

    @Test
    fun `kiinnitetty ilman kayttoa loytyy etuliitteella`() {
        val e = engine()
        e.setPinned("zebra", true)
        assertEquals(listOf("zebra"), e.suggest("ze"))
    }

    @Test
    fun `displayForm palauttaa asun tai avaimen`() {
        val e = engine()
        e.onWordCommitted("Jako")
        assertEquals("Jako", e.displayForm("jako"))
        assertEquals("20", e.displayForm("20"))
    }

    @Test
    fun `rivilta hyvaksytty kirjoitettu sana opitaan ja saa hyvaksynnan`() {
        val e = engine()
        e.onTypedWordAccepted("chrome")
        assertEquals(listOf("chrome"), e.suggest("ch"))
        assertTrue(e.isOwnWord("chrome"))
        assertEquals(1, e.signals("chrome")!!.acceptedCount)
    }

    @Test
    fun `rivilta hyvaksytty sana nollaa ohitukset`() {
        val e = engine()
        e.onWordCommitted("chrome")
        e.onSuggestionsIgnored(listOf("chrome"), "muu")
        e.onTypedWordAccepted("chrome")
        assertEquals(0, e.signals("chrome")!!.ignoredCount)
    }

    @Test
    fun `rivilta hyvaksytty sana jatkaa ketjua vain kerran`() {
        val e = engine()
        e.onWordCommitted("eka")
        e.onTypedWordAccepted("toka")
        val bigrams = e.drainDirty().bigrams
        assertEquals(1, bigrams.first { it.previous == "eka" && it.next == "toka" }.count)
        assertTrue(bigrams.none { it.previous == "toka" && it.next == "toka" })
    }

    @Test
    fun `rivilta hyvaksytty kelvoton sana ketjuttaa muttei opi`() {
        val e = engine()
        e.onTypedWordAccepted("20")
        assertTrue(e.suggest("2").isEmpty())
        assertFalse(e.isOwnWord("20"))
    }

    @Test
    fun `near loytaa oman sanan kirjoitusvirheella`() {
        val e = engine()
        e.onWordCommitted("prx4")
        e.onWordCommitted("nouto")
        assertEquals(listOf("prx4"), e.near("prx5", 1))
    }

    @Test
    fun `near ei anna estettya eika sanaa itseaan`() {
        val e = engine()
        e.onWordCommitted("moro")
        assertTrue(e.near("Moro", 1).isEmpty())
        e.onWordCommitted("morot")
        e.blockWord("morot")
        assertTrue(e.near("Moro", 1).isEmpty())
    }

    @Test
    fun `previousMatches loytaa sanaa edeltaneet sanat`() {
        val e = engine()
        listOf("koira", "on", "kiva").forEach { e.onWordCommitted(it) }
        e.resetContext()
        listOf("talo", "on").forEach { e.onWordCommitted(it) }
        val matches = e.previousMatches("on")
        assertTrue(matches.getValue("koira") > 0f)
        assertTrue(matches.getValue("talo") > 0f)
        assertFalse("kiva" in matches)
    }

    @Test
    fun `onCorrectionAccepted kirjaa hyvaksynnan ilman ketjua`() {
        val e = engine()
        e.onWordCommitted("eka")
        e.drainDirty()
        e.onCorrectionAccepted("toka")
        assertEquals(1, e.signals("toka")!!.acceptedCount)
        assertTrue(e.drainDirty().bigrams.isEmpty())
    }
}
