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
}
