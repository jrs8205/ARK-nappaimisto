package org.jarsi.ark.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.jarsi.ark.R

/** Näppäimistön asetukset ja käyttöönotto. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Asetussivu noudattaa näppäimistön teemavalintaa järjestelmäteeman sijaan.
        applyNightMode(
            PreferenceManager.getDefaultSharedPreferences(this).getString("teema", "tumma")
        )
        super.onCreate(savedInstanceState)
        // Reunasta reunaan -tilassa sisältö menisi tila- ja navigointipalkkien
        // alle; palkkien korkeus varataan reunuksina.
        val content = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.asetukset, rootKey)
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
            // Teeman vaihto näkyy asetussivulla heti.
            findPreference<ListPreference>("teema")?.setOnPreferenceChangeListener { _, value ->
                applyNightMode(value as? String)
                true
            }
        }
    }

    private companion object {
        fun applyNightMode(theme: String?) {
            AppCompatDelegate.setDefaultNightMode(
                if (theme == "vaalea") {
                    AppCompatDelegate.MODE_NIGHT_NO
                } else {
                    AppCompatDelegate.MODE_NIGHT_YES
                }
            )
        }
    }
}
