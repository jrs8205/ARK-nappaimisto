package org.jarsi.ark.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import org.jarsi.ark.R
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Korjausnäkymä näppäinalueen tilalle: kentän koko teksti sanoina, joista
 * napautettu korostuu ja saa vaihtoehtonsa ehdotusriville. Tuntemattomat
 * sanat alleviivataan, jotta kirjoitusvirheet erottuvat silmäyksellä.
 */
class CorrectionPanelView(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onWordTapped(index: Int)
        fun onDone()

        /** Käyttäjä pyysi koko tekstin parannusta. */
        fun onImprove()

        /** Käyttäjä hyväksyi parannusehdotuksen [text]. */
        fun onAcceptImprovement(text: String)
    }

    var listener: Listener? = null

    /** Paranna-nappi näytetään vain, kun API-avain on asetettu. */
    var improveEnabled = false

    private var theme = KeyboardTheme.load(context)
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    private val textView = TextView(context).apply {
        textSize = 18f
        setLineSpacing(dp(6).toFloat(), 1f)
        movementMethod = LinkMovementMethod.getInstance()
        // Napautus ei väläytä koko kappaletta; valinta piirretään itse.
        highlightColor = 0
        setPadding(dp(16), dp(12), dp(16), dp(76))
    }

    private val scroll = ScrollView(context).apply {
        addView(
            textView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private val emptyView = TextView(context).apply {
        text = context.getString(R.string.korjaus_tyhja)
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(24), dp(24), dp(24))
        visibility = GONE
    }

    private val doneButton = ImageView(context).apply {
        setPadding(dp(14), dp(14), dp(14), dp(14))
        contentDescription = context.getString(R.string.enter_valmis)
        setOnClickListener { listener?.onDone() }
    }

    private val improveButton = ImageView(context).apply {
        setPadding(dp(14), dp(14), dp(14), dp(14))
        contentDescription = context.getString(R.string.korjaus_paranna)
        visibility = GONE
        setOnClickListener { listener?.onImprove() }
    }

    private val improvementTitle = TextView(context).apply {
        textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    // Versiot listataan kortteina; napautus ottaa version käyttöön.
    private val improvementList = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
    }

    private val rejectButton = TextView(context).apply {
        text = context.getString(R.string.korjaus_hylkaa)
        textSize = 16f
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { hideImprovement() }
    }

    // Parannusehdotus peittää tekstin, kunnes se hyväksytään tai hylätään.
    private val improvementOverlay = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        visibility = GONE
        isClickable = true
        addView(
            improvementTitle,
            android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            ScrollView(context).apply {
                addView(
                    improvementList,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        addView(
            android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                addView(rejectButton)
            },
            android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    init {
        addView(
            scroll,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        addView(
            emptyView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        addView(
            doneButton,
            LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(16), dp(16))
            },
        )
        addView(
            improveButton,
            LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.START).apply {
                setMargins(dp(16), 0, 0, dp(16))
            },
        )
        addView(
            improvementOverlay,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    /** Näyttää odotustilan, kunnes parannus valmistuu tai epäonnistuu. */
    fun showImprovementLoading(titleRes: Int = R.string.korjaus_parannetaan) {
        improvementTitle.text = context.getString(titleRes)
        improvementList.removeAllViews()
        improvementOverlay.visibility = VISIBLE
    }

    fun showImprovement(versions: List<String>) {
        improvementTitle.text = context.getString(R.string.korjaus_parannus_otsikko)
        improvementList.removeAllViews()
        versions.forEach { version ->
            improvementList.addView(
                TextView(context).apply {
                    text = version
                    textSize = 17f
                    setLineSpacing(dp(4).toFloat(), 1f)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    setTextColor(theme.text)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(theme.specialKey)
                    }
                    setOnClickListener { listener?.onAcceptImprovement(version) }
                },
                android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
        }
        improvementOverlay.visibility = VISIBLE
    }

    fun hideImprovement() {
        improvementOverlay.visibility = GONE
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
        textView.setTextColor(theme.text)
        emptyView.setTextColor(theme.hint)
        doneButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(theme.accent)
        }
        doneButton.setImageDrawable(
            context.getDrawable(R.drawable.ic_check)?.mutate()?.apply { setTint(theme.accentText) }
        )
        improveButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(theme.specialKey)
        }
        improveButton.setImageDrawable(
            context.getDrawable(R.drawable.ic_sparkle)?.mutate()?.apply { setTint(theme.text) }
        )
        improvementOverlay.setBackgroundColor(theme.background)
        improvementTitle.setTextColor(theme.accent)
        rejectButton.setTextColor(theme.hint)
    }

    fun render(text: CharSequence, words: List<IntRange>, unknown: Set<Int>, selected: Int) {
        emptyView.visibility = if (text.isBlank()) VISIBLE else GONE
        improveButton.visibility =
            if (improveEnabled && text.isNotBlank()) VISIBLE else GONE
        val spannable = SpannableString(text)
        words.forEachIndexed { index, range ->
            val start = range.first
            val end = range.last + 1
            spannable.setSpan(WordSpan(index), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (index in unknown) {
                spannable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (index == selected) {
                spannable.setSpan(
                    BackgroundColorSpan(theme.accent), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(theme.accentText), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        textView.text = spannable
    }

    private inner class WordSpan(private val index: Int) : ClickableSpan() {
        override fun onClick(widget: View) {
            listener?.onWordTapped(index)
        }

        // Sana pysyy tekstin värisenä; linkkityyli (sininen + alleviivaus)
        // ei kuulu tähän näkymään.
        override fun updateDrawState(ds: TextPaint) = Unit
    }
}
