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
}
