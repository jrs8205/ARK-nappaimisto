package org.jarsi.ark.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import org.jarsi.ark.R
import java.util.Locale

/**
 * Käännöskielten hallinta: kaikki tuetut kielet listana, ladatut merkittynä.
 * Napautus lataa mallin (kertalataus, ~30 Mt) tai poistaa ladatun.
 * Käännösnäkymän kielivalinnat kiertävät ladattujen kielten joukossa.
 */
class TranslationLanguagesActivity : AppCompatActivity() {

    private data class Row(val code: String, val name: String)

    private val fiLocale = Locale.forLanguageTag("fi")
    private val modelManager = RemoteModelManager.getInstance()

    private lateinit var adapter: LanguageAdapter
    private val rows: List<Row> = TranslateLanguage.getAllLanguages()
        .map { code ->
            Row(
                code,
                Locale(code).getDisplayLanguage(fiLocale)
                    .replaceFirstChar { it.titlecase(fiLocale) },
            )
        }
        .sortedBy { it.name }
    private val downloaded = mutableSetOf<String>()
    private val downloading = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = LanguageAdapter()
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@TranslationLanguagesActivity)
            adapter = this@TranslationLanguagesActivity.adapter
        }
        setContentView(list)
        ViewCompat.setOnApplyWindowInsetsListener(list) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        refreshDownloaded()
    }

    private fun refreshDownloaded() {
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                downloaded.clear()
                models.mapTo(downloaded) { it.language }
                adapter.notifyDataSetChanged()
            }
    }

    private fun onRowClicked(row: Row) {
        if (row.code in downloading) return
        if (row.code in downloaded) {
            MaterialAlertDialogBuilder(this)
                .setTitle(row.name)
                .setMessage(R.string.kieli_poista_varmistus)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.kieli_poista) { _, _ -> deleteModel(row) }
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.kieli_lataa_otsikko))
                .setMessage(getString(R.string.kieli_lataa_varmistus))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.kieli_lataa) { _, _ -> downloadModel(row) }
                .show()
        }
    }

    private fun downloadModel(row: Row) {
        downloading += row.code
        adapter.notifyDataSetChanged()
        modelManager.download(
            TranslateRemoteModel.Builder(row.code).build(),
            DownloadConditions.Builder().build(),
        )
            .addOnSuccessListener {
                downloading -= row.code
                downloaded += row.code
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                downloading -= row.code
                adapter.notifyDataSetChanged()
                Toast.makeText(this, R.string.kieli_virhe, Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteModel(row: Row) {
        modelManager.deleteDownloadedModel(TranslateRemoteModel.Builder(row.code).build())
            .addOnSuccessListener {
                downloaded -= row.code
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.kieli_virhe, Toast.LENGTH_SHORT).show()
            }
    }

    private inner class LanguageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val density = parent.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()
            val name = TextView(parent.context).apply {
                textSize = 16f
                id = View.generateViewId()
            }
            val status = TextView(parent.context).apply {
                textSize = 12f
                alpha = 0.7f
                id = View.generateViewId()
            }
            val column = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
                addView(name)
                addView(status)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            return object : RecyclerView.ViewHolder(column) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            val column = holder.itemView as LinearLayout
            val name = column.getChildAt(0) as TextView
            val status = column.getChildAt(1) as TextView
            name.text = row.name
            status.text = when {
                row.code in downloading -> getString(R.string.kieli_ladataan)
                row.code in downloaded -> getString(R.string.kieli_ladattu)
                else -> ""
            }
            status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
            column.setOnClickListener { onRowClicked(row) }
        }

        override fun getItemCount() = rows.size
    }
}
