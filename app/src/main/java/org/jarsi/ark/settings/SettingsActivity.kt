package org.jarsi.ark.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.jarsi.ark.R
import org.jarsi.ark.data.ApiKeyStore
import org.jarsi.ark.data.Backup
import org.jarsi.ark.engine.TextImprover
import org.jarsi.ark.data.BackupCodec
import org.jarsi.ark.data.LearnedDataStamp
import org.jarsi.ark.data.LearnedDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Näppäimistön asetukset ja käyttöönotto. Teema seuraa järjestelmää. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Vanhoilla Android-versioilla ei ole järjestelmän tummaa tilaa;
        // niillä koko sovellus on aina tumma, kuten näppäimistökin.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
        super.onCreate(savedInstanceState)
        SettingsUi.install(this, content = null, showBack = false)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.asetus_sisalto, SettingsFragment())
                .commit()
            // Ensimmäinen avaus ohjataan esittelyyn, joka opastaa myös
            // näppäimistön käyttöönoton.
            val prefs = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
            if (!prefs.getBoolean(IntroActivity.PREF_SEEN, false)) {
                startActivity(Intent(this, IntroActivity::class.java))
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        // Korttirivit erottuvat raoillaan; kirjaston jakoviivat pois.
        override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setDivider(null)
            setDividerHeight(0)
        }

        private val ioExecutor = Executors.newSingleThreadExecutor()
        private var database: LearnedDatabase? = null

        private val exportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri -> if (uri != null) exportBackup(uri) }

        private val importLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> if (uri != null) importBackup(uri) }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.asetukset, rootKey)
            // API-avaimet kirjoitetaan piilotettuina kuten salasanat, eikä
            // niitä tallenneta avoimina: ApiKeyStore salaa Android Keystorella.
            setupApiKeyPreference("claude_api_avain", ApiKeyStore.Slot.CLAUDE)
            setupApiKeyPreference("openai_api_avain", ApiKeyStore.Slot.OPENAI)
            // Aiemmin avoimena tallennettu avain salataan kertaalleen.
            preferenceManager.sharedPreferences?.let { prefs ->
                ioExecutor.execute {
                    ApiKeyStore.read(prefs, ApiKeyStore.Slot.CLAUDE)
                    ApiKeyStore.read(prefs, ApiKeyStore.Slot.OPENAI)
                }
            }
            setupAiServiceVisibility()
            setupDictationEngine()
            findPreference<Preference>("avaa_ime_asetukset")?.setOnPreferenceClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                true
            }
            findPreference<Preference>("vaihda_nappaimisto")?.setOnPreferenceClickListener {
                val imm = requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
                true
            }
            findPreference<Preference>("esittely")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), IntroActivity::class.java))
                true
            }
            findPreference<Preference>("opitut_sanat")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), LearnedWordsActivity::class.java))
                true
            }
            findPreference<Preference>("erikoismerkit")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), SymbolOrderActivity::class.java))
                true
            }
            findPreference<Preference>("tyokalurivi")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), ToolbarOrderActivity::class.java))
                true
            }
            findPreference<Preference>("kaannoskielet")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), TranslationLanguagesActivity::class.java))
                true
            }
            findPreference<Preference>("varmuuskopio_vie")?.setOnPreferenceClickListener {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                exportLauncher.launch("ark-varmuuskopio-$date.json")
                true
            }
            findPreference<Preference>("varmuuskopio_tuo")?.setOnPreferenceClickListener {
                importLauncher.launch(arrayOf("application/json"))
                true
            }
            setupModelPreference(
                key = "claude_malli",
                defaultModel = TextImprover.MODEL,
                slot = ApiKeyStore.Slot.CLAUDE,
                modelsUrl = TextImprover.MODELS_ENDPOINT,
                summaryRes = R.string.asetus_malli_nykyinen,
                authorize = { connection, apiKey ->
                    connection.setRequestProperty("x-api-key", apiKey)
                    connection.setRequestProperty("anthropic-version", "2023-06-01")
                },
                parse = TextImprover::parseModels,
            )
            setupModelPreference(
                key = "openai_malli",
                defaultModel = TextImprover.OPENAI_MODEL,
                slot = ApiKeyStore.Slot.OPENAI,
                modelsUrl = TextImprover.OPENAI_MODELS_ENDPOINT,
                summaryRes = R.string.asetus_malli_nykyinen_openai,
                authorize = { connection, apiKey ->
                    connection.setRequestProperty("authorization", "Bearer $apiKey")
                },
                parse = TextImprover::parseOpenAiModels,
            )
        }

        private fun setupApiKeyPreference(key: String, slot: ApiKeyStore.Slot) {
            findPreference<EditTextPreference>(key)?.apply {
                setOnBindEditTextListener { edit ->
                    edit.inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                setOnPreferenceChangeListener { _, newValue ->
                    preferenceManager.sharedPreferences?.let {
                        ApiKeyStore.save(it, newValue as? String ?: "", slot)
                    }
                    // Palvelurivin "Käytössä / Ei käytössä" seuraa avainta heti.
                    refreshAiServiceRows(
                        findPreference<ListPreference>("ai_palvelu")?.value
                    )
                    false
                }
            }
        }

        /**
         * Sanelumoottorin valinta: OpenAI-tunnistus vaatii OpenAI-avaimen,
         * ja valinnan yhteydessä muistutetaan jos se puuttuu — sanelu
         * pudonnee muuten laitteen tunnistukseen.
         */
        private fun setupDictationEngine() {
            val pref = findPreference<ListPreference>("sanelu_moottori") ?: return
            // Poistetun paikallisen tunnistuksen vanha valinta palautetaan
            // oletukseen, ettei lista jää tyhjän arvon varaan.
            if (pref.value != null && pref.value !in
                resources.getStringArray(R.array.sanelu_moottori_arvot)
            ) {
                pref.value = "jarjestelma"
            }
            val names = resources.getStringArray(R.array.sanelu_moottori_nimet)
            val values = resources.getStringArray(R.array.sanelu_moottori_arvot)
            pref.summaryProvider = Preference.SummaryProvider<ListPreference> { p ->
                val name = names.getOrNull(values.indexOf(p.value ?: "jarjestelma"))
                    ?: names[0]
                getString(R.string.asetus_sanelu_moottori_nykyinen, name)
            }
            pref.setOnPreferenceChangeListener { _, newValue ->
                val needsKey = newValue == "openai" &&
                    preferenceManager.sharedPreferences?.let {
                        ApiKeyStore.exists(it, ApiKeyStore.Slot.OPENAI)
                    } != true
                if (needsKey) {
                    Toast.makeText(
                        requireContext(),
                        R.string.sanelu_openai_ei_avainta,
                        Toast.LENGTH_LONG,
                    ).show()
                }
                true
            }
        }

        /** Näyttää vain valitun AI-palvelun avaimen ja mallin rivit. */
        private fun setupAiServiceVisibility() {
            val service = findPreference<ListPreference>("ai_palvelu") ?: return
            refreshAiServiceRows(service.value)
            service.setOnPreferenceChangeListener { _, newValue ->
                refreshAiServiceRows(newValue as? String)
                true
            }
        }

        private fun refreshAiServiceRows(value: String?) {
            val service = findPreference<ListPreference>("ai_palvelu") ?: return
            val names = resources.getStringArray(R.array.ai_palvelu_nimet)
            val values = resources.getStringArray(R.array.ai_palvelu_arvot)
            val chatgpt = value == "chatgpt"
            val name = names.getOrNull(values.indexOf(value ?: "claude")) ?: names[0]
            // "Käytössä" vasta kun valitulla palvelulla on avain — muuten
            // rivi kertoo, mitä puuttuu.
            val hasKey = preferenceManager.sharedPreferences?.let {
                ApiKeyStore.exists(
                    it,
                    if (chatgpt) ApiKeyStore.Slot.OPENAI else ApiKeyStore.Slot.CLAUDE,
                )
            } == true
            service.summary = getString(
                if (hasKey) {
                    R.string.asetus_ai_palvelu_nykyinen
                } else {
                    R.string.asetus_ai_palvelu_ei_avainta
                },
                name,
            )
            findPreference<Preference>("claude_api_avain")?.isVisible = !chatgpt
            findPreference<Preference>("claude_malli")?.isVisible = !chatgpt
            findPreference<Preference>("openai_api_avain")?.isVisible = chatgpt
            findPreference<Preference>("openai_malli")?.isVisible = chatgpt
        }

        /**
         * Paranna teksti -mallin valinta: lista haetaan palvelun
         * Models-rajapinnasta käyttäjän omalla avaimella, joten uudet
         * mallit näkyvät ilman sovelluspäivitystä.
         */
        private fun setupModelPreference(
            key: String,
            defaultModel: String,
            slot: ApiKeyStore.Slot,
            modelsUrl: String,
            summaryRes: Int,
            authorize: (javax.net.ssl.HttpsURLConnection, String) -> Unit,
            parse: (String) -> List<Pair<String, String>>,
        ) {
            val pref = findPreference<Preference>(key) ?: return
            fun updateSummary() {
                val current = preferenceManager.sharedPreferences
                    ?.getString(key, null)?.takeIf { it.isNotBlank() }
                    ?: defaultModel
                pref.summary = getString(summaryRes, current)
            }
            updateSummary()
            pref.setOnPreferenceClickListener {
                val apiKey = preferenceManager.sharedPreferences
                    ?.let { ApiKeyStore.read(it, slot) }.orEmpty()
                if (apiKey.isEmpty()) {
                    Toast.makeText(
                        requireContext(), R.string.malli_aseta_avain, Toast.LENGTH_SHORT
                    ).show()
                    return@setOnPreferenceClickListener true
                }
                fetchModels(modelsUrl, apiKey, authorize, parse) { models, error ->
                    if (models.isNullOrEmpty()) {
                        // Syy näkyviin, jotta väärä tai rajattu avain erottuu
                        // verkko-ongelmasta ilman arvailua.
                        val message = error
                            ?.let { getString(R.string.malli_haku_virhe_syy, it) }
                            ?: getString(R.string.malli_haku_virhe)
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    } else {
                        val current = preferenceManager.sharedPreferences
                            ?.getString(key, null) ?: defaultModel
                        val checked = models.indexOfFirst { it.first == current }
                        // Nopeus- ja hintaluokka auttaa valinnassa, kun
                        // mallilista elää eikä hintoja saada rajapinnasta.
                        val labels = models.map { model ->
                            TextImprover.modelHint(model.first)
                                ?.let { "${model.second} – $it" } ?: model.second
                        }
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(pref.title)
                            .setSingleChoiceItems(
                                labels.toTypedArray(), checked
                            ) { dialog, index ->
                                preferenceManager.sharedPreferences?.edit()
                                    ?.putString(key, models[index].first)
                                    ?.apply()
                                updateSummary()
                                dialog.dismiss()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
                true
            }
        }

        private fun fetchModels(
            url: String,
            apiKey: String,
            authorize: (javax.net.ssl.HttpsURLConnection, String) -> Unit,
            parse: (String) -> List<Pair<String, String>>,
            onResult: (List<Pair<String, String>>?, String?) -> Unit,
        ) {
            ioExecutor.execute {
                var error: String? = null
                val models = try {
                    val connection = java.net.URL(url)
                        .openConnection() as javax.net.ssl.HttpsURLConnection
                    try {
                        connection.connectTimeout = 10_000
                        connection.readTimeout = 20_000
                        authorize(connection, apiKey)
                        if (connection.responseCode in 200..299) {
                            connection.inputStream.bufferedReader().use { it.readText() }
                                .let(parse)
                        } else {
                            val body = connection.errorStream
                                ?.bufferedReader()?.use { it.readText() }
                            error = TextImprover.parseErrorMessage(body)
                                ?: "HTTP ${connection.responseCode}"
                            null
                        }
                    } finally {
                        connection.disconnect()
                    }
                } catch (e: Exception) {
                    error = e.javaClass.simpleName
                    null
                }
                activity?.runOnUiThread {
                    if (isAdded) onResult(models, error)
                }
            }
        }

        override fun onDestroy() {
            ioExecutor.shutdown()
            super.onDestroy()
        }

        // Suoritetaan taustasäikeessä; tietokanta avataan vasta tarvittaessa.
        private fun openDao(context: Context) =
            (database ?: LearnedDatabase.create(context).also { database = it }).dao()

        private fun exportBackup(uri: Uri) {
            val context = requireContext().applicationContext
            ioExecutor.execute {
                try {
                    val dao = openDao(context)
                    val backup = Backup(
                        words = dao.allWords(),
                        bigrams = dao.allBigrams(),
                        trigrams = dao.allTrigrams(),
                        // Kuvaleikkeet jäävät pois: ne ovat tiedostoja, eivät dataa.
                        clips = dao.allClips().filter { it.pinned && it.text != null },
                    )
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(BackupCodec.encode(backup).toByteArray(Charsets.UTF_8))
                    } ?: error("Tiedosto ei auennut")
                    toast(
                        context.getString(
                            R.string.varmuuskopio_viety, backup.words.size, backup.clips.size
                        )
                    )
                } catch (e: Exception) {
                    toast(context.getString(R.string.varmuuskopio_virhe))
                }
            }
        }

        private fun importBackup(uri: Uri) {
            val context = requireContext().applicationContext
            ioExecutor.execute {
                try {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: error("Tiedosto ei auennut")
                    val backup = BackupCodec.decode(text)
                    val dao = openDao(context)
                    dao.upsertWords(BackupCodec.mergeWords(dao.allWords(), backup.words))
                    dao.upsertBigrams(BackupCodec.mergeBigrams(dao.allBigrams(), backup.bigrams))
                    dao.upsertTrigrams(
                        BackupCodec.mergeTrigrams(dao.allTrigrams(), backup.trigrams)
                    )
                    BackupCodec.newClips(dao.allClips(), backup.clips, System.currentTimeMillis())
                        .forEach { dao.upsertClip(it) }
                    LearnedDataStamp.bump()
                    toast(
                        context.getString(
                            R.string.varmuuskopio_tuotu, backup.words.size, backup.clips.size
                        )
                    )
                } catch (e: Exception) {
                    toast(context.getString(R.string.varmuuskopio_virhe))
                }
            }
        }

        private fun toast(message: String) {
            val activity = activity ?: return
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
