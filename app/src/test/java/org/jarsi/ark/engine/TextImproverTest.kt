package org.jarsi.ark.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextImproverTest {

    @Test
    fun `pyynnossa on malli teksti ja ohje`() {
        val json = JSONObject(TextImprover.buildRequest("minä menee kauppaan"))
        assertEquals(TextImprover.MODEL, json.getString("model"))
        assertTrue(json.getInt("max_tokens") > 0)
        assertTrue(json.getString("system").isNotBlank())
        val message = json.getJSONArray("messages").getJSONObject(0)
        assertEquals("user", message.getString("role"))
        assertEquals("minä menee kauppaan", message.getString("content"))
    }

    @Test
    fun `erikoismerkit sailyvat pyynnossa`() {
        val text = "rivi \"lainaus\" ja \\kenoviiva"
        val json = JSONObject(TextImprover.buildRequest(text))
        assertEquals(
            text,
            json.getJSONArray("messages").getJSONObject(0).getString("content"),
        )
    }

    @Test
    fun `vastauksesta poimitaan teksti`() {
        val body = """{"content":[{"type":"text","text":" Minä menen kauppaan. "}],
            "stop_reason":"end_turn"}"""
        assertEquals("Minä menen kauppaan.", TextImprover.parseResponse(body))
    }

    @Test
    fun `vastauksen muut lohkot ohitetaan`() {
        val body = """{"content":[{"type":"thinking","thinking":"..."},
            {"type":"text","text":"Valmis teksti"}]}"""
        assertEquals("Valmis teksti", TextImprover.parseResponse(body))
    }

    @Test
    fun `mallin voi vaihtaa pyyntoon`() {
        val json = JSONObject(TextImprover.buildRequest("moi", "claude-opus-4-8"))
        assertEquals("claude-opus-4-8", json.getString("model"))
    }

    @Test
    fun `versiot poimitaan json-vastauksesta`() {
        val body = """{"content":[{"type":"text","text":
            "{\"versiot\": [\"Eka versio.\", \"Toka versio.\", \"Kolmas.\"]}"}]}"""
        assertEquals(
            listOf("Eka versio.", "Toka versio.", "Kolmas."),
            TextImprover.parseVersions(body),
        )
    }

    @Test
    fun `koodiaidat riisutaan versioista`() {
        val body = """{"content":[{"type":"text","text":
            "```json\n{\"versiot\": [\"Vain yksi.\"]}\n```"}]}"""
        assertEquals(listOf("Vain yksi."), TextImprover.parseVersions(body))
    }

    @Test
    fun `muotoa noudattamaton vastaus on yksi versio`() {
        val body = """{"content":[{"type":"text","text":"Pelkkä korjattu teksti."}]}"""
        assertEquals(listOf("Pelkkä korjattu teksti."), TextImprover.parseVersions(body))
    }

    @Test
    fun `mallilista tulkitaan vastauksesta`() {
        val body = """{"data":[
            {"id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5"},
            {"id":"claude-opus-4-8","display_name":"Claude Opus 4.8"}]}"""
        assertEquals(
            listOf(
                "claude-haiku-4-5" to "Claude Haiku 4.5",
                "claude-opus-4-8" to "Claude Opus 4.8",
            ),
            TextImprover.parseModels(body),
        )
        assertEquals(emptyList<Pair<String, String>>(), TextImprover.parseModels("roska"))
    }

    @Test
    fun `virhevastauksesta tulee null`() {
        assertNull(TextImprover.parseResponse("""{"type":"error","error":{"message":"x"}}"""))
        assertNull(TextImprover.parseResponse("ei jsonia"))
        assertNull(TextImprover.parseResponse("""{"content":[{"type":"text","text":"  "}]}"""))
    }

    @Test
    fun `tokenkatto kasvaa tekstin mukana ja pysyy rajoissa`() {
        assertEquals(512 + 150, TextImprover.maxTokensFor("a".repeat(100)))
        assertEquals(8192, TextImprover.maxTokensFor("a".repeat(100_000)))
        assertEquals(512, TextImprover.maxTokensFor(""))
    }

    @Test
    fun `pyynto kayttaa dynaamista tokenkattoa`() {
        val body = TextImprover.buildRequest("moi")
        assertEquals(true, body.contains("\"max_tokens\":${512 + 4}"))
    }

    @Test
    fun `mallivihje tunnetuille malleille ja null muille`() {
        assertEquals("nopein ja edullisin", TextImprover.modelHint("claude-haiku-4-5"))
        assertEquals("nopea, keskihintainen", TextImprover.modelHint("claude-sonnet-5"))
        assertEquals("harkitseva, kallis", TextImprover.modelHint("claude-opus-4-8"))
        assertEquals("harkitsevin, kallein", TextImprover.modelHint("claude-fable-5"))
        assertEquals("nopein ja edullisin", TextImprover.modelHint("gpt-5-nano"))
        assertEquals("nopea ja edullinen", TextImprover.modelHint("gpt-5-mini"))
        assertEquals("harkitseva, kallis", TextImprover.modelHint("gpt-5"))
        assertEquals("harkitseva, kallis", TextImprover.modelHint("o3"))
        assertNull(TextImprover.modelHint("uusi-tuntematon-malli"))
    }

    @Test
    fun `openai-pyynnossa on jarjestelmaohje ja tokenkatto`() {
        val body = TextImprover.buildOpenAiRequest("moi", "gpt-5-mini")
        assertEquals(true, "\"model\":\"gpt-5-mini\"" in body)
        assertEquals(true, "\"max_completion_tokens\":${(512 + 4) * 2}" in body)
        assertEquals(true, "\"role\":\"system\"" in body)
        assertEquals(true, "\"role\":\"user\"" in body)
    }

    @Test
    fun `openai-vastaus tulkitaan choices-rakenteesta`() {
        val body = """{"choices":[{"message":{"role":"assistant",
            "content":"{\"versiot\": [\"Eka.\", \"Toka.\", \"Kolmas.\"]}"}}]}"""
        assertEquals(
            listOf("Eka.", "Toka.", "Kolmas."),
            TextImprover.parseOpenAiVersions(body),
        )
        assertNull(TextImprover.parseOpenAiResponse("""{"error":{"message":"x"}}"""))
        assertNull(TextImprover.parseOpenAiResponse("ei jsonia"))
    }

    @Test
    fun `virheviesti tulkitaan molempien palveluiden muodosta`() {
        assertEquals(
            "Incorrect API key provided",
            TextImprover.parseErrorMessage(
                """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}"""
            ),
        )
        assertNull(TextImprover.parseErrorMessage("ei jsonia"))
        assertNull(TextImprover.parseErrorMessage(null))
        assertNull(TextImprover.parseErrorMessage("""{"data":[]}"""))
    }

    @Test
    fun `openai-mallilista suodattaa muut kuin chat-mallit`() {
        val body = """{"data":[
            {"id":"gpt-5-mini"},
            {"id":"gpt-4o"},
            {"id":"o3"},
            {"id":"whisper-1"},
            {"id":"gpt-4o-audio-preview"},
            {"id":"text-embedding-3-small"},
            {"id":"dall-e-3"}]}"""
        val models = TextImprover.parseOpenAiModels(body).map { it.first }
        assertEquals(true, "gpt-5-mini" in models)
        assertEquals(true, "gpt-4o" in models)
        assertEquals(true, "o3" in models)
        assertEquals(false, models.any { "whisper" in it || "audio" in it || "embedding" in it || "dall" in it })
    }
}
