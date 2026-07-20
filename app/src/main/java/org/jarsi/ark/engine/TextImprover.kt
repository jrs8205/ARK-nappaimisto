package org.jarsi.ark.engine

import org.json.JSONException
import org.json.JSONObject
import org.json.JSONArray

/**
 * Paranna teksti -toiminnon pyyntöjen rakennus ja vastausten tulkinta.
 * Teksti lähetetään Anthropicin Claude-palveluun vain käyttäjän omasta
 * napautuksesta ja vain kun API-avain on asetettu; avain säilyy laitteella.
 */
object TextImprover {

    const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    const val MODEL = "claude-haiku-4-5"

    private const val SYSTEM_PROMPT =
        "Olet kirjoitusavustaja. Paranna käyttäjän teksti: korjaa kirjoitus- " +
            "ja kielioppivirheet ja sujuvoita kömpelöt ilmaukset. Säilytä " +
            "tekstin kieli, merkitys, sävy ja likimääräinen pituus. Palauta " +
            "vain parannettu teksti ilman selityksiä, lainausmerkkejä tai " +
            "muotoilua."

    fun buildRequest(text: String): String = JSONObject()
        .put("model", MODEL)
        .put("max_tokens", 2048)
        .put("system", SYSTEM_PROMPT)
        .put(
            "messages",
            JSONArray().put(JSONObject().put("role", "user").put("content", text)),
        )
        .toString()

    /** Parannettu teksti onnistuneesta vastauksesta tai null. */
    fun parseResponse(body: String): String? = try {
        val content = JSONObject(body).optJSONArray("content")
        var result: String? = null
        if (content != null) {
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") {
                    result = block.optString("text").trim()
                    break
                }
            }
        }
        result?.takeIf { it.isNotEmpty() }
    } catch (e: JSONException) {
        null
    }
}
