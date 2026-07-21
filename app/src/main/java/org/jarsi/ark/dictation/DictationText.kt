package org.jarsi.ark.dictation

import java.util.Locale

/** Sanellun tekstin muotoilu: istunnon ja lauseiden alut isolla kirjaimella. */
object DictationText {

    private val fiLocale = Locale.forLanguageTag("fi")

    /** Nostaa ensimmäisen merkin isoksi, jos se on pieni kirjain. */
    fun capitalize(text: String): String {
        val index = text.indexOfFirst { !it.isWhitespace() }
        if (index < 0) return text
        val c = text[index]
        if (!c.isLetter() || c.isUpperCase()) return text
        return text.substring(0, index) + c.titlecase(fiLocale) + text.substring(index + 1)
    }

    /** Päättyykö teksti lauseen lopettavaan välimerkkiin. */
    fun endsSentence(text: String): Boolean =
        text.trimEnd().lastOrNull()?.let { it in ".!?…" } == true
}
