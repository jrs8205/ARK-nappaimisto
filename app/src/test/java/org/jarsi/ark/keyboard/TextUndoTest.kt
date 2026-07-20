package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUndoTest {

    @Test
    fun `kirjattu toimenpide perutaan kun teksti tasmaa`() {
        val undo = TextUndo()
        undo.record("liitetty teksti", "")
        val restore = undo.consume { len -> "liitetty teksti".takeLast(len) }
        assertEquals(TextUndo.Restore("liitetty teksti".length, ""), restore)
    }

    @Test
    fun `korvattu teksti palautetaan`() {
        val undo = TextUndo()
        undo.record("korjattu ", "kirjotettu ")
        val restore = undo.consume { "korjattu " }
        assertEquals(TextUndo.Restore(9, "kirjotettu "), restore)
    }

    @Test
    fun `muuttunut teksti estaa peruutuksen`() {
        val undo = TextUndo()
        undo.record("sana ", "")
        assertNull(undo.consume { "muuta" })
    }

    @Test
    fun `kirjaus kuluu yhdella kaytolla`() {
        val undo = TextUndo()
        undo.record("sana ", "")
        assertTrue(undo.consume { "sana " } != null)
        assertNull(undo.consume { "sana " })
    }

    @Test
    fun `epaonnistunut peruutus kuluttaa kirjauksen`() {
        val undo = TextUndo()
        undo.record("sana ", "")
        assertNull(undo.consume { "muuta" })
        assertNull(undo.consume { "sana " })
    }

    @Test
    fun `ilman kirjausta ei peruta`() {
        assertNull(TextUndo().consume { "mitä vain" })
    }

    @Test
    fun `tyhjennys poistaa kirjauksen`() {
        val undo = TextUndo()
        undo.record("sana ", "")
        undo.clear()
        assertNull(undo.consume { "sana " })
        assertFalse(undo.hasRecord)
    }

    @Test
    fun `uusi kirjaus korvaa vanhan`() {
        val undo = TextUndo()
        undo.record("eka ", "")
        undo.record("toka ", "")
        assertNull(undo.consume { "eka " })
        undo.record("toka ", "")
        assertEquals(TextUndo.Restore(5, ""), undo.consume { "toka " })
    }

    @Test
    fun `puuttuva kenttateksti estaa peruutuksen`() {
        val undo = TextUndo()
        undo.record("sana ", "")
        assertNull(undo.consume { null })
    }

    @Test
    fun `tyhjaa kirjausta ei tallenneta`() {
        val undo = TextUndo()
        undo.record("", "")
        assertFalse(undo.hasRecord)
    }
}
