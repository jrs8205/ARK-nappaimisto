package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardHeightTest {

    // Pixel 8a: 411 × 914 dp, 3-nappinavigointi 48 dp pystyssä, 0 vaa'assa.

    @Test
    fun `pystyssa 130 prosenttia sailyy ennallaan`() {
        val scale = KeyboardHeight.effectiveScale(
            prefScale = 1.3f, landscape = false, windowHeightDp = 914f, bottomInsetDp = 48f,
        )
        assertEquals(1.3f, scale, 0.0001f)
    }

    @Test
    fun `pystyssa pienella puhelimella 130 prosenttia sailyy`() {
        // Samsung A40: 411 × 891 dp.
        val scale = KeyboardHeight.effectiveScale(
            prefScale = 1.3f, landscape = false, windowHeightDp = 891f, bottomInsetDp = 48f,
        )
        assertEquals(1.3f, scale, 0.0001f)
    }

    @Test
    fun `vaakasuunnassa kokonaiskorkeus jaa kattoon`() {
        val scale = KeyboardHeight.effectiveScale(
            prefScale = 1.3f, landscape = true, windowHeightDp = 411f, bottomInsetDp = 0f,
        )
        assertTrue(scale < 1.3f)
        val total = KeyboardHeight.totalHeightDp(scale, landscape = true, bottomInsetDp = 0f)
        assertEquals(411f * KeyboardHeight.MAX_WINDOW_FRACTION, total, 0.5f)
    }

    @Test
    fun `vaakasuunnassa myos 100 prosenttia kutistuu`() {
        val scale = KeyboardHeight.effectiveScale(
            prefScale = 1f, landscape = true, windowHeightDp = 411f, bottomInsetDp = 0f,
        )
        assertTrue(scale < 1f)
    }

    @Test
    fun `navigointipalkin varaus pienentaa skaalaa`() {
        val without = KeyboardHeight.effectiveScale(
            prefScale = 1.3f, landscape = true, windowHeightDp = 411f, bottomInsetDp = 0f,
        )
        val with = KeyboardHeight.effectiveScale(
            prefScale = 1.3f, landscape = true, windowHeightDp = 411f, bottomInsetDp = 24f,
        )
        assertTrue(with < without)
    }

    @Test
    fun `skaala ei koskaan ylita asetusta eika alita lattiaa`() {
        assertEquals(
            0.8f,
            KeyboardHeight.effectiveScale(0.8f, false, 2000f, 0f),
            0.0001f,
        )
        assertEquals(
            KeyboardHeight.MIN_SCALE,
            KeyboardHeight.effectiveScale(1.3f, true, 100f, 0f),
            0.0001f,
        )
    }

    @Test
    fun `perusrivi on 56 dp pystyssa ja 44 dp vaakasuunnassa`() {
        assertEquals(56f, KeyboardHeight.baseRowDp(landscape = false), 0f)
        assertEquals(44f, KeyboardHeight.baseRowDp(landscape = true), 0f)
    }
}
