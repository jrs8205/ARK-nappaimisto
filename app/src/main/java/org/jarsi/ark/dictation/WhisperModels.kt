package org.jarsi.ark.dictation

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Ladattavat Whisper-tunnistusmallit (kvantisoidut ggml-tiedostot). */
enum class WhisperModel(
    val prefValue: String,
    val fileName: String,
    val url: String,
    val sizeMb: Int,
) {
    BASE(
        "whisper_base",
        "ggml-base-q5_1.bin",
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
        57,
    ),
    SMALL(
        "whisper_small",
        "ggml-small-q5_1.bin",
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        182,
    ),

    // Karsittu large-malli: lähes parhaan mallin laatu selvästi mediumia
    // nopeampana — siksi medium-porrasta ei tarjota lainkaan.
    TURBO(
        "whisper_turbo",
        "ggml-large-v3-turbo-q5_0.bin",
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        547,
    ),
    ;

    companion object {
        fun fromPref(value: String?): WhisperModel? =
            entries.firstOrNull { it.prefValue == value }
    }
}

/** Mallitiedostojen sijainti, kertalataus ja poisto. */
object WhisperModels {

    fun file(context: Context, model: WhisperModel): File =
        File(File(context.filesDir, "whisper"), model.fileName)

    fun isDownloaded(context: Context, model: WhisperModel): Boolean =
        file(context, model).exists()

    fun delete(context: Context, model: WhisperModel) {
        file(context, model).delete()
    }

    /**
     * Lataa mallin kerran. Kutsutaan taustasäikeestä; [onProgress] saa
     * prosentteja. Keskeneräinen tiedosto kirjoitetaan .osa-nimellä ja
     * nimetään valmiiksi vasta lopuksi, joten katkennut lataus ei jätä
     * rikkinäistä mallia käyttöön. [cancel] keskeyttää siististi.
     */
    fun download(
        context: Context,
        model: WhisperModel,
        cancel: AtomicBoolean,
        onProgress: (Int) -> Unit,
    ): Boolean {
        val target = file(context, model)
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".osa")
        var connection: HttpURLConnection? = null
        try {
            connection = URL(model.url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode !in 200..299) return false
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        if (cancel.get()) return false
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = (written * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            return partial.renameTo(target)
        } catch (e: Exception) {
            return false
        } finally {
            connection?.disconnect()
            partial.delete()
        }
    }
}
