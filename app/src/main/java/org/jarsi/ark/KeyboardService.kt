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
import android.view.inputmethod.ExtractedTextRequest
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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.jarsi.ark.view.ClipboardPanelView
import org.jarsi.ark.view.CorrectionPanelView
import org.jarsi.ark.view.EmojiPanelView
import org.jarsi.ark.view.KeyboardView
import org.jarsi.ark.view.TranslateBarView
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
    private var correctionPanel: CorrectionPanelView? = null
    private var emojiPanel: EmojiPanelView? = null
    private var translateBar: TranslateBarView? = null
    private var translateMode = false
    private val translateBuffer = StringBuilder()
    private var translateRunnable: Runnable? = null
    private var translationGeneration = 0
    private var translator: Translator? = null
    private var translatorReady = false
    private var translationLangs: List<String> =
        listOf(TranslateLanguage.FINNISH, TranslateLanguage.ENGLISH)
    private var correctionText = ""
    private var correctionWords: List<IntRange> = emptyList()
    private var correctionUnknown: Set<Int> = emptySet()
    private var correctionStartOffset = 0
    private var correctionSelected = -1
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
        ).also {
            // Omat opitut sanat vihjeiksi tunnistimelle (prx4, jarsi.org…).
            it.biasWords = { learning.biasWords(BIAS_WORD_MAX) }
        }
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

    private var autoCorrectEnabled = true

    // Viimeisin automaattikorjaus (kirjoitettu, korjattu): askelpalautin
    // heti perään palauttaa kirjoitetun; mikä tahansa muu näppäin mitätöi.
    private var pendingRevert: Pair<String, String>? = null

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
                assets.open("sanalista.txt").bufferedReader().useLines {
                    // Laajempi yleisten sanojen joukko palvelee myös
                    // korjausnäkymän pikkusanavaihtoehtoja.
                    dictionary.load(it, topWordCount = COMMON_WORD_POOL)
                }
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
        translator?.close()
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
        hideCorrectionPanel()
        hideEmojiPanel()
        hideTranslateBar()
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

    private fun hideAllPanels() {
        hideClipboardPanel()
        hideCorrectionPanel()
        hideEmojiPanel()
    }

    private fun showEmojiPanel() {
        val panel = emojiPanel ?: return
        val kb = keyboardView ?: return
        hideAllPanels()
        if (kb.height > 0) {
            panel.layoutParams = panel.layoutParams.apply { height = kb.height }
        }
        kb.visibility = View.GONE
        panel.visibility = View.VISIBLE
        toolbar?.emojiActive = true
    }

    private fun hideEmojiPanel() {
        val panel = emojiPanel ?: return
        if (panel.visibility != View.VISIBLE) return
        panel.visibility = View.GONE
        keyboardView?.visibility = View.VISIBLE
        toolbar?.emojiActive = false
    }

    private fun translationSource(): String =
        prefs.getString(PREF_TRANSLATE_SOURCE, TranslateLanguage.FINNISH)
            ?: TranslateLanguage.FINNISH

    private fun translationTarget(): String =
        prefs.getString(PREF_TRANSLATE_TARGET, TranslateLanguage.ENGLISH)
            ?: TranslateLanguage.ENGLISH

    private fun languageName(code: String): String =
        Locale(code).getDisplayLanguage(fiLocale).replaceFirstChar { it.titlecase(fiLocale) }

    /**
     * Live-käännöstila: käännösrivi korvaa ehdotusrivin, näppäily kertyy
     * riville ja kenttään kirjoittuu käännös keskeneräisenä tekstinä.
     */
    private fun showTranslateBar() {
        val bar = translateBar ?: return
        // Salasanakenttiä ei käännetä.
        if (passwordField) return
        hideAllPanels()
        translateMode = true
        translateBuffer.clear()
        bar.visibility = View.VISIBLE
        suggestionBar?.visibility = View.GONE
        toolbar?.translationActive = true
        refreshTranslationLanguages()
        updateTranslateBar()
        prepareTranslator()
    }

    private fun hideTranslateBar() {
        if (!translateMode) return
        translateMode = false
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        // Keskeneräinen käännös jää kenttään sellaisenaan.
        currentInputConnection?.finishComposingText()
        translateBuffer.clear()
        translateBar?.visibility = View.GONE
        suggestionBar?.visibility = if (suggestionsVisible) View.VISIBLE else View.GONE
        toolbar?.translationActive = false
        translationGeneration++
        translator?.close()
        translator = null
        translatorReady = false
        updateSuggestions()
    }

    private fun updateTranslateBar() {
        val bar = translateBar ?: return
        bar.setLanguages(
            translationSource().uppercase(fiLocale),
            translationTarget().uppercase(fiLocale),
        )
        val hint = if (translatorReady) {
            getString(R.string.kaannos_kirjoita, languageName(translationSource()))
        } else {
            getString(R.string.kaannos_ladataan)
        }
        bar.setBuffer(translateBuffer.toString(), hint)
    }

    private fun onTranslatePairChanged() {
        updateTranslateBar()
        prepareTranslator()
        scheduleLiveTranslate()
    }

    /** Avaa kääntäjän kieliparille ja varmistaa mallit (kertalataus). */
    private fun prepareTranslator() {
        val generation = ++translationGeneration
        translator?.close()
        translatorReady = false
        val client = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(translationSource())
                .setTargetLanguage(translationTarget())
                .build()
        )
        translator = client
        client.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                if (generation == translationGeneration && translateMode) {
                    translatorReady = true
                    updateTranslateBar()
                    scheduleLiveTranslate()
                }
            }
            .addOnFailureListener {
                if (generation == translationGeneration && translateMode) {
                    Toast.makeText(
                        this, R.string.kaannos_lataus_virhe, Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun onTranslateBufferChanged() {
        updateTranslateBar()
        scheduleLiveTranslate()
    }

    // Käännetään pienellä viiveellä, ettei jokaista näppäilyä käännetä erikseen.
    private fun scheduleLiveTranslate() {
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { runLiveTranslate() }
        translateRunnable = runnable
        mainHandler.postDelayed(runnable, LIVE_TRANSLATE_DELAY_MS)
    }

    private fun runLiveTranslate() {
        if (!translateMode || !translatorReady) return
        val ic = currentInputConnection ?: return
        val text = translateBuffer.toString()
        if (text.isBlank()) {
            ic.setComposingText("", 1)
            return
        }
        val client = translator ?: return
        val generation = translationGeneration
        client.translate(text).addOnSuccessListener { result ->
            // Tulos kelpaa vain, jos rivi ei ehtinyt muuttua välissä.
            if (translateMode && generation == translationGeneration &&
                text == translateBuffer.toString()
            ) {
                currentInputConnection?.setComposingText(result, 1)
            }
        }
    }

    /** Viimeistelee käännöksen (esim. ennen enterin toimintoa). */
    private fun finishTranslation(onDone: () -> Unit) {
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        val text = translateBuffer.toString()
        val client = translator
        if (text.isBlank() || client == null || !translatorReady) {
            currentInputConnection?.finishComposingText()
            translateBuffer.clear()
            updateTranslateBar()
            onDone()
            return
        }
        val complete = {
            currentInputConnection?.finishComposingText()
            translateBuffer.clear()
            updateTranslateBar()
            onDone()
        }
        client.translate(text)
            .addOnSuccessListener { result ->
                currentInputConnection?.setComposingText(result, 1)
                complete()
            }
            .addOnFailureListener { complete() }
    }

    /** Kielivalinnat kiertävät ladattujen mallien joukossa. */
    private fun refreshTranslationLanguages() {
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                translationLangs = (
                    models.map { it.language } +
                        listOf(TranslateLanguage.FINNISH, TranslateLanguage.ENGLISH)
                    )
                    .distinct()
                    .sortedBy { languageName(it) }
            }
    }

    private fun cycleTranslationLanguage(sourceSide: Boolean) {
        val langs = translationLangs
        if (langs.size < 2) return
        val current = if (sourceSide) translationSource() else translationTarget()
        val other = if (sourceSide) translationTarget() else translationSource()
        var index = langs.indexOf(current)
        repeat(langs.size) {
            index = (index + 1) % langs.size
            val candidate = langs[index]
            if (candidate != other) {
                prefs.edit()
                    .putString(
                        if (sourceSide) PREF_TRANSLATE_SOURCE else PREF_TRANSLATE_TARGET,
                        candidate,
                    )
                    .apply()
                onTranslatePairChanged()
                return
            }
        }
    }

    private fun showCorrectionPanel() {
        val panel = correctionPanel ?: return
        val kb = keyboardView ?: return
        // Salasanakentän sisältöä ei näytetä avoimena tekstinä.
        if (passwordField) return
        hideClipboardPanel()
        hideEmojiPanel()
        hideTranslateBar()
        if (kb.height > 0) {
            panel.layoutParams = panel.layoutParams.apply { height = kb.height }
        }
        kb.visibility = View.GONE
        panel.visibility = View.VISIBLE
        toolbar?.correctionActive = true
        // Rivi tyhjenee kunnes sanaa napautetaan; vireillä olevat
        // rivipäivitykset mitätöidään.
        suggestGeneration++
        suggestionBar?.setSuggestions(emptyList())
        shownCompletions = emptyList()
        refreshCorrectionPanel()
    }

    private fun hideCorrectionPanel() {
        val panel = correctionPanel ?: return
        if (panel.visibility != View.VISIBLE) return
        panel.visibility = View.GONE
        keyboardView?.visibility = View.VISIBLE
        toolbar?.correctionActive = false
        correctionSelected = -1
        updateSuggestions()
    }

    /** Lukee kentän koko tekstin ja sen alkusiirtymän korvauksia varten. */
    private fun readFullText(): Pair<String, Int>? {
        val ic = currentInputConnection ?: return null
        val extracted = try {
            ic.getExtractedText(ExtractedTextRequest(), 0)
        } catch (e: Exception) {
            null
        }
        extracted?.text?.let { return it.toString() to extracted.startOffset }
        // Varareitti kentille, jotka eivät tue poimintaa: kelpaa vain kun
        // tekstin alku mahtuu ikkunaan, muuten korvauskohta ei olisi tiedossa.
        val before = ic.getTextBeforeCursor(CORRECTION_LOOKBACK, 0) ?: return null
        if (before.length >= CORRECTION_LOOKBACK) return null
        val after = ic.getTextAfterCursor(CORRECTION_LOOKBACK, 0) ?: ""
        return (before.toString() + after) to 0
    }

    private fun refreshCorrectionPanel(selected: Int = -1) {
        val panel = correctionPanel ?: return
        val full = readFullText()
        correctionText = full?.first ?: ""
        correctionStartOffset = full?.second ?: 0
        correctionWords = WordTools.wordRanges(correctionText)
        correctionUnknown = emptySet()
        correctionSelected = selected
        panel.render(correctionText, correctionWords, correctionUnknown, correctionSelected)
        val text = correctionText
        val words = correctionWords
        suggestExecutor.execute {
            // Alleviivataan sanat, joita ei löydy sanastosta eikä opituista.
            val unknown = words.indices.filterTo(HashSet()) { i ->
                val word = text.substring(words[i])
                word.any { it.isLetter() } &&
                    dictionary.frequencyOf(word) == 0L &&
                    !learning.isOwnWord(word)
            }
            mainHandler.post {
                if (text == correctionText && correctionPanel?.visibility == View.VISIBLE) {
                    correctionUnknown = unknown
                    panel.render(correctionText, correctionWords, unknown, correctionSelected)
                }
            }
        }
    }

    private fun onCorrectionWordTapped(index: Int) {
        val range = correctionWords.getOrNull(index) ?: return
        correctionSelected = index
        correctionPanel?.render(correctionText, correctionWords, correctionUnknown, index)
        val word = correctionText.substring(range)
        val context = WordTools.previousWords(correctionText.subSequence(0, range.first))
        val nextWord = correctionWords.getOrNull(index + 1)?.let { correctionText.substring(it) }
        val generation = ++suggestGeneration
        suggestExecutor.execute {
            var result = suggestionEngine.alternatives(word, context, nextWord)
            if (word.first().isUpperCase()) {
                result = result.map { s -> s.replaceFirstChar { it.titlecase(fiLocale) } }
            }
            if (generation == suggestGeneration) {
                mainHandler.post {
                    if (generation == suggestGeneration && correctionSelected == index) {
                        suggestionBar?.setSuggestions(result)
                        // Vaihtoehdot eivät ole täydennyksiä: ohitussakkoa ei kirjata.
                        shownCompletions = emptyList()
                    }
                }
            }
        }
        feedback()
    }

    /** Korvaa korjausnäkymässä valitun sanan kentässä ja päivittää näkymän. */
    private fun replaceCorrectionWord(word: String) {
        val range = correctionWords.getOrNull(correctionSelected) ?: return
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.setSelection(
            correctionStartOffset + range.first,
            correctionStartOffset + range.last + 1,
        )
        ic.commitText(word, 1)
        ic.endBatchEdit()
        if (learningEnabled) {
            learning.onCorrectionAccepted(word)
            maybeFlush()
        }
        feedback()
        suggestGeneration++
        suggestionBar?.setSuggestions(emptyList())
        refreshCorrectionPanel()
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

    /**
     * Välilyönti päättää sanan: sana opitaan, ja tuntematon sana korjataan
     * taustalla lähimpään tunnettuun. Korjauksen peruu askelpalauttimella.
     */
    private fun commitSpaceWithAutoCorrect() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(MAX_WORD_LOOKBACK, 0) ?: ""
        val typed = WordTools.currentWord(before)
        val context = WordTools.previousWords(before)
        val shown = shownCompletions
        shownCompletions = emptyList()
        ic.commitText(" ", 1)
        if (typed.isEmpty() || !learningEnabled) return
        if (!autoCorrectEnabled) {
            learning.onSuggestionsIgnored(shown, typed)
            learning.onWordCommitted(typed)
            maybeFlush()
            return
        }
        // Ratkaisu tehdään taustalla, ettei sanastohaku nyi näppäilyä.
        suggestExecutor.execute {
            val corrected = suggestionEngine.autoCorrect(typed, context)
            mainHandler.post { applyAutoCorrect(typed, corrected, shown) }
        }
    }

    private fun applyAutoCorrect(typed: String, corrected: String?, shown: List<String>) {
        val display = corrected?.let {
            if (typed.first().isUpperCase()) {
                it.replaceFirstChar { c -> c.titlecase(fiLocale) }
            } else {
                it
            }
        }
        var applied = false
        val ic = currentInputConnection
        if (display != null && display != typed && ic != null) {
            val tail = "$typed "
            // Korjataan vain jos teksti on yhä ennallaan — nopea kirjoittaja
            // on voinut jo jatkaa, eikä tekstiä saa muuttaa selän takana.
            if (ic.getTextBeforeCursor(tail.length, 0)?.toString() == tail) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(tail.length, 0)
                ic.commitText("$display ", 1)
                ic.endBatchEdit()
                pendingRevert = typed to display
                applied = true
            }
        }
        val finalWord = if (applied) display ?: typed else typed
        learning.onSuggestionsIgnored(shown, finalWord)
        learning.onWordCommitted(finalWord)
        maybeFlush()
    }

    /** Askelpalautin heti korjauksen jälkeen palauttaa kirjoitetun sanan. */
    private fun revertAutoCorrect(): Boolean {
        val (typed, corrected) = pendingRevert ?: return false
        pendingRevert = null
        val ic = currentInputConnection ?: return false
        val tail = "$corrected "
        if (ic.getTextBeforeCursor(tail.length, 0)?.toString() != tail) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(tail.length, 0)
        ic.commitText(typed, 1)
        ic.endBatchEdit()
        if (learningEnabled) {
            // Peruttu sana opitaan omaksi, jottei sitä korjata enää uudestaan;
            // ketju katkaistaan, ettei korjatusta jää väärää sanaparia.
            learning.resetContext()
            learning.onWordCommitted(typed)
            maybeFlush()
        }
        return true
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
        correctionPanel?.applySettings(theme)
        emojiPanel?.applySettings(theme)
        translateBar?.applySettings(theme)
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
                    // Sanelu kirjoittaa keskeneräistä tekstiä samalla
                    // mekanismilla kuin käännös; tilat eivät voi olla yhtä aikaa.
                    hideTranslateBar()
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

                override fun onToggleCorrection() {
                    feedback()
                    if (correctionPanel?.visibility == View.VISIBLE) {
                        hideCorrectionPanel()
                    } else {
                        showCorrectionPanel()
                    }
                }

                override fun onToggleEmoji() {
                    feedback()
                    if (emojiPanel?.visibility == View.VISIBLE) {
                        hideEmojiPanel()
                    } else {
                        showEmojiPanel()
                    }
                }

                override fun onToggleTranslation() {
                    feedback()
                    if (translateMode) hideTranslateBar() else showTranslateBar()
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
        translateBar = TranslateBarView(this).also {
            it.listener = object : TranslateBarView.Listener {
                override fun onCycleSource() {
                    feedback()
                    cycleTranslationLanguage(sourceSide = true)
                }

                override fun onSwap() {
                    feedback()
                    val source = translationSource()
                    prefs.edit()
                        .putString(PREF_TRANSLATE_SOURCE, translationTarget())
                        .putString(PREF_TRANSLATE_TARGET, source)
                        .apply()
                    onTranslatePairChanged()
                }

                override fun onCycleTarget() {
                    feedback()
                    cycleTranslationLanguage(sourceSide = false)
                }

                override fun onClear() {
                    feedback()
                    translateBuffer.clear()
                    currentInputConnection?.setComposingText("", 1)
                    updateTranslateBar()
                }
            }
            it.visibility = View.GONE
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
        correctionPanel = CorrectionPanelView(this).also {
            it.listener = object : CorrectionPanelView.Listener {
                override fun onWordTapped(index: Int) = onCorrectionWordTapped(index)

                override fun onDone() {
                    feedback()
                    hideCorrectionPanel()
                }
            }
            it.visibility = View.GONE
            container.addView(it, LinearLayout.LayoutParams(params))
        }
        emojiPanel = EmojiPanelView(this).also {
            it.listener = object : EmojiPanelView.Listener {
                override fun onEmojiPicked(emoji: String) {
                    if (translateMode) {
                        // Emojit kulkevat käännösrivin kautta muun tekstin mukana.
                        translateBuffer.append(emoji)
                        onTranslateBufferChanged()
                    } else {
                        currentInputConnection?.commitText(emoji, 1)
                    }
                    feedback()
                }

                override fun onBackspace() {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    feedback(AudioManager.FX_KEYPRESS_DELETE)
                }

                override fun onClose() {
                    feedback()
                    hideEmojiPanel()
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
        hideAllPanels()
        hideTranslateBar()
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
        autoCorrectEnabled = prefs.getBoolean("automaattikorjaus", true)
        symbolOrder = SymbolOrder.load(prefs.getString(SymbolOrder.PREF_KEY, null))
        pendingRevert = null
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
        if (correctionPanel?.visibility == View.VISIBLE) {
            replaceCorrectionWord(word)
            return
        }
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
        // Korjausnäkymä hallitsee riviä itse; tavalliset päivitykset ohitetaan.
        if (correctionPanel?.visibility == View.VISIBLE) return
        // Käännöstilassa ehdotusrivi on piilossa käännösrivin alla.
        if (translateMode) return
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
        pendingRevert = null
        if (translateMode) {
            // Käännöstilassa näppäily kertyy käännösriville; kenttään
            // kirjoittuu vain käännös.
            val output = if (shiftState != ShiftState.OFF && text.length == 1) {
                text.uppercase(fiLocale)
            } else {
                text
            }
            translateBuffer.append(output)
            if (shiftState == ShiftState.SHIFT) {
                shiftState = ShiftState.OFF
                manualShift = false
                keyboardView?.shiftState = shiftState
            }
            onTranslateBufferChanged()
            feedback()
            return
        }
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
        // Korjauksen voi perua vain heti perään; muut näppäimet mitätöivät.
        if (action != KeyAction.Backspace && action != KeyAction.Shift) pendingRevert = null
        when (action) {
            KeyAction.Shift -> handleShift()
            KeyAction.Backspace -> {
                if (translateMode && translateBuffer.isNotEmpty()) {
                    // Poisto kohdistuu käännösriviin, ei kenttään.
                    val length = translateBuffer.length
                    val remove = if (length >= 2 &&
                        Character.isSurrogatePair(
                            translateBuffer[length - 2], translateBuffer[length - 1]
                        )
                    ) 2 else 1
                    translateBuffer.setLength(length - remove)
                    onTranslateBufferChanged()
                } else if (!revertAutoCorrect()) {
                    // Näppäintapahtumana, jotta valitun tekstin poisto toimii
                    // sovelluksissa oikein.
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
                feedback(AudioManager.FX_KEYPRESS_DELETE)
            }
            KeyAction.Enter -> {
                if (translateMode) {
                    // Käännös viimeistellään kenttään ennen enterin toimintoa.
                    finishTranslation { handleEnter() }
                    return
                }
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
                if (translateMode) {
                    translateBuffer.append(' ')
                    onTranslateBufferChanged()
                } else {
                    commitSpaceWithAutoCorrect()
                }
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
        const val CORRECTION_LOOKBACK = 5000
        const val COMMON_WORD_POOL = 24
        const val BIAS_WORD_MAX = 100
        const val PREF_TRANSLATE_SOURCE = "kaannos_lahde"
        const val PREF_TRANSLATE_TARGET = "kaannos_kohde"
        const val LIVE_TRANSLATE_DELAY_MS = 300L
        const val AUTO_SPACE_PUNCTUATION = ".,!?:;…"
        const val FLUSH_THRESHOLD = 50
        const val SHOWN_TOP_COUNT = 3
        const val FILE_AUTHORITY = "org.jarsi.ark.nappaimisto.tiedostot"
    }
}
