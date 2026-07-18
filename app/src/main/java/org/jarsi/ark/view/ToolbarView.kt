package org.jarsi.ark.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import org.jarsi.ark.R
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/** Työkalurivi ehdotusrivin yläpuolella: kursorinsiirtotila, verkko-osoitteet ja asetukset. */
class ToolbarView(context: Context) : View(context) {

    interface Listener {
        fun onToggleArrows()
        fun onToggleWeb()
        fun onOpenSettings()
    }

    var listener: Listener? = null

    /** Korostaa kursorinapin, kun nuolitila on päällä. */
    var arrowsActive = false
        set(value) {
            field = value
            invalidate()
        }

    /** Korostaa verkkonapin, kun osoitesivu on auki. */
    var webActive = false
        set(value) {
            field = value
            invalidate()
        }

    private var theme = KeyboardTheme.load(context)
    private var pressedIndex = -1

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private enum class Tool { ARROWS, WEB, SETTINGS }

    private val tools = listOf(Tool.ARROWS, Tool.WEB, Tool.SETTINGS)

    private val cursorIcon = context.getDrawable(R.drawable.ic_cursor_move)?.mutate()
    private val settingsIcon = context.getDrawable(R.drawable.ic_settings)?.mutate()

    fun applySettings(newTheme: KeyboardTheme) {
        theme = newTheme
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(40f).roundToInt())
    }

    // www-nappi on tekstin vuoksi leveämpi kuin kuvakenapit.
    private fun buttonWidth(tool: Tool, size: Float): Float =
        if (tool == Tool.WEB) size * 1.6f else size

    private fun buttonRect(index: Int): RectF {
        val size = height - dp(8f)
        var left = dp(6f)
        for (i in 0 until index) {
            left += buttonWidth(tools[i], size) + dp(6f)
        }
        return RectF(left, dp(4f), left + buttonWidth(tools[index], size), dp(4f) + size)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        tools.forEachIndexed { i, tool ->
            val rect = buttonRect(i)
            val active = (tool == Tool.ARROWS && arrowsActive) || (tool == Tool.WEB && webActive)
            buttonPaint.color = when {
                i == pressedIndex -> theme.keyPressed
                active -> theme.accent
                else -> theme.specialKey
            }
            canvas.drawRoundRect(rect, dp(8f), dp(8f), buttonPaint)
            val contentColor = if (active) theme.accentText else theme.text
            if (tool == Tool.WEB) {
                labelPaint.color = contentColor
                labelPaint.textSize = rect.height() * 0.42f
                canvas.drawText(
                    "www",
                    rect.centerX(),
                    rect.centerY() + labelPaint.textSize * 0.35f,
                    labelPaint,
                )
                return@forEachIndexed
            }
            val icon = when (tool) {
                Tool.ARROWS -> cursorIcon
                else -> settingsIcon
            } ?: return@forEachIndexed
            icon.setTint(contentColor)
            val iconSize = (rect.height() * 0.62f).roundToInt()
            val cx = rect.centerX().roundToInt()
            val cy = rect.centerY().roundToInt()
            icon.setBounds(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2)
            icon.draw(canvas)
        }
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
                        Tool.WEB -> listener?.onToggleWeb()
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
