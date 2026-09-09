package org.jarsi.ark.keyboard

import android.text.InputType

/**
 * Automaattinen iso alkukirjain päätellään itse kursorin edeltävästä
 * tekstistä, kuten Gboard tekee, eikä kysytä kentältä (getCursorCapsMode):
 * moni sovellus ei toteuta kyselyä tai vastaa vanhentuneesta tekstistä
 * heti näppäimistön oman muokkauksen jälkeen. Säännöt seuraavat Androidin
 * TextUtils.getCapsMode-menetelmää, mutta lauseen päättävät merkit ovat
 * samat kuin muualla näppäimistössä (myös kolme pistettä).
 */
object AutoCaps {

    private const val CAP_FLAGS = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
        InputType.TYPE_TEXT_FLAG_CAP_WORDS or
        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

    /** Tavallisen tekstin liput, kun kenttää ei ole (käännösnäkymä). */
    const val SENTENCES = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

    /**
     * Pyytääkö kenttä isoa alkukirjainta lainkaan. Vain tekstikentät:
     * numerokenttien lipuilla on samat bittiarvot eri merkityksessä.
     */
    fun requested(inputType: Int): Boolean =
        inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputType and CAP_FLAGS != 0

    /** Kuuluuko seuraavan kirjaimen olla iso, kun [before] on kursorin edellä. */
    fun wanted(before: CharSequence, inputType: Int): Boolean {
        if (!requested(inputType)) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) return true
        val sentences = inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
        // Avaavat lainaus- ja sulkumerkit eivät katkaise: lauseen alku on
        // iso myös sulkeen sisällä.
        var i = before.length
        while (i > 0 && isOpening(before[i - 1])) i--
        // Kappaleen alku, edellä mahdollisesti välejä.
        var j = i
        while (j > 0 && (before[j - 1] == ' ' || before[j - 1] == '\t')) j--
        if (j == 0 || before[j - 1] == '\n') return true
        // Sanakentässä jokainen väli aloittaa ison; lausekentässä väli on
        // vasta edellytys.
        if (!sentences) return i != j
        if (i == j) return false
        while (j > 0 && isClosing(before[j - 1])) j--
        if (j == 0 || !SmartSpace.isSentenceEnder(before[j - 1])) return false
        // Piste sanan sisällä (jarsi.org., e.g.) on lyhenne, ei lauseen loppu.
        var k = j - 2
        while (k >= 0) {
            val c = before[k]
            if (c == '.') return false
            if (!c.isLetter()) break
            k--
        }
        return true
    }

    private fun isOpening(c: Char): Boolean =
        c == '"' || c == '\'' || Character.getType(c) == Character.START_PUNCTUATION.toInt()

    private fun isClosing(c: Char): Boolean =
        c == '"' || c == '\'' || Character.getType(c) == Character.END_PUNCTUATION.toInt()
}
