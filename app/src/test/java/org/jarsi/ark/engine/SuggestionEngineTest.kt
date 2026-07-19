package org.jarsi.ark.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    private var now = 1_000_000_000_000L

    private fun build(vararg dictLines: String): Pair<SuggestionEngine, LearningEngine> {
        val dict = DictionaryEngine().apply { load(dictLines.asSequence()) }
        val learning = LearningEngine { now }.apply { load(emptyList(), emptyList(), emptyList()) }
        return SuggestionEngine(dict, learning) to learning
    }

    @Test
    fun `alternatives ehdottaa laheiset sanat lahin ensin`() {
        val (s, l) = build("koira 100", "koiran 50", "kissa 80")
        l.onWordCommitted("koirra")
        val result = s.alternatives("koirra")
        assertEquals("koira", result.first())
        assertTrue("koiran" in result)
        assertTrue("kissa" !in result)
        assertTrue("koirra" !in result)
    }

    @Test
    fun `alternatives ei ehdota estettya`() {
        val (s, l) = build("talo 100", "tila 90")
        l.blockWord("tila")
        assertTrue("tila" !in s.alternatives("tilat"))
        assertEquals(listOf("talo"), s.alternatives("talto"))
    }

    @Test
    fun `alternatives lyhyt sana sallii vain yhden muokkauksen kirjoitusvirheena`() {
        val (s, _) = build("talo 100")
        assertTrue("talo" in s.alternatives("tilo"))
    }

    @Test
    fun `lyhyt sana saa yleiset sanat vaihtoehdoiksi`() {
        val (s, _) = build("ja 100", "on 90", "tai 80")
        val result = s.alternatives("ei")
        assertTrue("ja" in result)
        assertTrue("on" in result)
        assertTrue("tai" in result)
        assertFalse("ei" in result)
    }

    @Test
    fun `pitkalle sanalle ei tarjota yleisia sanoja`() {
        val (s, _) = build("ja 100", "koira 90")
        assertEquals(listOf("koira"), s.alternatives("koirra"))
    }

    @Test
    fun `alternatives taydentaa lyhyen sanan kontekstista`() {
        val (s, l) = build("on 100", "ei 90")
        listOf("koira", "on", "kiva").forEach { l.onWordCommitted(it) }
        l.resetContext()
        assertEquals(listOf("on"), s.alternatives("ei", listOf("Koira"), nextWord = "kiva"))
    }

    @Test
    fun `alternatives yhdistaa kirjoitusvirheen ja kontekstin`() {
        val (s, l) = build("koira 100", "kissa 90")
        listOf("se", "kissa", "naukuu").forEach { l.onWordCommitted(it) }
        l.resetContext()
        val result = s.alternatives("koirra", listOf("se"), nextWord = "naukuu")
        assertTrue("koira" in result)
        assertTrue("kissa" in result)
    }

    @Test
    fun `omat sanat nousevat karkeen`() {
        val (s, l) = build("auto 100", "autio 50")
        l.onWordCommitted("autotalli")
        assertEquals(listOf("autotalli", "auto", "autio"), s.suggest("au"))
    }

    @Test
    fun `sama sana vain kerran ja omalla asulla`() {
        val (s, l) = build("jako 100")
        l.onWordCommitted("Jako")
        assertEquals(listOf("Jako"), s.suggest("ja"))
    }

    @Test
    fun `estetty yleissana suodattuu`() {
        val (s, l) = build("auto 100", "autio 50")
        l.blockWord("auto")
        assertEquals(listOf("autio"), s.suggest("au"))
        assertEquals(listOf("autio"), s.emptyInput(emptyList(), includeCommon = true))
    }

    @Test
    fun `tyhjalla etuliitteella tyhja`() {
        val (s, _) = build("auto 100")
        assertTrue(s.suggest("").isEmpty())
    }

    @Test
    fun `max rajaa kokonaismaaran`() {
        val (s, l) = build("aa 5", "ab 4", "ac 3", "ad 2")
        l.onWordCommitted("aatto")
        assertEquals(3, s.suggest("a", max = 3).size)
    }

    @Test
    fun `ennustus nousee karkeen tyhjalla syotteella`() {
        val (s, l) = build("ja 100", "on 90")
        listOf("prx4", "Jako").forEach { l.onWordCommitted(it) }
        assertEquals(listOf("Jako", "ja", "on"), s.emptyInput(listOf("prx4"), includeCommon = true))
    }

    @Test
    fun `ilman yleisia vain ennustukset`() {
        val (s, l) = build("ja 100")
        listOf("prx4", "Jako").forEach { l.onWordCommitted(it) }
        assertEquals(listOf("Jako"), s.emptyInput(listOf("prx4"), includeCommon = false))
    }

    @Test
    fun `etuliite suodattaa ennustukset karkeen`() {
        val (s, l) = build("nopea 100")
        listOf("Jako", "20", "nouto").forEach { l.onWordCommitted(it) }
        assertEquals("nouto", s.suggest("no", listOf("Jako", "20")).first())
    }

    @Test
    fun `kiinnitetty voittaa kaiken`() {
        val (s, l) = build("auto 100", "autio 50")
        l.setPinned("autio", true)
        assertEquals("autio", s.suggest("au").first())
    }

    @Test
    fun `hyvaksytty yleissana ohittaa yleisemman`() {
        val (s, l) = build("auto 100", "autio 50")
        l.onSuggestionAccepted("autio")
        l.onSuggestionAccepted("autio")
        assertEquals(listOf("autio", "auto"), s.suggest("au"))
    }

    @Test
    fun `toistuvat ohitukset pudottavat`() {
        val (s, l) = build("auto 100", "autio 50")
        l.onSuggestionsIgnored(listOf("auto"), "muu")
        l.onSuggestionsIgnored(listOf("auto"), "muu")
        assertEquals(listOf("autio", "auto"), s.suggest("au"))
    }

    @Test
    fun `kiinnitysbonus rajautuu kahteen`() {
        val (s, l) = build("maa 100", "meri 90", "metsa 80", "mies 70")
        l.setPinned("meri", true)
        l.setPinned("metsa", true)
        l.setPinned("mies", true)
        assertEquals(listOf("meri", "metsa", "maa", "mies"), s.suggest("m").take(4))
    }

    @Test
    fun `yksi ohitus ei viela rankaise`() {
        val (s, l) = build("auto 100", "autio 50")
        l.onSuggestionsIgnored(listOf("auto"), "muu")
        assertEquals(listOf("auto", "autio"), s.suggest("au"))
    }
}
