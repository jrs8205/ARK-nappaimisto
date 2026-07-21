package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateBufferTest {

    @Test
    fun `lisays menee kursorin kohtaan`() {
        val b = TranslateBuffer()
        b.insert("ab")
        b.moveLeft()
        b.insert("x")
        assertEquals("axb", b.text)
        assertEquals(2, b.cursor)
    }

    @Test
    fun `backspace poistaa kursorin edelta keskella tekstia`() {
        val b = TranslateBuffer()
        b.insert("abc")
        b.moveLeft()
        assertTrue(b.backspace())
        assertEquals("ac", b.text)
        assertEquals(1, b.cursor)
    }

    @Test
    fun `backspace alussa ei tee mitaan`() {
        val b = TranslateBuffer()
        b.insert("ab")
        b.moveToStart()
        assertFalse(b.backspace())
        assertEquals("ab", b.text)
    }

    @Test
    fun `kursori liikkuu emojin yli kokonaisena`() {
        val b = TranslateBuffer()
        b.insert("a👍b")
        b.moveLeft()
        b.moveLeft()
        assertEquals(1, b.cursor)
        b.moveRight()
        assertEquals(3, b.cursor)
    }

    @Test
    fun `backspace poistaa emojin kokonaan`() {
        val b = TranslateBuffer()
        b.insert("a👍")
        assertTrue(b.backspace())
        assertEquals("a", b.text)
    }

    @Test
    fun `siirto askelina pysahtyy reunoihin`() {
        val b = TranslateBuffer()
        b.insert("ab")
        b.move(-5)
        assertEquals(0, b.cursor)
        b.move(5)
        assertEquals(2, b.cursor)
    }

    @Test
    fun `setCursor rajataan ja napsahtaa grafeemirajaan`() {
        val b = TranslateBuffer()
        b.insert("a👍b")
        b.setCursor(2)
        assertEquals(1, b.cursor)
        b.setCursor(99)
        assertEquals(4, b.cursor)
        b.setCursor(-1)
        assertEquals(0, b.cursor)
    }

    @Test
    fun `alkuun ja loppuun siirtyminen`() {
        val b = TranslateBuffer()
        b.insert("abc")
        b.moveToStart()
        assertEquals(0, b.cursor)
        b.moveToEnd()
        assertEquals(3, b.cursor)
    }

    @Test
    fun `tyhjennys nollaa tekstin ja kursorin`() {
        val b = TranslateBuffer()
        b.insert("abc")
        b.clear()
        assertTrue(b.isEmpty())
        assertEquals(0, b.cursor)
        assertEquals("", b.text)
    }

    @Test
    fun `toString antaa tekstin`() {
        val b = TranslateBuffer()
        b.insert("moi")
        assertEquals("moi", b.toString())
    }

    @Test
    fun `valimerkki saa valin heti peraansa`() {
        val b = TranslateBuffer()
        b.insert("sana")
        b.smartType(",")
        assertEquals("sana, ", b.text)
        b.insert("ja")
        b.smartType("?")
        assertEquals("sana, ja? ", b.text)
    }

    @Test
    fun `numeron perassa ei tule valia`() {
        val b = TranslateBuffer()
        b.insert("3")
        b.smartType(".")
        assertEquals("3.", b.text)
        b.smartType("1")
        assertEquals("3.1", b.text)
    }

    @Test
    fun `valin edelle kirjoitettu valimerkki siirtaa valin taakse`() {
        val b = TranslateBuffer()
        b.insert("sana")
        b.smartType(".")
        b.smartType(".")
        b.smartType(".")
        assertEquals("sana... ", b.text)
    }

    @Test
    fun `keskella tekstia ei tule valia`() {
        val b = TranslateBuffer()
        b.insert("ab")
        b.setCursor(1)
        b.smartType(".")
        assertEquals("a.b", b.text)
    }

    @Test
    fun `osoite onnistuu yhdella poistolla`() {
        val b = TranslateBuffer()
        b.insert("jarsi")
        b.smartType(".")
        assertEquals("jarsi. ", b.text)
        assertTrue(b.backspace())
        b.smartType("o")
        assertEquals("jarsi.o", b.text)
    }

    @Test
    fun `rivin alussa valimerkki on tavallinen`() {
        val b = TranslateBuffer()
        b.smartType(".")
        assertEquals(".", b.text)
    }

    @Test
    fun `smartSpace ohittaa tuplavalin`() {
        val b = TranslateBuffer()
        b.insert("sana")
        b.smartType(",")
        b.smartSpace()
        assertEquals("sana, ", b.text)
        b.insert("x")
        b.smartSpace()
        assertEquals("sana, x ", b.text)
    }

    @Test
    fun `backspaceWord poistaa sanan ja hantavalit`() {
        val b = TranslateBuffer()
        b.insert("kaksi sanaa ")
        assertTrue(b.backspaceWord())
        assertEquals("kaksi ", b.text)
        assertTrue(b.backspaceWord())
        assertEquals("", b.text)
        assertEquals(0, b.cursor)
    }

    @Test
    fun `backspaceWord keskella tekstia poistaa kursorin edelta`() {
        val b = TranslateBuffer()
        b.insert("eka toka kolmas")
        b.move(-7)
        assertTrue(b.backspaceWord())
        assertEquals("eka  kolmas", b.text)
        assertEquals(4, b.cursor)
    }

    @Test
    fun `backspaceWord alussa ei tee mitaan`() {
        val b = TranslateBuffer()
        b.insert("sana")
        b.moveToStart()
        assertFalse(b.backspaceWord())
        assertEquals("sana", b.text)
    }

    @Test
    fun `deleteBeforeCursor poistaa halutun maaran kursorin edelta`() {
        val b = TranslateBuffer()
        b.insert("moikka")
        b.deleteBeforeCursor(3)
        assertEquals("moi", b.text)
        assertEquals(3, b.cursor)
        b.moveToStart()
        b.deleteBeforeCursor(5)
        assertEquals("moi", b.text)
        assertEquals(0, b.cursor)
    }

    @Test
    fun `kaksoisvalilyonti muuttuu pisteeksi`() {
        val b = TranslateBuffer()
        b.insert("sana ")
        assertTrue(b.doubleSpacePeriod())
        assertEquals("sana. ", b.text)
        assertEquals(6, b.cursor)
    }

    @Test
    fun `kaksoisvalilyonti ei laukea valimerkin jalkeen eika alussa`() {
        val b = TranslateBuffer()
        b.insert("sana. ")
        assertFalse(b.doubleSpacePeriod())
        assertEquals("sana. ", b.text)
        val alku = TranslateBuffer()
        alku.insert(" ")
        assertFalse(alku.doubleSpacePeriod())
    }
}