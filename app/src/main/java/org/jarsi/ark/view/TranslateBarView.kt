package org.jarsi.ark.view

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.jarsi.ark.R
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Live-käännösrivi ehdotusrivin paikalle: tähän kirjoitetaan lähdekieltä,
 * ja kenttään menee käännös sitä mukaa. Kielikoodia napauttamalla kieli
 * vaihtuu ladattujen joukossa, ⇄ kääntää suunnan ja ✕ tyhjentää rivin.
 */
class TranslateBarView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onCycleSource()
        fun onSwap()
        fun onCycleTarget()
        fun onClear()
    }

    var listener: Listener? = null

    private var theme = KeyboardTheme.load(context)
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    private val sourceLabel = TextView(context).apply {
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(12), dp(8), dp(8), dp(8))
        setOnClickListener { listener?.onCycleSource() }
    }

    private val swapLabel = TextView(context).apply {
        text = "⇄"
        textSize = 16f
        setPadding(dp(4), dp(8), dp(4), dp(8))
        contentDescription = context.getString(R.string.cd_vaihda_suunta)
        setOnClickListener { listener?.onSwap() }
    }

    private val targetLabel = TextView(context).apply {
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(8), dp(8), dp(12), dp(8))
        setOnClickListener { listener?.onCycleTarget() }
    }

    private val bufferView = TextView(context).apply {
        textSize = 16f
        maxLines = 1
        // Rivin loppu pysyy näkyvissä, kun teksti ei enää mahdu.
        ellipsize = TextUtils.TruncateAt.START
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private val clearLabel = TextView(context).apply {
        text = "✕"
        textSize = 16f
        setPadding(dp(12), dp(8), dp(12), dp(8))
        visibility = GONE
        contentDescription = context.getString(R.string.cd_tyhjenna_rivi)
        setOnClickListener { listener?.onClear() }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(40)
        addView(sourceLabel)
        addView(swapLabel)
        addView(targetLabel)
        addView(bufferView, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(clearLabel)
    }

    fun applySettings(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(theme.background)
        sourceLabel.setTextColor(theme.accent)
        targetLabel.setTextColor(theme.accent)
        swapLabel.setTextColor(theme.text)
        clearLabel.setTextColor(theme.hint)
    }

    fun setLanguages(source: String, target: String) {
        sourceLabel.text = source
        targetLabel.text = target
    }

    /** Näyttää keskeneräisen tekstin tai [hint]-vihjeen, kun rivi on tyhjä. */
    fun setBuffer(text: String, hint: String) {
        if (text.isEmpty()) {
            bufferView.text = hint
            bufferView.setTextColor(theme.hint)
            clearLabel.visibility = GONE
        } else {
            bufferView.text = "$text▏"
            bufferView.setTextColor(theme.text)
            clearLabel.visibility = VISIBLE
        }
    }
}
