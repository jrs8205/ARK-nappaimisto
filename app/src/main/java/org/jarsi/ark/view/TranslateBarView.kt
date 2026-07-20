package org.jarsi.ark.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import org.jarsi.ark.R
import org.jarsi.ark.engine.WordTools
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.abs
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

        /** Käyttäjä kopioi valitun lähdetekstin. */
        fun onCopy(text: String)
    }

    var listener: Listener? = null

    private var theme = KeyboardTheme.load(context)
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    // Näytetyn tekstin kursorikohta napautuksen takaisinmappausta varten;
    // -1 kun rivillä näkyy vihjeteksti.
    private var shownCursor = -1

    // Kursori vilkkuu kuten tekstikentässä: puoli sekuntia näkyvissä ja
    // puoli piilossa; siirto tuo sen aina heti näkyviin.
    private var cursorVisible = true
    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (shownCursor < 0 || !isShown) return
            cursorVisible = !cursorVisible
            bufferView.invalidate()
            postDelayed(this, CURSOR_BLINK_MS)
        }
    }

    private fun restartBlink() {
        cursorVisible = true
        removeCallbacks(blinkRunnable)
        postDelayed(blinkRunnable, CURSOR_BLINK_MS)
    }

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
            if (!cursorVisible) return
            val oldColor = paint.color
            // Tekstin väri: tummassa teemassa valkoinen kuten kentän kursori,
            // vaaleassa tumma, jotta viiva näkyy aina.
            paint.color = theme.text
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

    // Näytetty lähdeteksti ja valittu väli (lähdetekstin indeksejä)
    // kopiointia varten; valinta piirretään korostustaustalla.
    private var shownText = ""
    private var shownHint = ""
    private var shownCursorTarget = 0
    private var selection: IntRange? = null
    private var selectionPopup: PopupWindow? = null
    private var popupAnchorX = 0

    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressRunnable = Runnable { onBufferLongPressed() }

    @SuppressLint("ClickableViewAccessibility")
    private val bufferView = TextView(context).apply {
        textSize = 16f
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    longPressFired = false
                    view.postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong(),
                    )
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.x - downX) > touchSlop ||
                        abs(event.y - downY) > touchSlop
                    ) {
                        view.removeCallbacks(longPressRunnable)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(longPressRunnable)
                    if (!longPressFired) {
                        if (selection != null) {
                            clearSelection()
                            render()
                        }
                        onBufferTapped(view as TextView, event.x, event.y)
                        view.performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> view.removeCallbacks(longPressRunnable)
            }
            // Kosketus kuluu tässä, ettei vieritys nappaa napautusta;
            // liike jää vierityksen siepattavaksi kuten ennenkin.
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
        if (text != shownText) clearSelection()
        shownText = text
        shownHint = hint
        shownCursorTarget = cursor
        render()
    }

    private fun render() {
        if (shownText.isEmpty()) {
            shownCursor = -1
            removeCallbacks(blinkRunnable)
            bufferView.text = shownHint
            bufferView.setTextColor(theme.hint)
            clearLabel.visibility = GONE
        } else {
            val position = shownCursorTarget.coerceIn(0, shownText.length)
            shownCursor = position
            bufferView.text = SpannableStringBuilder(shownText).apply {
                insert(position, CURSOR_PLACEHOLDER)
                setSpan(
                    cursorSpan,
                    position,
                    position + CURSOR_PLACEHOLDER.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                selection?.let { sel ->
                    // Paikkamerkki siirtää näyttöindeksejä kursorin kohdalla.
                    val start = sel.first + if (sel.first >= position) 1 else 0
                    val end = sel.last + 1 + if (sel.last + 1 > position) 1 else 0
                    setSpan(
                        BackgroundColorSpan(selectionColor()),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            bufferView.setTextColor(theme.text)
            clearLabel.visibility = VISIBLE
            restartBlink()
            scrollCursorIntoView(position)
        }
    }

    private fun selectionColor(): Int =
        (theme.accent and 0x00FFFFFF) or (SELECTION_ALPHA shl 24)

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        // Vilkutus pyörii vain rivin ollessa esillä.
        if (isVisible && shownCursor >= 0) {
            restartBlink()
            bufferView.invalidate()
        } else if (!isVisible) {
            removeCallbacks(blinkRunnable)
            clearSelection()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blinkRunnable)
        dismissSelectionPopup()
        super.onDetachedFromWindow()
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

    /** Pitkä painallus maalaa sanan ja avaa Kopioi-valikon sen ylle. */
    private fun onBufferLongPressed() {
        if (shownCursor < 0) return
        val offset = bufferView.getOffsetForPosition(downX, downY)
        if (offset < 0) return
        val position = if (offset > shownCursor) offset - 1 else offset
        val range = WordTools.wordRangeAt(shownText, position) ?: return
        longPressFired = true
        selection = range
        bufferView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        render()
        val location = IntArray(2)
        bufferView.getLocationInWindow(location)
        popupAnchorX = location[0] + downX.roundToInt()
        showSelectionPopup()
    }

    private fun showSelectionPopup() {
        dismissSelectionPopup()
        val sel = selection ?: return
        val allSelected = sel.first == 0 && sel.last >= shownText.length - 1

        val container = LinearLayout(context).apply {
            orientation = HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(theme.specialKey)
            }
        }
        fun addItem(label: String, onClick: () -> Unit) {
            container.addView(
                TextView(context).apply {
                    text = label
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(theme.text)
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    setOnClickListener { onClick() }
                },
                LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        addItem(context.getString(R.string.kaannos_kopioi)) {
            selection?.let { s ->
                listener?.onCopy(shownText.substring(s.first, s.last + 1))
            }
            clearSelection()
            render()
        }
        if (!allSelected) {
            addItem(context.getString(R.string.kaannos_valitse_kaikki)) {
                selection = 0 until shownText.length
                render()
                showSelectionPopup()
            }
        }

        container.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED,
        )
        val popupWidth = container.measuredWidth
        val popupHeight = container.measuredHeight
        val barLocation = IntArray(2)
        getLocationInWindow(barLocation)
        val x = (popupAnchorX - popupWidth / 2)
            .coerceIn(dp(4), (resources.displayMetrics.widthPixels - popupWidth - dp(4)).coerceAtLeast(dp(4)))
        selectionPopup = PopupWindow(container, popupWidth, popupHeight).apply {
            isClippingEnabled = false
            showAtLocation(
                this@TranslateBarView,
                Gravity.NO_GRAVITY,
                x,
                barLocation[1] - popupHeight - dp(6),
            )
        }
    }

    private fun clearSelection() {
        selection = null
        dismissSelectionPopup()
    }

    private fun dismissSelectionPopup() {
        selectionPopup?.dismiss()
        selectionPopup = null
    }

    private companion object {
        // Leveydetön merkki kantaa kursoripiirron; itse teksti ei muutu.
        const val CURSOR_PLACEHOLDER = "\u200B"

        // Sama tahti kuin Androidin tekstikenttien kursorilla.
        const val CURSOR_BLINK_MS = 500L

        // Valinnan korostustaustan läpinäkyvyys korostusväristä.
        const val SELECTION_ALPHA = 0x55
    }
}
