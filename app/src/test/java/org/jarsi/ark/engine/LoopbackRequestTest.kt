package org.jarsi.ark.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class LoopbackRequestTest {

    private fun target(request: String, maxBytes: Int = LoopbackRequest.MAX_BYTES): String? =
        LoopbackRequest.readTarget(ByteArrayInputStream(request.toByteArray()), maxBytes)

    @Test
    fun `selaimen pyynnosta poimitaan polku ja kysely`() {
        val request = "GET /auth/callback?code=ac_1&state=tila HTTP/1.1\r\n" +
            "Host: 127.0.0.1:4321\r\nConnection: keep-alive\r\n\r\n"
        assertEquals("/auth/callback?code=ac_1&state=tila", target(request))
    }

    @Test
    fun `pelkat rivinvaihdot kelpaavat`() {
        assertEquals("/x", target("GET /x HTTP/1.0\nHost: a\n\n"))
    }

    @Test
    fun `pyynto ilman otsakkeiden loppua kelpaa kun virta paattyy`() {
        assertEquals("/x?a=1", target("GET /x?a=1 HTTP/1.1\r\nHost: a\r\n"))
    }

    @Test
    fun `liian pitka pyynto hylataan`() {
        val request = "GET /" + "a".repeat(10_000) + " HTTP/1.1\r\n\r\n"
        assertNull(target(request, maxBytes = 8192))
    }

    @Test
    fun `paattymaton pyyntorivi hylataan`() {
        assertNull(target("GET /auth/callback?code=x HTTP/1.1"))
        assertNull(target(""))
    }

    @Test
    fun `pyyntorivi ilman kohdetta hylataan`() {
        assertNull(target("GET\r\n\r\n"))
        assertNull(target("GET auth/callback HTTP/1.1\r\n\r\n"))
    }
}
