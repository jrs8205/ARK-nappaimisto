package org.jarsi.ark.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    private var now = 1_000_000_000_000L

    private fun build(vararg dictLines: String): Pair<SuggestionEngine, LearningEngine> {
        val dict = DictionaryEngine().apply { load(dictLines.asSequence()) }
        val learning = LearningEngine { now }.apply { load(emptyList(), emptyList()) }
        return SuggestionEngine(dict, learning) to learning
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
        assertEquals(listOf("autio"), s.topWords())
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
}
