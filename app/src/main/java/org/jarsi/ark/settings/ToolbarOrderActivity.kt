package org.jarsi.ark.settings

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.jarsi.ark.R
import org.jarsi.ark.keyboard.ToolbarOrder
import org.jarsi.ark.keyboard.ToolbarTool

/**
 * Työkalurivin muokkaus: nappia raahataan pitkällä painalluksella uuteen
 * kohtaan, ja Piilotettu-osioon vedetyt napit jäävät pois työkaluriviltä.
 * Asetusnappi ei ole listalla, koska se näkyy aina viimeisenä.
 */
class ToolbarOrderActivity : AppCompatActivity() {

    private object Divider

    private lateinit var prefs: SharedPreferences
    private val items = mutableListOf<Any>()
    private val adapter = ToolAdapter()
    private var density = 1f

    private fun dp(value: Int) = (value * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        density = resources.displayMetrics.density
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        loadItems()

        val help = TextView(this).apply {
            text = getString(R.string.tyokalurivi_ohje)
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ToolbarOrderActivity)
            adapter = this@ToolbarOrderActivity.adapter
        }
        ItemTouchHelper(touchCallback).attachToRecyclerView(list)
        val resetButton = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.tyokalurivi_palauta)
            setOnClickListener { confirmReset() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                help,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                resetButton,
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

    private fun loadItems() {
        val config = ToolbarOrder.load(prefs.getString(ToolbarOrder.PREF_KEY, null))
        val editable = config.order.filter { it != ToolbarTool.SETTINGS }
        items.clear()
        items.addAll(editable.filter { it !in config.hidden })
        items.add(Divider)
        items.addAll(editable.filter { it in config.hidden })
    }

    private fun dividerIndex() = items.indexOfFirst { it is Divider }

    private fun save() {
        val divider = dividerIndex()
        val visible = items.take(divider).filterIsInstance<ToolbarTool>()
        val hidden = items.drop(divider + 1).filterIsInstance<ToolbarTool>()
        // Asetusnappi on aina järjestyksen viimeisenä ja aina näkyvissä.
        val order = visible + hidden + ToolbarTool.SETTINGS
        prefs.edit()
            .putString(ToolbarOrder.PREF_KEY, ToolbarOrder.serialize(order, hidden.toSet()))
            .apply()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tyokalurivi_palauta)
            .setMessage(R.string.tyokalurivi_palauta_varmistus)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.tyokalurivi_palauta) { _, _ ->
                prefs.edit().remove(ToolbarOrder.PREF_KEY).apply()
                loadItems()
                adapter.notifyDataSetChanged()
            }
            .show()
    }

    private fun toolName(tool: ToolbarTool): String = getString(
        when (tool) {
            ToolbarTool.ARROWS -> R.string.tyokalu_nuolet
            ToolbarTool.WEB -> R.string.tyokalu_www
            ToolbarTool.MIC -> R.string.tyokalu_mikrofoni
            ToolbarTool.EMOJI -> R.string.tyokalu_emojit
            ToolbarTool.CLIPBOARD -> R.string.tyokalu_leikepoyta
            ToolbarTool.CORRECTION -> R.string.tyokalu_oikoluku
            ToolbarTool.TRANSLATE -> R.string.tyokalu_kaannos
            ToolbarTool.UNDO -> R.string.tyokalu_peruutus
            ToolbarTool.SETTINGS -> R.string.tyokalu_asetukset
        }
    )

    private fun toolIcon(tool: ToolbarTool): Int? = when (tool) {
        ToolbarTool.ARROWS -> R.drawable.ic_cursor_move
        ToolbarTool.WEB -> null
        ToolbarTool.MIC -> R.drawable.ic_mic
        ToolbarTool.EMOJI -> R.drawable.ic_emoji
        ToolbarTool.CLIPBOARD -> R.drawable.ic_clipboard
        ToolbarTool.CORRECTION -> R.drawable.ic_spellcheck
        ToolbarTool.TRANSLATE -> R.drawable.ic_translate
        ToolbarTool.UNDO -> R.drawable.ic_undo
        ToolbarTool.SETTINGS -> R.drawable.ic_settings
    }

    private val touchCallback = object : ItemTouchHelper.Callback() {
        override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
            if (items.getOrNull(vh.bindingAdapterPosition) is ToolbarTool) {
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            } else {
                makeMovementFlags(0, 0)
            }

        override fun onMove(
            rv: RecyclerView,
            vh: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = vh.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            items.add(to, items.removeAt(from))
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            save()
            // Piilotetut rivit himmennetään paikan mukaan; siirron jälkeen
            // koko lista piirretään uusiksi.
            adapter.notifyDataSetChanged()
        }
    }

    private inner class ToolAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int) =
            if (items[position] is ToolbarTool) TYPE_TOOL else TYPE_DIVIDER

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_DIVIDER) {
                val column = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(20), dp(16), dp(8))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    addView(
                        TextView(parent.context).apply {
                            text = context.getString(R.string.tyokalurivi_piilotetut)
                            textSize = 14f
                            setTypeface(typeface, Typeface.BOLD)
                        }
                    )
                    addView(
                        TextView(parent.context).apply {
                            text = context.getString(R.string.tyokalurivi_piilotetut_vihje)
                            textSize = 12f
                            alpha = 0.7f
                        }
                    )
                }
                object : RecyclerView.ViewHolder(column) {}
            } else {
                val view = TextView(parent.context).apply {
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL
                    compoundDrawablePadding = dp(12)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                object : RecyclerView.ViewHolder(view) {}
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val tool = items[position] as? ToolbarTool ?: return
            val text = holder.itemView as TextView
            text.text = toolName(tool)
            val icon = toolIcon(tool)
            if (icon != null) {
                text.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
                text.compoundDrawableTintList = ColorStateList.valueOf(text.currentTextColor)
                text.setPadding(dp(16), dp(14), dp(16), dp(14))
            } else {
                // www-napilla ei ole kuvaketta; sisennys pitää rivit tasassa.
                text.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                text.setPadding(dp(16) + dp(24) + dp(12), dp(14), dp(16), dp(14))
            }
            // Piilotetut napit himmennetään.
            text.alpha = if (position > dividerIndex()) 0.5f else 1f
        }

        override fun getItemCount() = items.size
    }

    private companion object {
        const val TYPE_TOOL = 0
        const val TYPE_DIVIDER = 1
    }
}
