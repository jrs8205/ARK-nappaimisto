package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {

    @Test
    fun `kirjainasettelussa on oletuksena numerorivi`() {
        val layout = Layouts.letters()
        assertEquals(5, layout.rows.size)
        assertEquals("1", layout.rows[0].first().label)
    }

    @Test
    fun `ilman numerorivia rivit vahenevat ja numerot siirtyvat pitkiin painalluksiin`() {
        val layout = Layouts.letters(numberRow = false)
        assertEquals(4, layout.rows.size)
        val topRow = layout.rows[0]
        assertEquals("q", topRow.first().label)
        assertEquals(listOf("1"), topRow[0].longPress)
        assertEquals(listOf("9"), topRow[8].longPress)
        assertEquals(listOf("0"), topRow[9].longPress)
        // å-näppäimelle ei tule numeroa.
        assertTrue(topRow[10].longPress.isEmpty())
    }

    @Test
    fun `numerorivin kanssa ylarivilla ei ole numeropainalluksia`() {
        val layout = Layouts.letters()
        assertTrue(layout.rows[1].all { it.longPress.isEmpty() })
    }
}
