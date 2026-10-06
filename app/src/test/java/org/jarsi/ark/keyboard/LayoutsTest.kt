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
        // Numero ensin (näkyy vihjeenä), perässä numerorivin omat merkit.
        assertEquals(listOf("1", "!"), topRow[0].longPress)
        assertEquals(listOf("2", "\"", "@"), topRow[1].longPress)
        assertEquals(listOf("4", "-", "$", "¤"), topRow[3].longPress)
        assertEquals(listOf("9", ")", "]"), topRow[8].longPress)
        assertEquals(listOf("0", "=", "}"), topRow[9].longPress)
        // å-näppäimelle ei tule numeroa.
        assertTrue(topRow[10].longPress.none { it.first().isDigit() })
    }

    @Test
    fun `ilman numerorivia muut kirjaimet kantavat erikoismerkit`() {
        val layout = Layouts.letters(numberRow = false)
        val middle = layout.rows[1].associate { it.label to it.longPress }
        assertEquals("@", middle.getValue("a").first())
        assertEquals("#", middle.getValue("s").first())
        assertTrue("€" in middle.getValue("d"))
        val bottom = layout.rows[2].associate { it.label to it.longPress }
        assertEquals("*", bottom.getValue("z").first())
        assertEquals("?", bottom.getValue("m").first())
        // Jokaisella kirjaimella on jokin merkki, ettei valikkoa tarvitse arvata.
        val letters = layout.rows.take(3).flatten().filter { it.action is KeyAction.Text }
        assertTrue(letters.all { it.longPress.isNotEmpty() })
    }

    @Test
    fun `numerorivin kanssa kirjaimissa ei ole erikoismerkkeja`() {
        val layout = Layouts.letters()
        val letters = layout.rows.drop(1).take(3).flatten().filter { it.action is KeyAction.Text }
        assertTrue(letters.all { it.longPress.isEmpty() })
    }

    @Test
    fun `numerorivin nelosen pitka painallus antaa ensin viivan`() {
        val four = Layouts.letters().rows[0][3]
        assertEquals("4", four.label)
        assertEquals("-", four.longPress.first())
        assertTrue("¤" in four.longPress)
    }

    @Test
    fun `numeronappaimiston pisteen pitka painallus antaa kaksoispisteen ja kauttaviivan`() {
        val dot = Layouts.numeric.rows[3][0]
        assertEquals(".", dot.label)
        assertEquals(listOf(":", "/"), dot.longPress)
    }

    @Test
    fun `numerorivin kanssa ylarivilla ei ole numeropainalluksia`() {
        val layout = Layouts.letters()
        assertTrue(layout.rows[1].all { it.longPress.isEmpty() })
    }
}
