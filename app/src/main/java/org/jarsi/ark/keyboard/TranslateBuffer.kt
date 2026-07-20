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
     * Lisää näppäillyn tekstin älykkäästi: kirjain lauseen päättävän
     * välimerkin perään saa välin eteensä ja ison alkukirjaimen, ja
     * rivin alkuun kirjoitettu kirjain alkaa isolla. Askelpalautin
     * peruu muunnoksen. Numeroiden perässä sääntö ei laukea (3.14).
     */
    fun smartInsert(s: String) {
        if (s.length == 1 && s[0].isLetter()) {
            val before = builder.substring(0, cursor)
            val trimmed = before.trimEnd(' ')
            val afterSentence = trimmed.isNotEmpty() &&
                trimmed.last() in SENTENCE_END &&
                (trimmed.length < 2 || !trimmed[trimmed.lastIndex - 1].isDigit())
            if (afterSentence) {
                val space = if (before.length == trimmed.length) " " else ""
                val inserted = space + s.uppercase()
                insert(inserted)
                smartRevert = SmartRevert(cursor, s, inserted.length)
                return
            }
            if (before.isBlank() && s[0].isLowerCase()) {
                insert(s.uppercase())
                smartRevert = SmartRevert(cursor, s, 1)
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
    }
}
