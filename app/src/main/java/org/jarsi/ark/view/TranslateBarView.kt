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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import org.jarsi.ark.R
import org.jarsi.ark.engine.WordTools
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Käännösnäkymä Google Kääntäjän tapaan — oma työkalu, joka ei kirjoita
 * kenttään mitään itsestään. Ylhäällä on monirivinen kirjoitusalue, joka
 * kasvaa tekstin mukana, ja sen alla käännös samalla tavalla väljänä.
 * Kopiointinappi vie käännöksen leikepöydälle, Lisää-pikanappi suoraan
 * kenttään ja ✨ hakee laadukkaamman käännöksen AI-palvelulta.
 * Kielikoodia napauttamalla kieli vaihtuu ladattujen joukossa, ⇄ kääntää
 * suunnan ja ✕ tyhjentää. Tekstiä napauttamalla kursori siirtyy;
 * pitkä painallus valitsee sanan kahvoineen.
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

        /** Käyttäjä liittää leikepöydän tekstin käännösriville. */
        fun onPaste()

        /** Käyttäjä pyysi paremman käännöksen AI-palvelulta. */
        fun onAiTranslate()

        /** Käyttäjä kopioi käännöksen leikepöydälle. */
        fun onCopyTranslation()

        /** Käyttäjä vie näkyvän käännöksen kenttään. */
        fun onInsert()
    }

    var listener: Listener? = null

    /** Onko leikepöydällä liitettävää tekstiä; palvelu päivittää. */
    var pasteAvailable = false

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

    // Valintakahvan raahaus: 0 = ei mikään, 1 = alkukahva, 2 = loppukahva.
    private var draggingHandle = 0
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    @SuppressLint("ClickableViewAccessibility")
    private val bufferView = object : TextView(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawSelectionHandles(canvas, this)
        }
    }.apply {
        textSize = 16f
        // Teksti rivittyy vapaasti kuten Google Kääntäjässä; alue kasvaa
        // sisällön mukana CappedScrollView'n ylärajaan asti.
        gravity = Gravity.TOP
        minimumHeight = dp(48)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnTouchListener { view, event ->
            val textView = view as TextView
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    longPressFired = false
                    draggingHandle = hitHandle(textView, event.x, event.y)
                    if (draggingHandle != 0) {
                        // Kahvan veto ei saa käynnistää vieritystä eikä valikkoa.
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        dismissSelectionPopup()
                    } else {
                        view.postDelayed(
                            longPressRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong(),
                        )
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (draggingHandle != 0) {
                        dragHandleTo(textView, event.x, event.y)
                    } else if (abs(event.x - downX) > touchSlop ||
                        abs(event.y - downY) > touchSlop
                    ) {
                        view.removeCallbacks(longPressRunnable)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(longPressRunnable)
                    if (draggingHandle != 0) {
                        draggingHandle = 0
                        showSelectionPopup()
                    } else if (!longPressFired) {
                        if (selection != null) {
                            clearSelection()
                            render()
                        }
                        onBufferTapped(textView, event.x, event.y)
                        view.performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPressRunnable)
                    draggingHandle = 0
                }
            }
            // Kosketus kuluu tässä, ettei vieritys nappaa napautusta;
            // liike jää vierityksen siepattavaksi paitsi kahvaa raahatessa.
            draggingHandle != 0 ||
                event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_DOWN
        }
    }

    // Kasvaa sisällön mukana ylärajaan asti, sitten vierii pystysuunnassa.
    private inner class CappedScrollView(private val maxHeightPx: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST),
            )
        }
    }

    private val sourceScroll = CappedScrollView(dp(120)).apply {
        isVerticalScrollBarEnabled = false
        addView(
            bufferView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
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

    // Käännösalue: monirivinen kuten kirjoitusaluekin. Kenttä ei muutu
    // ennen kuin käännös kopioidaan tai viedään pikanapilla.
    private val translationView = TextView(context).apply {
        textSize = 16f
        gravity = Gravity.TOP
        minimumHeight = dp(44)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private val translationScroll = CappedScrollView(dp(120)).apply {
        isVerticalScrollBarEnabled = false
        addView(
            translationView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private val aiLabel = TextView(context).apply {
        text = "✨"
        textSize = 16f
        setPadding(dp(12), dp(10), dp(12), dp(10))
        contentDescription = context.getString(R.string.cd_ai_kaannos)
        setOnClickListener { listener?.onAiTranslate() }
    }

    private val copyIcon = ImageView(context).apply {
        setImageResource(R.drawable.ic_clipboard)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        contentDescription = context.getString(R.string.cd_kopioi_kaannos)
        setOnClickListener { listener?.onCopyTranslation() }
    }

    private val insertLabel = TextView(context).apply {
        text = context.getString(R.string.kaannos_lisaa)
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(12), dp(10), dp(14), dp(10))
        setOnClickListener { listener?.onInsert() }
    }

    // Otsikkorivi: kielivalinnat vasemmalla, tyhjennys oikealla.
    private val headerRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(40)
        addView(sourceLabel)
        addView(swapLabel)
        addView(targetLabel)
        addView(View(context), LayoutParams(0, 1, 1f))
        addView(clearLabel)
    }

    // Toimintorivi käännöksen alla: ✨, kopiointi ja vienti kenttään.
    private val actionRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(44)
        addView(View(context), LayoutParams(0, 1, 1f))
        addView(aiLabel)
        addView(copyIcon, LayoutParams(dp(44), dp(44)))
        addView(insertLabel)
    }

    init {
        orientation = VERTICAL
        // Rako alareunaan erottaa toimintonapit alla olevasta
        // työkalurivistä, etteivät painallukset osu väärään riviin.
        setPadding(0, 0, 0, dp(10))
        val rowParams =
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(headerRow, rowParams)
        addView(sourceScroll, rowParams)
        addView(translationScroll, rowParams)
        addView(actionRow, rowParams)
    }

    fun applySettings(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(theme.background)
        sourceLabel.setTextColor(theme.accent)
        targetLabel.setTextColor(theme.accent)
        swapLabel.setTextColor(theme.text)
        clearLabel.setTextColor(theme.hint)
        aiLabel.setTextColor(theme.text)
        insertLabel.setTextColor(theme.accent)
        copyIcon.setColorFilter(theme.text)
        // Käännös erottuu omaksi alueekseen korttitaustalla.
        translationScroll.setBackgroundColor(theme.specialKey)
        actionRow.setBackgroundColor(theme.specialKey)
    }

    /** Näyttää käännöksen tai [hint]-vihjeen sen puuttuessa. */
    fun setTranslation(text: String, hint: String) {
        if (text.isEmpty()) {
            translationView.text = hint
            translationView.setTextColor(theme.hint)
        } else {
            translationView.text = text
            translationView.setTextColor(theme.text)
        }
        val alpha = if (text.isEmpty()) 0.4f else 1f
        insertLabel.alpha = alpha
        copyIcon.alpha = alpha
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
            // Kahvaa raahatessa näkymä ei saa hyppiä kursorin perässä.
            if (draggingHandle == 0) scrollCursorIntoView(position)
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

    /** Vierittää pystysuunnassa niin, että kursoririvi pysyy näkyvissä. */
    private fun scrollCursorIntoView(position: Int) {
        bufferView.post {
            val layout = bufferView.layout ?: return@post
            val clamped = position.coerceIn(0, layout.text.length)
            val line = layout.getLineForOffset(clamped)
            val top = layout.getLineTop(line) + bufferView.totalPaddingTop
            val bottom = layout.getLineBottom(line) + bufferView.totalPaddingTop
            val viewport = sourceScroll.height
            if (viewport <= 0) return@post
            val scrollY = sourceScroll.scrollY
            if (top < scrollY) {
                sourceScroll.smoothScrollTo(0, top)
            } else if (bottom > scrollY + viewport) {
                sourceScroll.smoothScrollTo(0, bottom - viewport)
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

    /** Näyttöindeksin piste (x, rivin alareuna) lähdetekstin kohdalle;
     *  paikkamerkki siirtää indeksejä kursorin kohdalla. [boundaryAfter]
     *  koskee valinnan loppurajaa, joka osuu paikkamerkin jälkeen. */
    private fun handlePoint(
        text: TextView,
        sourceIndex: Int,
        boundaryAfter: Boolean,
    ): Pair<Float, Float>? {
        val layout = text.layout ?: return null
        val display = if (boundaryAfter) {
            sourceIndex + if (sourceIndex > shownCursor) 1 else 0
        } else {
            sourceIndex + if (sourceIndex >= shownCursor) 1 else 0
        }
        val clamped = display.coerceIn(0, layout.text.length)
        val line = layout.getLineForOffset(clamped)
        val x = layout.getPrimaryHorizontal(clamped) + text.totalPaddingLeft
        val y = layout.getLineBottom(line).toFloat() + text.totalPaddingTop
        return x to y
    }

    private fun hitHandle(text: TextView, x: Float, y: Float): Int {
        val sel = selection ?: return 0
        val start = handlePoint(text, sel.first, false) ?: return 0
        val end = handlePoint(text, sel.last + 1, true) ?: return 0
        val slop = dp(24)
        fun near(p: Pair<Float, Float>) =
            abs(x - p.first) <= slop && abs(y - p.second) <= slop
        val toStart = abs(x - start.first) + abs(y - start.second)
        val toEnd = abs(x - end.first) + abs(y - end.second)
        return when {
            near(start) && toStart <= toEnd -> 1
            near(end) -> 2
            else -> 0
        }
    }

    private fun dragHandleTo(text: TextView, x: Float, y: Float) {
        val sel = selection ?: return
        val offset = text.getOffsetForPosition(x, y)
        if (offset < 0) return
        val position = (if (offset > shownCursor) offset - 1 else offset)
            .coerceIn(0, shownText.length)
        val updated = if (draggingHandle == 1) {
            minOf(position, sel.last)..sel.last
        } else {
            sel.first..maxOf(position - 1, sel.first)
        }
        if (updated != sel) {
            selection = updated
            render()
        }
    }

    private fun drawSelectionHandles(canvas: Canvas, text: TextView) {
        val sel = selection ?: return
        val start = handlePoint(text, sel.first, false) ?: return
        val end = handlePoint(text, sel.last + 1, true) ?: return
        handlePaint.color = theme.accent
        val radius = dp(5).toFloat()
        canvas.drawCircle(start.first, start.second + dp(3), radius, handlePaint)
        canvas.drawCircle(end.first, end.second + dp(3), radius, handlePaint)
    }

    /** Pitkä painallus maalaa sanan ja avaa Kopioi-valikon sen ylle. */
    private fun onBufferLongPressed() {
        if (shownCursor < 0) {
            // Tyhjälläkin rivillä voi liittää leikepöydän tekstin.
            if (pasteAvailable) {
                longPressFired = true
                val location = IntArray(2)
                bufferView.getLocationInWindow(location)
                popupAnchorX = location[0] + downX.roundToInt()
                showSelectionPopup()
            }
            return
        }
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
        val sel = selection
        if (sel == null && !pasteAvailable) return

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
        if (sel != null) {
            addItem(context.getString(R.string.kaannos_kopioi)) {
                selection?.let { s ->
                    listener?.onCopy(shownText.substring(s.first, s.last + 1))
                }
                clearSelection()
                render()
            }
            val allSelected = sel.first == 0 && sel.last >= shownText.length - 1
            if (!allSelected) {
                addItem(context.getString(R.string.kaannos_valitse_kaikki)) {
                    selection = 0 until shownText.length
                    render()
                    showSelectionPopup()
                }
            }
        }
        if (pasteAvailable) {
            addItem(context.getString(R.string.leike_liita)) {
                dismissSelectionPopup()
                clearSelection()
                render()
                listener?.onPaste()
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
