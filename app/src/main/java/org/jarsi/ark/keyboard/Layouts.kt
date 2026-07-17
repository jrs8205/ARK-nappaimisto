package org.jarsi.ark.keyboard

/** Näppäinasettelut: suomalainen QWERTY, symbolisivut ja numeronäppäimistö. */
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

    private val letterRow1 = listOf(
        key("q"),
        key("w"),
        key("e", listOf("é", "è", "ê", "ë")),
        key("r"),
        key("t", listOf("þ")),
        key("y", listOf("ý", "ÿ")),
        key("u", listOf("ü", "ú", "ù", "û")),
        key("i", listOf("í", "ì", "î", "ï")),
        key("o", listOf("ó", "ò", "ô", "õ", "œ")),
        key("p"),
        key("å"),
    )

    private val letterRow2 = listOf(
        key("a", listOf("á", "à", "â", "ã", "æ")),
        key("s", listOf("š", "ß", "ś")),
        key("d", listOf("ð")),
        key("f"),
        key("g", listOf("ğ")),
        key("h"),
        key("j"),
        key("k"),
        key("l"),
        key("ö", listOf("ø")),
        key("ä", listOf("æ")),
    )

    private val letterRow3 = listOf(
        Key(KeyAction.Shift, "⇧", widthWeight = 1.5f),
        key("z", listOf("ž", "ź")),
        key("x"),
        key("c", listOf("ç", "ć", "č")),
        key("v"),
        key("b"),
        key("n", listOf("ñ", "ń")),
        key("m", listOf("µ")),
        Key(KeyAction.Backspace, "⌫", widthWeight = 1.5f, repeatable = true),
    )

    private fun bottomRow(extraKey: String?): List<Key> = listOf(
        Key(KeyAction.Symbols, "?123", widthWeight = 1.5f),
        if (extraKey != null) key(extraKey) else key(",", listOf(";", ":")),
        Key(KeyAction.Space, "", widthWeight = 4f),
        key(".", listOf(",", "?", "!", ":", ";", "…", "-", "\"")),
        Key(KeyAction.Enter, "⏎", widthWeight = 1.5f),
    )

    /**
     * Kirjainasettelu. [extraKey] korvaa pilkun kenttäkohtaisella merkillä,
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

    val numeric = KeyboardLayout(
        listOf(
            listOf(key("1"), key("2"), key("3"), Key(KeyAction.Backspace, "⌫", repeatable = true)),
            listOf(key("4"), key("5"), key("6"), key("-", listOf("*", "#"))),
            listOf(key("7"), key("8"), key("9"), key(",")),
            listOf(key("."), key("0"), key("+"), Key(KeyAction.Enter, "⏎")),
        )
    )
}
