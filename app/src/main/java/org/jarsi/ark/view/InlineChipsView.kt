package org.jarsi.ark.view

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import org.jarsi.ark.keyboard.InlineChips
import org.jarsi.ark.keyboard.KeyboardHeight
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Täyttöpalvelun inline-ehdotukset (esim. Holvin "Vahvista Holvissa")
 * vaakarullattavana chipirivinä ehdotusrivin paikalla. Chipit ovat
 * täyttöpalvelun piirtämiä [android.widget.inline.InlineContentView]-
 * näkymiä, joten ne tarvitsevat oikean näkymäpuun eivätkä mahdu Canvasille
 * piirrettyyn ehdotusriviin.
 */
class InlineChipsView(context: Context) : HorizontalScrollView(context) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = (value * density).roundToInt()

    private var heightScale = 1f

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6f), 0, dp(6f), 0)
    }

    val count: Int get() = row.childCount

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        addView(
            row,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun applySettings(theme: KeyboardTheme, scale: Float) {
        setBackgroundColor(theme.background)
        if (heightScale != scale) {
            heightScale = scale
            requestLayout()
        }
    }

    /** Rivin korkeus pikseleinä; sama kuin ehdotusrivillä, jotta vaihto ei hypi. */
    fun barHeightPx(): Int = (dp(KeyboardHeight.SUGGESTION_BAR_DP) * heightScale).roundToInt()

    /** Yksittäisen chipin korkeus rivin sisällä. */
    fun chipHeightPx(): Int =
        InlineChips.specBounds(barHeightPx(), resources.displayMetrics.widthPixels, density).height

    fun setChips(views: List<View>) {
        row.removeAllViews()
        for (view in views) {
            // Alustan antamat mitat säilytetään (ks. InlineChips.chipSize).
            val given = view.layoutParams
            val size = InlineChips.chipSize(
                givenWidth = given?.width ?: InlineChips.WRAP_CONTENT,
                givenHeight = given?.height ?: InlineChips.WRAP_CONTENT,
                rowChipHeight = chipHeightPx(),
            )
            row.addView(
                view,
                LinearLayout.LayoutParams(size.width, size.height).apply { marginEnd = dp(6f) },
            )
        }
        scrollTo(0, 0)
    }

    fun clear() {
        row.removeAllViews()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(barHeightPx(), MeasureSpec.EXACTLY),
        )
    }
}
