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
    fun `mallilista tulkitaan vastauksesta ja jarjestetaan kyvykkain ensin`() {
        val body = """{"data":[
            {"id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5"},
            {"id":"claude-opus-4-8","display_name":"Claude Opus 4.8"}]}"""
        assertEquals(
            listOf(
                "claude-opus-4-8" to "Claude Opus 4.8",
                "claude-haiku-4-5" to "Claude Haiku 4.5",
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
    fun `openai-pyynnossa on ohje seka tokenkatto ja se osoittaa responses-rajapintaan`() {
        val body = TextImprover.buildOpenAiRequest("moi", "gpt-5-mini")
        assertEquals(true, "\"model\":\"gpt-5-mini\"" in body)
        assertEquals(true, "\"max_output_tokens\":${512 + 4 + 6144}" in body)
        assertEquals(true, "\"instructions\":" in body)
        assertEquals(true, "\"input\":\"moi\"" in body)
        assertEquals(true, TextImprover.OPENAI_ENDPOINT.endsWith("/responses"))
    }

    @Test
    fun `openai-paattelytaso vain gpt-5-malleille`() {
        assertEquals(
            true,
            "\"reasoning\":{\"effort\":\"low\"}" in TextImprover.buildOpenAiRequest("x", "gpt-5.6-terra"),
        )
        assertEquals(
            false,
            "reasoning" in TextImprover.buildOpenAiRequest("x", "gpt-4o"),
        )
        assertEquals(
            false,
            "reasoning" in TextImprover.buildOpenAiRequest("x", "o3"),
        )
    }

    @Test
    fun `openai-tokenkatto pysyy ylarajassa`() {
        assertEquals(8192 + 6144, TextImprover.openAiMaxTokensFor("a".repeat(100_000)))
    }

    @Test
    fun `openai-vastaus tulkitaan responses-rakenteesta`() {
        val body = """{"status":"completed","output":[
            {"type":"reasoning","summary":[]},
            {"type":"message","role":"assistant","content":[
                {"type":"output_text",
                 "text":"{\"versiot\": [\"Eka.\", \"Toka.\", \"Kolmas.\"]}"}]}]}"""
        assertEquals(
            listOf("Eka.", "Toka.", "Kolmas."),
            TextImprover.parseOpenAiVersions(body),
        )
        assertNull(TextImprover.parseOpenAiResponse("""{"error":{"message":"x"}}"""))
        assertNull(TextImprover.parseOpenAiResponse("ei jsonia"))
        assertNull(TextImprover.parseOpenAiResponse("""{"output":[{"type":"reasoning"}]}"""))
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
            {"id":"gpt-5-mini","created":30},
            {"id":"gpt-4o","created":10},
            {"id":"o3","created":20},
            {"id":"whisper-1","created":40},
            {"id":"gpt-4o-audio-preview","created":40},
            {"id":"text-embedding-3-small","created":40},
            {"id":"dall-e-3","created":40}]}"""
        val models = TextImprover.parseOpenAiModels(body).map { it.first }
        assertEquals(listOf("gpt-5-mini", "o3", "gpt-4o"), models)
    }

    @Test
    fun `openai-mallilista pudottaa paivatyt kopiot ja aliakset`() {
        val body = """{"data":[
            {"id":"gpt-5.6-terra","created":60},
            {"id":"gpt-4o-2024-08-06","created":50},
            {"id":"chatgpt-4o-latest","created":50},
            {"id":"gpt-4.5-preview","created":50}]}"""
        assertEquals(
            listOf("gpt-5.6-terra"),
            TextImprover.parseOpenAiModels(body).map { it.first },
        )
    }

    @Test
    fun `openai-mallilista rajautuu kahteentoista uusimpaan`() {
        val items = (1..20).joinToString(",") { """{"id":"gpt-m$it","created":$it}""" }
        val models = TextImprover.parseOpenAiModels("""{"data":[$items]}""")
        assertEquals(12, models.size)
        assertEquals("gpt-m20", models.first().first)
    }

    @Test
    fun `claude-mallilista nayttaa vain perheen uusimman kyvykkain ensin`() {
        val body = """{"data":[
            {"id":"claude-sonnet-5","display_name":"Claude Sonnet 5"},
            {"id":"claude-fable-5","display_name":"Claude Fable 5"},
            {"id":"claude-opus-4-8","display_name":"Claude Opus 4.8"},
            {"id":"claude-opus-4-7","display_name":"Claude Opus 4.7"},
            {"id":"claude-sonnet-4-6","display_name":"Claude Sonnet 4.6"},
            {"id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5"}]}"""
        assertEquals(
            listOf("claude-fable-5", "claude-opus-4-8", "claude-sonnet-5", "claude-haiku-4-5"),
            TextImprover.parseModels(body).map { it.first },
        )
    }

    @Test
    fun `kaannospyynnot sisaltavat kielet tekstin ja paattelytason`() {
        val claude = TextImprover.buildTranslateRequest("moi", "suomi", "englanti")
        assertEquals(true, "suomi" in claude)
        assertEquals(true, "englanti" in claude)
        assertEquals(true, "\"content\":\"moi\"" in claude)
        val openai = TextImprover.buildOpenAiTranslateRequest("moi", "suomi", "ruotsi", "gpt-5-mini")
        assertEquals(true, "\"input\":\"moi\"" in openai)
        assertEquals(true, "ruotsi" in openai)
        assertEquals(true, "\"reasoning\":{\"effort\":\"low\"}" in openai)
        assertEquals(false, "reasoning" in TextImprover.buildOpenAiTranslateRequest("x", "a", "b", "gpt-4o"))
    }

    @Test
    fun `openai-sukupolven sisalla kyvykkain ensin`() {
        val body = """{"data":[
            {"id":"gpt-5.6-terra","created":102},
            {"id":"gpt-5.6-luna","created":101},
            {"id":"gpt-5.6-sol","created":100},
            {"id":"gpt-5","created":90}]}"""
        assertEquals(
            listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna", "gpt-5"),
            TextImprover.parseOpenAiModels(body).map { it.first },
        )
    }
}
