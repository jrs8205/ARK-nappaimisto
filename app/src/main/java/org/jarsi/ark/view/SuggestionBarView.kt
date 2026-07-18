package org.jarsi.ark.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Vaakasuunnassa sormella vieritettävä ehdotusrivi. Piirretään Canvasille
 * ilman alinäkymiä kuten näppäimistökin.
 */
class SuggestionBarView(context: Context) : View(context) {

    var listener: ((String) -> Unit)? = null

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

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val dividerPaint = Paint()
    private val pressedPaint = Paint()

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
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                if (!dragging && abs(dx) > touchSlop) {
                    dragging = true
                    pressedIndex = -1
                }
                if (dragging) {
                    val maxOffset = max(0f, contentWidth - width)
                    scrollOffset = (downOffset - dx).coerceIn(0f, maxOffset)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    val index = indexAt(event.x)
                    suggestions.getOrNull(index)?.let { listener?.invoke(it) }
                }
                pressedIndex = -1
                invalidate()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
            }
        }
        return true
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
