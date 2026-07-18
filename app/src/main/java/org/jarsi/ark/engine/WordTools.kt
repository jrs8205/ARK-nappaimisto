package org.jarsi.ark.engine

/** Tekstityökalut: keskeneräisen sanan poiminta kursorin edeltä. */
object WordTools {

    private fun isCore(c: Char) = c.isLetterOrDigit() || c == '-'

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
