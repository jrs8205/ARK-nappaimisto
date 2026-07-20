package org.jarsi.ark.keyboard

/**
 * Älykäs jälkiväli: kun välimerkki on juuri kirjoitettu sanan perään ja
 * seuraavaksi kirjoitetaan kirjain, kirjaimen eteen kuuluu välilyönti —
 * "sana.uusi" muuttuu muotoon "sana. Uusi". Lauseen päättävän merkin
 * jälkeen uusi lause alkaa isolla, jos kenttä sitä pyytää. Numerot eivät
 * laukaise väliä, joten desimaalit (3.14) säilyvät, ja väärä laukaisu
 * (esim. jarsi.org) perutaan askelpalauttimella heti perään.
 */
object SmartSpace {

    /** Välimerkit, joiden perään kirjoitettu kirjain saa välin eteensä. */
    const val PUNCTUATION = ".,!?:;…"

    /** Lauseen päättävät merkit, joiden jälkeen uusi lause alkaa isolla. */
    private const val SENTENCE_ENDERS = ".!?…"

    /** Ratkaisu: väli lisätään; [capitalize] kertoo isonnetaanko kirjain. */
    data class Decision(val capitalize: Boolean)

    fun isPunctuation(c: Char): Boolean = c in PUNCTUATION

    fun isSentenceEnder(c: Char): Boolean = c in SENTENCE_ENDERS

    /**
     * Päättää saako kirjoitettu merkki välin eteensä. [armed] on tosi vain
     * kun välimerkki on juuri kirjoitettu tähän kohtaan, [input] on näppäimen
     * tuottama teksti, [before] kursorin edellä oleva merkki ja [capSentences]
     * kertoo pyytääkö kenttä isoa alkukirjainta lauseen alkuun.
     */
    fun decide(armed: Boolean, input: String, before: Char?, capSentences: Boolean): Decision? {
        if (!armed) return null
        if (input.length != 1 || !input[0].isLetter()) return null
        if (before == null || !isPunctuation(before)) return null
        return Decision(capitalize = capSentences && isSentenceEnder(before))
    }

    /** Pysyykö tila aseistettuna syötteen jälkeen (välimerkkirypäs, esim. "..."). */
    fun rearm(input: String): Boolean = input.length == 1 && isPunctuation(input[0])
}
