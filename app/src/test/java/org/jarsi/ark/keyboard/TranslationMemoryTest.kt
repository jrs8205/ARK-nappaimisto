package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationMemoryTest {

    @Test
    fun `sama teksti ja kielipari palauttaa muistetun kaannoksen`() {
        val memory = TranslationMemory()
        memory.put("Hei, mitä kuuluu?", "fi", "en", "Hi, how are you?", fromAi = true)
        val remembered = memory.get("Hei, mitä kuuluu?", "fi", "en")
        assertEquals("Hi, how are you?", remembered?.translation)
        assertTrue(remembered!!.fromAi)
    }

    @Test
    fun `kasin korjattu konekaannos ei ole AI-kaannos`() {
        val memory = TranslationMemory()
        memory.put("Hei", "fi", "en", "Hello there", fromAi = false)
        assertFalse(memory.get("Hei", "fi", "en")!!.fromAi)
    }

    @Test
    fun `toinen kielipari ei kelpaa`() {
        val memory = TranslationMemory()
        memory.put("Hei", "fi", "en", "Hi", fromAi = true)
        assertNull(memory.get("Hei", "fi", "sv"))
        assertNull(memory.get("Hei", "en", "fi"))
    }

    @Test
    fun `muuttunut teksti ei kelpaa`() {
        val memory = TranslationMemory()
        memory.put("Hei", "fi", "en", "Hi", fromAi = true)
        assertNull(memory.get("Hei vaan", "fi", "en"))
    }

    @Test
    fun `reunojen tyhja tila ei muuta tekstia`() {
        // Älykäs jälkiväli jättää usein välin tekstin perään.
        val memory = TranslationMemory()
        memory.put("Hei, mitä kuuluu? ", "fi", "en", "Hi, how are you?", fromAi = true)
        assertEquals("Hi, how are you?", memory.get("Hei, mitä kuuluu?", "fi", "en")?.translation)
        assertEquals("Hi, how are you?", memory.get(" Hei, mitä kuuluu?\n", "fi", "en")?.translation)
    }

    @Test
    fun `rivinvaihto tekstin sisalla erottaa tekstit`() {
        val memory = TranslationMemory()
        memory.put("eka\ntoka", "fi", "en", "first\nsecond", fromAi = true)
        assertNull(memory.get("eka toka", "fi", "en"))
    }

    @Test
    fun `tyhja kaannos poistaa muistetun`() {
        val memory = TranslationMemory()
        memory.put("Hei", "fi", "en", "Hi", fromAi = true)
        memory.put("Hei", "fi", "en", "  ", fromAi = true)
        assertNull(memory.get("Hei", "fi", "en"))
    }

    @Test
    fun `tyhjaa tekstia ei muisteta`() {
        val memory = TranslationMemory()
        memory.put("  ", "fi", "en", "Hi", fromAi = true)
        assertNull(memory.get("  ", "fi", "en"))
        assertNull(memory.get("", "fi", "en"))
    }

    @Test
    fun `uusi kaannos korvaa vanhan`() {
        val memory = TranslationMemory()
        memory.put("Hei", "fi", "en", "Hi", fromAi = true)
        memory.put("Hei", "fi", "en", "Hello", fromAi = false)
        val remembered = memory.get("Hei", "fi", "en")!!
        assertEquals("Hello", remembered.translation)
        assertFalse(remembered.fromAi)
    }

    @Test
    fun `tayttyessa vahiten kaytetty unohtuu`() {
        val memory = TranslationMemory(capacity = 2)
        memory.put("yksi", "fi", "en", "one", fromAi = true)
        memory.put("kaksi", "fi", "en", "two", fromAi = true)
        // Haku tuoreuttaa: "yksi" on nyt viimeksi käytetty.
        memory.get("yksi", "fi", "en")
        memory.put("kolme", "fi", "en", "three", fromAi = true)
        assertEquals("one", memory.get("yksi", "fi", "en")?.translation)
        assertNull(memory.get("kaksi", "fi", "en"))
        assertEquals("three", memory.get("kolme", "fi", "en")?.translation)
    }

    @Test
    fun `myohastynyt kaannos tallentuu kun merkinta ei muuttunut`() {
        val memory = TranslationMemory()
        // Käsin korjattu konekäännös ennen AI-pyyntöä.
        memory.put("Hei", "fi", "en", "Hello there", fromAi = false)
        val atStart = memory.get("Hei", "fi", "en")
        assertTrue(memory.putIfUnchanged("Hei", "fi", "en", "Hi", fromAi = true, expected = atStart))
        val remembered = memory.get("Hei", "fi", "en")!!
        assertEquals("Hi", remembered.translation)
        assertTrue(remembered.fromAi)
    }

    @Test
    fun `myohastynyt kaannos tallentuu tyhjaan muistiin`() {
        val memory = TranslationMemory()
        assertTrue(memory.putIfUnchanged("Hei", "fi", "en", "Hi", fromAi = true, expected = null))
        assertEquals("Hi", memory.get("Hei", "fi", "en")?.translation)
    }

    @Test
    fun `myohastynyt kaannos ei korvaa pyynnon aikana tehtya korjausta`() {
        val memory = TranslationMemory()
        memory.put("Hei", "fi", "en", "Hello there", fromAi = false)
        val atStart = memory.get("Hei", "fi", "en")
        // Käyttäjä korjaa käännöstä pyynnön ollessa kesken — myös saman
        // tekstin uudelleen kirjoittaminen on uusi korjaus.
        memory.put("Hei", "fi", "en", "Hello there", fromAi = false)
        assertFalse(memory.putIfUnchanged("Hei", "fi", "en", "Hi", fromAi = true, expected = atStart))
        assertEquals("Hello there", memory.get("Hei", "fi", "en")?.translation)
    }

    @Test
    fun `myohastynyt kaannos ei korvaa pyynnon aikana syntynytta merkintaa`() {
        val memory = TranslationMemory()
        memory.put("Hei", "fi", "en", "Hello there", fromAi = false)
        assertFalse(memory.putIfUnchanged("Hei", "fi", "en", "Hi", fromAi = true, expected = null))
        assertEquals("Hello there", memory.get("Hei", "fi", "en")?.translation)
    }

    @Test
    fun `tyhjennys unohtaa kaiken`() {
        val memory = TranslationMemory()
        memory.put("Hei", "fi", "en", "Hi", fromAi = true)
        memory.clear()
        assertNull(memory.get("Hei", "fi", "en"))
    }
}
