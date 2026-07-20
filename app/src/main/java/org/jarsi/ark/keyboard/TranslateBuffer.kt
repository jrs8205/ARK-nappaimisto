package org.jarsi.ark.keyboard

import java.text.BreakIterator

/**
 * Käännösrivin muokattava teksti kursoreineen: lisäys ja poisto tapahtuvat
 * kursorin kohdalla ja kursori liikkuu grafeemi kerrallaan, joten monen
 * koodiyksikön merkit (emojit) käsitellään aina kokonaisina.
 */
class TranslateBuffer {

    private val builder = StringBuilder()

    var cursor: Int = 0
        private set

    val text: String get() = builder.toString()

    fun isEmpty(): Boolean = builder.isEmpty()

    fun isNotEmpty(): Boolean = builder.isNotEmpty()

    fun insert(s: String) {
        smartRevert = null
        builder.insert(cursor, s)
        cursor += s.length
    }

    // Älykkään lisäyksen peruutustieto: askelpalautin palauttaa
    // esimerkiksi " O":n takaisin "o":ksi, jotta jarsi.org onnistuu.
    private var smartRevert: SmartRevert? = null

    private data class SmartRevert(
        val cursorAfter: Int,
        val original: String,
        val insertedLength: Int,
    )

    /**
     * Lisää näppäillyn tekstin älykkäästi: kirjain suoraan lauseen
     * päättävän välimerkin perään saa välin eteensä ja ison
     * alkukirjaimen. Askelpalautin peruu muunnoksen, joten osoitteet
     * kuten jarsi.org onnistuvat. Numeroiden perässä sääntö ei laukea
     * (3.14). Välin jälkeinen iso kirjain tulee shift-tilasta, joka
     * näkyy käyttäjälle nuolen värissä.
     */
    fun smartInsert(s: String) {
        if (s.length == 1 && s[0].isLetter()) {
            val before = builder.substring(0, cursor)
            val last = before.lastOrNull()
            val digitBefore = before.length >= 2 &&
                before[before.lastIndex - 1].isDigit()
            val inserted = when {
                last == null || digitBefore -> null
                last in SENTENCE_END -> " " + s.uppercase()
                last in PAUSE_MARKS -> " $s"
                else -> null
            }
            if (inserted != null) {
                insert(inserted)
                smartRevert = SmartRevert(cursor, s, inserted.length)
                return
            }
        }
        insert(s)
    }

    /** Poistaa grafeemin kursorin edeltä; alussa ei tee mitään. */
    fun backspace(): Boolean {
        smartRevert?.let { revert ->
            smartRevert = null
            if (cursor == revert.cursorAfter) {
                val start = cursor - revert.insertedLength
                builder.replace(start, cursor, revert.original)
                cursor = start + revert.original.length
                return true
            }
        }
        if (cursor == 0) return false
        val start = previousBoundary(cursor)
        builder.delete(start, cursor)
        cursor = start
        return true
    }

    fun moveLeft(): Boolean {
        smartRevert = null
        if (cursor == 0) return false
        cursor = previousBoundary(cursor)
        return true
    }

    fun moveRight(): Boolean {
        smartRevert = null
        if (cursor >= builder.length) return false
        cursor = nextBoundary(cursor)
        return true
    }

    /** Siirtää kursoria [steps] grafeemia (negatiivinen vasemmalle). */
    fun move(steps: Int) {
        if (steps < 0) {
            repeat(-steps) { if (!moveLeft()) return }
        } else {
            repeat(steps) { if (!moveRight()) return }
        }
    }

    fun moveToStart() {
        cursor = 0
    }

    fun moveToEnd() {
        cursor = builder.length
    }

    /** Siirtää kursorin kohtaan [index] lähimpään grafeemirajaan rajattuna. */
    fun setCursor(index: Int) {
        smartRevert = null
        val clamped = index.coerceIn(0, builder.length)
        val iterator = characterIterator()
        cursor = if (iterator.isBoundary(clamped)) {
            clamped
        } else {
            iterator.preceding(clamped).takeIf { it != BreakIterator.DONE } ?: 0
        }
    }

    fun clear() {
        builder.setLength(0)
        cursor = 0
    }

    override fun toString(): String = text

    private fun characterIterator(): BreakIterator =
        BreakIterator.getCharacterInstance().also { it.setText(builder.toString()) }

    private fun previousBoundary(index: Int): Int =
        characterIterator().preceding(index).takeIf { it != BreakIterator.DONE } ?: 0

    private fun nextBoundary(index: Int): Int =
        characterIterator().following(index).takeIf { it != BreakIterator.DONE } ?: builder.length

    private companion object {
        const val SENTENCE_END = ".!?…"

        // Tauottavat välimerkit saavat välin, mutta eivät isoa kirjainta.
        const val PAUSE_MARKS = ",;:"
    }
}
