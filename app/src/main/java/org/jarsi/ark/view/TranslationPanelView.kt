package org.jarsi.ark.view

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import org.jarsi.ark.R
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Käännösnäkymä näppäinalueen tilalle: kielipari ylhäällä (napautus
 * vaihtaa kieltä, ⇄ kääntää suunnan), kentän teksti ja sen käännös
 * allekkain, ja alhaalla nappi joka korvaa kentän tekstin käännöksellä.
 */
class TranslationPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onCycleSource()
        fun onSwap()
        fun onCycleTarget()
        fun onApply()
    }

    var listener: Listener? = null

    private var theme = KeyboardTheme.load(context)
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    private val sourceButton = TextView(context).apply {
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setOnClickListener { listener?.onCycleSource() }
    }

    private val swapButton = TextView(context).apply {
        text = "⇄"
        textSize = 18f
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener { listener?.onSwap() }
    }

    private val targetButton = TextView(context).apply {
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setOnClickListener { listener?.onCycleTarget() }
    }

    private val originalView = TextView(context).apply {
        textSize = 14f
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }

    private val translationView = TextView(context).apply {
        textSize = 17f
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }

    private val applyButton = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
        text = context.getString(R.string.kaannos_korvaa)
        isEnabled = false
        setOnClickListener { listener?.onApply() }
    }

    init {
        orientation = VERTICAL
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                addView(sourceButton)
                addView(swapButton)
                addView(targetButton)
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(
            ScrollView(context).apply {
                addView(
                    LinearLayout(context).apply {
                        orientation = VERTICAL
                        addView(originalView)
                        addView(Space(context), LayoutParams(0, dp(4)))
                        addView(translationView)
                    },
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        addView(
            applyButton,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    // IME-ikkuna ulottuu navigointipalkin alle; palkin korkeus varataan
    // täytteenä samoin kuin muissa paneeleissa.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (bottom != paddingBottom) setPadding(0, 0, 0, bottom)
        return super.onApplyWindowInsets(insets)
    }

    fun applySettings(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(theme.background)
        sourceButton.setTextColor(theme.accent)
        targetButton.setTextColor(theme.accent)
        swapButton.setTextColor(theme.text)
        originalView.setTextColor(theme.hint)
        translationView.setTextColor(theme.text)
        applyButton.setTextColor(theme.accent)
    }

    fun setLanguages(source: String, target: String) {
        sourceButton.text = source
        targetButton.text = target
    }

    fun setOriginal(text: String) {
        originalView.text = text
    }

    /** Tilateksti (lataus, virhe, tyhjä kenttä); korvausnappi pois käytöstä. */
    fun setStatus(text: String) {
        translationView.text = text
        translationView.setTextColor(theme.hint)
        applyButton.isEnabled = false
    }

    fun setTranslation(text: String) {
        translationView.text = text
        translationView.setTextColor(theme.text)
        applyButton.isEnabled = true
    }
}
