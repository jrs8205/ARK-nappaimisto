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
        fun onToggleDictation()
        fun onToggleEmoji()
        fun onToggleClipboard()
        fun onToggleCorrection()
        fun onToggleTranslation()
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

    /** Korostaa mikrofoninapin sanelun ajaksi. */
    var micActive = false
        set(value) {
            field = value
            if (value) {
                // Aloitussykäys kertoo, että nyt voi alkaa puhua.
                micLevel = 1f
            } else {
                micShownLevel = 0f
            }
            invalidate()
        }

    /** Puheen voimakkuus 0–1; mikrofonin kehä sykkii sen mukana. */
    var micLevel = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (micActive) invalidate()
        }

    private var micShownLevel = 0f
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Korostaa leikepöytänapin, kun näkymä on auki. */
    var clipboardActive = false
        set(value) {
            field = value
            invalidate()
        }

    /** Korostaa korjausnapin, kun korjausnäkymä on auki. */
    var correctionActive = false
        set(value) {
            field = value
            invalidate()
        }

    /** Korostaa emojinapin, kun emojipaneeli on auki. */
    var emojiActive = false
        set(value) {
            field = value
            invalidate()
        }

    /** Korostaa käännösnapin, kun käännösnäkymä on auki. */
    var translationActive = false
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

    private enum class Tool { ARROWS, WEB, MIC, EMOJI, CLIPBOARD, CORRECTION, TRANSLATE, SETTINGS }

    private val tools = listOf(
        Tool.ARROWS, Tool.WEB, Tool.MIC, Tool.EMOJI,
        Tool.CLIPBOARD, Tool.CORRECTION, Tool.TRANSLATE, Tool.SETTINGS,
    )

    private val cursorIcon = context.getDrawable(R.drawable.ic_cursor_move)?.mutate()
    private val micIcon = context.getDrawable(R.drawable.ic_mic)?.mutate()
    private val emojiIcon = context.getDrawable(R.drawable.ic_emoji)?.mutate()
    private val clipboardIcon = context.getDrawable(R.drawable.ic_clipboard)?.mutate()
    private val correctionIcon = context.getDrawable(R.drawable.ic_spellcheck)?.mutate()
    private val translateIcon = context.getDrawable(R.drawable.ic_translate)?.mutate()
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

    // Kapealla näytöllä nappien leveydet kutistuvat suhteessa, jotta koko
    // rivi mahtuu aina näkyviin.
    private fun widthScale(): Float {
        val size = height - dp(8f)
        var natural = dp(6f)
        for (tool in tools) natural += buttonWidth(tool, size) + dp(6f)
        return if (natural > width && natural > 0f) width / natural else 1f
    }

    private fun buttonRect(index: Int): RectF {
        val size = height - dp(8f)
        val scale = widthScale()
        var left = dp(6f) * scale
        for (i in 0 until index) {
            left += (buttonWidth(tools[i], size) + dp(6f)) * scale
        }
        return RectF(
            left,
            dp(4f),
            left + buttonWidth(tools[index], size) * scale,
            dp(4f) + size,
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        tools.forEachIndexed { i, tool ->
            val rect = buttonRect(i)
            val active = (tool == Tool.ARROWS && arrowsActive) ||
                (tool == Tool.WEB && webActive) ||
                (tool == Tool.MIC && micActive) ||
                (tool == Tool.EMOJI && emojiActive) ||
                (tool == Tool.CLIPBOARD && clipboardActive) ||
                (tool == Tool.CORRECTION && correctionActive) ||
                (tool == Tool.TRANSLATE && translationActive)
            if (tool == Tool.MIC && micActive) {
                // Kehä sykkii puheen tahdissa ja laskee itsestään hiljaisuudessa.
                micShownLevel += (micLevel - micShownLevel) * 0.3f
                micLevel *= 0.94f
                val alpha = (60 + 140 * micShownLevel).toInt().coerceIn(0, 255)
                haloPaint.color = (theme.accent and 0x00FFFFFF) or (alpha shl 24)
                val radius = rect.height() / 2f + dp(2f) + dp(6f) * micShownLevel
                canvas.drawCircle(rect.centerX(), rect.centerY(), radius, haloPaint)
                postInvalidateOnAnimation()
            }
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
                Tool.MIC -> micIcon
                Tool.EMOJI -> emojiIcon
                Tool.CLIPBOARD -> clipboardIcon
                Tool.CORRECTION -> correctionIcon
                Tool.TRANSLATE -> translateIcon
                else -> settingsIcon
            } ?: return@forEachIndexed
            icon.setTint(contentColor)
            val iconSize = (minOf(rect.height(), rect.width()) * 0.62f).roundToInt()
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
                        Tool.MIC -> listener?.onToggleDictation()
                        Tool.EMOJI -> listener?.onToggleEmoji()
                        Tool.CLIPBOARD -> listener?.onToggleClipboard()
                        Tool.CORRECTION -> listener?.onToggleCorrection()
                        Tool.TRANSLATE -> listener?.onToggleTranslation()
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
