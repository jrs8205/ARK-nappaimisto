package org.jarsi.ark.view

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Space
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import org.jarsi.ark.R
import org.jarsi.ark.clipboard.Clip
import org.jarsi.ark.theme.KeyboardTheme
import java.io.File
import kotlin.math.roundToInt

/**
 * Leikepöytänäkymä näppäinalueen tilalle: lista leikkeistä, joilla on
 * kolmen pisteen valikko ja kiinnitysneula.
 */
class ClipboardPanelView(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onPaste(clip: Clip)
        fun onTogglePin(clip: Clip)
        fun onWebSearch(clip: Clip)
        fun onDelete(clip: Clip)
        fun onCreatePin()
    }

    var listener: Listener? = null

    private var theme = KeyboardTheme.load(context)
    private val adapter = ClipsAdapter()
    private var menuPopup: PopupWindow? = null

    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    private val emptyView = TextView(context).apply {
        text = context.getString(R.string.leikepoyta_tyhja)
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(24), dp(24), dp(24))
        visibility = GONE
    }

    private val recycler = RecyclerView(context).apply {
        // Kaksi saraketta, erikorkuiset kortit: lyhyet leikkeet ja kuvat
        // asettuvat vierekkäin, pitkät saavat kasvaa.
        layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        adapter = this@ClipboardPanelView.adapter
        clipToPadding = false
        setPadding(0, dp(4), 0, dp(64))
    }

    private val addButton = ImageView(context).apply {
        setPadding(dp(14), dp(14), dp(14), dp(14))
        setOnClickListener { listener?.onCreatePin() }
    }

    init {
        addView(
            recycler,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        addView(
            emptyView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        addView(
            addButton,
            LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(16), dp(16))
            },
        )
    }

    fun applySettings(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(theme.background)
        emptyView.setTextColor(theme.hint)
        addButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(theme.accent)
        }
        tintedIcon(addButton, R.drawable.ic_add, theme.accentText)
        adapter.notifyDataSetChanged()
    }

    fun setClips(clips: List<Clip>) {
        closeMenu()
        adapter.submit(clips)
        emptyView.visibility = if (clips.isEmpty()) VISIBLE else GONE
    }

    fun closeMenu() {
        menuPopup?.dismiss()
        menuPopup = null
    }

    private fun tintedIcon(view: ImageView, resId: Int, color: Int) {
        view.setImageDrawable(context.getDrawable(resId)?.mutate()?.apply { setTint(color) })
    }

    private fun showClipMenu(anchor: View, clip: Clip) {
        closeMenu()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(theme.specialKey)
            }
        }

        fun addItem(iconRes: Int, label: String, action: () -> Unit) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(18), dp(10))
                val icon = ImageView(context)
                tintedIcon(icon, iconRes, theme.text)
                addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
                addView(
                    TextView(context).apply {
                        text = label
                        textSize = 14f
                        setTextColor(theme.text)
                        setPadding(dp(12), 0, 0, 0)
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                setOnClickListener {
                    closeMenu()
                    action()
                }
            }
            container.addView(row)
        }

        addItem(R.drawable.ic_clipboard, context.getString(R.string.leike_liita)) {
            listener?.onPaste(clip)
        }
        addItem(
            if (clip.pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin_outline,
            context.getString(
                if (clip.pinned) R.string.ehdotus_irrota else R.string.ehdotus_kiinnita
            ),
        ) { listener?.onTogglePin(clip) }
        if (clip.text != null) {
            addItem(R.drawable.ic_web_search, context.getString(R.string.leike_hae_verkosta)) {
                listener?.onWebSearch(clip)
            }
        }
        addItem(R.drawable.ic_delete, context.getString(R.string.leike_poista)) {
            listener?.onDelete(clip)
        }

        menuPopup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(GradientDrawable())
            elevation = dp(4).toFloat()
            showAsDropDown(anchor, -dp(120), 0)
        }
    }

    private inner class ClipsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<Clip> = emptyList()

        fun submit(newItems: List<Clip>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val text = TextView(parent.context).apply {
                textSize = 14f
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                typeface = Typeface.DEFAULT
            }
            val image = ImageView(parent.context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_START
                visibility = GONE
            }
            val content = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    text,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    image,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84)),
                )
            }
            val more = ImageView(parent.context).apply {
                setPadding(dp(4), dp(2), dp(4), dp(6))
            }
            val pin = ImageView(parent.context).apply {
                setPadding(dp(4), dp(6), dp(4), dp(2))
            }
            // Kolme pistettä ylhäällä ja neula alhaalla, väli venyy kortin
            // mukana — kumpaankin on helpompi osua sormella.
            val side = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                addView(more, LinearLayout.LayoutParams(dp(28), dp(28)))
                addView(Space(parent.context), LinearLayout.LayoutParams(0, 0, 1f))
                addView(pin, LinearLayout.LayoutParams(dp(28), dp(28)))
            }
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                // Minimikorkeus korttiin asti, jotta neula mahtuu aina näkyviin
                // myös yksirivisillä leikkeillä.
                minimumHeight = dp(76)
                setPadding(dp(12), dp(8), dp(4), dp(8))
                addView(
                    content,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    side,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                layoutParams = StaggeredGridLayoutManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(dp(6), dp(4), dp(6), dp(4))
                }
            }
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val clip = items[position]
            val row = holder.itemView as LinearLayout
            val content = row.getChildAt(0) as LinearLayout
            val text = content.getChildAt(0) as TextView
            val image = content.getChildAt(1) as ImageView
            val side = row.getChildAt(1) as LinearLayout
            val more = side.getChildAt(0) as ImageView
            // Indeksissä 1 on venyvä Space; neula on sen jälkeen.
            val pin = side.getChildAt(2) as ImageView

            row.background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(theme.key)
            }
            text.setTextColor(theme.text)

            if (clip.text != null) {
                text.text = clip.text
                text.visibility = VISIBLE
                image.visibility = GONE
            } else {
                text.visibility = GONE
                image.visibility = VISIBLE
                image.setImageBitmap(clip.imagePath?.let(::decodeThumbnail))
            }

            tintedIcon(more, R.drawable.ic_more_vert, theme.hint)
            tintedIcon(
                pin,
                if (clip.pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin_outline,
                if (clip.pinned) theme.accent else theme.hint,
            )

            row.setOnClickListener { listener?.onPaste(clip) }
            pin.setOnClickListener { listener?.onTogglePin(clip) }
            more.setOnClickListener { showClipMenu(more, clip) }
        }

        override fun getItemCount() = items.size
    }

    private fun decodeThumbnail(path: String) = try {
        if (File(path).exists()) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val sample = (bounds.outHeight / dp(84)).coerceAtLeast(1)
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
