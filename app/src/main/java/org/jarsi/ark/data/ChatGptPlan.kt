package org.jarsi.ark.data

import android.content.SharedPreferences
import androidx.core.content.edit
import org.jarsi.ark.engine.AiRequests
import org.jarsi.ark.engine.ChatGptAuth
import java.net.HttpURLConnection
import java.net.URL

/**
 * ChatGPT-tilauksen kirjautumisen säilytys ja token-pään kutsut.
 * Tietue tallennetaan ApiKeyStoreen salattuna eikä se kuulu
 * varmuuskopioon (rekisteröinti on laitekohtainen). Verkkokutsut ovat
 * synkronisia ja kuuluvat taustasäikeeseen.
 */
object ChatGptPlan {

    /**
     * Tilaustilan mallivalinta omalla avaimellaan: tilauksen mallijoukko
     * on eri kuin API-avaimen (esim. gpt-5-mini ei kelpaa tilauksella).
     */
    const val PREF_MODEL = "chatgpt_tilaus_malli"

    private const val MODELS_ENDPOINT = "https://api.openai.com/v1/models"

    sealed class Access {
        data class Ok(val login: ChatGptAuth.Login) : Access()
        data class Failed(val message: String, val signInRequired: Boolean) : Access()
    }

    fun load(prefs: SharedPreferences): ChatGptAuth.Login? =
        ApiKeyStore.read(prefs, ApiKeyStore.Slot.CHATGPT_LOGIN)
            ?.let { ChatGptAuth.Login.fromJson(it) }

    fun exists(prefs: SharedPreferences): Boolean =
        ApiKeyStore.exists(prefs, ApiKeyStore.Slot.CHATGPT_LOGIN)

    fun save(prefs: SharedPreferences, login: ChatGptAuth.Login): Boolean =
        ApiKeyStore.save(prefs, login.toJson(), ApiKeyStore.Slot.CHATGPT_LOGIN)

    fun clear(prefs: SharedPreferences) {
        ApiKeyStore.save(prefs, "", ApiKeyStore.Slot.CHATGPT_LOGIN)
        prefs.edit { remove(PREF_MODEL) }
    }

    /**
     * Voimassa oleva tietue, tarvittaessa päivitettynä. Päivitykset
     * sarjallistetaan, koska refresh-token kiertyy joka päivityksessä
     * eikä kahta rinnakkaista saa päästää samalle tokenille.
     */
    @Synchronized
    fun access(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Access {
        val login = load(prefs)
            ?: return Access.Failed("ei kirjautumista", signInRequired = true)
        if (!login.needsRefresh(now)) return Access.Ok(login)
        return refresh(prefs, login, now)
    }

    /**
     * Token-päivitys. Pysyvästi hylätty myöntö (kumottu tai vanhentunut
     * refresh-token) poistaa tietueen, jotta käyttäjä ohjataan uuteen
     * kirjautumiseen; tilapäinen vika säilyttää sen.
     */
    @Synchronized
    fun refresh(
        prefs: SharedPreferences,
        login: ChatGptAuth.Login,
        now: Long = System.currentTimeMillis(),
    ): Access {
        val (_, body) = postForm(
            ChatGptAuth.TOKEN_ENDPOINT,
            ChatGptAuth.refreshBody(login.clientId, login.refreshToken),
        )
        if (body == null) return Access.Failed("verkkovirhe", signInRequired = false)
        return when (
            val result = ChatGptAuth.parseTokenResponse(
                body, login.clientId, login.hostId, nonce = null, now = now, previous = login,
            )
        ) {
            is ChatGptAuth.TokenResult.Ok -> {
                save(prefs, result.login)
                Access.Ok(result.login)
            }
            is ChatGptAuth.TokenResult.Error -> {
                if (result.signInRequired) clear(prefs)
                Access.Failed(result.message, result.signInRequired)
            }
        }
    }

    /** Tilauksen mallilista tuoreella tokenilla: (lista, virhe). */
    fun models(prefs: SharedPreferences): Pair<List<Pair<String, String>>?, String?> {
        val login = when (val access = access(prefs)) {
            is Access.Ok -> access.login
            is Access.Failed -> return null to access.message
        }
        val (status, body) = get(MODELS_ENDPOINT, login.accessToken)
        if (status !in 200..299) {
            return null to (AiRequests.parseErrorMessage(body) ?: "HTTP $status")
        }
        return AiRequests.parseChatGptPlanModels(body.orEmpty()) to null
    }

    /**
     * Käännöksessä käytettävä malli: käyttäjän valinta tai OpenAI:n
     * listan ensimmäinen, joka tallennetaan valinnaksi. Mallin nimeä ei
     * ole koodattu kiinteästi, jotta uudet sukupolvet tulevat käyttöön
     * ilman sovelluspäivitystä.
     */
    fun model(prefs: SharedPreferences, accessToken: String): String? {
        prefs.getString(PREF_MODEL, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val (_, body) = get(MODELS_ENDPOINT, accessToken)
        val first = AiRequests.parseChatGptPlanModels(body.orEmpty()).firstOrNull()?.first
            ?: return null
        prefs.edit { putString(PREF_MODEL, first) }
        return first
    }

    /** Kumoaa refresh-tokenin palvelimella; paikallinen poisto tehdään erikseen. */
    fun revoke(login: ChatGptAuth.Login): Boolean =
        postForm(
            ChatGptAuth.REVOKE_ENDPOINT,
            ChatGptAuth.revokeBody(login.clientId, login.refreshToken),
        ).first in 200..299

    /** Lomake-POST: (HTTP-tila, runko). Tila 0 = yhteys epäonnistui. */
    fun postForm(url: String, form: String): Pair<Int, String?> = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            readReply(connection)
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        0 to null
    }

    private fun get(url: String, bearer: String): Pair<Int, String?> = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("authorization", "Bearer $bearer")
            readReply(connection)
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        0 to null
    }

    private fun readReply(connection: HttpURLConnection): Pair<Int, String?> {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return status to stream?.bufferedReader()?.use { it.readText() }
    }
}
