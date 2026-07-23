package org.jarsi.ark.dictation

/**
 * Sanelun tekstikirjanpito: deltat kertyvät keskeneräiseksi tekstiksi,
 * ja valmis transkriptio korvaa ne. Suoratoistomallilla koko istunto on
 * yhtä lausumaa (valmis vasta lopetuksessa), palvelimen VAD:lla lausumia
 * valmistuu pitkin matkaa — sama kirjanpito kattaa molemmat.
 */
class DictationTranscript {

    private val pending = StringBuilder()

    /** Keskeneräinen teksti näytettäväksi; deltojen alkuväli siivottu. */
    val partial: String
        get() = pending.toString().trimStart()

    val hasPartial: Boolean
        get() = partial.isNotEmpty()

    /** Lisää deltan ja palauttaa näytettävän keskeneräisen tekstin. */
    fun onDelta(text: String): String {
        pending.append(text)
        return partial
    }

    /** Lausuma valmistui: tyhjentää keskeneräisen ja palauttaa lopullisen. */
    fun onCompleted(text: String): String {
        pending.setLength(0)
        return text.trim()
    }
}
