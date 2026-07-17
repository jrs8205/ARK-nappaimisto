package org.jarsi.ark.theme

/** Näppäimistön väriteema. */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val specialKey: Int,
    val keyPressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val accentText: Int,
) {
    companion object {
        val TUMMA = KeyboardTheme(
            background = 0xFF14161B.toInt(),
            key = 0xFF2A2E37.toInt(),
            specialKey = 0xFF20232B.toInt(),
            keyPressed = 0xFF3D4350.toInt(),
            text = 0xFFE8EAF0.toInt(),
            hint = 0xFF8A8F9C.toInt(),
            accent = 0xFF4C8DFF.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
        )

        val VAALEA = KeyboardTheme(
            background = 0xFFE9EBEF.toInt(),
            key = 0xFFFFFFFF.toInt(),
            specialKey = 0xFFD4D8E0.toInt(),
            keyPressed = 0xFFBFC6D1.toInt(),
            text = 0xFF1B1D22.toInt(),
            hint = 0xFF6E7480.toInt(),
            accent = 0xFF1B6EF3.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
        )

        val AMOLED = KeyboardTheme(
            background = 0xFF000000.toInt(),
            key = 0xFF15161A.toInt(),
            specialKey = 0xFF0B0C0F.toInt(),
            keyPressed = 0xFF2A2C33.toInt(),
            text = 0xFFE8EAF0.toInt(),
            hint = 0xFF7A7F8C.toInt(),
            accent = 0xFF4C8DFF.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
        )

        fun fromName(name: String?): KeyboardTheme = when (name) {
            "vaalea" -> VAALEA
            "amoled" -> AMOLED
            else -> TUMMA
        }
    }
}
