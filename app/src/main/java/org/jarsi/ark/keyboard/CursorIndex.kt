package org.jarsi.ark.keyboard

/**
 * Käännösnäkymän tekstialueet piirtävät kursorin leveydettömän
 * paikkamerkin päälle, joten näytetyssä tekstissä on yksi merkki
 * enemmän kuin puskurissa. Nämä muunnokset kulkevat näytön ja tekstin
 * indeksien välillä molempiin suuntiin; [cursor] -1 tarkoittaa, ettei
 * alueella ole kohdistusta eikä paikkamerkkiä.
 */
object CursorIndex {

    /** Näytetyn tekstin indeksi tekstin omaksi indeksiksi. */
    fun toText(offset: Int, cursor: Int): Int =
        if (cursor >= 0 && offset > cursor) offset - 1 else offset

    /**
     * Tekstin indeksi näytetyn tekstin indeksiksi. [boundaryAfter] koskee
     * rajaa, joka kuuluu paikkamerkin eteen — valinnan loppurajaa.
     */
    fun toDisplay(index: Int, cursor: Int, boundaryAfter: Boolean = false): Int {
        if (cursor < 0) return index
        val shift = if (boundaryAfter) index > cursor else index >= cursor
        return index + if (shift) 1 else 0
    }
}
