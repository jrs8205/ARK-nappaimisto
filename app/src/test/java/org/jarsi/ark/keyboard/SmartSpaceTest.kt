package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSpaceTest {

    @Test
    fun `kirjain pisteen jalkeen saa valin ja ison alkukirjaimen`() {
        val decision = SmartSpace.decide(armed = true, input = "u", before = '.', capSentences = true)
        assertEquals(SmartSpace.Decision(capitalize = true), decision)
    }

    @Test
    fun `kirjain pilkun jalkeen saa valin ilman isoa alkukirjainta`() {
        val decision = SmartSpace.decide(armed = true, input = "u", before = ',', capSentences = true)
        assertEquals(SmartSpace.Decision(capitalize = false), decision)
    }

    @Test
    fun `ilman kentan aloituspyyntoa kirjain jaa pieneksi`() {
        val decision = SmartSpace.decide(armed = true, input = "u", before = '.', capSentences = false)
        assertEquals(SmartSpace.Decision(capitalize = false), decision)
    }

    @Test
    fun `lauseen paattajat isontavat ja muut valimerkit eivat`() {
        for (ender in listOf('.', '!', '?', '…')) {
            val decision = SmartSpace.decide(armed = true, input = "u", before = ender, capSentences = true)
            assertEquals("merkki $ender", SmartSpace.Decision(capitalize = true), decision)
        }
        for (other in listOf(',', ':', ';')) {
            val decision = SmartSpace.decide(armed = true, input = "u", before = other, capSentences = true)
            assertEquals("merkki $other", SmartSpace.Decision(capitalize = false), decision)
        }
    }

    @Test
    fun `numero pisteen jalkeen ei laukaise valia`() {
        // Desimaalit kuten 3.14 säilyvät ehjinä.
        assertNull(SmartSpace.decide(armed = true, input = "1", before = '.', capSentences = true))
    }

    @Test
    fun `aseistamaton tila ei laukaise valia`() {
        assertNull(SmartSpace.decide(armed = false, input = "u", before = '.', capSentences = true))
    }

    @Test
    fun `monimerkkinen syote ei laukaise valia`() {
        // Verkko-osoitepalat kuten https:// eivät saa väliä eteensä.
        assertNull(SmartSpace.decide(armed = true, input = "https://", before = '.', capSentences = true))
        assertNull(SmartSpace.decide(armed = true, input = "", before = '.', capSentences = true))
    }

    @Test
    fun `ilman valimerkkia edella ei laukaise valia`() {
        assertNull(SmartSpace.decide(armed = true, input = "u", before = 'a', capSentences = true))
        assertNull(SmartSpace.decide(armed = true, input = "u", before = ' ', capSentences = true))
        assertNull(SmartSpace.decide(armed = true, input = "u", before = null, capSentences = true))
    }

    @Test
    fun `valimerkki pitaa tilan aseistettuna`() {
        // Kolme pistettä peräkkäin: "..." pysyy yhtenä ryppäänä ja vasta
        // sitä seuraava kirjain saa välin.
        assertTrue(SmartSpace.rearm("."))
        assertTrue(SmartSpace.rearm("…"))
        assertTrue(SmartSpace.rearm("!"))
        assertFalse(SmartSpace.rearm("u"))
        assertFalse(SmartSpace.rearm("ab"))
        assertFalse(SmartSpace.rearm(""))
    }

    @Test
    fun `lauseen paattajien tunnistus`() {
        assertTrue(SmartSpace.isSentenceEnder('.'))
        assertTrue(SmartSpace.isSentenceEnder('…'))
        assertFalse(SmartSpace.isSentenceEnder(','))
        assertTrue(SmartSpace.isPunctuation(','))
        assertFalse(SmartSpace.isPunctuation('a'))
    }

    @Test
    fun `kaksoisvalilyonnin pisteelle kelpaavat merkit`() {
        assertTrue(SmartSpace.canEndSentence('a'))
        assertTrue(SmartSpace.canEndSentence('ä'))
        assertTrue(SmartSpace.canEndSentence('9'))
        assertTrue(SmartSpace.canEndSentence(')'))
        assertTrue(SmartSpace.canEndSentence('"'))
        assertFalse(SmartSpace.canEndSentence('.'))
        assertFalse(SmartSpace.canEndSentence(','))
        assertFalse(SmartSpace.canEndSentence(' '))
        assertFalse(SmartSpace.canEndSentence('…'))
    }
}
