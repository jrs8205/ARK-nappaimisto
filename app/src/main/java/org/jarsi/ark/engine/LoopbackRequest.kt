package org.jarsi.ark.engine

import java.io.InputStream

/**
 * Kirjautumisen paikallisen kuuntelijan HTTP-pyynnön tulkinta. Kuuntelija
 * ottaa vastaan vain selaimen paluun, joten pyynnöstä tarvitaan pelkkä
 * pyyntörivin kohde; otsakkeet luetaan loppuun (tai virran päättymiseen),
 * jotta vastaus menee perille. Koko on rajattu, koska mikä tahansa
 * laitteen sovellus voi kirjoittaa kuuntelijaan loputtomasti.
 */
object LoopbackRequest {

    const val MAX_BYTES = 8192

    /** Pyyntörivin kohde (polku + kysely) tai null, jos pyyntö on virheellinen tai liian pitkä. */
    fun readTarget(input: InputStream, maxBytes: Int = MAX_BYTES): String? {
        val bytes = ByteArray(maxBytes)
        var length = 0
        while (true) {
            if (length == maxBytes) return null
            val b = input.read()
            if (b < 0) break
            bytes[length++] = b.toByte()
            if (length >= 2 && bytes[length - 1] == LF && (bytes[length - 2] == LF ||
                    (length >= 4 && bytes[length - 2] == CR && bytes[length - 3] == LF))
            ) {
                break
            }
        }
        val text = String(bytes, 0, length, Charsets.ISO_8859_1)
        val newline = text.indexOf('\n')
        if (newline < 0) return null
        val parts = text.substring(0, newline).trimEnd('\r').split(' ')
        val target = parts.getOrNull(1) ?: return null
        return target.takeIf { it.startsWith("/") }
    }

    private const val LF = '\n'.code.toByte()
    private const val CR = '\r'.code.toByte()
}
