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
    }

    var listener: Listener? = null

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
    }

    fun render(text: CharSequence, words: List<IntRange>, unknown: Set<Int>, selected: Int) {
        emptyView.visibility = if (text.isBlank()) VISIBLE else GONE
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
