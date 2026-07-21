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
    // Numerorivin ollessa piilossa numerot löytyvät ylärivin pitkällä painalluksella.
    private val letterChars1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "å")

    private fun letterRow1(numberRow: Boolean) = letterChars1.mapIndexed { i, c ->
        key(c, if (!numberRow && i < 10) listOf("${(i + 1) % 10}") else emptyList())
    }

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
     * esimerkiksi @ sähköpostikentässä tai / osoitekentässä. [numberRow]
     * piilottaa numerorivin; numerot jäävät ?123-sivulle ja ylärivin
     * pitkiin painalluksiin.
     */
    fun letters(extraKey: String? = null, numberRow: Boolean = true) = KeyboardLayout(
        buildList {
            if (numberRow) add(Layouts.numberRow)
            add(letterRow1(numberRow))
            add(letterRow2)
            add(letterRow3)
            add(bottomRow(extraKey))
        }
    )

    private fun symbolKey(symbol: String) = key(symbol, SymbolOrder.alternatesFor(symbol))

    private fun backspace() =
        Key(KeyAction.Backspace, "⌫", widthWeight = 1.5f, repeatable = true)

    private val symbolBottomRow = listOf(
        Key(KeyAction.Letters, "ABC", widthWeight = 1.5f),
        key(","),
        Key(KeyAction.Space, "", widthWeight = 4f),
        key("."),
        Key(KeyAction.Enter, "⏎", widthWeight = 1.5f),
    )

    /** ?123-sivu: järjestyksen ensimmäiset merkit. */
    fun symbols1(order: List<String>): KeyboardLayout {
        val symbols = if (order.size == SymbolOrder.default.size) order else SymbolOrder.default
        return KeyboardLayout(
            listOf(
                numberRow,
                symbols.subList(0, 10).map(::symbolKey),
                symbols.subList(10, 19).map(::symbolKey),
                listOf(Key(KeyAction.SymbolsMore, "=\\<", widthWeight = 1.5f)) +
                    symbols.subList(19, 25).map(::symbolKey) + backspace(),
                symbolBottomRow,
            )
        )
    }

    /** =\<-sivu: järjestyksen loput merkit. */
    fun symbols2(order: List<String>): KeyboardLayout {
        val symbols = if (order.size == SymbolOrder.default.size) order else SymbolOrder.default
        return KeyboardLayout(
            listOf(
                symbols.subList(25, 35).map(::symbolKey),
                symbols.subList(35, 45).map(::symbolKey),
                listOf(Key(KeyAction.Symbols, "?123", widthWeight = 1.5f)) +
                    symbols.subList(45, 52).map(::symbolKey) + backspace(),
                symbolBottomRow,
            )
        )
    }

    /** Verkko-osoitesivu: osoitteiden alut ja yleisimmät päätteet. Avataan työkaluriviltä. */
    val symbols3 = KeyboardLayout(
        listOf(
            listOf(key("https://"), key("http://"), key("www.")),
            listOf(key(".fi"), key(".com"), key(".net"), key(".org")),
            listOf(
                Key(KeyAction.Symbols, "?123", widthWeight = 1.5f),
                key(".io"), key(".eu"), key(".info"),
                backspace(),
            ),
            symbolBottomRow,
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
