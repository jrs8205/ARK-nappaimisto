package org.jarsi.ark.keyboard

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Täyttöpalvelun inline-ehdotusten (esim. Holvin "Vahvista Holvissa") rivin
 * säännöt. Chipit ovat ehdotusrivin paikalla: kun niitä on, sanaehdotukset
 * väistyvät, ja salasanakentässäkin rivi näkyy vain chippien kanssa.
 */
object InlineChips {
    const val MAX_SUGGESTIONS = 5
    const val MIN_WIDTH_DP = 48f
    const val CHIP_PADDING_DP = 4f

    data class RowState(val chipsVisible: Boolean, val suggestionsVisible: Boolean)

    fun rowState(suggestionsEnabled: Boolean, chipCount: Int): RowState =
        if (chipCount > 0) RowState(chipsVisible = true, suggestionsVisible = false)
        else RowState(chipsVisible = false, suggestionsVisible = suggestionsEnabled)

    /** Sama arvo kuin ViewGroup.LayoutParams.WRAP_CONTENT; puhdas, ettei testi tarvitse Androidia. */
    const val WRAP_CONTENT = -2

    data class Size(val width: Int, val height: Int)

    /**
     * Rivin chipin asettelumitat. Alusta antaa chipille mittaamansa koon
     * LayoutParams-oliona; se on säilytettävä, koska chipillä ei ole omaa
     * sisäistä kokoa ja WRAP_CONTENT-leveys mittautuisi nollaksi (näkymätön
     * chip, opittu 23.9.2026).
     */
    fun chipSize(givenWidth: Int, givenHeight: Int, rowChipHeight: Int): Size = Size(
        width = if (givenWidth > 0) givenWidth else WRAP_CONTENT,
        height = if (givenHeight > 0) givenHeight else rowChipHeight,
    )

    /** Chipin sallitut mitat pikseleinä täyttöpalvelulle. */
    data class Bounds(val minWidth: Int, val maxWidth: Int, val height: Int)

    fun specBounds(barHeightPx: Int, screenWidthPx: Int, density: Float): Bounds {
        val minWidth = (MIN_WIDTH_DP * density).roundToInt().coerceAtMost(screenWidthPx)
        val height = barHeightPx - 2 * (CHIP_PADDING_DP * density).roundToInt()
        return Bounds(
            minWidth = max(1, minWidth),
            maxWidth = max(1, max(minWidth, screenWidthPx)),
            height = max(1, height),
        )
    }
}
