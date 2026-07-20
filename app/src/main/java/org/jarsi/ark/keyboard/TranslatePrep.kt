package org.jarsi.ark.keyboard

/**
 * Käännettävän tekstin esikäsittely: konekäännös osuu selvästi paremmin
 * kokonaisiin lauseisiin, joissa on iso alkukirjain ja loppuvälimerkki.
 * Lisätty loppupiste riisutaan tuloksesta [clean]-kutsulla, joten kenttään
 * ei päädy välimerkkiä, jota käyttäjä ei kirjoittanut.
 */
object TranslatePrep {

    data class Prepared(val text: String, val addedStop: Boolean)

    private val WHITESPACE = Regex("\\s+")

    fun prepare(text: String): Prepared {
        val compact = text.trim().replace(WHITESPACE, " ")
        if (compact.isEmpty()) return Prepared("", false)
        val capitalized = capitalizeSentences(compact)
        val addStop = capitalized.last().isLetterOrDigit()
        return Prepared(if (addStop) "$capitalized." else capitalized, addStop)
    }

    /**
     * Siistii käännöstuloksen: riisuu [prepare]-vaiheessa lisätyn
     * loppupisteen, kapitalisoi lauseiden alut ja englanniksi
     * käännettäessä yksinäisen i-sanan. Konekäännös palauttaa nämä
     * ajoittain väärin.
     */
    fun clean(result: String, prepared: Prepared, englishTarget: Boolean = false): String {
        var out = result.trimEnd()
        if (prepared.addedStop && out.endsWith(".")) out = out.dropLast(1)
        out = capitalizeSentences(out)
        // Ruotsin tapaisissa kielissä "i" on oma sanansa; korjaus vain
        // englantiin, jossa pikkukirjaiminen minä-sana on aina virhe.
        if (englishTarget) out = ENGLISH_I.replace(out, "I")
        return out
    }

    private val ENGLISH_I = Regex("\\bi\\b")

    private fun capitalizeSentences(text: String): String {
        val out = StringBuilder(text.length)
        // Lauseenraja vahvistuu vasta välimerkkiä seuraavasta välistä,
        // jotta sanan sisäinen piste (jarsi.org) ei aloita uutta lausetta.
        var boundary = true
        var pendingBoundary = false
        for (c in text) {
            when {
                c == '.' || c == '!' || c == '?' || c == '…' -> {
                    pendingBoundary = true
                    out.append(c)
                }
                c.isWhitespace() -> {
                    if (pendingBoundary) {
                        boundary = true
                        pendingBoundary = false
                    }
                    out.append(c)
                }
                else -> {
                    pendingBoundary = false
                    if (boundary && c.isLetter()) {
                        out.append(c.uppercaseChar())
                        boundary = false
                    } else {
                        out.append(c)
                        if (c.isLetterOrDigit()) boundary = false
                    }
                }
            }
        }
        return out.toString()
    }
}
