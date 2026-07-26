package org.jarsi.ark

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.webkit.MimeTypeMap
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
import org.jarsi.ark.data.ApiKeyStore
import org.jarsi.ark.data.ClipEntity
import org.jarsi.ark.dictation.DictationController
import org.jarsi.ark.dictation.DictationText
import org.jarsi.ark.dictation.OpenAiDictation
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
import org.jarsi.ark.engine.TextImprover
import org.jarsi.ark.engine.WordTools
import org.jarsi.ark.keyboard.KeyAction
import org.jarsi.ark.keyboard.Layouts
import org.jarsi.ark.keyboard.ShiftState
import org.jarsi.ark.keyboard.SmartSpace
import org.jarsi.ark.keyboard.SymbolOrder
import org.jarsi.ark.keyboard.TextUndo
import org.jarsi.ark.keyboard.ToolbarOrder
import org.jarsi.ark.keyboard.TranslateBuffer
import org.jarsi.ark.keyboard.TranslateLines
import org.jarsi.ark.keyboard.TranslatePrep
import org.jarsi.ark.keyboard.nextOnTap
import org.jarsi.ark.settings.SettingsActivity
import org.jarsi.ark.theme.KeyboardTheme
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
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
import java.util.concurrent.RejectedExecutionException

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
    private val pendingClips = mutableListOf<Clip>()
    private var clipboardPanel: ClipboardPanelView? = null
    private var correctionPanel: CorrectionPanelView? = null
    private var emojiPanel: EmojiPanelView? = null
    private var translateBar: TranslateBarView? = null
    private var translateMode = false
    private val translateBuffer = TranslateBuffer()
    private var translateRunnable: Runnable? = null

    // Käännöstä voi korjata paikallaan ennen vientiä: näppäily kohdistuu
    // omaan puskuriinsa, kunnes lähdetekstiä napautetaan.
    private val translationBuffer = TranslateBuffer()
    private var translationEditing = false

    /** Puskuri, johon näppäily käännöstilassa kohdistuu. */
    private fun activeTranslateBuffer(): TranslateBuffer =
        if (translationEditing) translationBuffer else translateBuffer

    // Sanelun keskeneräisen jakson pituus käännösnäkymässä: seuraava tulos
    // korvaa sen, kuten kentässä keskeneräinen composing-teksti.
    private var dictationPending = 0

    /**
     * Kirjoittaa sanelun tuloksen käännösnäkymään sille alueelle, jota
     * parhaillaan kirjoitetaan. Keskeneräinen jakso korvautuu seuraavalla,
     * jottei sama puhe päädy tekstiin kahdesti.
     */
    private fun dictateIntoTranslation(text: String, final: Boolean) {
        val buffer = activeTranslateBuffer()
        if (dictationPending > 0) buffer.deleteBeforeCursor(dictationPending)
        val inserted = if (final) "$text " else text
        buffer.insert(inserted)
        dictationPending = if (final) 0 else inserted.length
        onTranslateBufferChanged()
    }

    /** Kohdistus takaisin lähdetekstiin; keskeneräinen korjaus jää käännökseen. */
    private fun stopTranslationEditing() {
        translationEditing = false
        translationBuffer.clear()
        // Kirjoitettava alue vaihtui: sanelun keskeneräinen jakso ei enää
        // osoita mihinkään.
        dictationPending = 0
    }

    // Käännöstilan viimeisin käyttöhetki muistiajan vertailuun; puskuri
    // elää vain prosessin muistissa, joten elapsedRealtime riittää.
    private var translateLastUsed = 0L

    // Näkyvä käännös ja sen tuoreus: tuore käännös vastaa rivin nykyistä
    // sisältöä ja kelpaa vietäväksi kenttään sellaisenaan.
    private var currentTranslation = ""
    private var translationFresh = false
    private var translationGeneration = 0
    private var aiTranslateGeneration = 0
    private var translator: Translator? = null
    private var translatorReady = false

    // Lähdekielen päättely kirjoitetusta tekstistä; käsin valittu lähde
    // lukitsee päättelyn, kunnes rivi tyhjennetään.
    private var languageIdentifier: LanguageIdentifier? = null
    private var translateSourceLocked = false

    // Kieliparilta puuttuu ladattu malli; lataus odottaa käyttäjän lupaa.
    private var translationModelsMissing = false
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

    // Jaettu kuuntelija: sama tulosten käsittely laitteen tunnistimelle
    // ja OpenAI-suoratoistomoottorille.
    private val dictationListener = object : DictationController.Listener {
                override fun onPartialText(text: String) {
                    lastEditTime = SystemClock.uptimeMillis()
                    val shown = if (dictationCapNext) DictationText.capitalize(text) else text
                    if (translateMode) {
                        dictateIntoTranslation(shown, final = false)
                        return
                    }
                    currentInputConnection?.setComposingText(shown, 1)
                }

                override fun onFinalText(text: String) {
                    lastEditTime = SystemClock.uptimeMillis()
                    val committed =
                        if (dictationCapNext) DictationText.capitalize(text) else text
                    if (translateMode) {
                        dictateIntoTranslation(committed, final = true)
                    } else {
                        val ic = currentInputConnection ?: return
                        // Lopullinen teksti korvaa keskeneräisen — ei sen perään,
                        // ettei sama puhe päädy kenttään kahdesti.
                        ic.beginBatchEdit()
                        ic.setComposingText("$committed ", 1)
                        ic.finishComposingText()
                        ic.endBatchEdit()
                        textUndo.record("$committed ")
                    }
                    // Lauseen loppu nostaa seuraavankin jakson isolle alkukirjaimelle.
                    dictationCapNext = DictationText.endsSentence(committed)
                    if (learningEnabled) {
                        // Sanellut sanat oppivat samoin kuin kirjoitetut.
                        WordTools.words(text).forEach { learning.onWordCommitted(it) }
                        maybeFlush()
                    }
                }

                override fun onDictationStateChanged(active: Boolean) {
                    toolbar?.micActive = active
                    if (!active) {
                        dictationPending = 0
                        currentInputConnection?.finishComposingText()
                    }
                }

                override fun onDictationError(messageResId: Int) {
                    Toast.makeText(this@KeyboardService, messageResId, Toast.LENGTH_SHORT).show()
                }

                override fun onDictationErrorMessage(message: String) {
                    Toast.makeText(
                        this@KeyboardService,
                        getString(R.string.sanelu_verkkovirhe, message),
                        Toast.LENGTH_LONG,
                    ).show()
                }

                override fun onSpeechLevel(level: Float) {
                    toolbar?.micLevel = level
                }
    }

    private val dictation by lazy {
        DictationController(this, dictationListener).also {
            // Omat opitut sanat vihjeiksi tunnistimelle (prx4, jarsi.org…).
            it.biasWords = { learning.biasWords(BIAS_WORD_MAX) }
        }
    }

    // OpenAI-tunnistus luodaan vasta, kun se on asetuksista valittu.
    private var openAiDictation: OpenAiDictation? = null

    // Kentän tai tilan vaihtuessa OpenAI-tulokset hylätään, ettei vanha
    // puhe valu uuteen kenttään; mikkinapin pysäytys viimeistelee ne.
    private fun stopDictation() {
        dictation.stop()
        openAiDictation?.cancel()
    }

    private fun dictationActive(): Boolean =
        dictation.isActive || openAiDictation?.isActive == true

    /**
     * Käynnistää sanelun asetusten hiljaisuusrajalla ja valitulla
     * moottorilla; alku isolla kirjaimella. Jos OpenAI-tunnistus on
     * valittu mutta avainta ei ole, pudotaan laitteen tunnistukseen,
     * jottei sanelu jää mykäksi.
     */
    private fun startDictation() {
        val silence = prefs.getInt(PREF_DICTATION_SILENCE, 5).coerceIn(2, 10) * 1000L
        dictationCapNext = true
        if (prefs.getString(PREF_DICTATION_ENGINE, null) == "openai") {
            if (ApiKeyStore.exists(prefs, ApiKeyStore.Slot.OPENAI)) {
                val engine = openAiDictation
                    ?: OpenAiDictation(prefs, dictationListener).also { openAiDictation = it }
                engine.silenceLimitMs = silence
                engine.start()
                return
            }
            Toast.makeText(this, R.string.sanelu_openai_ei_avainta, Toast.LENGTH_SHORT).show()
        }
        dictation.silenceLimitMs = silence
        dictation.start()
    }
    // Seuraava sanelujakso alkaa isolla kirjaimella (istunnon ja lauseen alut).
    private var dictationCapNext = true

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val suggestExecutor = Executors.newSingleThreadExecutor()

    // Paranna teksti -verkkopyynnöt omassa säikeessään, ettei hidas
    // yhteys viivästytä oppimisdatan kirjoituksia.
    private val improveExecutor = Executors.newSingleThreadExecutor()
    private var improveGeneration = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var suggestGeneration = 0
    private var suggestionsVisible = true
    private var spaceAfterSuggestion = true
    private var commonWordsEnabled = true

    // 2 = automaattinen välilyönti juuri lisätty, 1 = commitin oma kursoripäivitys
    // ohitettu, 0 = ei voimassa. Välimerkki imaisee välin vain tilassa > 0.
    private var autoSpaceState = 0

    // 2 = välimerkki juuri kirjoitettu, 1 = commitin oma kursoripäivitys
    // ohitettu, 0 = ei voimassa. Kirjain saa välin eteensä vain tilassa > 0.
    private var smartSpaceState = 0

    // Välimerkin perään lisättiin juuri automaattiväli; heti perään
    // painettu välilyönti ohitetaan tuplavälin estämiseksi.
    private var punctSpaceAdded = false

    // Edellisen välilyöntipainalluksen hetki kaksoisvälilyönnin pisteelle.
    private var lastSpaceTime = 0L
    private var doubleSpacePeriodEnabled = true

    // Kenttäkohtaiset ehdot: älykäs jälkiväli ei sovi osoite-, sähköposti-
    // eikä koodikenttiin, ja iso alkukirjain seuraa kentän omaa pyyntöä.
    private var smartSpaceField = false
    private var capSentencesField = false

    // Viimeisin älykäs jälkiväli (kirjoitettu kirjain, kenttään mennyt häntä):
    // askelpalautin heti perään palauttaa kirjaimen ilman väliä, jotta
    // esim. jarsi.org jatkuu pisteen jälkeen ehjänä.
    private var pendingSpaceRevert: Pair<String, String>? = null

    // Työkalurivin peruutusnapin kertaperuutus näppäimistön toimenpiteille.
    private val textUndo = TextUndo()

    private var autoCorrectEnabled = true
    private var noSuggestionsField = false
    private var numberRowEnabled = true

    // Editori-istunnon tunniste: viivästynyt callback (automaattikorjaus,
    // käännöksen viimeistely) ei saa kirjoittaa toiseen kenttään.
    private var editorSessionId = 0

    // Tuhotulle palvelulle palaavat callbackit eivät saa jonottaa uutta työtä.
    private var destroyed = false

    // Viimeisin oma muokkaushetki: sen jälkeiset valintamuutokset ovat
    // käyttäjän kursorihyppyjä, jotka katkaisevat sanaketjun.
    private var lastEditTime = 0L

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
                    if (destroyed) return@post
                    database = db
                    loadedStamp = stamp
                    learning.load(words, pairs, triples)
                    // Latauksen aikana kopioidut leikkeet säilyvät ja tallentuvat.
                    val pending = pendingClips.toList()
                    pendingClips.clear()
                    clipStore.load(clips + pending)
                    pending.forEach(::persistClip)
                    pruneClips()
                }
            } catch (e: Exception) {
                // Tietokanta ei auennut: oppiminen jää pois, näppäimistö toimii silti.
            }
            mainHandler.post { updateSuggestions() }
        }.start()
    }

    override fun onDestroy() {
        editorSessionId++
        dictation.stop()
        openAiDictation?.destroy()
        translator?.close()
        languageIdentifier?.close()
        clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        flushLearned()
        destroyed = true
        suggestExecutor.shutdownNow()
        improveExecutor.shutdownNow()
        // Sulkeutuu vasta kun jonossa oleva kirjoitus on valmis.
        ioExecutor.shutdown()
        super.onDestroy()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Lupa-aktiviteetin avaus piilottaa näppäimistön hetkeksi; silloin
        // sanelua ei pysäytetä, jotta se voi alkaa luvan myöntämisen jälkeen.
        if (!pendingDictation) {
            stopDictation()
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
                    if (destroyed) return@post
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
        // Tiedostopääte säilyttää todellisen kuvatyypin liittämistä varten.
        // Tuntematonta tyyppiä ei tallenneta väärällä nimellä eikä väitetä
        // liitettäessä PNG:ksi — sellainen leike ohitetaan.
        val type = contentResolver.getType(uri)
        val extension = when (type) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/png" -> "png"
            "image/gif" -> "gif"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(type ?: "")
        } ?: return
        executeIo {
            val dir = File(filesDir, "clips").apply { mkdirs() }
            val file = File(dir, "leike_${System.currentTimeMillis()}.$extension")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        // Kokoraja suojaa tallennustilaa jättikuvilta.
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_IMAGE_CLIP_BYTES) error("kuvaleike on liian suuri")
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: return@executeIo
                mainHandler.post {
                    val saved = clipStore.addImage(file.absolutePath)
                    persistClip(saved)
                    pruneClips()
                    refreshClipboardPanel()
                }
            } catch (e: Exception) {
                // Epäonnistunut tai liian suuri kopio siivotaan levyltä pois.
                file.delete()
            }
        }
    }

    private fun persistClip(clip: Clip) {
        val db = database
        if (db == null) {
            // Tietokanta latautuu vielä: leike talteen, tallennus latauksen perään.
            pendingClips += clip
            return
        }
        executeIo {
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
        executeIo {
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
        // Käännösnäkymä jää auki kuten emojipaneelinkin alla: leike
        // liitetään siihen alueeseen, jota parhaillaan kirjoitetaan.
        stopDictation()
        resetSidePage()
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

    /**
     * Sivutila palaa kirjaimiin paneelin tai sanelun alta, ettei nuoli-
     * tai www-korostus jää aktiiviseksi toisen tilan rinnalle.
     */
    private fun resetSidePage() {
        if (page == Page.ARROWS || page == Page.SYMBOLS3) {
            page = Page.LETTERS
            updateLayout()
        }
    }

    private fun showEmojiPanel() {
        val panel = emojiPanel ?: return
        val kb = keyboardView ?: return
        hideAllPanels()
        stopDictation()
        resetSidePage()
        panel.refreshRecentsIfNeeded()
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

    /** Kielen nimi pienellä alkukirjaimella kielipillereitä varten. */
    private fun languageNameLower(code: String): String =
        Locale(code).getDisplayLanguage(fiLocale)

    /**
     * Käännösnäkymä: näppäily kertyy ylärivin lähdetekstiin ja käännös
     * näkyy alarivillä livenä. Kenttään ei kirjoitu mitään itsestään —
     * käännös viedään Lisää-napilla tai enterillä. Ehdotusrivi jää
     * näkyviin ja palvelee lähdetekstiä.
     */
    private fun showTranslateBar() {
        val bar = translateBar ?: return
        // Salasanakenttiä ei käännetä.
        if (passwordField) return
        // Vanha käännösteksti unohtuu muistiajan kuluttua: tuntien takainen
        // teksti avautuisi muuten yllättäen uuden käännöstyön pohjaksi.
        val memoryMs = prefs.getInt(PREF_TRANSLATE_MEMORY, 30) * 60_000L
        if (translateBuffer.text.isNotEmpty() && translateLastUsed > 0 &&
            SystemClock.elapsedRealtime() - translateLastUsed > memoryMs
        ) {
            translateBuffer.clear()
            lastInsertedTranslation = ""
        }
        if (translateBuffer.text.isEmpty()) translateSourceLocked = false
        // Sanelu ja käännös eivät voi olla yhtä aikaa päällä.
        stopDictation()
        hideAllPanels()
        resetSidePage()
        translateMode = true
        currentTranslation = ""
        translationFresh = false
        stopTranslationEditing()
        bar.visibility = View.VISIBLE
        toolbar?.translationActive = true
        refreshTranslationLanguages()
        updateTranslateBar()
        checkTranslationModels()
        updateSuggestions()
    }

    /**
     * Sulkee käännösnäkymän. Kenttään ei kosketa, ja teksti säilyy
     * puskurissa kunnes käyttäjä itse tyhjentää sen (✕ tai poistot) —
     * käännöstyö ei katoa sulkuun, kentän vaihtoon eikä sovelluksen
     * vaihtoon, koska mikään ei koskaan kirjoitu kenttään itsestään.
     */
    private fun hideTranslateBar() {
        if (!translateMode) return
        translateLastUsed = SystemClock.elapsedRealtime()
        translateMode = false
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        translationGeneration++
        aiTranslateGeneration++
        translator?.close()
        translator = null
        translatorReady = false
        translationModelsMissing = false
        currentTranslation = ""
        translationFresh = false
        stopTranslationEditing()
        translateBar?.visibility = View.GONE
        suggestionBar?.visibility = if (suggestionsVisible) View.VISIBLE else View.GONE
        toolbar?.translationActive = false
        updateSuggestions()
    }

    private fun updateTranslateBar() {
        val bar = translateBar ?: return
        bar.setLanguages(
            translationSource(),
            languageNameLower(translationSource()),
            translationTarget(),
            languageNameLower(translationTarget()),
        )
        // Kirjoittaa saa heti myös malleja odottaessa; vain valmistelu
        // näyttää oman vihjeensä.
        val hint = if (translatorReady || translationModelsMissing) {
            getString(R.string.kaannos_kirjoita, languageName(translationSource()))
        } else {
            getString(R.string.kaannos_ladataan)
        }
        bar.pasteAvailable = clipboardManager?.hasPrimaryClip() == true
        // ✨ piilotetaan kokonaan, kun valitulla palvelulla ei ole avainta —
        // nappi joka ei tee mitään hämmentäisi.
        bar.aiEnabled = ApiKeyStore.exists(
            prefs,
            if (openAiSelected()) ApiKeyStore.Slot.OPENAI else ApiKeyStore.Slot.CLAUDE,
        )
        // Kursori näkyy vain siinä alueessa, johon näppäily kohdistuu.
        bar.setBuffer(
            translateBuffer.text,
            if (translationEditing) -1 else translateBuffer.cursor,
            hint,
        )
        bar.setTranslation(
            currentTranslation,
            getString(
                if (translationModelsMissing) {
                    R.string.kaannos_mallit_puuttuvat
                } else {
                    R.string.kaannos_tyhja
                }
            ),
            if (translationEditing) translationBuffer.cursor else -1,
        )
        bar.setModelsMissing(translationModelsMissing)
        // Shift-nuoli seuraa käännösrivin tekstiä kuten kenttää.
        if (translateMode) updateAutoCaps()
    }

    private fun onTranslatePairChanged() {
        // Uusi kielipari tuo uuden käännöksen, joten korjaus päättyy.
        stopTranslationEditing()
        updateTranslateBar()
        checkTranslationModels()
        scheduleLiveTranslate()
    }

    /**
     * Mallit ladataan vasta käyttäjän luvalla: jos kieliparilta puuttuu
     * malli, käännösalue näyttää kertalatauksen kehotteen Lataa-nappeineen
     * eikä mitään ladata itsestään. Tarkistusvirheessä toimitaan kuten
     * ennen, ettei kääntäjä jää verkko-ongelmasta lukkoon.
     */
    private fun checkTranslationModels() {
        val generation = ++translationGeneration
        translator?.close()
        translator = null
        translatorReady = false
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                if (generation != translationGeneration || !translateMode) {
                    return@addOnSuccessListener
                }
                val have = models.mapTo(mutableSetOf()) { it.language }
                translationModelsMissing =
                    translationSource() !in have || translationTarget() !in have
                if (translationModelsMissing) {
                    updateTranslateBar()
                } else {
                    prepareTranslator()
                }
            }
            .addOnFailureListener {
                if (generation == translationGeneration && translateMode) {
                    prepareTranslator()
                }
            }
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
        if (translationEditing) {
            // Käsin korjattu käännös on se, joka viedään ja kopioidaan;
            // uutta konekäännöstä ei haeta ennen kuin lähdeteksti muuttuu.
            currentTranslation = translationBuffer.toString()
            translationFresh = currentTranslation.isNotBlank()
            updateTranslateBar()
            updateSuggestions()
            return
        }
        // Lähdetekstin muutos vanhentaa näkyvän käännöksen, kunnes uusi valmistuu.
        translationFresh = false
        updateTranslateBar()
        scheduleLiveTranslate()
        updateSuggestions()
    }

    // Käännetään pienellä viiveellä, ettei jokaista näppäilyä käännetä erikseen.
    private fun scheduleLiveTranslate() {
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { runLiveTranslate() }
        translateRunnable = runnable
        mainHandler.postDelayed(runnable, LIVE_TRANSLATE_DELAY_MS)
    }

    private fun runLiveTranslate() {
        // Käsin korjattua käännöstä ei ylikirjoiteta kesken muokkauksen.
        if (!translateMode || !translatorReady || translationEditing) return
        val text = translateBuffer.toString()
        if (text.isBlank()) {
            currentTranslation = ""
            translationFresh = false
            translateBar?.setTranslation("", getString(R.string.kaannos_tyhja))
            return
        }
        detectSourceLanguage(text)
        val client = translator ?: return
        val generation = translationGeneration
        translateKeepingLines(
            client,
            text,
            onResult = { result ->
                // Tulos kelpaa vain, jos rivi ei ehtinyt muuttua välissä.
                if (translateMode && generation == translationGeneration &&
                    text == translateBuffer.toString()
                ) {
                    currentTranslation = result
                    translationFresh = true
                    translateBar?.setTranslation(
                        currentTranslation, getString(R.string.kaannos_tyhja)
                    )
                }
            },
        )
    }

    /**
     * Päättelee lähdekielen kirjoitetusta tekstistä käyttäjän omien
     * (ladattujen) kielten joukosta, ettei päättely koskaan vaadi uuden
     * mallin latausta. Kohdekielellä kirjoitettu teksti kääntää suunnan.
     * Rinnakkainen käännös nykyparilla saa jatkua: kielenvaihto käynnistää
     * uuden käännöksen ja korvaa tuloksen.
     */
    private fun detectSourceLanguage(text: String) {
        if (translateSourceLocked || text.length < AUTODETECT_MIN_CHARS) return
        val identifier = languageIdentifier ?: LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(AUTODETECT_CONFIDENCE)
                .build()
        ).also { languageIdentifier = it }
        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag ->
                if (!translateMode || text != translateBuffer.toString()) {
                    return@addOnSuccessListener
                }
                val code = TranslateLanguage.fromLanguageTag(tag)
                    ?: return@addOnSuccessListener
                if (code !in translationLangs || code == translationSource()) {
                    return@addOnSuccessListener
                }
                pickTranslationLanguage(sourceSide = true, code = code)
            }
    }

    /**
     * Kääntää tekstin rivi kerrallaan, jotta rivinvaihdot säilyvät —
     * esikäsittely ja konekäännös litistäisivät ne muuten yhdeksi riviksi.
     * [onResult] kutsutaan päälangalla vain onnistuneella käännöksellä;
     * [onFailure] virheellä.
     */
    private fun translateKeepingLines(
        client: Translator,
        text: String,
        onResult: (String) -> Unit,
        onFailure: () -> Unit = {},
    ) {
        val lines = TranslateLines.split(text)
        val jobs = TranslateLines.translatable(lines).map { TranslatePrep.prepare(it) }
        if (jobs.isEmpty()) {
            onResult("")
            return
        }
        val english = translationTarget() == TranslateLanguage.ENGLISH
        Tasks.whenAllSuccess<String>(jobs.map { client.translate(it.text) })
            .addOnSuccessListener { results ->
                val cleaned = results.mapIndexed { index, result ->
                    TranslatePrep.clean(result, jobs[index], english)
                }
                onResult(TranslateLines.merge(lines, cleaned))
            }
            .addOnFailureListener { onFailure() }
    }

    /**
     * Vie näkyvän käännöksen kenttään ja suorittaa [onDone] vasta sen
     * jälkeen. Tuore käännös viedään heti; muuten käännetään ensin, ettei
     * kenttään mene rivin sisältöä vastaamatonta tekstiä.
     */
    private fun insertTranslation(onDone: () -> Unit = {}) {
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        val text = translateBuffer.toString()
        if (text.isBlank()) {
            onDone()
            return
        }
        if (translationFresh && currentTranslation.isNotBlank()) {
            commitTranslation(currentTranslation)
            onDone()
            return
        }
        val client = translator
        if (client == null || !translatorReady) {
            Toast.makeText(this, R.string.kaannos_ladataan, Toast.LENGTH_SHORT).show()
            return
        }
        val generation = ++translationGeneration
        val session = editorSessionId
        translateKeepingLines(
            client,
            text,
            onResult = { result ->
                // Vanhentunut tulos hylätään: käyttäjä ehti jatkaa, sulkea
                // tilan, vaihtaa kenttää tai syötenäkymä ehti sulkeutua.
                if (!translateMode || generation != translationGeneration ||
                    session != editorSessionId || translateBuffer.toString() != text
                ) {
                    return@translateKeepingLines
                }
                commitTranslation(result)
                onDone()
            },
            onFailure = {
                if (translateMode && generation == translationGeneration &&
                    session == editorSessionId
                ) {
                    Toast.makeText(this, R.string.kaannos_virhe, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    // Viimeisin kenttään viety käännös: uusi vienti korvaa sen, ettei
    // jatkettu teksti kahdennu kenttään. Nollataan kentän vaihtuessa.
    private var lastInsertedTranslation = ""

    /**
     * Kirjoittaa käännöksen kenttään. Jos edellinen viety käännös on
     * yhä kursorin edellä, se korvataan uudella versiolla — kentässä
     * pysyy aina yksi ajantasainen kopio käännöksestä. Tekstit säilyvät
     * näkymässä muokattavina; ✕ tyhjentää.
     */
    private fun commitTranslation(translation: String) {
        val ic = currentInputConnection ?: return
        markOwnEdit()
        ic.beginBatchEdit()
        val previous = lastInsertedTranslation
        if (previous.isNotEmpty() &&
            ic.getTextBeforeCursor(previous.length, 0)?.toString() == previous
        ) {
            ic.deleteSurroundingText(previous.length, 0)
        }
        ic.commitText(translation, 1)
        ic.endBatchEdit()
        textUndo.record(translation)
        lastInsertedTranslation = translation
        feedback()
    }

    /** Hakee laadukkaamman käännöksen valitulta AI-palvelulta alariville. */
    private fun requestAiTranslation() {
        feedback()
        val text = translateBuffer.toString()
        if (text.isBlank()) return
        if (text.length > TextImprover.MAX_INPUT_CHARS) {
            Toast.makeText(
                this, R.string.korjaus_paranna_liian_pitka, Toast.LENGTH_SHORT
            ).show()
            return
        }
        val openAi = openAiSelected()
        val apiKey = ApiKeyStore.read(
            prefs,
            if (openAi) ApiKeyStore.Slot.OPENAI else ApiKeyStore.Slot.CLAUDE,
        ).orEmpty()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, R.string.malli_aseta_avain, Toast.LENGTH_SHORT).show()
            return
        }
        // AI-käännös korvaa alueen sisällön, joten käsin korjaus päättyy.
        stopTranslationEditing()
        translateBar?.setTranslation("", getString(R.string.kaannos_ai_kaannetaan))
        val sourceName = languageName(translationSource())
        val targetName = languageName(translationTarget())
        val generation = ++aiTranslateGeneration
        improveExecutor.execute {
            val body = if (openAi) {
                val model = prefs.getString(PREF_OPENAI_MODEL, null)
                    ?.takeIf { it.isNotBlank() } ?: TextImprover.OPENAI_MODEL
                TextImprover.buildOpenAiTranslateRequest(text, sourceName, targetName, model)
            } else {
                val model = prefs.getString(PREF_IMPROVE_MODEL, null)
                    ?.takeIf { it.isNotBlank() } ?: TextImprover.MODEL
                TextImprover.buildTranslateRequest(text, sourceName, targetName, model)
            }
            val (response, error) = postAi(openAi, apiKey, body)
            val translation = response?.let {
                if (openAi) {
                    TextImprover.parseOpenAiResponse(it)
                } else {
                    TextImprover.parseResponse(it)
                }
            }
            mainHandler.post {
                if (generation != aiTranslateGeneration || destroyed || !translateMode) {
                    return@post
                }
                if (text != translateBuffer.toString()) return@post
                if (!translation.isNullOrBlank()) {
                    currentTranslation = translation
                    translationFresh = true
                    translateBar?.setTranslation(
                        translation, getString(R.string.kaannos_tyhja)
                    )
                } else {
                    // Live-käännös palaa näkyviin ja syy kerrotaan.
                    updateTranslateBar()
                    val reason = error ?: getString(R.string.korjaus_tyhja_vastaus)
                    Toast.makeText(
                        this,
                        getString(R.string.kaannos_ai_virhe_syy, reason),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
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
                // Kielivalikossa ovat kaikki tuetut kielet: omat (ladatut)
                // ensin ja loput latausmerkinnällä aakkosjärjestyksessä.
                val downloaded = translationLangs.toSet()
                translateBar?.setLanguageChoices(
                    TranslateLanguage.getAllLanguages()
                        .map {
                            TranslateBarView.LanguageChoice(
                                it, languageName(it), it in downloaded
                            )
                        }
                        .sortedWith(
                            compareByDescending<TranslateBarView.LanguageChoice> {
                                it.downloaded
                            }.thenBy { it.name }
                        )
                )
            }
    }

    /** Asettaa käännösparin kielen; sama kieli molemmin puolin vaihtaa suunnan. */
    private fun pickTranslationLanguage(sourceSide: Boolean, code: String) {
        val other = if (sourceSide) translationTarget() else translationSource()
        if (code == other) {
            prefs.edit()
                .putString(PREF_TRANSLATE_SOURCE, translationTarget())
                .putString(PREF_TRANSLATE_TARGET, translationSource())
                .apply()
        } else {
            prefs.edit()
                .putString(
                    if (sourceSide) PREF_TRANSLATE_SOURCE else PREF_TRANSLATE_TARGET,
                    code,
                )
                .apply()
        }
        onTranslatePairChanged()
    }

    private fun showCorrectionPanel() {
        val panel = correctionPanel ?: return
        val kb = keyboardView ?: return
        // Salasanakentän sisältöä ei näytetä avoimena tekstinä, eikä
        // numerokentissä (esim. kortinnumero) ole oikoluettavaa.
        if (passwordField || page == Page.NUMERIC) return
        hideClipboardPanel()
        hideEmojiPanel()
        hideTranslateBar()
        stopDictation()
        resetSidePage()
        if (kb.height > 0) {
            panel.layoutParams = panel.layoutParams.apply { height = kb.height }
        }
        kb.visibility = View.GONE
        panel.visibility = View.VISIBLE
        toolbar?.correctionActive = true
        panel.improveEnabled = ApiKeyStore.exists(
            prefs,
            if (openAiSelected()) ApiKeyStore.Slot.OPENAI else ApiKeyStore.Slot.CLAUDE,
        )
        panel.hideImprovement()
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
        panel.hideImprovement()
        improveGeneration++
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
        executeSuggest {
            // Alleviivataan sanat, joita ei löydy sanastosta eikä
            // vakiintuneista omista. Kerran kirjoitettu tuntematon
            // alleviivataan vielä — muuten tuore lyöntivirhe "opittaisiin"
            // heti eikä oikoluku huomaisi sitä koskaan.
            val unknown = words.indices.filterTo(HashSet()) { i ->
                val word = text.substring(words[i])
                word.any { it.isLetter() } &&
                    dictionary.frequencyOf(word) == 0L &&
                    !learning.isEstablishedWord(word)
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
        executeSuggest {
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
        textUndo.record(word, correctionText.substring(range))
        if (learningEnabled) {
            learning.onCorrectionAccepted(word)
            maybeFlush()
        }
        feedback()
        suggestGeneration++
        suggestionBar?.setSuggestions(emptyList())
        refreshCorrectionPanel()
    }

    /** Lähettää kentän tekstin parannettavaksi ja näyttää ehdotuksen. */
    private fun requestImprovement() {
        feedback()
        val text = correctionText
        if (text.isBlank()) return
        if (text.length > TextImprover.MAX_INPUT_CHARS) {
            // Merkkiraja estää vahingossa valitun jättitekstin lähettämisen.
            Toast.makeText(
                this, R.string.korjaus_paranna_liian_pitka, Toast.LENGTH_SHORT
            ).show()
            return
        }
        val openAi = openAiSelected()
        val apiKey = ApiKeyStore.read(
            prefs,
            if (openAi) ApiKeyStore.Slot.OPENAI else ApiKeyStore.Slot.CLAUDE,
        ).orEmpty()
        if (apiKey.isEmpty()) return
        correctionPanel?.showImprovementLoading(
            if (openAi) R.string.korjaus_parannetaan_openai else R.string.korjaus_parannetaan
        )
        val generation = ++improveGeneration
        improveExecutor.execute {
            val (result, error) = runImprovement(openAi, apiKey, text)
            mainHandler.post {
                if (generation != improveGeneration || destroyed) return@post
                val panel = correctionPanel ?: return@post
                if (panel.visibility != View.VISIBLE) return@post
                if (!result.isNullOrEmpty() && text == correctionText) {
                    if (result.all { it.trim() == text.trim() }) {
                        // Malli ei löytänyt korjattavaa: sama teksti
                        // kortteina näyttäisi siltä kuin mitään ei
                        // tapahtunut, joten kerrotaan asia suoraan.
                        panel.hideImprovement()
                        Toast.makeText(
                            this, R.string.korjaus_ei_korjattavaa, Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        panel.showImprovement(result)
                    }
                } else {
                    panel.hideImprovement()
                    // Syy näkyviin (väärä avain, kiintiö, tyhjä vastaus…),
                    // jotta vika selviää ilman arvailua.
                    val message = error
                        ?.let { getString(R.string.korjaus_paranna_virhe_syy, it) }
                        ?: getString(R.string.korjaus_paranna_virhe)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Onko asetuksissa valittu AI-palveluksi ChatGPT. */
    private fun openAiSelected(): Boolean =
        prefs.getString(PREF_AI_SERVICE, "claude") == "chatgpt"

    /** Parannusversiot tai virheen selite käyttäjälle näytettäväksi. */
    private fun runImprovement(
        openAi: Boolean,
        apiKey: String,
        text: String,
    ): Pair<List<String>?, String?> {
        val body = if (openAi) {
            val model = prefs.getString(PREF_OPENAI_MODEL, null)
                ?.takeIf { it.isNotBlank() } ?: TextImprover.OPENAI_MODEL
            TextImprover.buildOpenAiRequest(text, model)
        } else {
            val model = prefs.getString(PREF_IMPROVE_MODEL, null)
                ?.takeIf { it.isNotBlank() } ?: TextImprover.MODEL
            TextImprover.buildRequest(text, model)
        }
        val (response, error) = postAi(openAi, apiKey, body)
        if (response == null) return null to error
        val versions = if (openAi) {
            TextImprover.parseOpenAiVersions(response)
        } else {
            TextImprover.parseVersions(response)
        }
        return if (versions.isEmpty()) {
            null to getString(R.string.korjaus_tyhja_vastaus)
        } else {
            versions to null
        }
    }

    /**
     * Lähettää pyynnön valittuun AI-palveluun ja palauttaa vastausrungon
     * tai virheen selitteen käyttäjälle näytettäväksi.
     */
    private fun postAi(
        openAi: Boolean,
        apiKey: String,
        body: String,
    ): Pair<String?, String?> = try {
        val endpoint = if (openAi) TextImprover.OPENAI_ENDPOINT else TextImprover.ENDPOINT
        val connection = java.net.URL(endpoint)
            .openConnection() as javax.net.ssl.HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json")
            if (openAi) {
                connection.setRequestProperty("authorization", "Bearer $apiKey")
            } else {
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() } to null
            } else {
                val errorBody = connection.errorStream
                    ?.bufferedReader()?.use { it.readText() }
                null to (TextImprover.parseErrorMessage(errorBody)
                    ?: "HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null to e.javaClass.simpleName
    }

    /** Korvaa kentän koko tekstin hyväksytyllä parannuksella. */
    private fun applyImprovement(text: String) {
        val panel = correctionPanel ?: return
        val ic = currentInputConnection ?: return
        val old = correctionText
        ic.beginBatchEdit()
        ic.setSelection(correctionStartOffset, correctionStartOffset + old.length)
        ic.commitText(text, 1)
        ic.endBatchEdit()
        textUndo.record(text, old)
        panel.hideImprovement()
        feedback()
        suggestGeneration++
        suggestionBar?.setSuggestions(emptyList())
        refreshCorrectionPanel()
    }

    private fun pasteClip(clip: Clip) {
        val ic = currentInputConnection ?: return
        markOwnEdit()
        if (clip.text != null) {
            if (translateMode) {
                // Liitetty teksti menee käännösnäkymään sille alueelle,
                // jota käyttäjä parhaillaan kirjoittaa.
                activeTranslateBuffer().insert(clip.text)
                onTranslateBufferChanged()
            } else {
                ic.commitText(clip.text, 1)
                textUndo.record(clip.text)
            }
            hideClipboardPanel()
            feedback()
            return
        }
        val path = clip.imagePath ?: return
        val info = currentInputEditorInfo ?: return
        val mime = when {
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".png") -> "image/png"
            else -> MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(path.substringAfterLast('.', ""))
        }
        if (mime == null) {
            Toast.makeText(this, R.string.leike_kuva_ei_tuettu, Toast.LENGTH_SHORT).show()
            return
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
            val committed = InputConnectionCompat.commitContent(
                ic, info, content,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null,
            )
            // Kenttä voi hylätä sisällön, vaikka se ilmoitti tukevansa tyyppiä.
            if (committed) {
                hideClipboardPanel()
                feedback()
            } else {
                Toast.makeText(this, R.string.leike_kuva_ei_tuettu, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.leike_kuva_ei_tuettu, Toast.LENGTH_SHORT).show()
        }
    }

    /** Kirjoittaa kertyneet oppimismuutokset tietokantaan taustasäikeessä. */
    private fun flushLearned() {
        val db = database ?: return
        if (learning.dirtyCount == 0) return
        val dirty = learning.drainDirty()
        executeIo {
            try {
                // Erä kirjoitetaan transaktiossa: joko kaikki tai ei mitään.
                db.runInTransaction {
                    dirty.removedWords.forEach { db.dao().deleteWord(it) }
                    dirty.removedChainWords.forEach {
                        db.dao().deleteBigramsFor(it)
                        db.dao().deleteTrigramsFor(it)
                    }
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
                            dirty.bigrams.map {
                                BigramEntity(it.previous, it.next, it.count, it.lastUsed)
                            }
                        )
                    }
                    if (dirty.trigrams.isNotEmpty()) {
                        db.dao().upsertTrigrams(
                            dirty.trigrams.map {
                                TrigramEntity(it.first, it.second, it.next, it.count, it.lastUsed)
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                // Kirjoitusvirhe ei saa kaataa näppäimistöä; erä palaa
                // jonoon ja kirjoitetaan seuraavan tallennuksen mukana.
                learning.requeueDirty(dirty)
            }
        }
    }

    private fun maybeFlush() {
        if (learning.dirtyCount >= FLUSH_THRESHOLD) flushLearned()
    }

    // Palvelu voi tuhoutua taustalatauksen aikana; suljettuun suorittimeen
    // ei saa jonottaa uutta työtä.
    private fun executeSuggest(task: Runnable) {
        if (destroyed || suggestExecutor.isShutdown) return
        try {
            suggestExecutor.execute(task)
        } catch (e: RejectedExecutionException) {
            // Palvelu on sulkeutumassa.
        }
    }

    private fun executeIo(task: Runnable) {
        if (destroyed || ioExecutor.isShutdown) return
        try {
            ioExecutor.execute(task)
        } catch (e: RejectedExecutionException) {
            // Palvelu on sulkeutumassa.
        }
    }

    private fun handleBackspaceKey() {
        if (translateMode && activeTranslateBuffer().isNotEmpty()) {
            // Poisto kohdistuu käännösnäkymään, ei kenttään; alueen alussa
            // poisto ei valu kenttään vaan jää tekemättä.
            if (activeTranslateBuffer().backspace()) {
                onTranslateBufferChanged()
            }
        } else if (!revertSmartSpace() && !revertAutoCorrect()) {
            // Näppäintapahtumana, jotta valitun tekstin poisto toimii
            // sovelluksissa oikein.
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        // Poiston jälkeen kenttä ei enää vastaa kirjattua toimenpidettä.
        textUndo.clear()
        feedback(AudioManager.FX_KEYPRESS_DELETE)
    }

    /**
     * Kaksoisvälilyönti pisteeksi: nopeasti kahdesti painettu väli muuttaa
     * "sana " -> "sana. ". Laukeaa vain lauseen loppuun sopivan merkin
     * jäljessä, joten "sana,  " tai "sana.  " eivät saa ylimääräistä
     * pistettä.
     */
    private fun performDoubleSpacePeriod(): Boolean {
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(3, 0)?.toString().orEmpty()
        if (before.length < 2 || !before.endsWith(" ")) return false
        if (!SmartSpace.canEndSentence(before[before.length - 2])) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(1, 0)
        ic.commitText(". ", 1)
        ic.endBatchEdit()
        // Heti perään painettu kolmas väli ohitetaan kuten automaattivälin.
        punctSpaceAdded = true
        return true
    }

    /** Pohjassa pidetyn askelpalauttimen toistokerta: poistaa sanan kerrallaan. */
    override fun onKeyRepeat(action: KeyAction) {
        if (action != KeyAction.Backspace) {
            onKey(action)
            return
        }
        autoSpaceState = 0
        smartSpaceState = 0
        punctSpaceAdded = false
        pendingRevert = null
        pendingSpaceRevert = null
        if (translateMode && activeTranslateBuffer().isNotEmpty()) {
            if (activeTranslateBuffer().backspaceWord()) {
                onTranslateBufferChanged()
            }
        } else {
            val ic = currentInputConnection ?: return
            markOwnEdit()
            val before = ic.getTextBeforeCursor(WORD_BACKSPACE_LOOKBACK, 0)
            if (before.isNullOrEmpty()) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            } else {
                ic.deleteSurroundingText(WordTools.wordBackspaceLength(before), 0)
            }
        }
        textUndo.clear()
        feedback(AudioManager.FX_KEYPRESS_DELETE)
    }

    /**
     * Työkalurivin peruutus: viimeisin näppäimistön toimenpide perutaan
     * omalla kirjauksella, jos kentän teksti on yhä ennallaan; muuten
     * pyydetään kentän omaa peruutusta (Ctrl+Z), jota useimmat
     * muokkauskentät tukevat.
     */
    private fun performUndo() {
        // Käännösrivillä poisto hoituu askelpalauttimella; peruutus koskee kenttää.
        if (translateMode) return
        val ic = currentInputConnection ?: return
        markOwnEdit()
        val undo = textUndo.consume { length -> ic.getTextBeforeCursor(length, 0) }
        if (undo != null) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(undo.deleteLength, 0)
            if (undo.restore.isNotEmpty()) ic.commitText(undo.restore, 1)
            ic.endBatchEdit()
            // Peruttu teksti ei saa jäädä sanaketjuun.
            learning.resetContext()
        } else {
            sendCtrlZ()
        }
        feedback()
        if (correctionPanel?.visibility == View.VISIBLE) {
            refreshCorrectionPanel()
        } else {
            updateSuggestions()
        }
    }

    private fun sendCtrlZ() {
        val ic = currentInputConnection ?: return
        val time = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, meta))
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, meta))
    }

    /**
     * Askelpalautin heti älykkään jälkivälin jälkeen palauttaa kirjaimen
     * ilman väliä ja isontamista, jolloin esim. jarsi.org jatkuu ehjänä.
     */
    private fun revertSmartSpace(): Boolean {
        val (typed, committed) = pendingSpaceRevert ?: return false
        pendingSpaceRevert = null
        val ic = currentInputConnection ?: return false
        if (ic.getTextBeforeCursor(committed.length, 0)?.toString() != committed) return false
        markOwnEdit()
        ic.beginBatchEdit()
        ic.deleteSurroundingText(committed.length, 0)
        ic.commitText(typed, 1)
        ic.endBatchEdit()
        return true
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
        if (!autoCorrectEnabled || noSuggestionsField) {
            learning.onSuggestionsIgnored(shown, typed)
            learning.onWordCommitted(typed)
            maybeFlush()
            return
        }
        // Ratkaisu tehdään taustalla, ettei sanastohaku nyi näppäilyä.
        val session = editorSessionId
        executeSuggest {
            val corrected = suggestionEngine.autoCorrect(typed, context)
            mainHandler.post { applyAutoCorrect(typed, corrected, shown, session) }
        }
    }

    private fun applyAutoCorrect(
        typed: String,
        corrected: String?,
        shown: List<String>,
        session: Int,
    ) {
        val display = corrected?.let {
            if (typed.first().isUpperCase()) {
                it.replaceFirstChar { c -> c.titlecase(fiLocale) }
            } else {
                it
            }
        }
        var applied = false
        val ic = currentInputConnection
        // Kenttä ei saa olla vaihtunut: sama häntä toisessa kentässä ei riitä.
        if (display != null && display != typed && ic != null && session == editorSessionId) {
            val tail = "$typed "
            // Korjataan vain jos teksti on yhä ennallaan — nopea kirjoittaja
            // on voinut jo jatkaa, eikä tekstiä saa muuttaa selän takana.
            if (ic.getTextBeforeCursor(tail.length, 0)?.toString() == tail) {
                markOwnEdit()
                ic.beginBatchEdit()
                ic.deleteSurroundingText(tail.length, 0)
                ic.commitText("$display ", 1)
                ic.endBatchEdit()
                pendingRevert = typed to display
                textUndo.record("$display ", "$typed ")
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
        markOwnEdit()
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
                    // Paneelit peittävät näppäimistön: sivunvaihto ei
                    // näkyisi, joten ne suljetaan ja siirrytään suoraan.
                    hideAllPanels()
                    page = if (page == Page.ARROWS) Page.LETTERS else Page.ARROWS
                    updateLayout()
                    updateSuggestions()
                    feedback()
                }

                override fun onToggleWeb() {
                    hideAllPanels()
                    page = if (page == Page.SYMBOLS3) Page.LETTERS else Page.SYMBOLS3
                    updateLayout()
                    updateSuggestions()
                    feedback()
                }

                override fun onToggleDictation() {
                    feedback()
                    // Sanelu ei saa kirjoittaa kenttään kesken korjauksen,
                    // ja sivutila palaa kirjaimiin ettei kahta korostusta
                    // jää päälle yhtä aikaa.
                    hideAllPanels()
                    resetSidePage()
                    // Käännöstilassa sanelu kirjoittaa käännösnäkymään sille
                    // alueelle, jota parhaillaan kirjoitetaan — kenttään ei
                    // mene mitään ennen vientiä.
                    when {
                        passwordField -> Unit
                        dictationActive() -> {
                            dictation.stop()
                            openAiDictation?.stop()
                        }
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED -> startDictation()
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

                override fun onUndo() {
                    // Peruutus muuttaa kenttää; auki jäänyt korjausnäkymä
                    // näyttäisi vanhentunutta tekstiä.
                    hideCorrectionPanel()
                    performUndo()
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
                override fun onPickSource(code: String) {
                    feedback()
                    // Käsivalinta ohittaa kielen päättelyn tältä tekstiltä.
                    translateSourceLocked = true
                    pickTranslationLanguage(sourceSide = true, code = code)
                }

                override fun onSwap() {
                    feedback()
                    // Käsin käännettyä suuntaa ei saa päätellä heti takaisin.
                    translateSourceLocked = true
                    // Tuore käännös siirtyy suunnanvaihdossa lähdetekstiksi
                    // kuten Google Kääntäjässä — muuten rivillä olisi
                    // vaihdon jälkeen kahdesti samankielinen teksti.
                    val moved = currentTranslation
                        .takeIf { translationFresh && it.isNotBlank() }
                    val source = translationSource()
                    prefs.edit()
                        .putString(PREF_TRANSLATE_SOURCE, translationTarget())
                        .putString(PREF_TRANSLATE_TARGET, source)
                        .apply()
                    if (moved != null) {
                        translateBuffer.clear()
                        translateBuffer.insert(moved)
                        currentTranslation = ""
                        translationFresh = false
                    }
                    stopTranslationEditing()
                    onTranslatePairChanged()
                }

                override fun onPickTarget(code: String) {
                    feedback()
                    pickTranslationLanguage(sourceSide = false, code = code)
                }

                override fun onClear() {
                    feedback()
                    translateBuffer.clear()
                    currentTranslation = ""
                    translationFresh = false
                    stopTranslationEditing()
                    // Uusi teksti saa taas päätellä kielensä.
                    translateSourceLocked = false
                    // Vietyä käännöstä EI unohdeta: tyhjennyksen jälkeen
                    // kirjoitettu uusi teksti korvaa Lisää-napilla vanhan
                    // kentästä, joten viestin voi kirjoittaa kokonaan
                    // uusiksi ennen lähettämistä.
                    updateTranslateBar()
                    updateSuggestions()
                }

                override fun onAiTranslate() = requestAiTranslation()

                override fun onCopyTranslation() {
                    val translation = currentTranslation
                    if (translation.isBlank()) return
                    feedback()
                    clipboardManager?.setPrimaryClip(
                        ClipData.newPlainText("", translation)
                    )
                    // Järjestelmä ei näytä omaa kopiointikuplaansa
                    // näppäimistön kopioinneista, joten kuittaus on omamme.
                    Toast.makeText(
                        this@KeyboardService,
                        R.string.kaannos_kopioitu,
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                override fun onInsert() {
                    insertTranslation()
                }

                override fun onDownloadModels() {
                    feedback()
                    translationModelsMissing = false
                    updateTranslateBar()
                    // Käyttäjä antoi luvan: lataus ja valmistelu käyntiin.
                    prepareTranslator()
                }

                override fun onCursorTap(position: Int) {
                    // Paluu lähdetekstiin päättää käännöksen muokkauksen;
                    // korjattu käännös jää näkyviin ja vietäväksi, kunnes
                    // lähdetekstin muutos tuo tilalle uuden käännöksen.
                    stopTranslationEditing()
                    translateBuffer.setCursor(position)
                    updateTranslateBar()
                    updateSuggestions()
                    feedback()
                }

                override fun onTranslationTap(position: Int) {
                    // Käännöstä korjataan paikallaan: teksti siirtyy omaan
                    // puskuriinsa ja näppäily kohdistuu siihen.
                    if (currentTranslation.isEmpty()) return
                    if (!translationEditing) {
                        translationBuffer.clear()
                        translationBuffer.insert(currentTranslation)
                        translationEditing = true
                        dictationPending = 0
                        // Kesken oleva käännös ei saa pyyhkiä korjausta.
                        translateRunnable?.let { mainHandler.removeCallbacks(it) }
                        translationGeneration++
                        aiTranslateGeneration++
                    }
                    translationBuffer.setCursor(position)
                    // Korjattu käännös on se, joka viedään ja kopioidaan.
                    translationFresh = true
                    updateTranslateBar()
                    updateSuggestions()
                    feedback()
                }

                override fun onCopy(text: String) {
                    feedback()
                    clipboardManager?.setPrimaryClip(ClipData.newPlainText("", text))
                    // Järjestelmä ei näytä omaa kopiointikuplaansa
                    // näppäimistön kopioinneista, joten kuittaus on omamme.
                    Toast.makeText(
                        this@KeyboardService,
                        R.string.kaannos_kopioitu,
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                override fun onPaste() {
                    feedback()
                    val clipText = clipboardManager?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(this@KeyboardService)
                        ?.toString()
                    if (clipText.isNullOrEmpty()) return
                    markOwnEdit()
                    activeTranslateBuffer().insert(clipText)
                    onTranslateBufferChanged()
                }
            }
            it.visibility = View.GONE
            // Käännösalueet kaiken ylle: työkalurivi, ehdotukset ja
            // näppäimet pysyvät yhtenäisenä pakettina kuten aina. Paino
            // tekee näkymästä joustavan: se saa tilan, joka muilta jää,
            // eikä esimerkiksi ?123-sivun ylimääräinen näppäinrivi työnnä
            // alinta riviä navigointipalkin alle.
            container.addView(
                it,
                0,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
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
                    executeIo {
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

                override fun onImprove() = requestImprovement()

                override fun onAcceptImprovement(text: String) = applyImprovement(text)
            }
            it.visibility = View.GONE
            container.addView(it, LinearLayout.LayoutParams(params))
        }
        emojiPanel = EmojiPanelView(this).also {
            it.listener = object : EmojiPanelView.Listener {
                override fun onEmojiPicked(emoji: String) {
                    markOwnEdit()
                    if (translateMode) {
                        // Emojit kulkevat käännösnäkymän kautta muun tekstin mukana.
                        activeTranslateBuffer().insert(emoji)
                        onTranslateBufferChanged()
                    } else {
                        currentInputConnection?.commitText(emoji, 1)
                        textUndo.record(emoji)
                    }
                    feedback()
                }

                override fun onBackspace() = handleBackspaceKey()

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

    /**
     * Käännösnäkymä piirtyy sovelluksen päälle Gboardin laajenevien
     * paneelien tapaan: sovellukselle kerrotaan vain näppäimistöosan
     * viemä tila. Ilman tätä koko ruudun korkuinen näkymä kutistaisi
     * sovelluksen olemattomiin, jolloin kenttä menettää fokuksen ja
     * käynnistää syötteen uudelleen — vienti kenttään katkesi ja näkymä
     * saattoi sulkeutua heti avauksen perään.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!translateMode) return
        val bar = translateBar ?: return
        if (bar.visibility != View.VISIBLE || bar.height == 0) return
        outInsets.contentTopInsets += bar.height
        outInsets.visibleTopInsets += bar.height
        // Koko näkymä ottaa silti kosketukset vastaan.
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
    }

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
        editorSessionId++
        shownCompletions = emptyList()
        shiftState = ShiftState.OFF
        manualShift = false
        // Salasanakentissä ja oppimisen kieltävissä kentissä (esim. incognito)
        // ei opita mitään; kentän vaihto katkaisee sanaketjun.
        learningEnabled = !passwordField &&
            info.imeOptions and EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        // NO_SUGGESTIONS-kentässä (esim. käyttäjätunnus tai koodi) tekstiä
        // ei automaattikorjata; ehdotusrivi näkyy silti.
        noSuggestionsField = inputClass == InputType.TYPE_CLASS_TEXT &&
            info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        learning.resetContext()
        reloadLearnedIfChanged()
        if (!restarting) {
            // Kentän vaihto (esim. sovelluksen lähetysnappi) katkaisee
            // sanelun ja sulkee näkymät. Saman kentän syötteen uudelleen-
            // käynnistys (restartInput, esim. sovelluksen reaktio näkymän
            // koon muutokseen) ei saa sulkea mitään — käännösnäkymä
            // välähtäisi muuten auki ja heti kiinni.
            stopDictation()
            hideAllPanels()
            // Kenttä vaihtui: näkymä sulkeutuu, mutta käännöstyö säilyy
            // puskurissa, koska mikään ei kirjoitu kenttiin itsestään.
            hideTranslateBar()
            // Uudessa kentässä ei ole vietyä käännöstä korvattavaksi.
            lastInsertedTranslation = ""
        }
        if (pendingDictation) {
            pendingDictation = false
            if (!passwordField &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startDictation()
            }
        }
        if (pendingClipboardPanel) {
            pendingClipboardPanel = false
            showClipboardPanel()
        }
        spaceAfterSuggestion = prefs.getBoolean("ehdotus_valilyonti", true)
        commonWordsEnabled = prefs.getBoolean("ehdotus_yleiset", true)
        autoCorrectEnabled = prefs.getBoolean("automaattikorjaus", true)
        numberRowEnabled = prefs.getBoolean(PREF_NUMBER_ROW, true)
        doubleSpacePeriodEnabled = prefs.getBoolean(PREF_DOUBLE_SPACE, true)
        lastSpaceTime = 0
        symbolOrder = SymbolOrder.load(prefs.getString(SymbolOrder.PREF_KEY, null))
        toolbar?.tools = ToolbarOrder.load(prefs.getString(ToolbarOrder.PREF_KEY, null)).visible
        pendingRevert = null
        // Osoitteissa, sähköposteissa ja koodikentissä pisteet kuuluvat
        // tekstiin, eikä väliä saa lisätä niiden perään automaattisesti.
        // NO_SUGGESTIONS ei estä väliä: esim. Google Keep merkitsee sillä
        // tavalliset kirjoituskentät, joissa jälkiväli nimenomaan halutaan.
        smartSpaceField = inputClass == InputType.TYPE_CLASS_TEXT &&
            !passwordField &&
            variation != InputType.TYPE_TEXT_VARIATION_URI &&
            variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        capSentencesField = inputClass == InputType.TYPE_CLASS_TEXT &&
            info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
        smartSpaceState = 0
        pendingSpaceRevert = null
        textUndo.clear()
        // Salasana- ja numerokentissä ehdotusrivi on piilossa. NO_SUGGESTIONS
        // EI piilota riviä: moni sovellus (esim. Google Keep) merkitsee sillä
        // tavallisia kirjoituskenttiä, ja oma oppiminen on tämän näppäimistön
        // ydin — lippu poistaa vain automaattikorjauksen.
        suggestionsVisible = prefs.getBoolean("ehdotukset", true) &&
            !passwordField &&
            page != Page.NUMERIC
        suggestionBar?.visibility = if (suggestionsVisible) View.VISIBLE else View.GONE
        updateLayout()
        updateAutoCaps()
        updateSuggestions()
    }

    private fun onSuggestionPicked(word: String) {
        markOwnEdit()
        if (correctionPanel?.visibility == View.VISIBLE) {
            replaceCorrectionWord(word)
            return
        }
        if (translateMode) {
            // Valinta korvaa käännösnäkymän keskeneräisen sanan; oppiminen
            // toimii kuten kentässä.
            val buffer = activeTranslateBuffer()
            val beforeCursor = buffer.text.substring(0, buffer.cursor)
            val current = WordTools.currentWord(beforeCursor)
            buffer.deleteBeforeCursor(current.length)
            buffer.insert(if (spaceAfterSuggestion) "$word " else word)
            if (learningEnabled) {
                learning.onSuggestionsIgnored(shownCompletions, word)
                shownCompletions = emptyList()
                if (word == current) {
                    learning.onTypedWordAccepted(word)
                } else {
                    learning.onSuggestionAccepted(word)
                }
                maybeFlush()
            }
            feedback()
            onTranslateBufferChanged()
            return
        }
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(MAX_WORD_LOOKBACK, 0) ?: ""
        val current = WordTools.currentWord(before)
        // Välimerkin perään valittu ennustus saa välin eteensä samoin kuin
        // kirjoitettu kirjain, ja lauseen alku isonnetaan.
        val beforeChar = before.lastOrNull()
        val smartPrefix = smartSpaceField && smartSpaceState > 0 && current.isEmpty() &&
            beforeChar != null && SmartSpace.isPunctuation(beforeChar)
        val display = if (smartPrefix && beforeChar != null && capSentencesField &&
            SmartSpace.isSentenceEnder(beforeChar)
        ) {
            word.replaceFirstChar { it.titlecase(fiLocale) }
        } else {
            word
        }
        val committed = (if (smartPrefix) " " else "") +
            if (spaceAfterSuggestion) "$display " else display
        ic.beginBatchEdit()
        if (current.isNotEmpty()) ic.deleteSurroundingText(current.length, 0)
        ic.commitText(committed, 1)
        ic.endBatchEdit()
        textUndo.record(committed, current)
        autoSpaceState = if (spaceAfterSuggestion) 2 else 0
        smartSpaceState = 0
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
        // Nuolitilassa kursoria liikutellaan tekstin yli; ehdotukset olisivat vain häiriöksi.
        if (!suggestionsVisible || page == Page.ARROWS) {
            bar.setSuggestions(emptyList())
            return
        }
        // Käännöstilassa ehdotukset lasketaan siitä alueesta, jota kirjoitetaan.
        val before: CharSequence = if (translateMode) {
            val buffer = activeTranslateBuffer()
            buffer.text.substring(0, buffer.cursor)
        } else {
            currentInputConnection?.getTextBeforeCursor(MAX_WORD_LOOKBACK, 0) ?: ""
        }
        // Lauseen alussa (automaattinen iso kirjain päällä) ehdotukset alkavat isolla.
        val shiftActive = shiftState != ShiftState.OFF
        val generation = ++suggestGeneration
        executeSuggest {
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
            Page.LETTERS -> Layouts.letters(extraKey, numberRowEnabled)
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
        markOwnEdit()
        pendingRevert = null
        textUndo.clear()
        if (translateMode) {
            // Käännöstilassa näppäily kertyy käännösnäkymään; kenttään
            // kirjoittuu vain valmis käännös.
            val output = if (shiftState != ShiftState.OFF && text.length == 1) {
                text.uppercase(fiLocale)
            } else {
                text
            }
            activeTranslateBuffer().smartType(output)
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
        // Rivin loppu kelpaa kuten kentän loppu: monirivisissä kentissä
        // (esim. Keepin muistiinpanot) kursorin jäljessä on rivinvaihto,
        // eikä se saa estää välimerkkisääntöjä.
        val afterCursor = ic.getTextAfterCursor(1, 0)?.toString()
        val atLineEnd = afterCursor.isNullOrEmpty() || afterCursor == "\n"
        if ((autoSpaceState > 0 || smartSpaceField) && text.length == 1 &&
            SmartSpace.isPunctuation(text[0]) &&
            ic.getTextBeforeCursor(1, 0)?.toString() == " " &&
            atLineEnd
        ) {
            // Välilyönti siirtyy välimerkin taakse: "sana ." -> "sana. "
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(text + " ", 1)
            ic.endBatchEdit()
            if (autoSpaceState > 0) autoSpaceState = 2
            smartSpaceState = 0
            punctSpaceAdded = true
            feedback()
            return
        }
        autoSpaceState = 0
        punctSpaceAdded = false
        // Vain yksittäiset merkit shiftautuvat; esim. verkko-osoitepalat pysyvät ennallaan.
        val output = if (shiftState != ShiftState.OFF && text.length == 1) {
            text.uppercase(fiLocale)
        } else {
            text
        }
        // Välitön jälkiväli kuten muissakin näppäimistöissä: välimerkki
        // sanan perässä saa välin heti peräänsä. Numeron perässä sääntö
        // ei laukea (3,14) eikä keskellä tekstiä; osoitteen (jarsi.org)
        // välin saa pois yhdellä askelpalauttimella.
        if (smartSpaceField && text.length == 1 && SmartSpace.isPunctuation(text[0])) {
            val prev = ic.getTextBeforeCursor(1, 0)?.lastOrNull()
            if (prev != null && prev != ' ' && !prev.isDigit() && atLineEnd) {
                ic.commitText(text + " ", 1)
                punctSpaceAdded = true
                feedback()
                return
            }
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
            KeyAction.Backspace, KeyAction.Enter, is KeyAction.Arrow -> {
                autoSpaceState = 0
                smartSpaceState = 0
                punctSpaceAdded = false
                markOwnEdit()
            }
            KeyAction.Space -> {
                autoSpaceState = 0
                smartSpaceState = 0
                markOwnEdit()
            }
            else -> Unit
        }
        // Korjauksen voi perua vain heti perään; muut näppäimet mitätöivät.
        if (action != KeyAction.Backspace && action != KeyAction.Shift) {
            pendingRevert = null
            pendingSpaceRevert = null
            textUndo.clear()
        }
        when (action) {
            KeyAction.Shift -> handleShift()
            KeyAction.Backspace -> handleBackspaceKey()
            KeyAction.Enter -> {
                if (translateMode) {
                    // Käännösnäkymä on oma työkalunsa: enter tekee
                    // rivinvaihdon omaan tekstiin kuten Google Kääntäjässä.
                    activeTranslateBuffer().insert("\n")
                    onTranslateBufferChanged()
                    feedback()
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
                val swallowPunctSpace = punctSpaceAdded
                punctSpaceAdded = false
                val doubleTap = doubleSpacePeriodEnabled &&
                    SystemClock.uptimeMillis() - lastSpaceTime < DOUBLE_SPACE_PERIOD_MS
                lastSpaceTime = SystemClock.uptimeMillis()
                if (translateMode) {
                    val buffer = activeTranslateBuffer()
                    if (doubleTap && buffer.doubleSpacePeriod()) {
                        // Kaksoisvälilyönti pisteeksi myös käännösnäkymässä.
                        lastSpaceTime = 0
                    } else {
                        buffer.smartSpace()
                    }
                    onTranslateBufferChanged()
                } else if (doubleTap && smartSpaceField && performDoubleSpacePeriod()) {
                    lastSpaceTime = 0
                } else if (swallowPunctSpace &&
                    currentInputConnection?.getTextBeforeCursor(1, 0)?.toString() == " "
                ) {
                    // Automaattivälin perään painettu väli ohitetaan,
                    // ettei "sana, " saa tuplaväliä totutusta näppäilystä.
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
                if (translateMode) {
                    // Nuolet liikuttavat kirjoitettavan alueen kursoria:
                    // vasen/oikea grafeemin, ylös alkuun ja alas loppuun.
                    val buffer = activeTranslateBuffer()
                    when (action.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> buffer.moveLeft()
                        KeyEvent.KEYCODE_DPAD_RIGHT -> buffer.moveRight()
                        KeyEvent.KEYCODE_DPAD_UP -> buffer.moveToStart()
                        KeyEvent.KEYCODE_DPAD_DOWN -> buffer.moveToEnd()
                        else -> Unit
                    }
                    updateTranslateBar()
                } else {
                    sendDownUpKeyEvents(action.keyCode)
                    // Kursorin siirto katkaisee sanaketjun.
                    learning.resetContext()
                }
                feedback()
            }
            KeyAction.None -> Unit
            is KeyAction.Text -> Unit
        }
    }

    override fun onSpaceSwipe(steps: Int) {
        if (translateMode) {
            // Liu'utus liikuttaa käännösnäkymän kursoria kentän sijaan.
            activeTranslateBuffer().move(steps)
            updateTranslateBar()
        } else {
            sendDownUpKeyEvents(
                if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            )
            // Kursorin siirto katkaisee sanaketjun.
            learning.resetContext()
        }
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
        // Sama sääntö kuin näppäimen nimellä: toiminto suoritetaan aina kun
        // kenttä ei ole sitä kieltänyt — myös monirivisissä viestikentissä,
        // joissa lähetysnappi on nimenomaan pyydetty.
        val hasAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        if (hasAction) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
        feedback(AudioManager.FX_KEYPRESS_RETURN)
    }

    private fun updateAutoCaps() {
        if (page != Page.LETTERS || manualShift || shiftState == ShiftState.CAPS) return
        val caps = if (translateMode) {
            translateAutoCaps()
        } else {
            val info = currentInputEditorInfo ?: return
            val ic = currentInputConnection ?: return
            info.inputType != InputType.TYPE_NULL &&
                ic.getCursorCapsMode(info.inputType) != 0
        }
        val newState = if (caps) ShiftState.SHIFT else ShiftState.OFF
        if (newState != shiftState) {
            shiftState = newState
            keyboardView?.shiftState = newState
        }
    }

    /**
     * Käännösnäkymän iso alkukirjain kuten kentissä: alueen alussa ja
     * lauseen päättävän välimerkin ja välin jälkeen.
     */
    private fun translateAutoCaps(): Boolean {
        val buffer = activeTranslateBuffer()
        val before = buffer.text.substring(0, buffer.cursor)
        if (before.isBlank()) return true
        if (!before.endsWith(" ")) return false
        val trimmed = before.trimEnd(' ')
        return trimmed.isNotEmpty() && trimmed.last() in ".!?…"
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
        // Maalaus on aina käyttäjän tekemä; muuten ilman tuoretta omaa
        // muokkausta valinnan muutos on kursorihyppy (kentän napautus).
        // Molemmat katkaisevat sanaketjun kuten nuolinäppäimetkin.
        if (newSelStart != newSelEnd ||
            SystemClock.uptimeMillis() - lastEditTime > EXTERNAL_SELECTION_MS
        ) {
            learning.resetContext()
        }
        updateAutoCaps()
        if (autoSpaceState > 0) autoSpaceState--
        if (smartSpaceState > 0) smartSpaceState--
        updateSuggestions()
    }

    /**
     * Kirjaa oman tekstimuokkauksen: heti perään tulevat valintamuutokset
     * ovat omia eivätkä katkaise sanaketjua. Ei-muokkaavat näppäimet
     * (shift, työkalurivi) eivät kutsu tätä.
     */
    private fun markOwnEdit() {
        lastEditTime = SystemClock.uptimeMillis()
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
        const val WORD_BACKSPACE_LOOKBACK = 48
        const val CORRECTION_LOOKBACK = 5000
        const val COMMON_WORD_POOL = 24
        const val BIAS_WORD_MAX = 100
        const val PREF_TRANSLATE_SOURCE = "kaannos_lahde"
        const val PREF_TRANSLATE_TARGET = "kaannos_kohde"
        const val PREF_IMPROVE_MODEL = "claude_malli"
        const val PREF_OPENAI_MODEL = "openai_malli"
        const val PREF_AI_SERVICE = "ai_palvelu"
        const val PREF_NUMBER_ROW = "numerorivi"
        const val PREF_DICTATION_SILENCE = "sanelu_hiljaisuus"
        const val PREF_TRANSLATE_MEMORY = "kaannos_muisti"

        /** Kielen päättely vaatii vähintään tämän verran tekstiä. */
        private const val AUTODETECT_MIN_CHARS = 6

        /** Oletuskynnystä (0,5) tiukempi raja lähikielten sekaannuksia vastaan. */
        private const val AUTODETECT_CONFIDENCE = 0.7f
        const val PREF_DICTATION_ENGINE = "sanelu_moottori"
        const val PREF_DOUBLE_SPACE = "kaksoisvali_piste"
        const val DOUBLE_SPACE_PERIOD_MS = 1100L
        const val LIVE_TRANSLATE_DELAY_MS = 300L
        const val EXTERNAL_SELECTION_MS = 1000L
        const val MAX_IMAGE_CLIP_BYTES = 10L * 1024 * 1024
        const val FLUSH_THRESHOLD = 50
        const val SHOWN_TOP_COUNT = 3
        const val FILE_AUTHORITY = "org.jarsi.ark.nappaimisto.tiedostot"
    }
}
