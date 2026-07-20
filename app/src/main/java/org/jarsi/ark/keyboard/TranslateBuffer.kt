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
        builder.insert(cursor, s)
        cursor += s.length
    }

    /** Poistaa grafeemin kursorin edeltä; alussa ei tee mitään. */
    fun backspace(): Boolean {
        if (cursor == 0) return false
        val start = previousBoundary(cursor)
        builder.delete(start, cursor)
        cursor = start
        return true
    }

    fun moveLeft(): Boolean {
        if (cursor == 0) return false
        cursor = previousBoundary(cursor)
        return true
    }

    fun moveRight(): Boolean {
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
}
