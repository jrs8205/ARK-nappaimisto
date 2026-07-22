package org.jarsi.ark.dictation

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationWavTest {

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    @Test
    fun `otsikko on kelvollinen 16 kHz mono PCM16`() {
        val wav = DictationWav.encode(FloatArray(1600) { 0.5f })
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals(1, readShort(wav, 20)) // PCM
        assertEquals(1, readShort(wav, 22)) // mono
        assertEquals(16_000, readInt(wav, 24)) // näytteenottotaajuus
        assertEquals(16, readShort(wav, 34)) // bittisyvyys
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
    }

    @Test
    fun `koot vastaavat naytemaaraa`() {
        val samples = 1600
        val wav = DictationWav.encode(FloatArray(samples))
        assertEquals(44 + samples * 2, wav.size)
        assertEquals(samples * 2, readInt(wav, 40)) // data-lohkon koko
        assertEquals(36 + samples * 2, readInt(wav, 4)) // RIFF-koko
    }

    @Test
    fun `naytteet muunnetaan ja rajataan oikein`() {
        val wav = DictationWav.encode(floatArrayOf(0f, 1f, -1f, 2f))
        assertEquals(0, readShort(wav, 44))
        assertEquals(32767, readShort(wav, 46))
        assertEquals(-32767, readShort(wav, 48).toShort().toInt())
        assertEquals(32767, readShort(wav, 50)) // yli yhden rajataan
    }
}
