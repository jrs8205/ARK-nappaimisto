package org.jarsi.ark.dictation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeEventsTest {

    @Test
    fun `istuntoviesti kuvaa transkription mallin ja kielen`() {
        val json = JSONObject(RealtimeEvents.sessionUpdate("gpt-realtime-whisper", "fi"))
        assertEquals("session.update", json.getString("type"))
        val session = json.getJSONObject("session")
        assertEquals("transcription", session.getString("type"))
        val input = session.getJSONObject("audio").getJSONObject("input")
        assertEquals("audio/pcm", input.getJSONObject("format").getString("type"))
        assertEquals(24_000, input.getJSONObject("format").getInt("rate"))
        val transcription = input.getJSONObject("transcription")
        assertEquals("gpt-realtime-whisper", transcription.getString("model"))
        assertEquals("fi", transcription.getString("language"))
    }

    @Test
    fun `suoratoistomalli ei kayta palvelimen puheentunnistusta`() {
        val json = JSONObject(RealtimeEvents.sessionUpdate("gpt-realtime-whisper", "fi"))
        val input = json.getJSONObject("session").getJSONObject("audio")
            .getJSONObject("input")
        assertTrue(!input.has("turn_detection"))
    }

    @Test
    fun `erapohjainen malli saa palvelimen puheentunnistuksen`() {
        val json = JSONObject(RealtimeEvents.sessionUpdate("gpt-4o-transcribe", "fi"))
        val input = json.getJSONObject("session").getJSONObject("audio")
            .getJSONObject("input")
        assertEquals("server_vad", input.getJSONObject("turn_detection").getString("type"))
    }

    @Test
    fun `viivevalinta kulkee transkription mukana`() {
        val json = JSONObject(
            RealtimeEvents.sessionUpdate("gpt-realtime-whisper", "fi", delay = "high")
        )
        val transcription = json.getJSONObject("session").getJSONObject("audio")
            .getJSONObject("input").getJSONObject("transcription")
        assertEquals("high", transcription.getString("delay"))
    }

    @Test
    fun `aanipala koodataan base64-muotoon`() {
        val json = JSONObject(RealtimeEvents.appendAudio(byteArrayOf(0x01, 0x02)))
        assertEquals("input_audio_buffer.append", json.getString("type"))
        assertEquals("AQI=", json.getString("audio"))
    }

    @Test
    fun `commit-viesti on pelkka tyyppi`() {
        val json = JSONObject(RealtimeEvents.commit())
        assertEquals("input_audio_buffer.commit", json.getString("type"))
    }

    @Test
    fun `naytteet muuntuvat little-endian-tavuiksi`() {
        val bytes = RealtimeEvents.pcmBytes(shortArrayOf(0x0102, -2), 2)
        assertEquals(4, bytes.size)
        assertEquals(0x02, bytes[0].toInt())
        assertEquals(0x01, bytes[1].toInt())
        assertEquals(-2, bytes[2].toInt())
        assertEquals(-1, bytes[3].toInt())
    }

    @Test
    fun `pcm-muunnos rajautuu annettuun pituuteen`() {
        val bytes = RealtimeEvents.pcmBytes(shortArrayOf(1, 2, 3, 4), 2)
        assertEquals(4, bytes.size)
    }

    @Test
    fun `delta-tapahtuma tunnistetaan`() {
        val event = RealtimeEvents.parse(
            """{"type":"conversation.item.input_audio_transcription.delta",
                "item_id":"item_1","delta":"Hei "}"""
        )
        assertEquals(RealtimeEvents.Incoming.Delta("item_1", "Hei "), event)
    }

    @Test
    fun `valmis transkriptio tunnistetaan`() {
        val event = RealtimeEvents.parse(
            """{"type":"conversation.item.input_audio_transcription.completed",
                "item_id":"item_1","transcript":"Hei maailma."}"""
        )
        assertEquals(RealtimeEvents.Incoming.Completed("item_1", "Hei maailma."), event)
    }

    @Test
    fun `epaonnistunut transkriptio kertoo syyn`() {
        val event = RealtimeEvents.parse(
            """{"type":"conversation.item.input_audio_transcription.failed",
                "item_id":"item_1","error":{"message":"kiintiö täynnä"}}"""
        )
        assertEquals(RealtimeEvents.Incoming.Failure("kiintiö täynnä"), event)
    }

    @Test
    fun `virhetapahtuma kertoo syyn`() {
        val event = RealtimeEvents.parse(
            """{"type":"error","error":{"message":"väärä avain"}}"""
        )
        assertEquals(RealtimeEvents.Incoming.Failure("väärä avain"), event)
    }

    @Test
    fun `muut tapahtumat ohitetaan`() {
        assertNull(RealtimeEvents.parse("""{"type":"input_audio_buffer.committed"}"""))
        assertNull(RealtimeEvents.parse("""{"type":"session.updated"}"""))
    }

    @Test
    fun `rikkinainen viesti ohitetaan`() {
        assertNull(RealtimeEvents.parse("ei json"))
        assertNull(RealtimeEvents.parse("""{"delta":"ilman tyyppiä"}"""))
    }
}
