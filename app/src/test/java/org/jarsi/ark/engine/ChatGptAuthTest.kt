package org.jarsi.ark.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class ChatGptAuthTest {

    private fun params(url: String): Map<String, String> =
        url.substringAfter("?").split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    @Test
    fun `uusi rekisterointi kayttaa dynaamista asiakastunnusta ja sovelluksen nimea`() {
        val url = ChatGptAuth.authorizeUrl(
            clientId = null,
            redirectUri = "http://127.0.0.1:4321/auth/callback",
            state = "tila",
            nonce = "kertakayttö",
            codeChallenge = "haaste",
            hostId = "urn:uuid:1",
            idTokenHint = null,
            loginHint = null,
        )
        assertTrue(url.startsWith(ChatGptAuth.AUTHORIZE_ENDPOINT + "?"))
        val p = params(url)
        assertEquals("dynamic_agent_client", p["client_id"])
        assertEquals("ARK-näppäimistö", p["agent_name_hint"])
        assertEquals("code", p["response_type"])
        assertEquals("http://127.0.0.1:4321/auth/callback", p["redirect_uri"])
        assertEquals(
            "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct",
            p["scope"],
        )
        assertEquals("https://api.openai.com/v1", p["resource"])
        assertEquals("tila", p["state"])
        assertEquals("kertakayttö", p["nonce"])
        assertEquals("haaste", p["code_challenge"])
        assertEquals("S256", p["code_challenge_method"])
        assertEquals("urn:uuid:1", p["ext_agent_host_id"])
        assertNull(p["id_token_hint"])
        assertNull(p["login_hint"])
    }

    @Test
    fun `uudelleenkirjautuminen kayttaa tallennettua tunnusta ilman nimivihjetta`() {
        val url = ChatGptAuth.authorizeUrl(
            clientId = "oaiapp_abc",
            redirectUri = "http://127.0.0.1:1/auth/callback",
            state = "s",
            nonce = "n",
            codeChallenge = "c",
            hostId = "urn:uuid:1",
            idTokenHint = "eyJ.id.token",
            loginHint = "user@example.com",
        )
        val p = params(url)
        assertEquals("oaiapp_abc", p["client_id"])
        assertNull(p["agent_name_hint"])
        assertEquals("eyJ.id.token", p["id_token_hint"])
        assertEquals("user@example.com", p["login_hint"])
    }

    @Test
    fun `pkce-haaste on verifierin sha256 base64url ilman taytetta`() {
        // RFC 7636 -liitteen B esimerkki.
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            ChatGptAuth.codeChallenge(verifier),
        )
    }

    @Test
    fun `satunnaiset arvot ovat base64url-muotoisia ja eri joka kerta`() {
        val a = ChatGptAuth.randomToken()
        val b = ChatGptAuth.randomToken()
        assertTrue(a.length >= 32)
        assertTrue(a.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(a != b)
    }

    @Test
    fun `paluu uudesta rekisteroinnista antaa koodin ja myonnetyn tunnuksen`() {
        val result = ChatGptAuth.parseCallback(
            "code=ac_1&scope=openid+email&state=tila&client_id=oaiapp_x",
            expectedState = "tila",
            savedClientId = null,
        )
        assertEquals(ChatGptAuth.Callback.Ok("ac_1", "oaiapp_x"), result)
    }

    @Test
    fun `paluu ilman tunnusta uudessa rekisteroinnissa on keskeneräinen`() {
        val result = ChatGptAuth.parseCallback(
            "code=ac_1&state=tila",
            expectedState = "tila",
            savedClientId = null,
        )
        assertEquals(ChatGptAuth.Callback.Incomplete, result)
    }

    @Test
    fun `uudelleenkirjautumisen paluu ilman tunnusta kayttaa tallennettua`() {
        val result = ChatGptAuth.parseCallback(
            "code=ac_2&state=tila",
            expectedState = "tila",
            savedClientId = "oaiapp_saved",
        )
        assertEquals(ChatGptAuth.Callback.Ok("ac_2", "oaiapp_saved"), result)
    }

    @Test
    fun `eri tunnus uudelleenkirjautumisessa hylataan`() {
        val result = ChatGptAuth.parseCallback(
            "code=ac_2&state=tila&client_id=oaiapp_other",
            expectedState = "tila",
            savedClientId = "oaiapp_saved",
        )
        assertEquals(ChatGptAuth.Callback.Mismatch, result)
    }

    @Test
    fun `vaara state hylataan ennen muita tarkistuksia`() {
        val result = ChatGptAuth.parseCallback(
            "code=ac_1&state=muu&client_id=oaiapp_x",
            expectedState = "tila",
            savedClientId = null,
        )
        assertEquals(ChatGptAuth.Callback.BadState, result)
    }

    @Test
    fun `kayttajan hylkaama kirjautuminen tunnistetaan`() {
        val result = ChatGptAuth.parseCallback(
            "error=access_denied&error_description=User+denied&state=tila",
            expectedState = "tila",
            savedClientId = null,
        )
        assertEquals(ChatGptAuth.Callback.Denied("access_denied"), result)
    }

    @Test
    fun `koodin vaihtopyynto on lomakemuotoinen ilman salaisuutta`() {
        val body = ChatGptAuth.tokenExchangeBody(
            clientId = "oaiapp_x",
            code = "ac 1",
            codeVerifier = "ver",
            redirectUri = "http://127.0.0.1:4321/auth/callback",
        )
        val p = params("?$body")
        assertEquals("authorization_code", p["grant_type"])
        assertEquals("oaiapp_x", p["client_id"])
        assertEquals("ac 1", p["code"])
        assertEquals("ver", p["code_verifier"])
        assertEquals("http://127.0.0.1:4321/auth/callback", p["redirect_uri"])
        assertEquals("https://api.openai.com/v1", p["resource"])
        assertFalse("client_secret" in body)
    }

    @Test
    fun `paivityspyynto kayttaa tallennettua tunnusta ja refresh-tokenia`() {
        val p = params("?" + ChatGptAuth.refreshBody("oaiapp_x", "rt.1.abc"))
        assertEquals("refresh_token", p["grant_type"])
        assertEquals("oaiapp_x", p["client_id"])
        assertEquals("rt.1.abc", p["refresh_token"])
        assertEquals("https://api.openai.com/v1", p["resource"])
        assertNull(p["scope"])
    }

    @Test
    fun `kumoamispyynto nimeaa refresh-tokenin`() {
        val p = params("?" + ChatGptAuth.revokeBody("oaiapp_x", "rt.1.abc"))
        assertEquals("rt.1.abc", p["token"])
        assertEquals("refresh_token", p["token_type_hint"])
        assertEquals("oaiapp_x", p["client_id"])
    }

    // --- token-vastaus ja kirjautumistietue ---

    private fun jwt(payload: String): String {
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"RS256"}""".toByteArray())
        return header + "." + enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    private val now = 1_791_474_648_000L

    private fun tokenBody(idToken: String?, scope: String = ChatGptAuth.SCOPE) = buildString {
        append("""{"access_token":"at","expires_in":3600,"token_type":"Bearer",""")
        append(""""scope":"$scope","refresh_token":"rt.1.x","earliest_refresh_at":1""")
        if (idToken != null) append(""","id_token":"$idToken"""")
        append("}")
    }

    private val validId = jwt(
        """{"iss":"https://auth.openai.com","aud":["oaiapp_x"],"nonce":"n1",
           "email":"user@example.com","exp":${now / 1000 + 3600},"sub":"abc"}"""
    )

    @Test
    fun `token-vastauksesta syntyy kirjautumistietue`() {
        val result = ChatGptAuth.parseTokenResponse(
            tokenBody(validId), clientId = "oaiapp_x", hostId = "urn:uuid:1",
            nonce = "n1", now = now, previous = null,
        )
        val login = (result as ChatGptAuth.TokenResult.Ok).login
        assertEquals("oaiapp_x", login.clientId)
        assertEquals("urn:uuid:1", login.hostId)
        assertEquals("user@example.com", login.email)
        assertEquals("at", login.accessToken)
        assertEquals("rt.1.x", login.refreshToken)
        assertEquals(validId, login.idToken)
        assertEquals(now + 3_600_000L, login.expiresAt)
        assertTrue(login.hasPlanScope)
    }

    @Test
    fun `ilman tilausoikeutta tietue ei kelpaa kaannokseen`() {
        val result = ChatGptAuth.parseTokenResponse(
            tokenBody(validId, scope = "openid profile email"), "oaiapp_x", "urn:uuid:1",
            nonce = "n1", now = now, previous = null,
        )
        assertFalse((result as ChatGptAuth.TokenResult.Ok).login.hasPlanScope)
    }

    @Test
    fun `vaara nonce hylataan`() {
        val result = ChatGptAuth.parseTokenResponse(
            tokenBody(validId), "oaiapp_x", "urn:uuid:1", nonce = "toinen", now = now, previous = null,
        )
        assertTrue(result is ChatGptAuth.TokenResult.Error)
    }

    @Test
    fun `vaara yleiso tai myontaja hylataan`() {
        val wrongAud = jwt(
            """{"iss":"https://auth.openai.com","aud":"oaiapp_other","nonce":"n1","exp":${now / 1000 + 10}}"""
        )
        assertTrue(
            ChatGptAuth.parseTokenResponse(
                tokenBody(wrongAud), "oaiapp_x", "h", "n1", now, null,
            ) is ChatGptAuth.TokenResult.Error
        )
        val wrongIss = jwt(
            """{"iss":"https://evil.example","aud":["oaiapp_x"],"nonce":"n1","exp":${now / 1000 + 10}}"""
        )
        assertTrue(
            ChatGptAuth.parseTokenResponse(
                tokenBody(wrongIss), "oaiapp_x", "h", "n1", now, null,
            ) is ChatGptAuth.TokenResult.Error
        )
    }

    @Test
    fun `aud voi olla merkkijono`() {
        val stringAud = jwt(
            """{"iss":"https://auth.openai.com","aud":"oaiapp_x","nonce":"n1","email":"a@b.c","exp":${now / 1000 + 10}}"""
        )
        val result = ChatGptAuth.parseTokenResponse(tokenBody(stringAud), "oaiapp_x", "h", "n1", now, null)
        assertTrue(result is ChatGptAuth.TokenResult.Ok)
    }

    @Test
    fun `paivitys ilman id-tokenia sailyttaa aiemman sahkopostin ja tunnuksen`() {
        val first = (ChatGptAuth.parseTokenResponse(
            tokenBody(validId), "oaiapp_x", "urn:uuid:1", "n1", now, null,
        ) as ChatGptAuth.TokenResult.Ok).login
        val refreshed = ChatGptAuth.parseTokenResponse(
            """{"access_token":"at2","expires_in":3600,"scope":"${ChatGptAuth.SCOPE}","refresh_token":"rt.1.y"}""",
            "oaiapp_x", "urn:uuid:1", nonce = null, now = now + 1000, previous = first,
        )
        val login = (refreshed as ChatGptAuth.TokenResult.Ok).login
        assertEquals("at2", login.accessToken)
        assertEquals("rt.1.y", login.refreshToken)
        assertEquals("user@example.com", login.email)
        assertEquals(validId, login.idToken)
    }

    @Test
    fun `paivitys ilman uutta refresh-tokenia pitaa vanhan`() {
        val first = (ChatGptAuth.parseTokenResponse(
            tokenBody(validId), "oaiapp_x", "urn:uuid:1", "n1", now, null,
        ) as ChatGptAuth.TokenResult.Ok).login
        val refreshed = ChatGptAuth.parseTokenResponse(
            """{"access_token":"at2","expires_in":3600,"scope":"${ChatGptAuth.SCOPE}"}""",
            "oaiapp_x", "urn:uuid:1", null, now, first,
        )
        assertEquals("rt.1.x", (refreshed as ChatGptAuth.TokenResult.Ok).login.refreshToken)
    }

    @Test
    fun `virhevastaus token-paasta antaa syyn`() {
        val result = ChatGptAuth.parseTokenResponse(
            """{"error":"invalid_grant","error_reason":"refresh_token_invalidated",
               "error_description":"The refresh token has been invalidated."}""",
            "oaiapp_x", "h", null, now, null,
        )
        val error = result as ChatGptAuth.TokenResult.Error
        assertTrue(error.message.contains("invalidated"))
        assertTrue(error.signInRequired)
    }

    @Test
    fun `tilapainen virhe ei vaadi uutta kirjautumista`() {
        val result = ChatGptAuth.parseTokenResponse("ei jsonia", "oaiapp_x", "h", null, now, null)
        val error = result as ChatGptAuth.TokenResult.Error
        assertFalse(error.signInRequired)
    }

    @Test
    fun `tietue sailyy json-kierroksen lapi`() {
        val login = (ChatGptAuth.parseTokenResponse(
            tokenBody(validId), "oaiapp_x", "urn:uuid:1", "n1", now, null,
        ) as ChatGptAuth.TokenResult.Ok).login
        assertEquals(login, ChatGptAuth.Login.fromJson(login.toJson()))
        assertNull(ChatGptAuth.Login.fromJson("rikki"))
        assertNull(ChatGptAuth.Login.fromJson("{}"))
    }

    @Test
    fun `paivitys tarvitaan minuuttia ennen vanhenemista`() {
        val login = (ChatGptAuth.parseTokenResponse(
            tokenBody(validId), "oaiapp_x", "urn:uuid:1", "n1", now, null,
        ) as ChatGptAuth.TokenResult.Ok).login
        assertFalse(login.needsRefresh(now + 3_600_000L - 61_000L))
        assertTrue(login.needsRefresh(now + 3_600_000L - 59_000L))
    }
}
