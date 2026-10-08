package org.jarsi.ark.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import org.jarsi.ark.R
import org.jarsi.ark.data.ChatGptPlan
import org.jarsi.ark.engine.ChatGptAuth
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Sign in with ChatGPT: avaa kirjautumissivun laitteen selaimeen ja
 * vastaanottaa paluun paikallisessa kuuntelijassa (127.0.0.1, vapaa
 * portti), koska OpenAI sallii avoimen lähdekoodin sovelluksille vain
 * loopback-paluun. Paluun jälkeen koodi vaihdetaan tokeneihin taustalla,
 * tietue tallennetaan ja oletusmalli poimitaan OpenAI:n listan kärjestä.
 * Tallennetulla rekisteröinnillä kirjaudutaan samaan tiliin uudelleen;
 * uloskirjautuminen poistaa rekisteröinnin, jolloin seuraava kirjautuminen
 * rekisteröi laitteen uudestaan.
 */
class ChatGptLoginActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var server: ServerSocket? = null
    private lateinit var status: TextView

    @Volatile
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply {
            text = getString(R.string.chatgpt_kirjautuminen_odottaa)
            textSize = 16f
            setPadding(0, 0, 0, pad)
        }
        val cancel = MaterialButton(this).apply {
            text = getString(android.R.string.cancel)
            setOnClickListener { finish() }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(status)
            addView(
                cancel,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        SettingsUi.install(this, content)
        startLogin()
    }

    private fun startLogin() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val previous = ChatGptPlan.load(prefs)
        // Laitetunnus on rekisteröintikohtainen: uusi rekisteröinti saa
        // uuden, ettei kahta laitetta sekoiteta samaan tunnukseen.
        val hostId = previous?.hostId ?: "urn:uuid:" + UUID.randomUUID()
        val verifier = ChatGptAuth.randomToken()
        val state = ChatGptAuth.randomToken()
        val nonce = ChatGptAuth.randomToken()
        // Nimenomaan IPv4-loopback: getLoopbackAddress() antaa Androidilla
        // ::1-osoitteen, johon 127.0.0.1-paluuosoite ei osu (todettu
        // emulaattorissa 8.10.2026).
        val socket = try {
            ServerSocket(0, 1, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        } catch (e: IOException) {
            fail(e.javaClass.simpleName)
            return
        }
        server = socket
        val redirectUri = "http://127.0.0.1:${socket.localPort}${ChatGptAuth.CALLBACK_PATH}"
        val url = ChatGptAuth.authorizeUrl(
            clientId = previous?.clientId,
            redirectUri = redirectUri,
            state = state,
            nonce = nonce,
            codeChallenge = ChatGptAuth.codeChallenge(verifier),
            hostId = hostId,
            idTokenHint = previous?.idToken,
            loginHint = previous?.email,
        )
        executor.execute {
            serve(socket, prefs, previous, hostId, state, nonce, verifier, redirectUri)
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            fail(getString(R.string.chatgpt_kirjautuminen_ei_selainta))
            return
        }
        mainHandler.postDelayed(
            { if (!done) fail(getString(R.string.chatgpt_kirjautuminen_aikakatkaisu)) },
            TIMEOUT_MS,
        )
    }

    private fun serve(
        socket: ServerSocket,
        prefs: android.content.SharedPreferences,
        previous: ChatGptAuth.Login?,
        hostId: String,
        state: String,
        nonce: String,
        verifier: String,
        redirectUri: String,
    ) {
        val query = try {
            awaitCallback(socket)
        } catch (e: IOException) {
            // Kuuntelija suljettiin (peruutus tai aikakatkaisu).
            return
        } ?: return
        val callback = ChatGptAuth.parseCallback(query, state, previous?.clientId)
        val (code, clientId) = when (callback) {
            is ChatGptAuth.Callback.Ok -> callback.code to callback.clientId
            is ChatGptAuth.Callback.Denied -> return post {
                fail(getString(R.string.chatgpt_kirjautuminen_peruttu))
            }
            ChatGptAuth.Callback.BadState, ChatGptAuth.Callback.Mismatch -> return post {
                fail(getString(R.string.chatgpt_kirjautuminen_vaara_tili))
            }
            ChatGptAuth.Callback.Incomplete -> return post {
                fail(getString(R.string.chatgpt_kirjautuminen_kesken))
            }
        }
        post { status.text = getString(R.string.chatgpt_kirjautuminen_viimeistely) }
        val (_, body) = ChatGptPlan.postForm(
            ChatGptAuth.TOKEN_ENDPOINT,
            ChatGptAuth.tokenExchangeBody(clientId, code, verifier, redirectUri),
        )
        if (body == null) {
            post { fail(getString(R.string.chatgpt_kirjautuminen_verkkovirhe)) }
            return
        }
        val result = ChatGptAuth.parseTokenResponse(
            body, clientId, hostId, nonce, System.currentTimeMillis(), previous,
        )
        when (result) {
            is ChatGptAuth.TokenResult.Error -> post { fail(result.message) }
            is ChatGptAuth.TokenResult.Ok -> {
                // Ilman tilausoikeutta tietue ei auttaisi käännöstä, joten
                // sitä ei tallenneta — käyttäjä kirjautuu uudelleen ja
                // hyväksyy oikeudet.
                if (!result.login.hasPlanScope) {
                    post { fail(getString(R.string.chatgpt_kirjautuminen_ei_oikeutta)) }
                    return
                }
                ChatGptPlan.save(prefs, result.login)
                ChatGptPlan.model(prefs, result.login.accessToken)
                post { succeed(result.login.email) }
            }
        }
    }

    /**
     * Palvelee pyyntöjä kunnes paluupolku saapuu; muut polut (esim.
     * favicon) saavat 404:n. Palauttaa paluun kyselyosan.
     */
    private fun awaitCallback(socket: ServerSocket): String? {
        while (!done) {
            socket.accept().use { client ->
                client.soTimeout = 10_000
                val reader = client.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return@use
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                }
                val target = requestLine.split(" ").getOrNull(1) ?: return@use
                if (target.substringBefore("?") == ChatGptAuth.CALLBACK_PATH) {
                    respond(client, 200, callbackPage())
                    return target.substringAfter("?", "")
                }
                respond(client, 404, "")
            }
        }
        return null
    }

    private fun callbackPage(): String =
        "<!doctype html><html lang=\"fi\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<title>ARK-näppäimistö</title></head>" +
            "<body style=\"font-family:sans-serif;margin:2em\"><h2>ARK-näppäimistö</h2>" +
            "<p>${getString(R.string.chatgpt_paluusivu)}</p></body></html>"

    private fun respond(client: Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Not Found"}\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        client.getOutputStream().use {
            it.write(head.toByteArray(Charsets.US_ASCII))
            it.write(bytes)
            it.flush()
        }
    }

    private fun post(action: () -> Unit) {
        mainHandler.post { if (!isDestroyed) action() }
    }

    private fun succeed(email: String?) {
        done = true
        closeServer()
        Toast.makeText(
            this,
            getString(R.string.chatgpt_kirjautuminen_valmis, email.orEmpty()),
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }

    private fun fail(reason: String) {
        done = true
        closeServer()
        status.text = getString(R.string.chatgpt_kirjautuminen_virhe, reason)
    }

    private fun closeServer() {
        try {
            server?.close()
        } catch (e: IOException) {
        }
        server = null
    }

    override fun onDestroy() {
        done = true
        closeServer()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val TIMEOUT_MS = 5 * 60_000L
    }
}
