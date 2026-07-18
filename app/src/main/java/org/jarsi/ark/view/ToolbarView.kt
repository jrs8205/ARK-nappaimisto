package org.jarsi.ark.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/** Työkalurivi ehdotusrivin yläpuolella: nuolitila ja asetukset. */
class ToolbarView(context: Context) : View(context) {

    interface Listener {
        fun onToggleArrows()
        fun onOpenSettings()
    }

    var listener: Listener? = null

    /** Korostaa nuolinapin, kun nuolitila on päällä. */
    var arrowsActive = false
        set(value) {
            field = value
            invalidate()
        }

    private var theme = KeyboardTheme.TUMMA
    private var pressedIndex = -1

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private enum class Tool { ARROWS, SETTINGS }

    private val tools = listOf(Tool.ARROWS, Tool.SETTINGS)

    fun applySettings(newTheme: KeyboardTheme) {
        theme = newTheme
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(40f).roundToInt())
    }

    private fun buttonRect(index: Int): RectF {
        val size = height - dp(8f)
        val left = dp(6f) + index * (size + dp(6f))
        return RectF(left, dp(4f), left + size, dp(4f) + size)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        tools.forEachIndexed { i, tool ->
            val rect = buttonRect(i)
            val active = tool == Tool.ARROWS && arrowsActive
            buttonPaint.color = when {
                i == pressedIndex -> theme.keyPressed
                active -> theme.accent
                else -> theme.specialKey
            }
            canvas.drawRoundRect(rect, dp(8f), dp(8f), buttonPaint)
            val iconColor = if (active) theme.accentText else theme.text
            when (tool) {
                Tool.ARROWS -> drawArrowsIcon(canvas, rect, iconColor)
                Tool.SETTINGS -> {
                    iconPaint.color = iconColor
                    iconPaint.textSize = rect.height() * 0.55f
                    canvas.drawText(
                        "⚙",
                        rect.centerX(),
                        rect.centerY() + iconPaint.textSize * 0.35f,
                        iconPaint,
                    )
                }
            }
        }
    }

    /** Nelisuuntainen nuolikuvake piirretään viivoina, jotta se näkyy joka laitteella. */
    private fun drawArrowsIcon(canvas: Canvas, rect: RectF, color: Int) {
        iconStroke.color = color
        iconStroke.strokeWidth = dp(1.8f)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() * 0.28f
        val a = rect.width() * 0.12f
        canvas.drawLine(cx - r, cy, cx + r, cy, iconStroke)
        canvas.drawLine(cx, cy - r, cx, cy + r, iconStroke)
        canvas.drawLine(cx + r, cy, cx + r - a, cy - a, iconStroke)
        canvas.drawLine(cx + r, cy, cx + r - a, cy + a, iconStroke)
        canvas.drawLine(cx - r, cy, cx - r + a, cy - a, iconStroke)
        canvas.drawLine(cx - r, cy, cx - r + a, cy + a, iconStroke)
        canvas.drawLine(cx, cy - r, cx - a, cy - r + a, iconStroke)
        canvas.drawLine(cx, cy - r, cx + a, cy - r + a, iconStroke)
        canvas.drawLine(cx, cy + r, cx - a, cy + r - a, iconStroke)
        canvas.drawLine(cx, cy + r, cx + a, cy + r - a, iconStroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = indexAt(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val index = indexAt(event.x, event.y)
                if (index >= 0 && index == pressedIndex) {
                    when (tools[index]) {
                        Tool.ARROWS -> listener?.onToggleArrows()
                        Tool.SETTINGS -> listener?.onOpenSettings()
                    }
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

    private fun indexAt(x: Float, y: Float): Int =
        tools.indices.firstOrNull { buttonRect(it).contains(x, y) } ?: -1
}
