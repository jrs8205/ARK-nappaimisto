package org.jarsi.ark.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import org.jarsi.ark.R
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Vaakasuunnassa sormella vieritettävä ehdotusrivi. Piirretään Canvasille
 * ilman alinäkymiä kuten näppäimistökin. Pitkä painallus avaa sanan
 * poisto-/estovalikon.
 */
class SuggestionBarView(context: Context) : View(context) {

    interface MenuListener {
        fun isOwnWord(word: String): Boolean
        fun isPinned(word: String): Boolean
        fun onTogglePin(word: String)
        fun onDeleteLearned(word: String)
        fun onBlockWord(word: String)
    }

    var listener: ((String) -> Unit)? = null
    var menuListener: MenuListener? = null

    private var theme = KeyboardTheme.load(context)
    private var heightScale = 1f
    private var suggestions: List<String> = emptyList()
    private var slotWidths = FloatArray(0)
    private var contentWidth = 0f
    private var scrollOffset = 0f

    private var downX = 0f
    private var downOffset = 0f
    private var dragging = false
    private var pressedIndex = -1
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Pitkän painalluksen valikko
    private enum class MenuAction { PIN, DELETE, BLOCK }

    private var menuPopup: PopupWindow? = null
    private var menuActions: List<MenuAction> = emptyList()
    private var menuViews: List<TextView> = emptyList()
    private var menuSelected = -1
    private var menuLeft = 0f
    private var menuCellWidths = FloatArray(0)
    private var menuWord: String? = null
    // Valinta aktivoituu vasta kun sormi on liikahtanut selvästi avauskohdasta,
    // ettei irrotus heti avauksen jälkeen suorita mitään vahingossa.
    private var menuAnchorX = 0f
    private var menuArmed = false
    private val longPressRunnable = Runnable { openMenu() }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val dividerPaint = Paint()
    private val pressedPaint = Paint()
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun applySettings(newTheme: KeyboardTheme, scale: Float) {
        theme = newTheme
        if (heightScale != scale) {
            heightScale = scale
            requestLayout()
        }
        invalidate()
    }

    fun setSuggestions(words: List<String>) {
        if (words == suggestions) return
        closeMenu()
        suggestions = words
        scrollOffset = 0f
        pressedIndex = -1
        computeSlots()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (dp(44f) * heightScale).roundToInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeSlots()
    }

    private fun computeSlots() {
        textPaint.textSize = height * 0.38f
        slotWidths = FloatArray(suggestions.size)
        var total = 0f
        suggestions.forEachIndexed { i, word ->
            slotWidths[i] = max(textPaint.measureText(word) + dp(28f), dp(60f))
            total += slotWidths[i]
        }
        // Jos ehdotukset eivät täytä riviä, ne levitetään koko leveydelle.
        if (total < width && suggestions.isNotEmpty()) {
            val extra = (width - total) / suggestions.size
            for (i in slotWidths.indices) slotWidths[i] += extra
            total = width.toFloat()
        }
        contentWidth = total
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        if (suggestions.isEmpty()) return
        textPaint.textSize = height * 0.38f
        dividerPaint.color = theme.hint and 0x60FFFFFF.toInt()
        var x = -scrollOffset
        suggestions.forEachIndexed { i, word ->
            val slot = slotWidths[i]
            if (x + slot > 0 && x < width) {
                if (i == pressedIndex) {
                    pressedPaint.color = theme.keyPressed
                    canvas.drawRect(x, 0f, x + slot, height.toFloat(), pressedPaint)
                }
                textPaint.color = theme.text
                canvas.drawText(
                    word,
                    x + slot / 2f,
                    height / 2f + textPaint.textSize * 0.35f,
                    textPaint,
                )
                // Kiinnitetty sana merkitään pienellä korostuspisteellä.
                if (menuListener?.isPinned(word) == true) {
                    pinPaint.color = theme.accent
                    canvas.drawCircle(x + slot - dp(9f), dp(9f), dp(3f), pinPaint)
                }
                if (i > 0) {
                    canvas.drawRect(x, height * 0.25f, x + dp(1f), height * 0.75f, dividerPaint)
                }
            }
            x += slot
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downOffset = scrollOffset
                dragging = false
                pressedIndex = indexAt(event.x)
                if (pressedIndex >= 0 && menuListener != null) {
                    postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong(),
                    )
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (menuPopup != null) {
                    updateMenuSelection(event.x)
                    return true
                }
                val dx = event.x - downX
                if (!dragging && abs(dx) > touchSlop) {
                    dragging = true
                    pressedIndex = -1
                    removeCallbacks(longPressRunnable)
                }
                if (dragging) {
                    val maxOffset = max(0f, contentWidth - width)
                    scrollOffset = (downOffset - dx).coerceIn(0f, maxOffset)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (menuPopup != null) {
                    commitMenu()
                } else if (!dragging) {
                    val index = indexAt(event.x)
                    suggestions.getOrNull(index)?.let { listener?.invoke(it) }
                }
                pressedIndex = -1
                invalidate()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                closeMenu()
                pressedIndex = -1
                invalidate()
            }
        }
        return true
    }

    private fun openMenu() {
        val menu = menuListener ?: return
        val word = suggestions.getOrNull(pressedIndex) ?: return
        menuWord = word
        menuActions = buildList {
            add(MenuAction.PIN)
            if (menu.isOwnWord(word)) add(MenuAction.DELETE)
            add(MenuAction.BLOCK)
        }
        menuSelected = -1
        menuAnchorX = downX
        menuArmed = false
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        val cellHeight = dp(48f)
        val labelPadding = dp(10f)
        textPaint.textSize = dp(13f)
        val labels = menuActions.map { action ->
            when (action) {
                MenuAction.PIN -> context.getString(
                    if (menu.isPinned(word)) R.string.ehdotus_irrota else R.string.ehdotus_kiinnita
                )
                MenuAction.DELETE -> context.getString(R.string.ehdotus_poista)
                MenuAction.BLOCK -> context.getString(R.string.ehdotus_esta)
            }
        }
        menuCellWidths = FloatArray(labels.size)
        labels.forEachIndexed { i, label ->
            menuCellWidths[i] = textPaint.measureText(label) + labelPadding * 2
        }
        // Valikko ei saa ylittää näkymän leveyttä millään näytöllä:
        // ylileveä valikko kutistetaan suhteessa ja teksti pienenee hieman.
        var menuTextSize = 13f
        val available = width - dp(8f)
        val naturalWidth = menuCellWidths.sum()
        if (naturalWidth > available) {
            val factor = available / naturalWidth
            for (i in menuCellWidths.indices) menuCellWidths[i] *= factor
            menuTextSize = 12f
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8f)
                setColor(theme.specialKey)
            }
        }
        menuViews = labels.mapIndexed { i, label ->
            TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = menuTextSize
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(theme.text)
                layoutParams = LinearLayout.LayoutParams(
                    menuCellWidths[i].roundToInt(),
                    cellHeight.roundToInt(),
                )
                container.addView(this)
            }
        }

        // Valikko avautuu painetun ehdotuksen ylle; pysyy näytön reunojen sisällä.
        var slotLeft = -scrollOffset
        for (i in 0 until pressedIndex) slotLeft += slotWidths[i]
        val slotCenter = slotLeft + slotWidths[pressedIndex] / 2f
        val totalWidth = menuCellWidths.sum()
        menuLeft = (slotCenter - totalWidth / 2f)
            .coerceIn(dp(4f), (width - totalWidth - dp(4f)).coerceAtLeast(dp(4f)))
        val location = IntArray(2)
        getLocationInWindow(location)
        menuPopup = PopupWindow(container, totalWidth.roundToInt(), cellHeight.roundToInt()).apply {
            isTouchable = false
            isClippingEnabled = false
            showAtLocation(
                this@SuggestionBarView,
                Gravity.NO_GRAVITY,
                location[0] + menuLeft.roundToInt(),
                location[1] - cellHeight.roundToInt() - dp(6f).roundToInt(),
            )
        }
    }

    private fun updateMenuSelection(x: Float) {
        if (menuActions.isEmpty()) return
        if (!menuArmed) {
            if (abs(x - menuAnchorX) < dp(8f)) return
            menuArmed = true
        }
        var index = -1
        var pos = menuLeft
        menuCellWidths.forEachIndexed { i, cellWidth ->
            if (x >= pos && x < pos + cellWidth) index = i
            pos += cellWidth
        }
        if (index != menuSelected) {
            menuSelected = index
            menuViews.forEachIndexed { i, view ->
                if (i == menuSelected) {
                    view.setBackgroundColor(theme.accent)
                    view.setTextColor(theme.accentText)
                } else {
                    view.setBackgroundColor(0)
                    view.setTextColor(theme.text)
                }
            }
            if (index >= 0) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    private fun commitMenu() {
        val action = menuActions.getOrNull(menuSelected)
        val word = menuWord
        closeMenu()
        if (action != null && word != null) {
            when (action) {
                MenuAction.PIN -> menuListener?.onTogglePin(word)
                MenuAction.DELETE -> menuListener?.onDeleteLearned(word)
                MenuAction.BLOCK -> menuListener?.onBlockWord(word)
            }
        }
    }

    private fun closeMenu() {
        menuPopup?.dismiss()
        menuPopup = null
        menuViews = emptyList()
        menuActions = emptyList()
        menuSelected = -1
        menuWord = null
        menuArmed = false
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        closeMenu()
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun indexAt(x: Float): Int {
        var pos = -scrollOffset
        slotWidths.forEachIndexed { i, slot ->
            if (x >= pos && x < pos + slot) return i
            pos += slot
        }
        return -1
    }
}
