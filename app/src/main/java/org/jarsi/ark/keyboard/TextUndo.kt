package org.jarsi.ark.keyboard

/**
 * Viimeisimmän näppäimistön tekemän tekstitoimenpiteen (liittäminen,
 * ehdotuksen valinta, automaattikorjaus, sanelu, käännös) kertaperuutus.
 * Peruutus tehdään vain, jos kentän teksti kursorin edellä on yhä juuri
 * se mikä toimenpiteestä jäi — muuten kirjaus hylätään, eikä tekstiä
 * kosketa arvaamalla.
 */
class TextUndo {

    /** Peruutus: poista [deleteLength] merkkiä ja kirjoita [restore] tilalle. */
    data class Restore(val deleteLength: Int, val restore: String)

    private var committed: String? = null
    private var replaced: String = ""

    val hasRecord: Boolean get() = committed != null

    /** Kirjaa toimenpiteen: kenttään meni [committedText] ja alta poistui [replacedText]. */
    fun record(committedText: String, replacedText: String = "") {
        if (committedText.isEmpty()) return
        committed = committedText
        replaced = replacedText
    }

    fun clear() {
        committed = null
        replaced = ""
    }

    /**
     * Kuluttaa kirjauksen ja palauttaa peruutuksen, jos [textBefore] antaa
     * kursorin edeltä täsmälleen kirjatun tekstin. Kirjaus kuluu myös
     * epäonnistuessa: vanhentunutta peruutusta ei jäädä odottamaan.
     */
    fun consume(textBefore: (Int) -> CharSequence?): Restore? {
        val expected = committed ?: return null
        val restoreText = replaced
        clear()
        val before = textBefore(expected.length)?.toString() ?: return null
        if (before != expected) return null
        return Restore(expected.length, restoreText)
    }
}
