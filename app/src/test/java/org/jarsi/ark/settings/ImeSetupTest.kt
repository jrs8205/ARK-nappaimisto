package org.jarsi.ark.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeSetupTest {

    private val paketti = "org.jarsi.ark.nappaimisto"

    @Test
    fun `oma nappaimisto tunnistetaan oletukseksi`() {
        assertTrue(
            ImeSetup.isOwnIme("org.jarsi.ark.nappaimisto/org.jarsi.ark.KeyboardService", paketti)
        )
    }

    @Test
    fun `vieras nappaimisto ei ole oma`() {
        assertFalse(
            ImeSetup.isOwnIme(
                "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
                paketti,
            )
        )
    }

    @Test
    fun `puuttuva arvo ei ole oma`() {
        assertFalse(ImeSetup.isOwnIme(null, paketti))
        assertFalse(ImeSetup.isOwnIme("", paketti))
    }

    @Test
    fun `pelkka paketti ilman komponenttia ei kelpaa`() {
        assertFalse(ImeSetup.isOwnIme("org.jarsi.ark.nappaimisto", paketti))
    }

    @Test
    fun `saman alkuinen vieras paketti ei ole oma`() {
        assertFalse(
            ImeSetup.isOwnIme("org.jarsi.ark.nappaimisto2/com.example.Ime", paketti)
        )
    }
}
