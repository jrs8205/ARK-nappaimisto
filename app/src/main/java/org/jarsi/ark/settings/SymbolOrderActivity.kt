package org.jarsi.ark.settings

import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.jarsi.ark.R
import org.jarsi.ark.keyboard.SymbolOrder

/**
 * Erikoismerkkien järjestely: merkkiä raahataan pitkällä painalluksella
 * uuteen kohtaan, myös symbolisivujen välillä. Solun taustaväri kertoo,
 * kummalle sivulle merkki asettuu; järjestys tallentuu joka siirrosta.
 */
class SymbolOrderActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val order = mutableListOf<String>()
    private val adapter = SymbolAdapter()
    private var page1Color = 0
    private var page2Color = 0
    private var density = 1f

    private fun dp(value: Int) = (value * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        density = resources.displayMetrics.density
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        order.addAll(SymbolOrder.load(prefs.getString(SymbolOrder.PREF_KEY, null)))

        val accent = TypedValue().let {
            theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, it, true)
            it.data
        }
        page1Color = 0x26808080
        page2Color = (accent and 0x00FFFFFF) or 0x40000000

        val help = TextView(this).apply {
            text = getString(R.string.erikoismerkit_ohje)
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        val legend = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
            addView(legendItem("Sivu ?123", pageOne = true))
            addView(legendItem("Sivu =\\<", pageOne = false))
        }
        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@SymbolOrderActivity, SPAN_COUNT)
            adapter = this@SymbolOrderActivity.adapter
            setPadding(dp(10), 0, dp(10), dp(8))
            clipToPadding = false
        }
        ItemTouchHelper(touchCallback).attachToRecyclerView(grid)
        val resetButton = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.erikoismerkit_palauta)
            setOnClickListener { confirmReset() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(help, wrapParams())
            addView(legend, wrapParams())
            addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(resetButton, wrapParams())
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun wrapParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun cellBackground(pageOne: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(if (pageOne) page1Color else page2Color)
    }

    private fun legendItem(label: String, pageOne: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            View(this@SymbolOrderActivity).apply { background = cellBackground(pageOne) },
            LinearLayout.LayoutParams(dp(16), dp(16)),
        )
        addView(
            TextView(this@SymbolOrderActivity).apply {
                text = label
                setPadding(dp(6), 0, dp(20), 0)
            },
        )
    }

    private fun save() {
        prefs.edit().putString(SymbolOrder.PREF_KEY, SymbolOrder.serialize(order)).apply()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.erikoismerkit_palauta)
            .setMessage(R.string.erikoismerkit_palauta_varmistus)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.erikoismerkit_palauta) { _, _ ->
                order.clear()
                order.addAll(SymbolOrder.default)
                save()
                adapter.notifyDataSetChanged()
            }
            .show()
    }

    private val touchCallback = object : ItemTouchHelper.Callback() {
        override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
            makeMovementFlags(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0,
            )

        override fun onMove(
            rv: RecyclerView,
            vh: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = vh.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            order.add(to, order.removeAt(from))
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            save()
            // Sivuraja kulkee paikan, ei merkin, mukaan: siirto värjää monta
            // solua uusiksi, joten koko ruudukko piirretään pudotuksen jälkeen.
            adapter.notifyDataSetChanged()
        }
    }

    private inner class SymbolAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): RecyclerView.ViewHolder {
            val view = TextView(parent.context).apply {
                gravity = Gravity.CENTER
                textSize = 20f
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52),
                ).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val text = holder.itemView as TextView
            text.text = order[position]
            text.background = cellBackground(position < SymbolOrder.PAGE1_COUNT)
        }

        override fun getItemCount() = order.size
    }

    private companion object {
        const val SPAN_COUNT = 6
    }
}
