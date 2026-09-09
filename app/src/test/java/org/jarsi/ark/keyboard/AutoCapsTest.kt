package org.jarsi.ark.keyboard

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCapsTest {

    private val sentences = AutoCaps.SENTENCES
    private val words = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    private val characters = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS

    @Test
    fun `tyhja kentta alkaa isolla kun kentta pyytaa lauseita`() {
        assertTrue(AutoCaps.wanted("", sentences))
        assertTrue(AutoCaps.wanted("   ", sentences))
    }

    @Test
    fun `ilman lippua ei isoa`() {
        assertFalse(AutoCaps.requested(InputType.TYPE_CLASS_TEXT))
        assertFalse(AutoCaps.wanted("", InputType.TYPE_CLASS_TEXT))
        assertFalse(AutoCaps.wanted("Hei. ", InputType.TYPE_CLASS_TEXT))
        assertFalse(AutoCaps.wanted("", InputType.TYPE_NULL))
    }

    @Test
    fun `numerokentan liput eivat ole isoja alkukirjaimia`() {
        // TYPE_NUMBER_FLAG_SIGNED jakaa bitin CAP_CHARACTERS-lipun kanssa.
        val signed = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        assertFalse(AutoCaps.requested(signed))
        assertFalse(AutoCaps.wanted("", signed))
    }

    @Test
    fun `lauseen paattava merkki ja vali aloittavat ison`() {
        assertTrue(AutoCaps.wanted("Hei. ", sentences))
        assertTrue(AutoCaps.wanted("Hei! ", sentences))
        assertTrue(AutoCaps.wanted("Hei? ", sentences))
        assertTrue(AutoCaps.wanted("Hei… ", sentences))
        assertTrue(AutoCaps.wanted("Hei.  ", sentences))
    }

    @Test
    fun `kesken lauseen ei isoa`() {
        assertFalse(AutoCaps.wanted("Hei", sentences))
        assertFalse(AutoCaps.wanted("Hei ", sentences))
        assertFalse(AutoCaps.wanted("Hei.", sentences))
        assertFalse(AutoCaps.wanted("Hei, ", sentences))
        assertFalse(AutoCaps.wanted("Hei: ", sentences))
    }

    @Test
    fun `rivinvaihto aloittaa isolla`() {
        assertTrue(AutoCaps.wanted("Hei\n", sentences))
        assertTrue(AutoCaps.wanted("Hei.\n", sentences))
        assertTrue(AutoCaps.wanted("Hei. \n", sentences))
        assertTrue(AutoCaps.wanted("Hei\n\t", sentences))
        assertTrue(AutoCaps.wanted("Hei\n", words))
    }

    @Test
    fun `sanan sisainen piste on lyhenne`() {
        assertFalse(AutoCaps.wanted("jarsi.org. ", sentences))
        assertTrue(AutoCaps.wanted("Hinta 3.14. ", sentences))
        assertTrue(AutoCaps.wanted("Katso esim. ", sentences))
    }

    @Test
    fun `avaavat merkit eivat katkaise lauseen alkua`() {
        assertTrue(AutoCaps.wanted("Hei. (", sentences))
        assertTrue(AutoCaps.wanted("Hei. \"", sentences))
        assertTrue(AutoCaps.wanted("(", sentences))
        assertFalse(AutoCaps.wanted("Hei (", sentences))
    }

    @Test
    fun `sulkevat merkit kuuluvat lauseen loppuun`() {
        assertTrue(AutoCaps.wanted("(Hei.) ", sentences))
        assertTrue(AutoCaps.wanted("Hei.\" ", sentences))
        assertFalse(AutoCaps.wanted("Hei) ", sentences))
    }

    @Test
    fun `sanakentta isontaa jokaisen sanan`() {
        assertTrue(AutoCaps.wanted("", words))
        assertTrue(AutoCaps.wanted("hei ", words))
        assertTrue(AutoCaps.wanted("hei. ", words))
        assertFalse(AutoCaps.wanted("hei", words))
    }

    @Test
    fun `merkkikentta isontaa aina`() {
        assertTrue(AutoCaps.wanted("hei", characters))
        assertTrue(AutoCaps.wanted("", characters))
    }
}
