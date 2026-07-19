package org.jarsi.ark.clipboard

import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.jarsi.ark.R
import org.jarsi.ark.data.ClipEntity
import org.jarsi.ark.data.LearnedDataStamp
import org.jarsi.ark.data.LearnedDatabase
import java.util.concurrent.Executors

/**
 * Läpinäkyvä aktiviteetti, jolla käyttäjä luo oman kiinnitetyn leikkeen
 * (esim. usein liitettävän osoitteen). Tallennus kirjoittaa suoraan
 * tietokantaan ja näppäimistö lataa leikkeet uudelleen muutosleimasta.
 */
class NewClipActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val input = EditText(this).apply {
            hint = getString(R.string.leike_uusi_vihje)
            maxLines = 4
        }
        val container = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.leike_uusi)
            .setView(container)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(R.string.leike_tallenna) { _, _ -> save(input.text.toString()) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun save(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) {
            finish()
            return
        }
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val db = LearnedDatabase.create(this)
                val now = System.currentTimeMillis()
                db.dao().upsertClip(ClipEntity(now, text, null, now, pinned = true))
                db.close()
                LearnedDataStamp.bump()
            } catch (e: Exception) {
                // Tallennusvirhe: leike jää luomatta.
            }
            runOnUiThread { finish() }
        }
        // Säie vapautuu heti työn valmistuttua eikä vuoda tallennusta kohden.
        executor.shutdown()
    }
}
