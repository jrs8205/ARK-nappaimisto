package org.jarsi.ark.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.jarsi.ark.R
import org.jarsi.ark.data.LearnedDataStamp
import org.jarsi.ark.data.LearnedDatabase
import org.jarsi.ark.data.WordEntity
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Opittujen sanojen hallinta: haku, kiinnitys, esto ja sen purku sekä
 * poisto. Muutokset kirjoitetaan suoraan oppimistietokantaan, ja
 * näppäimistöpalvelu lataa datan uudelleen muutosleiman perusteella.
 */
class LearnedWordsActivity : AppCompatActivity() {

    private sealed interface Item {
        data class Header(val title: String) : Item
        data class Row(val entity: WordEntity) : Item
    }

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var database: LearnedDatabase? = null
    private var allWords: List<WordEntity> = emptyList()
    private var query = ""

    private lateinit var adapter: WordsAdapter
    private lateinit var emptyView: TextView
    private val fiLocale = Locale.forLanguageTag("fi")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val search = EditText(this).apply {
            hint = getString(R.string.opitut_haku)
            maxLines = 1
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        // Haun pikatyhjennys; näkyy vain kun kentässä on tekstiä.
        val clearSearch = TextView(this).apply {
            text = "✕"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(16), dp(12))
            visibility = View.GONE
            setOnClickListener { search.setText("") }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim()?.lowercase(fiLocale) ?: ""
                clearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                refreshList()
            }
        })
        emptyView = TextView(this).apply {
            text = getString(R.string.opitut_tyhja)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(32), dp(16), dp(32))
            visibility = View.GONE
        }
        adapter = WordsAdapter()
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@LearnedWordsActivity)
            adapter = this@LearnedWordsActivity.adapter
        }
        val clearAllButton = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.opitut_tyhjenna)
            setOnClickListener { confirmClearAll() }
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                clearSearch,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                topRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                emptyView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                list,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            // Tuhoisa toiminto erillään hausta, omana painikkeenaan alareunassa.
            addView(
                clearAllButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

    }

    override fun onResume() {
        super.onResume()
        // Lista luetaan aina näkymään palattaessa, jotta näppäimistön
        // taustalla oppimat sanat näkyvät ilman erillistä muutosta.
        ioExecutor.execute {
            try {
                val db = database ?: LearnedDatabase.create(this)
                val words = db.dao().allWords()
                runOnUiThread {
                    database = db
                    allWords = words
                    refreshList()
                }
            } catch (e: Exception) {
                runOnUiThread { emptyView.visibility = View.VISIBLE }
            }
        }
    }

    override fun onDestroy() {
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private fun refreshList() {
        val filtered = if (query.isEmpty()) {
            allWords
        } else {
            allWords.filter { it.key.contains(query) }
        }
        // Hallinnassa näkyvät kirjoitetut, ehdotuksista valitut ja kiinnitetyt
        // sanat; estetyt omassa osiossaan, jotta eston voi purkaa.
        val learned = filtered
            .filter { !it.blocked && (it.count > 0 || it.acceptedCount > 0 || it.pinned) }
            .sortedWith(
                compareByDescending<WordEntity> { it.count + it.acceptedCount }.thenBy { it.key }
            )
        val blocked = filtered.filter { it.blocked }.sortedBy { it.key }
        val items = buildList {
            if (learned.isNotEmpty()) {
                add(Item.Header(getString(R.string.opitut_osio_opitut, learned.size)))
                learned.forEach { add(Item.Row(it)) }
            }
            if (blocked.isNotEmpty()) {
                add(Item.Header(getString(R.string.opitut_osio_estetyt, blocked.size)))
                blocked.forEach { add(Item.Row(it)) }
            }
        }
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        adapter.submit(items)
    }

    private fun showActions(entity: WordEntity) {
        val pinLabel = getString(
            if (entity.pinned) R.string.ehdotus_irrota else R.string.ehdotus_kiinnita
        )
        val blockLabel = getString(
            if (entity.blocked) R.string.opitut_salli else R.string.ehdotus_esta
        )
        val deleteLabel = getString(R.string.ehdotus_poista)
        val labels = arrayOf(pinLabel, blockLabel, deleteLabel)
        MaterialAlertDialogBuilder(this)
            .setTitle(entity.word)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> mutate(entity) { db ->
                        db.dao().upsertWords(listOf(entity.copy(pinned = !entity.pinned)))
                    }
                    1 -> mutate(entity) { db ->
                        if (entity.blocked && entity.count == 0 && entity.acceptedCount == 0) {
                            // Pelkkä estorivi: eston purku siivoaa rivin kokonaan.
                            db.dao().deleteWord(entity.key)
                        } else {
                            db.dao().upsertWords(listOf(entity.copy(blocked = !entity.blocked)))
                        }
                    }
                    2 -> mutate(entity) { db -> db.dao().deleteWord(entity.key) }
                }
            }
            .show()
    }

    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.opitut_tyhjenna)
            .setMessage(R.string.opitut_tyhjenna_varmistus)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.opitut_tyhjenna) { _, _ -> clearAll() }
            .show()
    }

    private fun clearAll() {
        val db = database ?: return
        ioExecutor.execute {
            try {
                db.dao().deleteAllWords()
                db.dao().deleteAllBigrams()
                db.dao().deleteAllTrigrams()
                LearnedDataStamp.bump()
                runOnUiThread {
                    allWords = emptyList()
                    refreshList()
                }
            } catch (e: Exception) {
                // Tyhjennysvirhe ei kaada näkymää; lista jää ennalleen.
            }
        }
    }

    private fun mutate(entity: WordEntity, change: (LearnedDatabase) -> Unit) {
        val db = database ?: return
        ioExecutor.execute {
            try {
                change(db)
                LearnedDataStamp.bump()
                val words = db.dao().allWords()
                runOnUiThread {
                    allWords = words
                    refreshList()
                }
            } catch (e: Exception) {
                // Kirjoitusvirhe ei kaada näkymää; lista jää ennalleen.
            }
        }
    }

    private inner class WordsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<Item> = emptyList()

        fun submit(newItems: List<Item>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int) =
            if (items[position] is Item.Header) TYPE_HEADER else TYPE_ROW

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val density = parent.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()
            return if (viewType == TYPE_HEADER) {
                val view = TextView(parent.context).apply {
                    setPadding(dp(16), dp(20), dp(16), dp(8))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                object : RecyclerView.ViewHolder(view) {}
            } else {
                val word = TextView(parent.context).apply {
                    textSize = 16f
                    id = View.generateViewId()
                }
                val meta = TextView(parent.context).apply {
                    textSize = 12f
                    alpha = 0.7f
                    id = View.generateViewId()
                }
                val column = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    addView(word)
                    addView(meta)
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                object : RecyclerView.ViewHolder(column) {}
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is Item.Header -> (holder.itemView as TextView).text = item.title
                is Item.Row -> {
                    val column = holder.itemView as LinearLayout
                    val word = column.getChildAt(0) as TextView
                    val meta = column.getChildAt(1) as TextView
                    word.text = item.entity.word
                    meta.text = buildString {
                        // Käyttö = itse kirjoitetut + ehdotusriviltä valitut kerrat.
                        val uses = item.entity.count + item.entity.acceptedCount
                        append(getString(R.string.opitut_kaytto, uses))
                        if (item.entity.pinned) {
                            append(" • ").append(getString(R.string.opitut_kiinnitetty))
                        }
                        if (item.entity.blocked) {
                            append(" • ").append(getString(R.string.opitut_estetty))
                        }
                    }
                    column.setOnClickListener { showActions(item.entity) }
                }
            }
        }

        override fun getItemCount() = items.size
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROW = 1
    }
}
