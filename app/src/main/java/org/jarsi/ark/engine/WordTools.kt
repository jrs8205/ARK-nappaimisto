package org.jarsi.ark.engine

import kotlin.math.abs

/** Tekstityökalut: keskeneräisen sanan poiminta kursorin edeltä. */
object WordTools {

    private fun isCore(c: Char) = c.isLetterOrDigit() || c == '-'

    private val NON_WORD = Regex("[^\\p{L}\\p{N}.-]+")

    /** Pilkkoo tekstin sanoiksi esimerkiksi sanellun lauseen oppimista varten. */
    fun words(text: CharSequence): List<String> =
        text.split(NON_WORD)
            .map { it.trim('-', '.') }
            .filter { it.isNotEmpty() }

    /**
     * Palauttaa kursoria välittömästi edeltävän keskeneräisen sanan tai
     * tyhjän, jos kursorin edellä on sananraja. Sanassa voi olla kirjaimia,
     * numeroita, yhdysmerkkejä ja sisäisiä pisteitä (jarsi.org); lauseen
     * lopettava piste ei tartu sanaan.
     */
    fun currentWord(textBefore: CharSequence): String {
        var start = textBefore.length
        while (start > 0) {
            val c = textBefore[start - 1]
            val internalDot = c == '.' && start < textBefore.length &&
                isCore(textBefore[start]) && start >= 2 && isCore(textBefore[start - 2])
            if (isCore(c) || internalDot) start-- else break
        }
        return textBefore.subSequence(start, textBefore.length).toString()
            .trimStart('-', '.')
    }

    /**
     * Ehdotusrivin lopullinen lista: kirjoitettava sana nousee kärkeen
     * sellaisenaan, jos sitä ei jo ole ehdotuksissa, jotta sen voi hyväksyä
     * napauttamalla. Asut erotellaan, jotta napautus säilyttää kirjoitetun
     * kirjainkoon.
     */
    fun withTypedWord(typed: String, suggestions: List<String>): List<String> =
        if (typed.isEmpty() || typed in suggestions) suggestions
        else listOf(typed) + suggestions

    /**
     * Kursoria välittömästi seuraava sanan loppuosa tai tyhjä, jos kursorin
     * jälkeen on sananraja. Sisäinen piste kuuluu jatkoon (jarsi|.org),
     * lauseen lopettava piste ei.
     */
    fun continuationAfter(textAfter: CharSequence): String {
        var end = 0
        while (end < textAfter.length) {
            val c = textAfter[end]
            val internalDot = c == '.' && end + 1 < textAfter.length &&
                isCore(textAfter[end + 1])
            if (isCore(c) || internalDot) end++ else break
        }
        return textAfter.subSequence(0, end).toString().trimEnd('-', '.')
    }

    /**
     * Damerau–Levenshtein-etäisyys, jossa viereisten merkkien vaihdos on
     * yksi muokkaus. Laskenta katkeaa rajaan: paluuarvo on [max] + 1 heti,
     * kun etäisyys ylittää sen.
     */
    fun editDistanceAtMost(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        val la = a.length
        val lb = b.length
        if (abs(la - lb) > max) return max + 1
        var prev2 = IntArray(0)
        var prev = IntArray(lb + 1) { it }
        for (i in 1..la) {
            val cur = IntArray(lb + 1)
            cur[0] = i
            var rowMin = i
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, prev2[j - 2] + 1)
                }
                cur[j] = value
                if (value < rowMin) rowMin = value
            }
            if (rowMin > max) return max + 1
            prev2 = prev
            prev = cur
        }
        return if (prev[lb] <= max) prev[lb] else max + 1
    }

    /**
     * Palauttaa enintään [count] valmista sanaa kursorin keskeneräisen sanan
     * edeltä aikajärjestyksessä (lähin viimeisenä). Erottimet — välilyönnit,
     * rivinvaihdot ja välimerkit — ohitetaan, joten ennustus toimii myös
     * rivinvaihtojen yli.
     */
    fun previousWords(textBefore: CharSequence, count: Int = 2): List<String> {
        val words = ArrayDeque<String>()
        var end = textBefore.length - currentWord(textBefore).length
        while (words.size < count && end > 0) {
            while (end > 0 && !isCore(textBefore[end - 1])) end--
            if (end == 0) break
            val word = currentWord(textBefore.subSequence(0, end))
            if (word.isEmpty()) break
            words.addFirst(word)
            end -= word.length
        }
        return words.toList()
    }
}
