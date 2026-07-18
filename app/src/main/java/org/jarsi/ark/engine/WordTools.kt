package org.jarsi.ark.engine

/** Tekstityökalut: keskeneräisen sanan poiminta kursorin edeltä. */
object WordTools {

    private fun isWordChar(c: Char) = c.isLetter() || c == '-'

    /**
     * Palauttaa kursoria välittömästi edeltävän keskeneräisen sanan tai tyhjän,
     * jos kursorin edellä on sananraja. Sana on kirjaimia ja yhdysmerkkejä;
     * numero tai muu merkki katkaisee sanan.
     */
    fun currentWord(textBefore: CharSequence): String {
        var start = textBefore.length
        while (start > 0 && isWordChar(textBefore[start - 1])) start--
        return textBefore.subSequence(start, textBefore.length)
            .toString()
            .trimStart('-')
    }
}
