package org.jarsi.ark.view

import android.content.Context
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.emoji2.emojipicker.EmojiPickerView
import org.jarsi.ark.R
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Emojipaneeli näppäinalueen tilalle: Googlen valmis emojivalitsin
 * (kategoriat, viimeksi käytetyt, laitetuen suodatus) sekä alarivi,
 * josta pääsee takaisin kirjaimiin ja voi poistaa merkkejä.
 */
class EmojiPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEmojiPicked(emoji: String)
        fun onBackspace()
        fun onClose()
    }

    var listener: Listener? = null

    private var theme = KeyboardTheme.load(context)
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    // Valitsin tarvitsee AppCompat-pohjaisen teeman; palvelun konteksti
    // kääritään, ja päällysteteema värjää valitun kategorian viivan.
    private var picker = buildPicker()

    // Kirjasto lukee viimeksi käytetyt prefsseistä vain valitsinta
    // luotaessa, eikä tarjoa julkista päivityskutsua — siksi valitsin
    // rakennetaan uudelleen seuraavalla avauksella, jos emojia on käytetty.
    private var recentsDirty = false

    private fun buildPicker() = EmojiPickerView(
        ContextThemeWrapper(context, R.style.Theme_Ark_EmojiValitsin)
    ).apply {
        setOnEmojiPickedListener { item ->
            recentsDirty = true
            listener?.onEmojiPicked(item.emoji)
        }
    }

    fun refreshRecentsIfNeeded() {
        if (!recentsDirty) return
        recentsDirty = false
        val params = picker.layoutParams
        removeView(picker)
        picker = buildPicker()
        addView(picker, 0, params)
    }

    private val abcButton = TextView(context).apply {
        text = "ABC"
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(20), dp(8), dp(20), dp(8))
        setOnClickListener { listener?.onClose() }
    }

    // Vektori-ikoni tekstimerkin sijaan, jotta nappi näyttää samalta
    // kaikilla laitteilla emoji-fontista riippumatta.
    private val backspaceButton = ImageView(context).apply {
        setImageResource(R.drawable.ic_backspace)
        setPadding(dp(20), dp(6), dp(20), dp(6))
        contentDescription = context.getString(R.string.cd_poista_merkki)
        setOnClickListener { listener?.onBackspace() }
    }

    init {
        orientation = VERTICAL
        addView(picker, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(abcButton, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(android.widget.Space(context), LayoutParams(0, 0, 1f))
                addView(backspaceButton, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    // IME-ikkuna ulottuu navigointipalkin alle; palkin korkeus varataan
    // täytteenä samoin kuin muissa paneeleissa.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (bottom != paddingBottom) setPadding(0, 0, 0, bottom)
        return super.onApplyWindowInsets(insets)
    }

    fun applySettings(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(theme.background)
        abcButton.setTextColor(theme.text)
        backspaceButton.setColorFilter(theme.text)
    }
}
