package org.jarsi.ark.keyboard

import android.view.KeyEvent

/** Näppäinasettelut: suomalainen QWERTY, symbolisivut, numerot ja nuolitila. */
object Layouts {

    private fun key(char: String, longPress: List<String> = emptyList()) =
        Key(KeyAction.Text(char), char, longPress = longPress)

    // Pitkän painalluksen merkit numeroriville suomalaisen fyysisen asettelun mukaan.
    private val numberRow = listOf(
        key("1", listOf("!")),
        key("2", listOf("\"", "@")),
        key("3", listOf("#")),
        key("4", listOf("¤", "$")),
        key("5", listOf("%")),
        key("6", listOf("&")),
        key("7", listOf("/", "\\")),
        key("8", listOf("(", "[")),
        key("9", listOf(")", "]")),
        key("0", listOf("=", "}")),
    )

    // Kirjainnäppäimissä ei ole tarkkeellisia lisämerkkejä: suomessa niitä ei tarvita.
    private val letterRow1 = listOf(
        key("q"), key("w"), key("e"), key("r"), key("t"), key("y"),
        key("u"), key("i"), key("o"), key("p"), key("å"),
    )

    private val letterRow2 = listOf(
        key("a"), key("s"), key("d"), key("f"), key("g"), key("h"),
        key("j"), key("k"), key("l"), key("ö"), key("ä"),
    )

    private val letterRow3 = listOf(
        Key(KeyAction.Shift, "⇧", widthWeight = 1.5f),
        key("z"), key("x"), key("c"), key("v"), key("b"), key("n"), key("m"),
        Key(KeyAction.Backspace, "⌫", widthWeight = 1.5f, repeatable = true),
    )

    private fun bottomRow(extraKey: String?): List<Key> {
        val row = mutableListOf(
            Key(KeyAction.Symbols, "?123", widthWeight = 1.5f),
            key(",", listOf(";", ":")),
        )
        // Kenttäkohtainen lisämerkki omana näppäimenään, jotta pilkku ei koskaan katoa.
        if (extraKey != null) row += key(extraKey)
        row += Key(KeyAction.Space, "", widthWeight = if (extraKey != null) 3f else 4f)
        row += key(".", listOf(",", "?", "!", ":", ";", "…", "-", "\""))
        row += Key(KeyAction.Enter, "⏎", widthWeight = 1.5f)
        return row
    }

    /**
     * Kirjainasettelu. [extraKey] lisää kenttäkohtaisen merkin pilkun viereen,
     * esimerkiksi @ sähköpostikentässä tai / osoitekentässä.
     */
    fun letters(extraKey: String? = null) = KeyboardLayout(
        listOf(numberRow, letterRow1, letterRow2, letterRow3, bottomRow(extraKey))
    )

    val symbols1 = KeyboardLayout(
        listOf(
            numberRow,
            listOf(
                key("@"), key("#"), key("€", listOf("$", "£", "¥")), key("_"), key("&"),
                key("-"), key("+"), key("("), key(")"), key("/"),
            ),
            listOf(
                key("*"), key("\""), key("'"), key(":"), key(";"), key("!"), key("?"),
                key("~"), key("="),
            ),
            listOf(
                Key(KeyAction.SymbolsMore, "=\\<", widthWeight = 1.5f),
                key("%"), key("["), key("]"), key("{"), key("}"), key("\\"),
                Key(KeyAction.Backspace, "⌫", widthWeight = 1.5f, repeatable = true),
            ),
            listOf(
                Key(KeyAction.Letters, "ABC", widthWeight = 1.5f),
                key(","),
                Key(KeyAction.Space, "", widthWeight = 4f),
                key("."),
                Key(KeyAction.Enter, "⏎", widthWeight = 1.5f),
            ),
        )
    )

    val symbols2 = KeyboardLayout(
        listOf(
            listOf(
                key("¹"), key("²"), key("³"), key("¼"), key("½"), key("¾"), key("§"),
                key("°"), key("|"), key("•"),
            ),
            listOf(
                key("£"), key("$"), key("¥"), key("¢"), key("±"), key("×"), key("÷"),
                key("¬"), key("¦"), key("¶"),
            ),
            listOf(
                Key(KeyAction.Symbols, "?123", widthWeight = 1.5f),
                key("©"), key("®"), key("™"), key("«"), key("»"), key("„"),
                Key(KeyAction.Backspace, "⌫", widthWeight = 1.5f, repeatable = true),
            ),
            listOf(
                Key(KeyAction.Letters, "ABC", widthWeight = 1.5f),
                key(","),
                Key(KeyAction.Space, "", widthWeight = 4f),
                key("."),
                Key(KeyAction.Enter, "⏎", widthWeight = 1.5f),
            ),
        )
    )

    /**
     * Nuolitila: isot suuntanäppäimet ristikkona koko näppäinalueelle.
     * Takaisin näppäimistöön palataan työkalurivin nuolikuvakkeesta.
     */
    val arrows = KeyboardLayout(
        listOf(
            listOf(
                Key(KeyAction.None),
                Key(KeyAction.Arrow(KeyEvent.KEYCODE_DPAD_UP), "▲", repeatable = true),
                Key(KeyAction.None),
            ),
            listOf(
                Key(KeyAction.Arrow(KeyEvent.KEYCODE_DPAD_LEFT), "◀", repeatable = true),
                Key(KeyAction.None),
                Key(KeyAction.Arrow(KeyEvent.KEYCODE_DPAD_RIGHT), "▶", repeatable = true),
            ),
            listOf(
                Key(KeyAction.None),
                Key(KeyAction.Arrow(KeyEvent.KEYCODE_DPAD_DOWN), "▼", repeatable = true),
                Key(KeyAction.None),
            ),
        )
    )

    val numeric = KeyboardLayout(
        listOf(
            listOf(key("1"), key("2"), key("3"), Key(KeyAction.Backspace, "⌫", repeatable = true)),
            listOf(key("4"), key("5"), key("6"), key("-", listOf("*", "#"))),
            listOf(key("7"), key("8"), key("9"), key(",")),
            listOf(key("."), key("0"), key("+"), Key(KeyAction.Enter, "⏎")),
        )
    )
}
