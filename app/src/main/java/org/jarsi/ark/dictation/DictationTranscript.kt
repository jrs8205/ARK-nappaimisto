package org.jarsi.ark.dictation

/**
 * Sanelun tekstikirjanpito lausumittain: kunkin lausuman deltat kertyvät
 * keskeneräiseksi tekstiksi ja valmis transkriptio korvaa ne. Palvelimen
 * VAD:lla lausumia voi olla käynnissä limittäin eikä valmistumisjärjestystä
 * taata — teksti toimitetaan silti aina aloitusjärjestyksessä: myöhemmin
 * alkanut valmis lausuma odottaa, kunnes aiemmat ovat valmiit.
 * Suoratoistomallilla koko istunto on yhtä lausumaa, jolloin kirjanpito
 * toimii yhtenä puskurina.
 */
class DictationTranscript {

    private class Item {
        val pending = StringBuilder()
        var completed: String? = null
        val text: String
            get() = (completed ?: pending.toString()).trim()
    }

    // Aloitusjärjestys = lausuman ensimmäisen tapahtuman saapumisjärjestys.
    private val items = LinkedHashMap<String, Item>()
    private val delivered = HashSet<String>()

    /** Toimittamaton teksti näytettäväksi lausumajärjestyksessä. */
    val partial: String
        get() = items.values.map { it.text }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    val hasPartial: Boolean
        get() = partial.isNotEmpty()

    /** Lisää lausuman deltan ja palauttaa näytettävän keskeneräisen tekstin. */
    fun onDelta(itemId: String, text: String): String {
        if (itemId !in delivered) {
            items.getOrPut(itemId) { Item() }.pending.append(text)
        }
        return partial
    }

    /**
     * Lausuma valmistui: palauttaa kenttään toimitettavan tekstin eli jonon
     * kärjestä kaikki valmiit lausumat aloitusjärjestyksessä, tai tyhjän
     * kun aiempi lausuma on vielä kesken ja toimitus odottaa sitä.
     */
    fun onCompleted(itemId: String, text: String): String {
        if (itemId in delivered) return ""
        items.getOrPut(itemId) { Item() }.completed = text.trim()
        val ready = mutableListOf<String>()
        val iterator = items.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val done = entry.value.completed ?: break
            if (done.isNotEmpty()) ready += done
            delivered += entry.key
            iterator.remove()
        }
        return ready.joinToString(" ")
    }

    /** Istunto päättyy: palauttaa kaiken toimittamattoman ja tyhjentää. */
    fun flush(): String {
        val rest = partial
        items.clear()
        return rest
    }
}
