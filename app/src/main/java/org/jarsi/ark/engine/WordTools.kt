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
}
