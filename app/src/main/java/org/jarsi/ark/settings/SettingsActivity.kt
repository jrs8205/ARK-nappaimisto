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
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.jarsi.ark.R
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
            // API-avain kirjoitetaan piilotettuna kuten salasana.
            findPreference<EditTextPreference>("claude_api_avain")
                ?.setOnBindEditTextListener { edit ->
                    edit.inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
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
            setupModelPreference()
        }

        /**
         * Paranna teksti -mallin valinta: lista haetaan Anthropicin
         * Models-rajapinnasta käyttäjän omalla avaimella, joten uudet
         * mallit näkyvät ilman sovelluspäivitystä.
         */
        private fun setupModelPreference() {
            val pref = findPreference<Preference>("claude_malli") ?: return
            fun updateSummary() {
                val current = preferenceManager.sharedPreferences
                    ?.getString("claude_malli", null)?.takeIf { it.isNotBlank() }
                    ?: TextImprover.MODEL
                pref.summary = getString(R.string.asetus_malli_nykyinen, current)
            }
            updateSummary()
            pref.setOnPreferenceClickListener {
                val apiKey = preferenceManager.sharedPreferences
                    ?.getString("claude_api_avain", null)?.trim().orEmpty()
                if (apiKey.isEmpty()) {
                    Toast.makeText(
                        requireContext(), R.string.malli_aseta_avain, Toast.LENGTH_SHORT
                    ).show()
                    return@setOnPreferenceClickListener true
                }
                fetchModels(apiKey) { models ->
                    if (models.isNullOrEmpty()) {
                        Toast.makeText(
                            requireContext(), R.string.malli_haku_virhe, Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val current = preferenceManager.sharedPreferences
                            ?.getString("claude_malli", null) ?: TextImprover.MODEL
                        val checked = models.indexOfFirst { it.first == current }
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.asetus_malli)
                            .setSingleChoiceItems(
                                models.map { it.second }.toTypedArray(), checked
                            ) { dialog, index ->
                                preferenceManager.sharedPreferences?.edit()
                                    ?.putString("claude_malli", models[index].first)
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
            apiKey: String,
            onResult: (List<Pair<String, String>>?) -> Unit,
        ) {
            ioExecutor.execute {
                val models = try {
                    val connection = java.net.URL(TextImprover.MODELS_ENDPOINT)
                        .openConnection() as javax.net.ssl.HttpsURLConnection
                    try {
                        connection.connectTimeout = 10_000
                        connection.readTimeout = 20_000
                        connection.setRequestProperty("x-api-key", apiKey)
                        connection.setRequestProperty("anthropic-version", "2023-06-01")
                        if (connection.responseCode in 200..299) {
                            connection.inputStream.bufferedReader().use { it.readText() }
                                .let(TextImprover::parseModels)
                        } else {
                            null
                        }
                    } finally {
                        connection.disconnect()
                    }
                } catch (e: Exception) {
                    null
                }
                activity?.runOnUiThread {
                    if (isAdded) onResult(models)
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
