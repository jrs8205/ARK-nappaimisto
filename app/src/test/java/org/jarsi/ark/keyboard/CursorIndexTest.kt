package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorIndexTest {

    @Test
    fun `kursorin edessa indeksit sailyvat`() {
        assertEquals(0, CursorIndex.toText(offset = 0, cursor = 3))
        assertEquals(3, CursorIndex.toText(offset = 3, cursor = 3))
    }

    @Test
    fun `kursorin jaljessa paikkamerkki siirtaa yhdella`() {
        assertEquals(3, CursorIndex.toText(offset = 4, cursor = 3))
        assertEquals(6, CursorIndex.toText(offset = 7, cursor = 3))
    }

    @Test
    fun `ilman kursoria naytto vastaa tekstia`() {
        assertEquals(4, CursorIndex.toText(offset = 4, cursor = -1))
        assertEquals(4, CursorIndex.toDisplay(index = 4, cursor = -1))
    }

    @Test
    fun `tekstin indeksi naytolle kursorin molemmin puolin`() {
        assertEquals(2, CursorIndex.toDisplay(index = 2, cursor = 3))
        // Kursorin kohdalla paikkamerkki tulee ensin.
        assertEquals(4, CursorIndex.toDisplay(index = 3, cursor = 3))
        assertEquals(6, CursorIndex.toDisplay(index = 5, cursor = 3))
    }

    @Test
    fun `valinnan loppuraja osuu paikkamerkin jalkeen`() {
        // Kursorin kohdalle päättyvä valinta ulottuu paikkamerkin eteen.
        assertEquals(3, CursorIndex.toDisplay(index = 3, cursor = 3, boundaryAfter = true))
        assertEquals(5, CursorIndex.toDisplay(index = 4, cursor = 3, boundaryAfter = true))
    }

    @Test
    fun `muunnokset ovat toistensa kaanteiset`() {
        val cursor = 2
        listOf(0, 1, 2, 3, 5).forEach { index ->
            assertEquals(index, CursorIndex.toText(CursorIndex.toDisplay(index, cursor), cursor))
        }
    }
}
