package org.jarsi.ark.dictation

/**
 * JNI-silta whisper.cpp:hen. Tunnistus palauttaa UTF-8-tavut, koska
 * JNI:n oma merkkijonomuunnos ei kestä nelitavuisia merkkejä.
 */
object WhisperNative {

    init {
        System.loadLibrary("ark_whisper")
    }

    /** Lataa mallin; palauttaa kahvan tai 0 jos lataus epäonnistui. */
    external fun nativeInit(modelPath: String): Long

    /**
     * Tunnistaa 16 kHz mono -näytteet (arvot -1..1). [prompt] antaa
     * edellisen pätkän tekstin jatkuvuudeksi; tyhjä ohitetaan.
     */
    external fun nativeTranscribe(
        handle: Long,
        pcm: FloatArray,
        prompt: String,
        threads: Int,
    ): ByteArray?

    external fun nativeFree(handle: Long)
}

/** Ladattu Whisper-malli; [close] vapauttaa muistin. */
class WhisperEngine private constructor(private var handle: Long) {

    companion object {
        fun load(modelPath: String): WhisperEngine? {
            val handle = WhisperNative.nativeInit(modelPath)
            return if (handle == 0L) null else WhisperEngine(handle)
        }
    }

    fun transcribe(pcm: FloatArray, prompt: String, threads: Int): String {
        val current = handle
        if (current == 0L) return ""
        val bytes = WhisperNative.nativeTranscribe(current, pcm, prompt, threads)
        return bytes?.toString(Charsets.UTF_8)?.trim().orEmpty()
    }

    fun close() {
        if (handle != 0L) {
            WhisperNative.nativeFree(handle)
            handle = 0L
        }
    }
}
