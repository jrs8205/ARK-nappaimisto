package org.jarsi.ark.settings

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import org.jarsi.ark.R
import org.jarsi.ark.keyboard.ToolbarOrder
import org.jarsi.ark.keyboard.ToolbarTool

/**
 * Työkalurivin muokkaus: nappia raahataan pitkällä painalluksella uuteen
 * kohtaan, ja rivin kytkimestä valitaan, näkyykö nappi työkalurivillä.
 * Asetusnappi ei ole listalla, koska se näkyy aina viimeisenä.
 */
class ToolbarOrderActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val items = mutableListOf<ToolbarTool>()
    private val hidden = mutableSetOf<ToolbarTool>()
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
        SettingsUi.install(this, root)
    }

    private fun loadItems() {
        val config = ToolbarOrder.load(prefs.getString(ToolbarOrder.PREF_KEY, null))
        items.clear()
        items.addAll(config.order.filter { it != ToolbarTool.SETTINGS })
        hidden.clear()
        hidden.addAll(config.hidden)
    }

    private fun save() {
        // Asetusnappi on aina järjestyksen viimeisenä ja aina näkyvissä.
        val order = items + ToolbarTool.SETTINGS
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

    private fun toolIcon(tool: ToolbarTool): Int = when (tool) {
        ToolbarTool.ARROWS -> R.drawable.ic_cursor_move
        ToolbarTool.WEB -> R.drawable.ic_globe
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
            makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

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
        }
    }

    private inner class ToolHolder(
        row: LinearLayout,
        val label: TextView,
        val toggle: MaterialSwitch,
    ) : RecyclerView.ViewHolder(row)

    private inner class ToolAdapter : RecyclerView.Adapter<ToolHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolHolder {
            val label = TextView(parent.context).apply {
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                compoundDrawablePadding = dp(12)
            }
            // Kahva kertoo raahattavuudesta ilman tekstin lukemista.
            val dragHandle = android.widget.ImageView(parent.context).apply {
                setImageResource(R.drawable.ic_drag_handle)
                alpha = 0.4f
                importantForAccessibility =
                    android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val toggle = MaterialSwitch(parent.context)
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                addView(
                    dragHandle,
                    LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                        marginEnd = dp(16)
                    },
                )
                addView(
                    label,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    toggle,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            dragHandle.imageTintList = ColorStateList.valueOf(label.currentTextColor)
            return ToolHolder(row, label, toggle)
        }

        override fun onBindViewHolder(holder: ToolHolder, position: Int) {
            val tool = items[position]
            holder.label.text = toolName(tool)
            holder.label.setCompoundDrawablesRelativeWithIntrinsicBounds(
                toolIcon(tool), 0, 0, 0
            )
            holder.label.compoundDrawableTintList =
                ColorStateList.valueOf(holder.label.currentTextColor)
            holder.label.alpha = if (tool in hidden) 0.5f else 1f
            holder.toggle.contentDescription = toolName(tool)
            holder.toggle.setOnCheckedChangeListener(null)
            holder.toggle.isChecked = tool !in hidden
            holder.toggle.setOnCheckedChangeListener { _, checked ->
                if (checked) hidden.remove(tool) else hidden.add(tool)
                holder.label.alpha = if (checked) 1f else 0.5f
                save()
            }
        }

        override fun getItemCount() = items.size
    }
}
