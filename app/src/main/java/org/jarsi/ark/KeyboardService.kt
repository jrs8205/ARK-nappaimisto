package org.jarsi.ark

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.preference.PreferenceManager
import org.jarsi.ark.keyboard.KeyAction
import org.jarsi.ark.keyboard.Layouts
import org.jarsi.ark.keyboard.ShiftState
import org.jarsi.ark.theme.KeyboardTheme
import org.jarsi.ark.view.KeyboardView
import java.util.Locale

/** ARK-näppäimistön pääpalvelu. */
class KeyboardService : InputMethodService(), KeyboardView.Listener {

    private enum class Page { LETTERS, SYMBOLS1, SYMBOLS2, NUMERIC }

    private var keyboardView: KeyboardView? = null
    private var page = Page.LETTERS
    private var extraKey: String? = null
    private var shiftState = ShiftState.OFF
    private var manualShift = false
    private var lastShiftTime = 0L
    private var passwordField = false
    private var soundEnabled = false
    private var vibrationEnabled = true

    private val fiLocale = Locale.forLanguageTag("fi")

    override fun onCreateInputView(): View = KeyboardView(this).also {
        it.listener = this
        keyboardView = it
    }

    // Koko näytön muokkaustila vaakasuunnassa peittäisi sovelluksen — pidetään pois.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        soundEnabled = prefs.getBoolean("aanet", false)
        vibrationEnabled = prefs.getBoolean("varina", true)

        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        passwordField = isPasswordField(inputClass, variation)

        keyboardView?.applySettings(
            KeyboardTheme.fromName(prefs.getString("teema", "tumma")),
            prefs.getInt("korkeus", 100) / 100f,
            // Salasanakentässä esikatselukupla jää pois, ettei syöte näy sivullisille.
            prefs.getBoolean("esikatselu", true) && !passwordField,
        )

        page = when (inputClass) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> Page.NUMERIC
            else -> Page.LETTERS
        }
        extraKey = if (inputClass == InputType.TYPE_CLASS_TEXT) {
            when (variation) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> "@"
                InputType.TYPE_TEXT_VARIATION_URI -> "/"
                else -> null
            }
        } else {
            null
        }

        keyboardView?.enterText = enterLabel(info)
        shiftState = ShiftState.OFF
        manualShift = false
        updateLayout()
        updateAutoCaps()
    }

    private fun isPasswordField(inputClass: Int, variation: Int): Boolean = when {
        inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) -> true
        inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD -> true
        else -> false
    }

    private fun enterLabel(info: EditorInfo): String {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "⏎"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> getString(R.string.enter_hae)
            EditorInfo.IME_ACTION_SEND -> getString(R.string.enter_laheta)
            EditorInfo.IME_ACTION_GO -> getString(R.string.enter_siirry)
            EditorInfo.IME_ACTION_NEXT -> getString(R.string.enter_seuraava)
            EditorInfo.IME_ACTION_DONE -> getString(R.string.enter_valmis)
            else -> "⏎"
        }
    }

    private fun updateLayout() {
        val layout = when (page) {
            Page.LETTERS -> Layouts.letters(extraKey)
            Page.SYMBOLS1 -> Layouts.symbols1
            Page.SYMBOLS2 -> Layouts.symbols2
            Page.NUMERIC -> Layouts.numeric
        }
        keyboardView?.setKeyboardLayout(layout)
        keyboardView?.shiftState = shiftState
    }

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        val output = if (shiftState != ShiftState.OFF) text.uppercase(fiLocale) else text
        ic.commitText(output, 1)
        if (shiftState == ShiftState.SHIFT) {
            shiftState = ShiftState.OFF
            manualShift = false
            keyboardView?.shiftState = shiftState
        }
        feedback()
    }

    override fun onKey(action: KeyAction) {
        when (action) {
            KeyAction.Shift -> handleShift()
            KeyAction.Backspace -> {
                // Näppäintapahtumana, jotta valitun tekstin poisto toimii sovelluksissa oikein.
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                feedback(AudioManager.FX_KEYPRESS_DELETE)
            }
            KeyAction.Enter -> handleEnter()
            KeyAction.Space -> {
                currentInputConnection?.commitText(" ", 1)
                feedback(AudioManager.FX_KEYPRESS_SPACEBAR)
            }
            KeyAction.Symbols -> {
                page = Page.SYMBOLS1
                updateLayout()
                feedback()
            }
            KeyAction.SymbolsMore -> {
                page = Page.SYMBOLS2
                updateLayout()
                feedback()
            }
            KeyAction.Letters -> {
                page = Page.LETTERS
                updateLayout()
                updateAutoCaps()
                feedback()
            }
            is KeyAction.Text -> Unit
        }
    }

    override fun onSpaceSwipe(steps: Int) {
        sendDownUpKeyEvents(
            if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        )
    }

    private fun handleShift() {
        val now = SystemClock.uptimeMillis()
        shiftState = when (shiftState) {
            ShiftState.OFF -> {
                manualShift = true
                ShiftState.SHIFT
            }
            ShiftState.SHIFT ->
                if (now - lastShiftTime < DOUBLE_TAP_CAPS_MS) {
                    ShiftState.CAPS
                } else {
                    manualShift = false
                    ShiftState.OFF
                }
            ShiftState.CAPS -> {
                manualShift = false
                ShiftState.OFF
            }
        }
        lastShiftTime = now
        keyboardView?.shiftState = shiftState
        feedback()
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val hasAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        val multiline = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (hasAction && !multiline) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
        feedback(AudioManager.FX_KEYPRESS_RETURN)
    }

    private fun updateAutoCaps() {
        if (page != Page.LETTERS || manualShift || shiftState == ShiftState.CAPS) return
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val caps = if (info.inputType != InputType.TYPE_NULL) {
            ic.getCursorCapsMode(info.inputType)
        } else {
            0
        }
        val newState = if (caps != 0) ShiftState.SHIFT else ShiftState.OFF
        if (newState != shiftState) {
            shiftState = newState
            keyboardView?.shiftState = newState
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        updateAutoCaps()
    }

    private fun feedback(soundEffect: Int = AudioManager.FX_KEYPRESS_STANDARD) {
        if (vibrationEnabled) {
            keyboardView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (soundEnabled) {
            getSystemService(AudioManager::class.java)?.playSoundEffect(soundEffect, -1f)
        }
    }

    private companion object {
        const val DOUBLE_TAP_CAPS_MS = 350L
    }
}
