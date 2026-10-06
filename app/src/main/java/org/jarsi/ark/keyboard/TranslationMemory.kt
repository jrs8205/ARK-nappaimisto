package org.jarsi.ark.keyboard

/**
 * Muistaa konekäännöstä paremmat käännökset — AI-käännökset ja käsin
 * korjatut — lähdetekstin ja kieliparin mukaan. Kun sama teksti avautuu
 * uudelleen, muistettu käännös näytetään konekäännöksen sijaan eikä
 * maksullista AI-pyyntöä tarvitse tehdä uudestaan.
 *
 * Muisti elää vain prosessin muistissa kuten käännösnäkymän puskurikin.
 * Reunojen tyhjä tila ei erota tekstejä (älykäs jälkiväli jättää välin
 * perään), mutta rivinvaihdot tekstin sisällä erottavat.
 */
class TranslationMemory(private val capacity: Int = DEFAULT_CAPACITY) {

    /**
     * Muistettu käännös. [fromAi] kertoo, onko se AI-palvelun tekemä
     * (mahdollisesti käsin korjattuna): vain silloin uusi AI-pyyntö
     * samasta tekstistä olisi turha.
     */
    data class Remembered(val translation: String, val fromAi: Boolean)

    private data class Key(val text: String, val source: String, val target: String)

    // Käyttöjärjestyksessä: vähiten käytetty on ensimmäisenä.
    private val entries = LinkedHashMap<Key, Remembered>(16, 0.75f, true)

    fun get(text: String, source: String, target: String): Remembered? {
        val key = key(text, source, target) ?: return null
        return entries[key]
    }

    /** Tallentaa käännöksen; tyhjä käännös unohtaa tekstin muistetun. */
    fun put(text: String, source: String, target: String, translation: String, fromAi: Boolean) {
        val key = key(text, source, target) ?: return
        if (translation.isBlank()) {
            entries.remove(key)
            return
        }
        entries[key] = Remembered(translation, fromAi)
        while (entries.size > capacity) {
            entries.remove(entries.keys.first())
        }
    }

    /**
     * Tallentaa käännöksen vain, jos tekstin merkintä on yhä täsmälleen
     * [expected] (sama olio kuin pyynnön alkaessa, tai molemmat puuttuvat).
     * Myöhästynyt AI-vastaus ei näin korvaa pyynnön aikana tehtyä korjausta,
     * mutta ei myöskään hukkaa maksettua tulosta, kun muistissa oli jo
     * ennen pyyntöä tehty korjaus. Palauttaa, tallentuiko käännös.
     */
    fun putIfUnchanged(
        text: String,
        source: String,
        target: String,
        translation: String,
        fromAi: Boolean,
        expected: Remembered?,
    ): Boolean {
        if (get(text, source, target) !== expected) return false
        put(text, source, target, translation, fromAi)
        return true
    }

    fun clear() {
        entries.clear()
    }

    private fun key(text: String, source: String, target: String): Key? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return Key(trimmed, source, target)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 20
    }
}
