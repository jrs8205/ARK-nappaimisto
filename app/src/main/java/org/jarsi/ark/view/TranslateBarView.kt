package org.jarsi.ark.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.jarsi.ark.R
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Live-käännösrivi ehdotusrivin paikalle: tähän kirjoitetaan lähdekieltä,
 * ja kenttään menee käännös sitä mukaa. Kielikoodia napauttamalla kieli
 * vaihtuu ladattujen joukossa, ⇄ kääntää suunnan ja ✕ tyhjentää rivin.
 * Tekstiä napauttamalla kursorin voi siirtää keskelle muokkausta varten.
 */
class TranslateBarView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onCycleSource()
        fun onSwap()
        fun onCycleTarget()
        fun onClear()

        /** Käyttäjä napautti lähdetekstiä: kursori kohtaan [position]. */
        fun onCursorTap(position: Int)
    }

    var listener: Listener? = null

    private var theme = KeyboardTheme.load(context)
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    // Näytetyn tekstin kursorikohta napautuksen takaisinmappausta varten;
    // -1 kun rivillä näkyy vihjeteksti.
    private var shownCursor = -1

    // Kursori piirretään kapeana pystyviivana, joka vie vain parin pisteen
    // raon — kokonainen merkki työntäisi kirjaimet erilleen kuin välilyönti.
    private val cursorSpan = object : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            fm?.let {
                val metrics = paint.fontMetricsInt
                it.top = metrics.top
                it.ascent = metrics.ascent
                it.descent = metrics.descent
                it.bottom = metrics.bottom
            }
            return dp(2)
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            val oldColor = paint.color
            paint.color = theme.accent
            canvas.drawRect(x, y + paint.ascent(), x + dp(2), y + paint.descent(), paint)
            paint.color = oldColor
        }
    }

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

    @SuppressLint("ClickableViewAccessibility")
    private val bufferView = TextView(context).apply {
        textSize = 16f
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                onBufferTapped(view as TextView, event.x, event.y)
                view.performClick()
            }
            // Kosketus kuluu tässä, ettei vieritys nappaa napautusta.
            event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_DOWN
        }
    }

    // Pitkä teksti vierii vaakasuunnassa; kursori pidetään näkyvissä.
    private val bufferScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        addView(
            bufferView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
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
        addView(bufferScroll, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
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

    /**
     * Näyttää keskeneräisen tekstin kursoreineen tai [hint]-vihjeen, kun
     * rivi on tyhjä. [cursor] on kohta lähdetekstissä.
     */
    fun setBuffer(text: String, cursor: Int, hint: String) {
        if (text.isEmpty()) {
            shownCursor = -1
            bufferView.text = hint
            bufferView.setTextColor(theme.hint)
            clearLabel.visibility = GONE
        } else {
            val position = cursor.coerceIn(0, text.length)
            shownCursor = position
            bufferView.text = SpannableStringBuilder(text).apply {
                insert(position, CURSOR_PLACEHOLDER)
                setSpan(
                    cursorSpan,
                    position,
                    position + CURSOR_PLACEHOLDER.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            bufferView.setTextColor(theme.text)
            clearLabel.visibility = VISIBLE
            scrollCursorIntoView(position)
        }
    }

    /** Vierittää niin, että kursori pysyy näkyvissä pienellä marginaalilla. */
    private fun scrollCursorIntoView(position: Int) {
        bufferView.post {
            val layout = bufferView.layout ?: return@post
            val x = layout.getPrimaryHorizontal(position).roundToInt() + bufferView.paddingLeft
            val margin = dp(24)
            val viewport = bufferScroll.width
            if (viewport <= 0) return@post
            val scrollX = bufferScroll.scrollX
            if (x < scrollX + margin) {
                bufferScroll.smoothScrollTo((x - margin).coerceAtLeast(0), 0)
            } else if (x > scrollX + viewport - margin) {
                bufferScroll.smoothScrollTo(x - viewport + margin, 0)
            }
        }
    }

    private fun onBufferTapped(view: TextView, x: Float, y: Float) {
        // Vihjetekstiä ei voi kohdistaa.
        if (shownCursor < 0) return
        val offset = view.getOffsetForPosition(x, y)
        if (offset < 0) return
        // Näytetyssä tekstissä on kursorin paikkamerkki; poistetaan sen vaikutus.
        val position = if (offset > shownCursor) offset - 1 else offset
        listener?.onCursorTap(position)
    }

    private companion object {
        // Leveydetön merkki kantaa kursoripiirron; itse teksti ei muutu.
        const val CURSOR_PLACEHOLDER = "\u200B"
    }
}
