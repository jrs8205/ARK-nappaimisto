package org.jarsi.ark.keyboard

import kotlin.math.min

/**
 * Näppäimistön korkeuden mitoitus. Käyttäjän korkeusasetus on pystysuunnan
 * koko; rivit kutistuvat vain, kun koko paketti (työkalurivi, ehdotusrivi,
 * viisi näppäinriviä ja navigointipalkin varaus) ylittäisi
 * [MAX_WINDOW_FRACTION] ikkunan korkeudesta, eli vaakasuunnassa ja pienillä
 * näytöillä. Suhde on Gboardin vaakasuunnan mitta (267 dp / 411 dp Pixel 8a:lla).
 */
object KeyboardHeight {
    const val MAX_WINDOW_FRACTION = 0.65f
    const val MIN_SCALE = 0.5f
    const val TOOLBAR_DP = 40f
    const val SUGGESTION_BAR_DP = 44f
    const val BOTTOM_PADDING_DP = 4f

    // Kirjainsivu numerorivin kanssa on korkein tavallinen sivu; sama
    // rivimäärä kaikille sivuille pitää skaalan vakiona sivua vaihdettaessa.
    private const val ROWS = 5

    fun baseRowDp(landscape: Boolean): Float = if (landscape) 44f else 56f

    fun effectiveScale(
        prefScale: Float,
        landscape: Boolean,
        windowHeightDp: Float,
        bottomInsetDp: Float,
    ): Float {
        val fixed = TOOLBAR_DP + BOTTOM_PADDING_DP + bottomInsetDp
        val scalable = SUGGESTION_BAR_DP + ROWS * baseRowDp(landscape)
        val cap = (MAX_WINDOW_FRACTION * windowHeightDp - fixed) / scalable
        return min(prefScale, cap).coerceAtLeast(MIN_SCALE)
    }

    fun totalHeightDp(scale: Float, landscape: Boolean, bottomInsetDp: Float): Float =
        TOOLBAR_DP + BOTTOM_PADDING_DP + bottomInsetDp +
            scale * (SUGGESTION_BAR_DP + ROWS * baseRowDp(landscape))
}
