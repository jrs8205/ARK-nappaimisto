package org.jarsi.ark.keyboard

import org.json.JSONArray
import org.json.JSONException

/**
 * Erikoismerkkisivujen merkkijärjestys yhtenä jatkuvana listana:
 * ensimmäiset [PAGE1_COUNT] merkkiä täyttävät ?123-sivun ja loput
 * =\<-sivun, joten raahaus siirtää merkkejä myös sivujen välillä.
 * Käyttäjän järjestys sovitetaan aina nykyiseen merkkivalikoimaan:
 * tuntemattomat ja kaksoiskappaleet siivotaan, uudet merkit lisätään
 * oletusjärjestyksessä loppuun.
 */
object SymbolOrder {

    const val PREF_KEY = "erikoismerkit"
    const val PAGE1_COUNT = 25

    val default: List<String> = listOf(
        "@", "#", "€", "_", "&", "-", "+", "(", ")", "/",
        "*", "\"", "'", ":", ";", "!", "?", "~", "=",
        "%", "[", "]", "{", "}", "\\",
        "¹", "²", "³", "¼", "½", "¾", "§", "°", "|", "•",
        "£", "$", "¥", "¢", "±", "×", "÷", "¬", "¦", "¶",
        "<", ">", "^", "`", "©", "®", "™",
    )

    // Pitkän painalluksen lisämerkit kulkevat merkin mukana paikasta toiseen.
    private val alternates = mapOf(
        "€" to listOf("$", "£", "¥"),
        "-" to listOf("–", "—"),
    )

    fun alternatesFor(symbol: String): List<String> = alternates[symbol].orEmpty()

    fun serialize(order: List<String>): String = JSONArray(order).toString()

    fun load(saved: String?): List<String> {
        if (saved == null) return default
        val parsed = try {
            val array = JSONArray(saved)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: JSONException) {
            return default
        }
        val known = default.toSet()
        val kept = parsed.filter { it in known }.distinct()
        return kept + default.filter { it !in kept }
    }
}
