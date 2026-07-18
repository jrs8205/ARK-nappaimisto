package org.jarsi.ark.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipStoreTest {

    private var now = 1_000_000_000_000L

    private fun store() = ClipStore { now }

    @Test
    fun `teksti tallentuu ja tyhja ei`() {
        val s = store()
        assertEquals("moi", s.addText(" moi ")!!.text)
        assertNull(s.addText("   "))
    }

    @Test
    fun `sama teksti ei tallennu kahdesti vaan nousee tuoreimmaksi`() {
        val s = store()
        val first = s.addText("moi")!!
        s.addText("toinen")
        now += 1000
        val again = s.addText("moi")!!
        assertEquals(first.id, again.id)
        assertEquals("moi", s.all().first().text)
        assertEquals(2, s.all().size)
    }

    @Test
    fun `kiinnittamaton vanhenee tunnissa mutta kiinnitetty ei`() {
        val s = store()
        val kiinnitetty = s.addText("pysyy")!!
        s.setPinned(kiinnitetty.id, true)
        s.addText("katoaa")
        now += 61L * 60 * 1000
        assertEquals(listOf("pysyy"), s.all().map { it.text })
        val prune = s.prune()
        assertEquals(1, prune.removedIds.size)
    }

    @Test
    fun `kiinnittamattomia pidetaan enintaan 20`() {
        val s = store()
        repeat(25) { i ->
            now += 1
            s.addText("leike$i")
        }
        val prune = s.prune()
        assertEquals(5, prune.removedIds.size)
        assertEquals(20, s.all().size)
        assertEquals("leike24", s.all().first().text)
    }

    @Test
    fun `kiinnitetyt ensin ja kuvapolku palautuu siivouksessa`() {
        val s = store()
        s.addText("teksti")
        now += 1
        val kuva = s.addImage("/data/kuva.png")
        now += 1
        val pinnattu = s.addText("pinni")!!
        s.setPinned(pinnattu.id, true)
        assertEquals(listOf("pinni", null, "teksti"), s.all().map { it.text })
        now += 61L * 60 * 1000
        val prune = s.prune()
        assertTrue(prune.removedImagePaths.contains("/data/kuva.png"))
        assertEquals(kuva.imagePath, "/data/kuva.png")
    }

    @Test
    fun `poisto vie leikkeen heti`() {
        val s = store()
        val clip = s.addText("pois")!!
        s.remove(clip.id)
        assertTrue(s.all().isEmpty())
    }
}
