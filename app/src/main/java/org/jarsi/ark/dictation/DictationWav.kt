package org.jarsi.ark.dictation

/** Pakkaa 16 kHz mono -näytteet WAV-muotoon tunnistuspalvelua varten. */
object DictationWav {

    private const val SAMPLE_RATE = 16_000
    private const val HEADER_SIZE = 44

    fun encode(samples: FloatArray): ByteArray {
        val dataSize = samples.size * 2
        val bytes = ByteArray(HEADER_SIZE + dataSize)

        fun putAscii(offset: Int, text: String) {
            text.forEachIndexed { i, c -> bytes[offset + i] = c.code.toByte() }
        }

        fun putInt(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        fun putShort(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        putAscii(0, "RIFF")
        putInt(4, 36 + dataSize)
        putAscii(8, "WAVE")
        putAscii(12, "fmt ")
        putInt(16, 16) // fmt-lohkon koko
        putShort(20, 1) // PCM
        putShort(22, 1) // mono
        putInt(24, SAMPLE_RATE)
        putInt(28, SAMPLE_RATE * 2) // tavua sekunnissa
        putShort(32, 2) // lohkon tasaus
        putShort(34, 16) // bittisyvyys
        putAscii(36, "data")
        putInt(40, dataSize)

        samples.forEachIndexed { i, sample ->
            val clamped = (sample.coerceIn(-1f, 1f) * 32767f).toInt()
            putShort(HEADER_SIZE + i * 2, clamped)
        }
        return bytes
    }
}
