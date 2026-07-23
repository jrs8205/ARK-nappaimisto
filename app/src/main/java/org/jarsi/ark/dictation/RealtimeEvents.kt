package org.jarsi.ark.dictation

import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

/**
 * OpenAI:n realtime-transkriptioistunnon viestit: rakentaa lähtevät
 * JSON-tapahtumat ja tulkitsee saapuvat. Puhdasta muunnosta ilman
 * verkkoa tai ääntä, jotta protokolla on testattavissa sellaisenaan.
 * Ääni kulkee 24 kHz mono PCM16 -paloina base64-koodattuna.
 */
object RealtimeEvents {

    const val SAMPLE_RATE = 24_000

    sealed interface Incoming {
        /** Osittainen transkriptio: teksti kertyy pala kerrallaan. */
        data class Delta(val itemId: String, val text: String) : Incoming

        /** Valmis transkriptio committoidulle äänelle. */
        data class Completed(val itemId: String, val text: String) : Incoming

        /** Istunnon tai yksittäisen pätkän virhe syineen. */
        data class Failure(val message: String) : Incoming
    }

    /**
     * Istunnon asetusviesti. Suoratoistomalli (gpt-realtime-*) hoitaa
     * jaksotuksen itse, joten se ei saa turn_detection-kenttää;
     * eräpohjaiset mallit tarvitsevat palvelimen VAD:n, joka jakaa
     * jatkuvan äänivirran lausumiin ilman että ääntä putoaa väleistä.
     */
    fun sessionUpdate(model: String, language: String, delay: String? = null): String {
        val transcription = JSONObject()
            .put("model", model)
            .put("language", language)
        if (delay != null) transcription.put("delay", delay)
        val input = JSONObject()
            .put(
                "format",
                JSONObject().put("type", "audio/pcm").put("rate", SAMPLE_RATE),
            )
            .put("transcription", transcription)
        if (!model.startsWith("gpt-realtime")) {
            input.put("turn_detection", JSONObject().put("type", "server_vad"))
        }
        return JSONObject()
            .put("type", "session.update")
            .put(
                "session",
                JSONObject()
                    .put("type", "transcription")
                    .put("audio", JSONObject().put("input", input)),
            )
            .toString()
    }

    fun appendAudio(pcm16: ByteArray): String = JSONObject()
        .put("type", "input_audio_buffer.append")
        .put("audio", Base64.getEncoder().encodeToString(pcm16))
        .toString()

    fun commit(): String =
        JSONObject().put("type", "input_audio_buffer.commit").toString()

    /** PCM16-näytteet little-endian-tavuiksi lähetystä varten. */
    fun pcmBytes(samples: ShortArray, length: Int): ByteArray {
        val bytes = ByteArray(length * 2)
        for (i in 0 until length) {
            val value = samples[i].toInt()
            bytes[i * 2] = value.toByte()
            bytes[i * 2 + 1] = (value shr 8).toByte()
        }
        return bytes
    }

    /** Tulkitsee palvelimen tapahtuman; muut kuin tekstit ja virheet ohitetaan. */
    fun parse(message: String): Incoming? {
        val json = try {
            JSONObject(message)
        } catch (e: JSONException) {
            return null
        }
        return when (json.optString("type")) {
            "conversation.item.input_audio_transcription.delta" ->
                Incoming.Delta(json.optString("item_id"), json.optString("delta"))
            "conversation.item.input_audio_transcription.completed" ->
                Incoming.Completed(json.optString("item_id"), json.optString("transcript"))
            "conversation.item.input_audio_transcription.failed",
            "error" -> Incoming.Failure(
                json.optJSONObject("error")?.optString("message").orEmpty()
            )
            else -> null
        }
    }
}
