package org.jarsi.ark.engine

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Sign in with ChatGPT -kirjautumisen puhdas logiikka: OAuth-osoitteet,
 * PKCE, paluuosoitteen tulkinta ja token-pyyntöjen rungot. Kulku on
 * OpenAI:n avoimen lähdekoodin sovelluksille tarkoitettu dynaaminen
 * rekisteröinti: ensimmäinen kirjautuminen lähettää
 * `client_id=dynamic_agent_client`, ja palvelu myöntää laitteelle oman
 * `oaiapp_`-tunnuksen, jota myöhemmät kirjautumiset ja token-päivitykset
 * käyttävät. Verkkoa tai Androidia tämä tiedosto ei kosketa.
 */
object ChatGptAuth {

    const val AUTHORIZE_ENDPOINT = "https://auth.openai.com/api/accounts/authorize"
    const val TOKEN_ENDPOINT = "https://auth.openai.com/api/accounts/oauth/token"
    const val REVOKE_ENDPOINT = "https://auth.openai.com/api/accounts/oauth/revoke"
    const val RESOURCE = "https://api.openai.com/v1"
    const val CALLBACK_PATH = "/auth/callback"
    const val SCOPE =
        "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"

    private const val DYNAMIC_CLIENT = "dynamic_agent_client"
    private const val APP_NAME = "ARK-näppäimistö"

    private val random = SecureRandom()

    private const val ISSUER = "https://auth.openai.com"
    private const val PLAN_SCOPE = "chatgpt.tokens.use.direct"

    // Päivitys tehdään hieman ennen vanhenemista, ettei kesken pyynnön
    // vanheneva token kaada käännöstä.
    private const val REFRESH_MARGIN_MS = 60_000L

    /**
     * Laitteen rekisteröinti ja tilin tokenit. Tallennetaan salattuna
     * ApiKeyStoreen; sähköposti näytetään asetuksissa ja annetaan
     * vihjeeksi uudelleenkirjautumiseen. Saman tilin `sub` on eri joka
     * rekisteröinnillä, joten tiliä ei tunnisteta sillä.
     */
    data class Login(
        val clientId: String,
        val hostId: String,
        val email: String?,
        val accessToken: String,
        val refreshToken: String,
        val idToken: String?,
        val expiresAt: Long,
        val scopes: List<String>,
    ) {
        val hasPlanScope: Boolean get() = PLAN_SCOPE in scopes

        fun needsRefresh(now: Long): Boolean = now >= expiresAt - REFRESH_MARGIN_MS

        fun toJson(): String = JSONObject()
            .put("client_id", clientId)
            .put("host_id", hostId)
            .put("email", email ?: JSONObject.NULL)
            .put("access_token", accessToken)
            .put("refresh_token", refreshToken)
            .put("id_token", idToken ?: JSONObject.NULL)
            .put("expires_at", expiresAt)
            .put("scopes", JSONArray(scopes))
            .toString()

        companion object {
            fun fromJson(json: String): Login? = try {
                val o = JSONObject(json)
                val scopes = o.optJSONArray("scopes")
                Login(
                    clientId = o.getString("client_id"),
                    hostId = o.getString("host_id"),
                    email = o.optString("email").takeIf { !o.isNull("email") && it.isNotEmpty() },
                    accessToken = o.getString("access_token"),
                    refreshToken = o.getString("refresh_token"),
                    idToken = o.optString("id_token").takeIf { !o.isNull("id_token") && it.isNotEmpty() },
                    expiresAt = o.getLong("expires_at"),
                    scopes = (0 until (scopes?.length() ?: 0)).map { scopes!!.getString(it) },
                )
            } catch (e: JSONException) {
                null
            }
        }
    }

    /** Token-vastauksen tulkinnan tulos. */
    sealed class TokenResult {
        data class Ok(val login: Login) : TokenResult()

        /**
         * [signInRequired]: palvelu hylkäsi myönnön pysyvästi (refresh-token
         * kumottu tai vanhentunut), joten vain uusi kirjautuminen auttaa;
         * muuten vika voi olla tilapäinen eikä tallennettuja tokeneita
         * pidä hävittää.
         */
        data class Error(val message: String, val signInRequired: Boolean) : TokenResult()
    }

    private val PERMANENT_GRANT_ERRORS = setOf(
        "invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired",
        "refresh_token_invalidated", "refresh_token_reused", "invalid_client",
    )

    /**
     * Tulkitsee token-päästä saadun rungon (koodin vaihto tai päivitys).
     * ID-token tarkistetaan myöntäjän, yleisön, nonce-arvon ja vanhenemisen
     * osalta; allekirjoitusta ei tarkisteta, koska token saadaan suoraan
     * token-päästä TLS-yhteydellä eikä selaimen kautta. Päivityksessä
     * [nonce] on null ja puuttuvat kentät peritään [previous]-tietueesta.
     */
    fun parseTokenResponse(
        body: String,
        clientId: String,
        hostId: String,
        nonce: String?,
        now: Long,
        previous: Login?,
    ): TokenResult {
        val o = try {
            JSONObject(body)
        } catch (e: JSONException) {
            return TokenResult.Error("vastaus ei ole JSON", signInRequired = false)
        }
        if (o.has("error") && !o.has("access_token")) {
            val code = o.optString("error")
            val reason = o.optString("error_reason").takeIf { it.isNotEmpty() }
            val description = o.optString("error_description").takeIf { it.isNotEmpty() }
            val permanent = code in PERMANENT_GRANT_ERRORS || reason in PERMANENT_GRANT_ERRORS
            return TokenResult.Error(description ?: reason ?: code, permanent)
        }
        val accessToken = o.optString("access_token").takeIf { it.isNotEmpty() }
            ?: return TokenResult.Error("access_token puuttuu", signInRequired = false)
        val refreshToken = o.optString("refresh_token").takeIf { it.isNotEmpty() }
            ?: previous?.refreshToken
            ?: return TokenResult.Error("refresh_token puuttuu", signInRequired = false)
        val idToken = o.optString("id_token").takeIf { it.isNotEmpty() }
        var email = previous?.email
        if (idToken != null) {
            val claims = jwtPayload(idToken)
                ?: return TokenResult.Error("id_token ei kelpaa", signInRequired = false)
            if (claims.optString("iss") != ISSUER) {
                return TokenResult.Error("id_token: väärä myöntäjä", signInRequired = false)
            }
            val audience = claims.opt("aud")
            val audienceOk = when (audience) {
                is JSONArray -> (0 until audience.length()).any { audience.optString(it) == clientId }
                else -> audience?.toString() == clientId
            }
            if (!audienceOk) return TokenResult.Error("id_token: väärä yleisö", signInRequired = false)
            if (nonce != null && claims.optString("nonce") != nonce) {
                return TokenResult.Error("id_token: nonce ei täsmää", signInRequired = false)
            }
            if (claims.optLong("exp") * 1000 <= now) {
                return TokenResult.Error("id_token on vanhentunut", signInRequired = false)
            }
            claims.optString("email").takeIf { it.isNotEmpty() }?.let { email = it }
        }
        val expiresIn = o.optLong("expires_in", 3600)
        val scopes = o.optString("scope").split(" ").filter { it.isNotEmpty() }
            .ifEmpty { previous?.scopes.orEmpty() }
        return TokenResult.Ok(
            Login(
                clientId = clientId,
                hostId = hostId,
                email = email,
                accessToken = accessToken,
                refreshToken = refreshToken,
                idToken = idToken ?: previous?.idToken,
                expiresAt = now + expiresIn * 1000,
                scopes = scopes,
            )
        )
    }

    private fun jwtPayload(token: String): JSONObject? = try {
        val parts = token.split(".")
        if (parts.size < 2) {
            null
        } else {
            JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
        }
    } catch (e: Exception) {
        null
    }

    /** Paluuosoitteen tulkinnan tulos. */
    sealed class Callback {
        data class Ok(val code: String, val clientId: String) : Callback()
        data class Denied(val error: String) : Callback()
        object BadState : Callback()
        object Mismatch : Callback()
        object Incomplete : Callback()
    }

    /**
     * Kirjautumissivun osoite. [clientId] null = uusi rekisteröinti;
     * tallennetulla tunnuksella mukaan menevät vihjeet aiemmasta
     * kirjautumisesta, jotta sama tili esivalitaan.
     */
    fun authorizeUrl(
        clientId: String?,
        redirectUri: String,
        state: String,
        nonce: String,
        codeChallenge: String,
        hostId: String,
        idTokenHint: String?,
        loginHint: String?,
    ): String {
        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to (clientId ?: DYNAMIC_CLIENT),
            "redirect_uri" to redirectUri,
            "scope" to SCOPE,
            "resource" to RESOURCE,
            "state" to state,
            "nonce" to nonce,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "ext_agent_host_id" to hostId,
        )
        if (clientId == null) {
            params["agent_name_hint"] = APP_NAME
        } else {
            idTokenHint?.let { params["id_token_hint"] = it }
            loginHint?.let { params["login_hint"] = it }
        }
        return AUTHORIZE_ENDPOINT + "?" + form(params)
    }

    /** Satunnainen base64url-merkkijono (state, nonce, PKCE-verifier). */
    fun randomToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** PKCE S256 -haaste verifieristä. */
    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Tulkitsee selaimen paluupyynnön kyselyosan. Uusi rekisteröinti vaatii
     * myönnetyn tunnuksen paluussa; tallennetulla tunnuksella paluu ei
     * yleensä sisällä sitä, mutta eri tunnus hylätään, ettei toisen
     * rekisteröinnin tokeneita sekoiteta tallennettuun.
     */
    fun parseCallback(query: String, expectedState: String, savedClientId: String?): Callback {
        val p = parseQuery(query)
        if (p["state"] != expectedState) return Callback.BadState
        p["error"]?.let { return Callback.Denied(it) }
        val code = p["code"]?.takeIf { it.isNotEmpty() } ?: return Callback.Incomplete
        val issued = p["client_id"]
        if (savedClientId == null) {
            return if (issued.isNullOrEmpty()) Callback.Incomplete else Callback.Ok(code, issued)
        }
        if (issued != null && issued != savedClientId) return Callback.Mismatch
        return Callback.Ok(code, savedClientId)
    }

    fun tokenExchangeBody(
        clientId: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): String = form(
        linkedMapOf(
            "grant_type" to "authorization_code",
            "client_id" to clientId,
            "code" to code,
            "code_verifier" to codeVerifier,
            "redirect_uri" to redirectUri,
            "resource" to RESOURCE,
        )
    )

    // Scope jätetään pois, jotta aiempi myöntö säilyy sellaisenaan.
    fun refreshBody(clientId: String, refreshToken: String): String = form(
        linkedMapOf(
            "grant_type" to "refresh_token",
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "resource" to RESOURCE,
        )
    )

    fun revokeBody(clientId: String, refreshToken: String): String = form(
        linkedMapOf(
            "token" to refreshToken,
            "token_type_hint" to "refresh_token",
            "client_id" to clientId,
        )
    )

    private fun form(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            k + "=" + URLEncoder.encode(v, "UTF-8")
        }

    private fun parseQuery(query: String): Map<String, String> =
        query.split("&").filter { it.isNotEmpty() }.associate {
            val key = it.substringBefore("=")
            val value = it.substringAfter("=", "")
            key to java.net.URLDecoder.decode(value, "UTF-8")
        }
}
