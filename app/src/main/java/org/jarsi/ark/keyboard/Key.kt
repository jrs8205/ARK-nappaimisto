package org.jarsi.ark.keyboard

/** Näppäimen toiminto: joko tekstin syöttö tai erikoistoiminto. */
sealed interface KeyAction {
    data class Text(val text: String) : KeyAction
    data object Shift : KeyAction
    data object Backspace : KeyAction
    data object Enter : KeyAction
    data object Space : KeyAction
    data object Symbols : KeyAction
    data object SymbolsMore : KeyAction
    data object Letters : KeyAction
}

data class Key(
    val action: KeyAction,
    val label: String = "",
    val widthWeight: Float = 1f,
    val longPress: List<String> = emptyList(),
    val repeatable: Boolean = false,
)

data class KeyboardLayout(val rows: List<List<Key>>)

enum class ShiftState { OFF, SHIFT, CAPS }
