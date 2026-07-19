package org.jarsi.ark

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.preference.PreferenceManager
import org.jarsi.ark.clipboard.Clip
import org.jarsi.ark.clipboard.ClipStore
import org.jarsi.ark.clipboard.NewClipActivity
import org.jarsi.ark.data.ClipEntity
import org.jarsi.ark.dictation.DictationController
import org.jarsi.ark.dictation.RecordAudioPermissionActivity
import org.jarsi.ark.data.BigramEntity
import org.jarsi.ark.data.LearnedDataStamp
import org.jarsi.ark.data.LearnedDatabase
import org.jarsi.ark.data.TrigramEntity
import org.jarsi.ark.data.WordEntity
import org.jarsi.ark.engine.DictionaryEngine
import org.jarsi.ark.engine.LearnedBigram
import org.jarsi.ark.engine.LearnedTrigram
import org.jarsi.ark.engine.LearnedWord
import org.jarsi.ark.engine.LearningEngine
import org.jarsi.ark.engine.SuggestionEngine
import org.jarsi.ark.engine.WordTools
import org.jarsi.ark.keyboard.KeyAction
import org.jarsi.ark.keyboard.Layouts
import org.jarsi.ark.keyboard.ShiftState
import org.jarsi.ark.keyboard.SymbolOrder
import org.jarsi.ark.keyboard.nextOnTap
import org.jarsi.ark.settings.SettingsActivity
import org.jarsi.ark.theme.KeyboardTheme
import org.jarsi.ark.view.ClipboardPanelView
import org.jarsi.ark.view.KeyboardView
import org.jarsi.ark.view.SuggestionBarView
import org.jarsi.ark.view.ToolbarView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

/** ARK-näppäimistön pääpalvelu. */
class KeyboardService : InputMethodService(), KeyboardView.Listener {

    private enum class Page { LETTERS, SYMBOLS1, SYMBOLS2, SYMBOLS3, NUMERIC, ARROWS }

    private var keyboardView: KeyboardView? = null
    private var suggestionBar: SuggestionBarView? = null
    private var toolbar: ToolbarView? = null
    private var page = Page.LETTERS
    private var extraKey: String? = null
    private var shiftState = ShiftState.OFF
    private var symbolOrder: List<String> = SymbolOrder.default
    private var manualShift = false
    private var passwordField = false
    private var soundEnabled = false
    private var vibrationEnabled = true

    private val dictionary = DictionaryEngine()
    private val learning = LearningEngine()
    private val suggestionEngine = SuggestionEngine(dictionary, learning)
    private var database: LearnedDatabase? = null
    private var learningEnabled = false
    private var loadedStamp = 0L
    private var pendingDictation = false
    private var pendingClipboardPanel = false

    private val clipStore = ClipStore()
    private var clipboardPanel: ClipboardPanelView? = null
    private var clipboardManager: ClipboardManager? = null
    private val clipChangedListener =
        ClipboardManager.OnPrimaryClipChangedListener { handleClipChanged() }

    private val dictation by lazy {
        DictationController(
            this,
            object : DictationController.Listener {
                override fun onPartialText(text: String) {
                    currentInputConnection?.setComposingText(text, 1)
                }

                override fun onFinalText(text: String) {
                    val ic = currentInputConnection ?: return
                    // Lopullinen teksti korvaa keskeneräisen — ei sen perään,
                    // ettei sama puhe päädy kenttään kahdesti.
                    ic.beginBatchEdit()
                    ic.setComposingText("$text ", 1)
                    ic.finishComposingText()
                    ic.endBatchEdit()
                    if (learningEnabled) {
                        // Sanellut sanat oppivat samoin kuin kirjoitetut.
                        WordTools.words(text).forEach { learning.onWordCommitted(it) }
                        maybeFlush()
                    }
                }

                override fun onDictationStateChanged(active: Boolean) {
                    toolbar?.micActive = active
                    if (!active) {
                        currentInputConnection?.finishComposingText()
                    }
                }

                override fun onDictationError(messageResId: Int) {
                    Toast.makeText(this@KeyboardService, messageResId, Toast.LENGTH_SHORT).show()
                }

                override fun onSpeechLevel(level: Float) {
                    toolbar?.micLevel = level
                }
            },
        )
    }
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val suggestExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var suggestGeneration = 0
    private var suggestionsVisible = true
    private var spaceAfterSuggestion = true
    private var commonWordsEnabled = true

    // 2 = automaattinen välilyönti juuri lisätty, 1 = commitin oma kursoripäivitys
    // ohitettu, 0 = ei voimassa. Välimerkki imaisee välin vain tilassa > 0.
    private var autoSpaceState = 0

    // Viimeksi näytetyt täydennysehdotukset ohitusten kirjaamista varten.
    // Vain täydennysrivit lasketaan — ei tyhjän syötteen ennustuksia.
    private var shownCompletions: List<String> = emptyList()

    private val fiLocale = Locale.forLanguageTag("fi")

    private lateinit var prefs: SharedPreferences

    // Ulkoasuasetukset otetaan käyttöön heti asetuksissa vaihdettaessa, jotta
    // näppäimistö on jo oikeassa teemassa kun asetuksista palataan.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "korkeus" || key == "esikatselu" || key == "varina") {
            applyVisualSettings()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
        // Sanalista ja oppimisdata ladataan taustalla; ehdotusrivi on tyhjä
        // kunnes lataus valmistuu.
        Thread {
            try {
                assets.open("sanalista.txt").bufferedReader().useLines { dictionary.load(it) }
            } catch (e: IOException) {
                // Sanalista puuttuu tai ei aukea: ehdotusrivi jää tyhjäksi.
            }
            try {
                val db = LearnedDatabase.create(this)
                val stamp = LearnedDataStamp.stamp
                val (words, pairs, triples) = readLearnedData(db)
                val clips = db.dao().allClips()
                    .map { Clip(it.id, it.text, it.imagePath, it.created, it.pinned) }
                mainHandler.post {
                    database = db
                    loadedStamp = stamp
                    learning.load(words, pairs, triples)
                    clipStore.load(clips)
                    pruneClips()
                }
            } catch (e: Exception) {
                // Tietokanta ei auennut: oppiminen jää pois, näppäimistö toimii silti.
            }
            mainHandler.post { updateSuggestions() }
        }.start()
    }

    override fun onDestroy() {
        dictation.stop()
        clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        flushLearned()
        suggestExecutor.shutdownNow()
        // Sulkeutuu vasta kun jonossa oleva kirjoitus on valmis.
        ioExecutor.shutdown()
        super.onDestroy()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Lupa-aktiviteetin avaus piilottaa näppäimistön hetkeksi; silloin
        // sanelua ei pysäytetä, jotta se voi alkaa luvan myöntämisen jälkeen.
        if (!pendingDictation) {
            dictation.stop()
        }
        flushLearned()
        learning.resetContext()
        super.onFinishInputView(finishingInput)
    }

    private fun readLearnedData(
        db: LearnedDatabase,
    ): Triple<List<LearnedWord>, List<LearnedBigram>, List<LearnedTrigram>> {
        val words = db.dao().allWords().map {
            LearnedWord(
                it.word, it.count, it.lastUsed, it.blocked, it.created,
                it.acceptedCount, it.ignoredCount, it.pinned,
            )
        }
        val pairs = db.dao().allBigrams()
            .map { LearnedBigram(it.previous, it.next, it.count, it.lastUsed) }
        val triples = db.dao().allTrigrams()
            .map { LearnedTrigram(it.first, it.second, it.next, it.count, it.lastUsed) }
        return Triple(words, pairs, triples)
    }

    /** Lataa oppimisdatan uudelleen, kun hallintanäkymä on muuttanut sitä. */
    private fun reloadLearnedIfChanged() {
        val db = database ?: return
        if (loadedStamp == LearnedDataStamp.stamp) return
        val stamp = LearnedDataStamp.stamp
        loadedStamp = stamp
        Thread {
            try {
                val (words, pairs, triples) = readLearnedData(db)
                val clips = db.dao().allClips()
                    .map { Clip(it.id, it.text, it.imagePath, it.created, it.pinned) }
                mainHandler.post {
                    learning.load(words, pairs, triples)
                    clipStore.load(clips)
                    pruneClips()
                    updateSuggestions()
                    refreshClipboardPanel()
                }
            } catch (e: Exception) {
                // Lukuvirhe: vanha data jää käyttöön, yritetään seuraavalla kerralla.
                loadedStamp = stamp - 1
            }
        }.start()
    }

    private fun handleClipChanged() {
        val manager = clipboardManager ?: return
        try {
            val clip = manager.primaryClip ?: return
            // Arkaluonteisiksi merkityt kopiot (esim. salasanat) ohitetaan kokonaan.
            val sensitive = clip.description.extras
                ?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
            if (sensitive || clip.itemCount == 0) return
            val item = clip.getItemAt(0)
            val text = item.text
            if (!text.isNullOrBlank()) {
                val saved = clipStore.addText(text.toString()) ?: return
                persistClip(saved)
                pruneClips()
                refreshClipboardPanel()
            } else {
                val uri = item.uri ?: return
                if (contentResolver.getType(uri)?.startsWith("image/") == true) {
                    copyImageClip(uri)
                }
            }
        } catch (e: Exception) {
            // Leikepöydän luku voi olla estetty taustalla; ohitetaan hiljaisesti.
        }
    }

    private fun copyImageClip(uri: Uri) {
        val type = contentResolver.getType(uri)
        val extension = when (type) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        ioExecutor.execute {
            try {
                val dir = File(filesDir, "clips").apply { mkdirs() }
                val file = File(dir, "leike_${System.currentTimeMillis()}.$extension")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: return@execute
                mainHandler.post {
                    val saved = clipStore.addImage(file.absolutePath)
                    persistClip(saved)
                    pruneClips()
                    refreshClipboardPanel()
                }
            } catch (e: Exception) {
                // Kuvan kopiointi epäonnistui: leike jää tallentamatta.
            }
        }
    }

    private fun persistClip(clip: Clip) {
        val db = database ?: return
        ioExecutor.execute {
            try {
                db.dao().upsertClip(
                    ClipEntity(clip.id, clip.text, clip.imagePath, clip.created, clip.pinned)
                )
            } catch (e: Exception) {
                // Kirjoitusvirhe ei saa kaataa näppäimistöä.
            }
        }
    }

    private fun pruneClips() {
        val prune = clipStore.prune()
        if (prune.removedIds.isEmpty()) return
        val db = database
        ioExecutor.execute {
            try {
                prune.removedIds.forEach { db?.dao()?.deleteClip(it) }
                prune.removedImagePaths.forEach { File(it).delete() }
            } catch (e: Exception) {
                // Siivousvirhe ei saa kaataa näppäimistöä.
            }
        }
    }

    private fun refreshClipboardPanel() {
        if (clipboardPanel?.visibility == View.VISIBLE) {
            clipboardPanel?.setClips(clipStore.all())
        }
    }

    private fun showClipboardPanel() {
        val panel = clipboardPanel ?: return
        val kb = keyboardView ?: return
        if (kb.height > 0) {
            panel.layoutParams = panel.layoutParams.apply { height = kb.height }
        }
        panel.setClips(clipStore.all())
        kb.visibility = View.GONE
        panel.visibility = View.VISIBLE
        toolbar?.clipboardActive = true
    }

    private fun hideClipboardPanel() {
        val panel = clipboardPanel ?: return
        if (panel.visibility != View.VISIBLE) return
        panel.closeMenu()
        panel.visibility = View.GONE
        keyboardView?.visibility = View.VISIBLE
        toolbar?.clipboardActive = false
    }

    private fun pasteClip(clip: Clip) {
        val ic = currentInputConnection ?: return
        if (clip.text != null) {
            ic.commitText(clip.text, 1)
            hideClipboardPanel()
            feedback()
            return
        }
        val path = clip.imagePath ?: return
        val info = currentInputEditorInfo ?: return
        val mime = when {
            path.endsWith(".jpg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            else -> "image/png"
        }
        val supported = EditorInfoCompat.getContentMimeTypes(info)
            .any { ClipDescription.compareMimeTypes(mime, it) }
        if (!supported) {
            Toast.makeText(this, R.string.leike_kuva_ei_tuettu, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, FILE_AUTHORITY, File(path))
            val content = InputContentInfoCompat(uri, ClipDescription("leike", arrayOf(mime)), null)
            InputConnectionCompat.commitContent(
                ic, info, content,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null,
            )
            hideClipboardPanel()
            feedback()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.leike_kuva_ei_tuettu, Toast.LENGTH_SHORT).show()
        }
    }

    /** Kirjoittaa kertyneet oppimismuutokset tietokantaan taustasäikeessä. */
    private fun flushLearned() {
        val db = database ?: return
        if (learning.dirtyCount == 0) return
        val dirty = learning.drainDirty()
        ioExecutor.execute {
            try {
                dirty.removedWords.forEach { db.dao().deleteWord(it) }
                if (dirty.words.isNotEmpty()) {
                    db.dao().upsertWords(
                        dirty.words.map {
                            WordEntity(
                                it.word.lowercase(fiLocale), it.word,
                                it.count, it.lastUsed, it.blocked, it.created,
                                it.acceptedCount, it.ignoredCount, it.pinned,
                            )
                        }
                    )
                }
                if (dirty.bigrams.isNotEmpty()) {
                    db.dao().upsertBigrams(
                        dirty.bigrams.map { BigramEntity(it.previous, it.next, it.count, it.lastUsed) }
                    )
                }
                if (dirty.trigrams.isNotEmpty()) {
                    db.dao().upsertTrigrams(
                        dirty.trigrams.map {
                            TrigramEntity(it.first, it.second, it.next, it.count, it.lastUsed)
                        }
                    )
                }
            } catch (e: Exception) {
                // Kirjoitusvirhe ei saa kaataa näppäimistöä; erä yritetään myöhemmin uudelleen.
            }
        }
    }

    private fun maybeFlush() {
        if (learning.dirtyCount >= FLUSH_THRESHOLD) flushLearned()
    }

    /** Oppii kursorin edellä olevan keskeneräisen sanan, jos oppiminen on sallittu. */
    private fun learnCurrentWord() {
        if (!learningEnabled) return
        val before = currentInputConnection?.getTextBeforeCursor(MAX_WORD_LOOKBACK, 0) ?: return
        val word = WordTools.currentWord(before)
        if (word.isNotEmpty()) {
            learning.onSuggestionsIgnored(shownCompletions, word)
            shownCompletions = emptyList()
            learning.onWordCommitted(word)
            maybeFlush()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Järjestelmän tumman tilan vaihtuminen päivittää teeman heti.
        applyVisualSettings()
    }

    private fun applyVisualSettings() {
        val theme = KeyboardTheme.load(this)
        val heightScale = prefs.getInt("korkeus", 100) / 100f
        vibrationEnabled = prefs.getBoolean("varina", true)
        keyboardView?.applySettings(
            theme,
            heightScale,
            // Salasanakentässä esikatselukupla jää pois, ettei syöte näy sivullisille.
            prefs.getBoolean("esikatselu", true) && !passwordField,
            vibrationEnabled,
        )
        suggestionBar?.applySettings(theme, heightScale)
        toolbar?.applySettings(theme)
        clipboardPanel?.applySettings(theme)
    }

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        toolbar = ToolbarView(this).also {
            it.listener = object : ToolbarView.Listener {
                override fun onToggleArrows() {
                    page = if (page == Page.ARROWS) Page.LETTERS else Page.ARROWS
                    updateLayout()
                    updateSuggestions()
                    feedback()
                }

                override fun onToggleWeb() {
                    page = if (page == Page.SYMBOLS3) Page.LETTERS else Page.SYMBOLS3
                    updateLayout()
                    updateSuggestions()
                    feedback()
                }

                override fun onToggleDictation() {
                    feedback()
                    when {
                        passwordField -> Unit
                        dictation.isActive -> dictation.stop()
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED -> dictation.start()
                        else -> {
                            // Lupa kysytään erillisellä aktiviteetilla; sanelu
                            // jatkuu automaattisesti kenttään palattaessa.
                            pendingDictation = true
                            startActivity(
                                Intent(
                                    this@KeyboardService,
                                    RecordAudioPermissionActivity::class.java,
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }

                override fun onToggleClipboard() {
                    feedback()
                    if (clipboardPanel?.visibility == View.VISIBLE) {
                        hideClipboardPanel()
                    } else {
                        showClipboardPanel()
                    }
                }

                override fun onOpenSettings() {
                    // Näppäimistö suljetaan ensin, ettei se jää sovelluksen
                    // ruutukaappaukseen ja välähdä vanhalla teemalla palattaessa.
                    requestHideSelf(0)
                    startActivity(
                        Intent(this@KeyboardService, SettingsActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            container.addView(it, LinearLayout.LayoutParams(params))
        }
        suggestionBar = SuggestionBarView(this).also {
            it.listener = ::onSuggestionPicked
            it.menuListener = object : SuggestionBarView.MenuListener {
                override fun isOwnWord(word: String) = learning.isOwnWord(word)

                override fun isPinned(word: String) = learning.isPinned(word)

                override fun onTogglePin(word: String) {
                    learning.setPinned(word, !learning.isPinned(word))
                    flushLearned()
                    updateSuggestions()
                    feedback()
                }

                override fun onDeleteLearned(word: String) {
                    learning.removeWord(word)
                    flushLearned()
                    updateSuggestions()
                    feedback()
                }

                override fun onBlockWord(word: String) {
                    learning.blockWord(word)
                    flushLearned()
                    updateSuggestions()
                    feedback()
                }
            }
            container.addView(it, LinearLayout.LayoutParams(params))
        }
        keyboardView = KeyboardView(this).also {
            it.listener = this
            container.addView(it, LinearLayout.LayoutParams(params))
        }
        clipboardPanel = ClipboardPanelView(this).also {
            it.listener = object : ClipboardPanelView.Listener {
                override fun onPaste(clip: Clip) = pasteClip(clip)

                override fun onTogglePin(clip: Clip) {
                    clipStore.setPinned(clip.id, !clip.pinned)?.let(::persistClip)
                    clipboardPanel?.setClips(clipStore.all())
                }

                override fun onWebSearch(clip: Clip) {
                    val query = clip.text ?: return
                    hideClipboardPanel()
                    try {
                        // Suora hakutulossivu: haku suoritetaan heti eikä vain
                        // esitäytetä hakukenttää.
                        val url = "https://www.google.com/search?q=" + Uri.encode(query)
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@KeyboardService,
                            R.string.leike_haku_epaonnistui,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                override fun onDelete(clip: Clip) {
                    clipStore.remove(clip.id)
                    val db = database
                    ioExecutor.execute {
                        try {
                            db?.dao()?.deleteClip(clip.id)
                            clip.imagePath?.let { path -> File(path).delete() }
                        } catch (e: Exception) {
                            // Poistovirhe ei saa kaataa näppäimistöä.
                        }
                    }
                    clipboardPanel?.setClips(clipStore.all())
                }

                override fun onCreatePin() {
                    // Oma kiinnitetty leike luodaan pienessä ikkunassa;
                    // leikepöytä avataan takaisin kenttään palattaessa.
                    pendingClipboardPanel = true
                    startActivity(
                        Intent(this@KeyboardService, NewClipActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            it.visibility = View.GONE
            container.addView(it, LinearLayout.LayoutParams(params))
        }
        return container
    }

    // Koko näytön muokkaustila vaakasuunnassa peittäisi sovelluksen — pidetään pois.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        soundEnabled = prefs.getBoolean("aanet", false)
        vibrationEnabled = prefs.getBoolean("varina", true)

        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        passwordField = isPasswordField(inputClass, variation)

        applyVisualSettings()

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
        // Salasanakentissä ei opita mitään; kentän vaihto katkaisee sanaketjun.
        learningEnabled = !passwordField
        learning.resetContext()
        reloadLearnedIfChanged()
        // Kentän vaihto (esim. sovelluksen lähetysnappi) katkaisee sanelun.
        dictation.stop()
        hideClipboardPanel()
        if (pendingDictation) {
            pendingDictation = false
            if (!passwordField &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                dictation.start()
            }
        }
        if (pendingClipboardPanel) {
            pendingClipboardPanel = false
            showClipboardPanel()
        }
        spaceAfterSuggestion = prefs.getBoolean("ehdotus_valilyonti", true)
        commonWordsEnabled = prefs.getBoolean("ehdotus_yleiset", true)
        symbolOrder = SymbolOrder.load(prefs.getString(SymbolOrder.PREF_KEY, null))
        // Salasana- ja numerokentissä ehdotusrivi on aina piilossa.
        suggestionsVisible = prefs.getBoolean("ehdotukset", true) &&
            !passwordField &&
            page != Page.NUMERIC
        suggestionBar?.visibility = if (suggestionsVisible) View.VISIBLE else View.GONE
        updateLayout()
        updateAutoCaps()
        updateSuggestions()
    }

    private fun onSuggestionPicked(word: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(MAX_WORD_LOOKBACK, 0) ?: ""
        val current = WordTools.currentWord(before)
        ic.beginBatchEdit()
        if (current.isNotEmpty()) ic.deleteSurroundingText(current.length, 0)
        ic.commitText(if (spaceAfterSuggestion) "$word " else word, 1)
        ic.endBatchEdit()
        autoSpaceState = if (spaceAfterSuggestion) 2 else 0
        if (learningEnabled) {
            // Muut näkyneet täydennykset ohitettiin; valittu sana saa hyväksynnän.
            learning.onSuggestionsIgnored(shownCompletions, word)
            shownCompletions = emptyList()
            if (word == current) {
                // Rivin kärjessä ollut oma kirjoitettu sana opitaan kuin kirjoitettuna.
                learning.onTypedWordAccepted(word)
            } else {
                learning.onSuggestionAccepted(word)
            }
            maybeFlush()
        }
        feedback()
        updateAutoCaps()
        updateSuggestions()
    }

    private fun updateSuggestions() {
        val bar = suggestionBar ?: return
        // Nuolitilassa kursoria liikutellaan tekstin yli; ehdotukset olisivat vain häiriöksi.
        if (!suggestionsVisible || page == Page.ARROWS) {
            bar.setSuggestions(emptyList())
            return
        }
        val before = currentInputConnection?.getTextBeforeCursor(MAX_WORD_LOOKBACK, 0) ?: ""
        // Lauseen alussa (automaattinen iso kirjain päällä) ehdotukset alkavat isolla.
        val shiftActive = shiftState != ShiftState.OFF
        val generation = ++suggestGeneration
        suggestExecutor.execute {
            val word = WordTools.currentWord(before)
            val context = WordTools.previousWords(before)
            var result = if (word.isNotEmpty()) {
                suggestionEngine.suggest(word, context)
            } else {
                suggestionEngine.emptyInput(context, commonWordsEnabled)
            }
            val capitalize = if (word.isEmpty()) shiftActive else word.first().isUpperCase()
            if (capitalize) {
                result = result.map { s -> s.replaceFirstChar { it.titlecase(fiLocale) } }
            }
            if (generation == suggestGeneration) {
                mainHandler.post {
                    if (generation == suggestGeneration) {
                        bar.setSuggestions(WordTools.withTypedWord(word, result))
                        shownCompletions =
                            if (word.isNotEmpty()) result.take(SHOWN_TOP_COUNT) else emptyList()
                    }
                }
            }
        }
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
            Page.SYMBOLS1 -> Layouts.symbols1(symbolOrder)
            Page.SYMBOLS2 -> Layouts.symbols2(symbolOrder)
            Page.SYMBOLS3 -> Layouts.symbols3
            Page.NUMERIC -> Layouts.numeric
            Page.ARROWS -> Layouts.arrows
        }
        keyboardView?.setKeyboardLayout(layout)
        keyboardView?.shiftState = shiftState
        // Työkalurivin korostukset seuraavat aina nykyistä sivua.
        toolbar?.arrowsActive = page == Page.ARROWS
        toolbar?.webActive = page == Page.SYMBOLS3
    }

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        // Erotinmerkki päättää keskeneräisen sanan: opitaan se ennen merkin syöttöä.
        if (text.length == 1 && !text[0].isLetterOrDigit() && text[0] != '-') {
            learnCurrentWord()
        }
        if (autoSpaceState > 0 && text.length == 1 && text[0] in AUTO_SPACE_PUNCTUATION &&
            ic.getTextBeforeCursor(1, 0)?.toString() == " "
        ) {
            // Ehdotuksen lisäämä välilyönti siirtyy välimerkin taakse: "sana ." -> "sana. "
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(text + " ", 1)
            ic.endBatchEdit()
            autoSpaceState = 2
            feedback()
            return
        }
        autoSpaceState = 0
        // Vain yksittäiset merkit shiftautuvat; esim. verkko-osoitepalat pysyvät ennallaan.
        val output = if (shiftState != ShiftState.OFF && text.length == 1) {
            text.uppercase(fiLocale)
        } else {
            text
        }
        ic.commitText(output, 1)
        if (shiftState == ShiftState.SHIFT) {
            shiftState = ShiftState.OFF
            manualShift = false
            keyboardView?.shiftState = shiftState
        }
        feedback()
    }

    override fun onKey(action: KeyAction) {
        // Tekstiä muuttava näppäin katkaisee automaattivälin seurannan.
        when (action) {
            KeyAction.Backspace, KeyAction.Enter, KeyAction.Space, is KeyAction.Arrow ->
                autoSpaceState = 0
            else -> Unit
        }
        when (action) {
            KeyAction.Shift -> handleShift()
            KeyAction.Backspace -> {
                // Näppäintapahtumana, jotta valitun tekstin poisto toimii sovelluksissa oikein.
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                feedback(AudioManager.FX_KEYPRESS_DELETE)
            }
            KeyAction.Enter -> {
                // Rivinvaihto päättää sanan muttei katkaise sanaketjua.
                learnCurrentWord()
                handleEnter()
                // Rivinvaihdon jälkeen kirjoitus jatkuu kirjaimilla, joten
                // erikoismerkkisivu ei saa jäädä päälle.
                if (page == Page.SYMBOLS1 || page == Page.SYMBOLS2) {
                    page = Page.LETTERS
                    updateLayout()
                    updateAutoCaps()
                    updateSuggestions()
                }
            }
            KeyAction.Space -> {
                learnCurrentWord()
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
                updateSuggestions()
                feedback()
            }
            is KeyAction.Arrow -> {
                sendDownUpKeyEvents(action.keyCode)
                // Kursorin siirto katkaisee sanaketjun.
                learning.resetContext()
                feedback()
            }
            KeyAction.None -> Unit
            is KeyAction.Text -> Unit
        }
    }

    override fun onSpaceSwipe(steps: Int) {
        sendDownUpKeyEvents(
            if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        )
        // Kursorin siirto katkaisee sanaketjun.
        learning.resetContext()
        // Kevyt napsaus jokaisesta askeleesta, jotta liu'utukseen saa tuntuman.
        if (vibrationEnabled) {
            keyboardView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }


    private fun handleShift() {
        shiftState = shiftState.nextOnTap()
        manualShift = shiftState != ShiftState.OFF
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
        if (autoSpaceState > 0) autoSpaceState--
        updateSuggestions()
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
        const val MAX_WORD_LOOKBACK = 48
        const val AUTO_SPACE_PUNCTUATION = ".,!?:;…"
        const val FLUSH_THRESHOLD = 50
        const val SHOWN_TOP_COUNT = 3
        const val FILE_AUTHORITY = "org.jarsi.ark.nappaimisto.tiedostot"
    }
}
