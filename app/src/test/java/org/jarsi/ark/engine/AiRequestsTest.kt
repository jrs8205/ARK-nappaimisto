package org.jarsi.ark.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRequestsTest {

    @Test
    fun `claude-kaannospyynnossa on malli teksti ja ohje`() {
        val json = JSONObject(AiRequests.buildTranslateRequest("moi", "suomi", "englanti"))
        assertEquals(AiRequests.MODEL, json.getString("model"))
        assertTrue(json.getInt("max_tokens") > 0)
        val system = json.getString("system")
        assertTrue("suomi" in system && "englanti" in system)
        val message = json.getJSONArray("messages").getJSONObject(0)
        assertEquals("user", message.getString("role"))
        assertEquals("moi", message.getString("content"))
    }

    @Test
    fun `erikoismerkit sailyvat pyynnossa`() {
        val text = "rivi \"lainaus\" ja \\kenoviiva"
        val json = JSONObject(AiRequests.buildTranslateRequest(text, "suomi", "englanti"))
        assertEquals(
            text,
            json.getJSONArray("messages").getJSONObject(0).getString("content"),
        )
        val openAi = JSONObject(AiRequests.buildOpenAiTranslateRequest(text, "suomi", "englanti"))
        assertEquals(text, openAi.getString("input"))
    }

    @Test
    fun `mallin voi vaihtaa pyyntoon`() {
        val json = JSONObject(AiRequests.buildTranslateRequest("moi", "a", "b", "claude-opus-4-8"))
        assertEquals("claude-opus-4-8", json.getString("model"))
    }

    @Test
    fun `vastauksesta poimitaan teksti`() {
        val body = """{"content":[{"type":"text","text":" Minä menen kauppaan. "}],
            "stop_reason":"end_turn"}"""
        assertEquals("Minä menen kauppaan.", AiRequests.parseResponse(body))
    }

    @Test
    fun `vastauksen muut lohkot ohitetaan`() {
        val body = """{"content":[{"type":"thinking","thinking":"..."},
            {"type":"text","text":"Valmis teksti"}]}"""
        assertEquals("Valmis teksti", AiRequests.parseResponse(body))
    }

    @Test
    fun `virhevastauksesta tulee null`() {
        assertNull(AiRequests.parseResponse("""{"type":"error","error":{"message":"x"}}"""))
        assertNull(AiRequests.parseResponse("ei jsonia"))
        assertNull(AiRequests.parseResponse("""{"content":[{"type":"text","text":"  "}]}"""))
    }

    @Test
    fun `tokenkatto kasvaa tekstin mukana ja pysyy rajoissa`() {
        fun claudeCap(text: String) =
            JSONObject(AiRequests.buildTranslateRequest(text, "a", "b")).getInt("max_tokens")
        fun openAiCap(text: String) =
            JSONObject(AiRequests.buildOpenAiTranslateRequest(text, "a", "b"))
                .getInt("max_output_tokens")
        assertEquals(256 + 100, claudeCap("a".repeat(100)))
        assertEquals(4096, claudeCap("a".repeat(100_000)))
        // OpenAI:n katto sisältää kiinteän varan näkymättömälle päättelylle.
        assertEquals(256 + 100 + 6144, openAiCap("a".repeat(100)))
        assertEquals(4096 + 6144, openAiCap("a".repeat(100_000)))
    }

    @Test
    fun `mallivihje tunnetuille malleille ja null muille`() {
        assertEquals("nopein ja edullisin", AiRequests.modelHint("claude-haiku-4-5"))
        assertEquals("nopea, keskihintainen", AiRequests.modelHint("claude-sonnet-5"))
        assertEquals("harkitseva, kallis", AiRequests.modelHint("claude-opus-4-8"))
        assertEquals("harkitsevin, kallein", AiRequests.modelHint("claude-fable-5"))
        assertEquals("nopein ja edullisin", AiRequests.modelHint("gpt-5-nano"))
        assertEquals("nopea ja edullinen", AiRequests.modelHint("gpt-5-mini"))
        assertEquals("harkitseva, kallis", AiRequests.modelHint("gpt-5"))
        assertEquals("harkitseva, kallis", AiRequests.modelHint("o3"))
        assertNull(AiRequests.modelHint("uusi-tuntematon-malli"))
    }

    @Test
    fun `openai-pyynnossa on ohje ja se osoittaa responses-rajapintaan`() {
        val body = AiRequests.buildOpenAiTranslateRequest("moi", "suomi", "ruotsi", "gpt-5-mini")
        assertTrue("\"model\":\"gpt-5-mini\"" in body)
        assertTrue("\"instructions\":" in body)
        assertTrue("ruotsi" in body)
        assertTrue("\"input\":\"moi\"" in body)
        assertTrue(AiRequests.OPENAI_ENDPOINT.endsWith("/responses"))
    }

    @Test
    fun `paattelytaso vain paattelymalleille`() {
        // Ilman rajausta päättelymalli voi polttaa koko tokenkaton
        // näkymättömään päättelyyn ja palauttaa tyhjän — silti laskutettuna.
        // o-sarja on myös päättelymalli.
        for (model in listOf("gpt-5-mini", "gpt-5.6-terra", "o3", "o4-mini")) {
            val json = JSONObject(AiRequests.buildOpenAiTranslateRequest("x", "a", "b", model))
            assertEquals(
                "$model tarvitsee päättelyrajauksen",
                "low",
                json.getJSONObject("reasoning").getString("effort"),
            )
        }
        assertFalse("reasoning" in AiRequests.buildOpenAiTranslateRequest("x", "a", "b", "gpt-4o"))
    }

    @Test
    fun `openai-vastaus tulkitaan responses-rakenteesta`() {
        val body = """{"status":"completed","output":[
            {"type":"reasoning","summary":[]},
            {"type":"message","role":"assistant","content":[
                {"type":"output_text","text":" Hello world. "}]}]}"""
        assertEquals("Hello world.", AiRequests.parseOpenAiResponse(body))
        assertNull(AiRequests.parseOpenAiResponse("""{"error":{"message":"x"}}"""))
        assertNull(AiRequests.parseOpenAiResponse("ei jsonia"))
        assertNull(AiRequests.parseOpenAiResponse("""{"output":[{"type":"reasoning"}]}"""))
    }

    @Test
    fun `virheviesti tulkitaan molempien palveluiden muodosta`() {
        assertEquals(
            "Incorrect API key provided",
            AiRequests.parseErrorMessage(
                """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}"""
            ),
        )
        assertNull(AiRequests.parseErrorMessage("ei jsonia"))
        assertNull(AiRequests.parseErrorMessage(null))
        assertNull(AiRequests.parseErrorMessage("""{"data":[]}"""))
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
            AiRequests.parseModels(body),
        )
        assertEquals(emptyList<Pair<String, String>>(), AiRequests.parseModels("roska"))
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
        val models = AiRequests.parseOpenAiModels(body).map { it.first }
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
            AiRequests.parseOpenAiModels(body).map { it.first },
        )
    }

    @Test
    fun `sanelumallilista sisaltaa vain puheentunnistusmallit uusin ensin`() {
        val body = """{"data":[
            {"id":"gpt-4o-transcribe","created":2},
            {"id":"gpt-4o-mini-transcribe","created":3},
            {"id":"whisper-1","created":1},
            {"id":"gpt-5-mini","created":9},
            {"id":"gpt-realtime-whisper","created":8},
            {"id":"gpt-realtime-2.1","created":8},
            {"id":"tts-1","created":7}]}"""
        assertEquals(
            listOf(
                "gpt-realtime-whisper",
                "gpt-4o-mini-transcribe",
                "gpt-4o-transcribe",
                "whisper-1",
            ),
            AiRequests.parseTranscribeModels(body).map { it.first },
        )
    }

    @Test
    fun `openai-mallilista rajautuu kahteentoista uusimpaan`() {
        val items = (1..20).joinToString(",") { """{"id":"gpt-m$it","created":$it}""" }
        val models = AiRequests.parseOpenAiModels("""{"data":[$items]}""")
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
            AiRequests.parseModels(body).map { it.first },
        )
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
            AiRequests.parseOpenAiModels(body).map { it.first },
        )
    }
}
