package org.jarsi.ark.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryEngineTest {

    private fun engine(vararg lines: String) =
        DictionaryEngine().apply { load(lines.asSequence()) }

    @Test
    fun `etuliitehaku palauttaa sanat yleisyysjarjestyksessa`() {
        val e = engine("sovellus 50", "sovelluksen 30", "sovi 80", "auto 100")
        assertEquals(listOf("sovi", "sovellus", "sovelluksen"), e.suggest("sov"))
    }

    @Test
    fun `haku toimii aakkosilla`() {
        val e = engine("äiti 10", "äijä 5", "auto 100")
        assertEquals(listOf("äiti", "äijä"), e.suggest("äi"))
    }

    @Test
    fun `iso alkukirjain loytaa pienella tallennetun`() {
        val e = engine("helsinki 10")
        assertEquals(listOf("helsinki"), e.suggest("Hel"))
    }

    @Test
    fun `max rajaa tulosten maaran`() {
        val e = engine("aa 1", "ab 2", "ac 3", "ad 4")
        assertEquals(listOf("ad", "ac"), e.suggest("a", max = 2))
    }

    @Test
    fun `tyhja etuliite palauttaa tyhjan`() {
        assertEquals(emptyList<String>(), engine("auto 1").suggest(""))
    }

    @Test
    fun `topWords antaa yleisimmat`() {
        val e = engine("aa 1", "bb 3", "cc 2")
        assertEquals(listOf("bb", "cc", "aa"), e.topWords())
    }

    @Test
    fun `virheelliset rivit ohitetaan`() {
        val e = engine("auto 10", "rikki", " 5", "hyvä x")
        assertTrue(e.isLoaded)
        assertEquals(listOf("auto"), e.suggest("a"))
    }

    @Test
    fun `lataamaton moottori palauttaa tyhjan`() {
        val e = DictionaryEngine()
        assertEquals(emptyList<String>(), e.suggest("a"))
        assertEquals(emptyList<String>(), e.topWords())
    }
}
