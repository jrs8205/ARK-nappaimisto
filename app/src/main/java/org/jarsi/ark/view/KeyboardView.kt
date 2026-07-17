package org.jarsi.ark.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import org.jarsi.ark.keyboard.Key
import org.jarsi.ark.keyboard.KeyAction
import org.jarsi.ark.keyboard.KeyboardLayout
import org.jarsi.ark.keyboard.Layouts
import org.jarsi.ark.keyboard.ShiftState
import org.jarsi.ark.theme.KeyboardTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Itse piirretty näppäimistönäkymä. Yksi View piirtää kaikki näppäimet Canvasille
 * ilman alinäkymiä, jotta kosketusviive ja muistinkulutus pysyvät pieninä.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onKey(action: KeyAction)
        fun onSpaceSwipe(steps: Int)
    }

    var listener: Listener? = null

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            field = value
            invalidate()
        }

    var enterText: String = "⏎"
        set(value) {
            field = value
            invalidate()
        }

    private var layout: KeyboardLayout = Layouts.letters()
    private var theme: KeyboardTheme = KeyboardTheme.TUMMA
    private var heightScale = 1f
    private var previewEnabled = true

    private val fiLocale = Locale.forLanguageTag("fi")
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val keyGap = dp(3f)
    private val keyCornerRadius = dp(8f)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }

    private class BoundedKey(val key: Key, val rect: RectF, val row: Int)

    private var boundedKeys: List<BoundedKey> = emptyList()
    private var rowHeight = 0f

    private inner class PressInfo(val bounded: BoundedKey) {
        var spaceAnchorX = 0f
        var spaceSwiping = false
        var committed = false
        var alternatesOpen = false
        val longPressRunnable = Runnable { handleLongPress(this) }
        val repeatRunnable: Runnable = object : Runnable {
            override fun run() {
                commit(bounded.key)
                postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
    }

    private val pressed = mutableMapOf<Int, PressInfo>()

    // Esikatselukupla
    private var previewPopup: PopupWindow? = null
    private var previewView: TextView? = null

    // Pitkän painalluksen vaihtoehtomerkit
    private var alternatesPopup: PopupWindow? = null
    private var alternateViews: List<TextView> = emptyList()
    private var alternateValues: List<String> = emptyList()
    private var alternateSelected = 0
    private var alternatesLeft = 0f
    private var alternateCellWidth = 0f

    fun setKeyboardLayout(newLayout: KeyboardLayout) {
        if (newLayout == layout) return
        cancelAll()
        layout = newLayout
        requestLayout()
        computeBounds()
        invalidate()
    }

    fun applySettings(newTheme: KeyboardTheme, scale: Float, preview: Boolean) {
        theme = newTheme
        previewEnabled = preview
        if (heightScale != scale) {
            heightScale = scale
            requestLayout()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val baseRowHeight = dp(if (landscape) 44f else 56f)
        val rows = layout.rows.size.coerceAtLeast(1)
        val height = (baseRowHeight * rows * heightScale + dp(4f)).roundToInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeBounds()
    }

    private fun computeBounds() {
        val rows = layout.rows
        if (rows.isEmpty() || width == 0 || height == 0) {
            boundedKeys = emptyList()
            return
        }
        rowHeight = (height - dp(4f)) / rows.size.toFloat()
        val result = mutableListOf<BoundedKey>()
        rows.forEachIndexed { rowIndex, row ->
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val rowWidth = width - dp(6f)
            var x = dp(3f)
            val top = rowIndex * rowHeight
            row.forEach { key ->
                val keyWidth = rowWidth * (key.widthWeight / totalWeight)
                result += BoundedKey(key, RectF(x, top, x + keyWidth, top + rowHeight), rowIndex)
                x += keyWidth
            }
        }
        boundedKeys = result
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val pressedKeys = pressed.values.mapTo(HashSet()) { it.bounded }
        for (bounded in boundedKeys) {
            val rect = RectF(
                bounded.rect.left + keyGap / 2f,
                bounded.rect.top + keyGap,
                bounded.rect.right - keyGap / 2f,
                bounded.rect.bottom - keyGap,
            )
            keyPaint.color = keyColor(bounded.key, bounded in pressedKeys)
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, keyPaint)
            drawLabel(canvas, bounded.key, rect)
        }
    }

    private fun keyColor(key: Key, isPressed: Boolean): Int = when {
        isPressed -> theme.keyPressed
        key.action == KeyAction.Enter -> theme.accent
        key.action == KeyAction.Shift && shiftState != ShiftState.OFF -> theme.accent
        key.action is KeyAction.Text || key.action == KeyAction.Space -> theme.key
        else -> theme.specialKey
    }

    private fun drawLabel(canvas: Canvas, key: Key, rect: RectF) {
        val label = labelFor(key)
        val accentKey = key.action == KeyAction.Enter ||
            (key.action == KeyAction.Shift && shiftState != ShiftState.OFF)
        labelPaint.color = when {
            key.action == KeyAction.Space -> theme.hint
            accentKey -> theme.accentText
            else -> theme.text
        }
        labelPaint.textSize = when {
            key.action == KeyAction.Space -> rowHeight * 0.26f
            label.length > 2 -> rowHeight * 0.28f
            key.action is KeyAction.Text -> rowHeight * 0.42f
            else -> rowHeight * 0.38f
        }
        if (label.isNotEmpty()) {
            canvas.drawText(label, rect.centerX(), rect.centerY() + labelPaint.textSize * 0.35f, labelPaint)
        }
        val hint = key.longPress.firstOrNull()
        if (hint != null && key.action is KeyAction.Text) {
            hintPaint.color = theme.hint
            hintPaint.textSize = rowHeight * 0.2f
            canvas.drawText(hint, rect.right - dp(5f), rect.top + hintPaint.textSize + dp(3f), hintPaint)
        }
    }

    private fun labelFor(key: Key): String = when (key.action) {
        KeyAction.Enter -> enterText
        KeyAction.Shift -> if (shiftState == ShiftState.CAPS) "⇪" else "⇧"
        KeyAction.Space -> "suomi"
        is KeyAction.Text ->
            if (shiftState != ShiftState.OFF && key.label.length == 1) {
                key.label.uppercase(fiLocale)
            } else {
                key.label
            }
        else -> key.label
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                handleDown(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    handleMove(event.getPointerId(i), event.getX(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                handleUp(event.getPointerId(event.actionIndex))
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleDown(pointerId: Int, x: Float, y: Float) {
        val bounded = keyAt(x, y) ?: return
        val info = PressInfo(bounded)
        pressed[pointerId] = info
        val key = bounded.key
        if (key.repeatable) {
            // Toistuva näppäin poistaa heti ja jatkaa pohjassa pidettäessä.
            info.committed = true
            commit(key)
            postDelayed(info.repeatRunnable, REPEAT_DELAY_MS)
        } else {
            if (key.longPress.isNotEmpty()) {
                postDelayed(info.longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            if (key.action == KeyAction.Space) {
                info.spaceAnchorX = x
            }
            if (previewEnabled && key.action is KeyAction.Text) {
                showPreview(bounded)
            }
        }
        invalidate()
    }

    private fun handleMove(pointerId: Int, x: Float) {
        val info = pressed[pointerId] ?: return
        if (info.alternatesOpen) {
            updateAlternateSelection(x)
            return
        }
        if (info.bounded.key.action == KeyAction.Space) {
            val threshold = dp(16f)
            var dx = x - info.spaceAnchorX
            while (abs(dx) >= threshold) {
                val step = if (dx > 0) 1 else -1
                listener?.onSpaceSwipe(step)
                info.spaceAnchorX += step * threshold
                info.spaceSwiping = true
                dx = x - info.spaceAnchorX
            }
        }
    }

    private fun handleUp(pointerId: Int) {
        val info = pressed.remove(pointerId) ?: return
        removeCallbacks(info.longPressRunnable)
        removeCallbacks(info.repeatRunnable)
        hidePreview()
        when {
            info.alternatesOpen -> commitAlternate()
            info.spaceSwiping || info.committed -> Unit
            else -> commit(info.bounded.key)
        }
        invalidate()
    }

    private fun cancelAll() {
        for (info in pressed.values) {
            removeCallbacks(info.longPressRunnable)
            removeCallbacks(info.repeatRunnable)
        }
        pressed.clear()
        hidePreview()
        hideAlternates()
        invalidate()
    }

    private fun commit(key: Key) {
        when (val action = key.action) {
            is KeyAction.Text -> listener?.onText(action.text)
            else -> listener?.onKey(action)
        }
    }

    private fun keyAt(x: Float, y: Float): BoundedKey? {
        if (boundedKeys.isEmpty() || rowHeight <= 0f) return null
        val row = (y / rowHeight).toInt().coerceIn(0, layout.rows.size - 1)
        val rowKeys = boundedKeys.filter { it.row == row }
        return rowKeys.firstOrNull { x >= it.rect.left && x < it.rect.right }
            ?: rowKeys.minByOrNull { abs(it.rect.centerX() - x) }
    }

    private fun handleLongPress(info: PressInfo) {
        val key = info.bounded.key
        if (key.longPress.isEmpty() || alternatesPopup != null) return
        info.alternatesOpen = true
        hidePreview()
        showAlternates(info.bounded)
    }

    private fun showAlternates(bounded: BoundedKey) {
        val base = (bounded.key.action as? KeyAction.Text)?.text
        alternateValues = buildList {
            if (base != null) add(base)
            addAll(bounded.key.longPress)
        }.map { if (shiftState != ShiftState.OFF && it.length == 1) it.uppercase(fiLocale) else it }
        alternateSelected = 0
        alternateCellWidth = dp(46f)
        val cellHeight = dp(52f)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = keyCornerRadius
                setColor(theme.specialKey)
            }
        }
        val views = mutableListOf<TextView>()
        for (value in alternateValues) {
            val cell = TextView(context).apply {
                text = value
                gravity = Gravity.CENTER
                textSize = 20f
                layoutParams = LinearLayout.LayoutParams(
                    alternateCellWidth.roundToInt(),
                    cellHeight.roundToInt(),
                )
            }
            views += cell
            container.addView(cell)
        }
        alternateViews = views
        updateAlternateHighlight()

        val totalWidth = alternateCellWidth * alternateValues.size
        alternatesLeft = (bounded.rect.centerX() - totalWidth / 2f)
            .coerceIn(dp(4f), (width - totalWidth - dp(4f)).coerceAtLeast(dp(4f)))
        val location = IntArray(2)
        getLocationInWindow(location)
        alternatesPopup = PopupWindow(container, totalWidth.roundToInt(), cellHeight.roundToInt()).apply {
            isTouchable = false
            isClippingEnabled = false
            showAtLocation(
                this@KeyboardView,
                Gravity.NO_GRAVITY,
                location[0] + alternatesLeft.roundToInt(),
                location[1] + (bounded.rect.top - cellHeight - dp(8f)).roundToInt(),
            )
        }
    }

    private fun updateAlternateSelection(x: Float) {
        if (alternateValues.isEmpty()) return
        val index = ((x - alternatesLeft) / alternateCellWidth).toInt()
            .coerceIn(0, alternateValues.size - 1)
        if (index != alternateSelected) {
            alternateSelected = index
            updateAlternateHighlight()
        }
    }

    private fun updateAlternateHighlight() {
        alternateViews.forEachIndexed { i, view ->
            if (i == alternateSelected) {
                view.setBackgroundColor(theme.accent)
                view.setTextColor(theme.accentText)
            } else {
                view.setBackgroundColor(0)
                view.setTextColor(theme.text)
            }
        }
    }

    private fun commitAlternate() {
        val value = alternateValues.getOrNull(alternateSelected)
        hideAlternates()
        if (value != null) listener?.onText(value)
    }

    private fun hideAlternates() {
        alternatesPopup?.dismiss()
        alternatesPopup = null
        alternateViews = emptyList()
        alternateValues = emptyList()
    }

    private fun showPreview(bounded: BoundedKey) {
        val popupWidth = (bounded.rect.width() * 1.2f).roundToInt()
        val popupHeight = (rowHeight * 1.1f).roundToInt()
        val location = IntArray(2)
        getLocationInWindow(location)
        val x = location[0] + (bounded.rect.centerX() - popupWidth / 2f).roundToInt()
        val y = location[1] + (bounded.rect.top - popupHeight - dp(6f)).roundToInt()

        val view = previewView ?: TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 26f
        }.also { previewView = it }
        view.text = labelFor(bounded.key)
        view.setTextColor(theme.text)
        view.background = GradientDrawable().apply {
            cornerRadius = keyCornerRadius
            setColor(theme.keyPressed)
        }

        val popup = previewPopup ?: PopupWindow(view, popupWidth, popupHeight).apply {
            isTouchable = false
            isClippingEnabled = false
        }.also { previewPopup = it }
        popup.width = popupWidth
        popup.height = popupHeight
        if (popup.isShowing) {
            popup.update(x, y, popupWidth, popupHeight)
        } else {
            popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        }
    }

    private fun hidePreview() {
        previewPopup?.dismiss()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
    }
}
