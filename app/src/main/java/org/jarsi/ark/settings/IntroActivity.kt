package org.jarsi.ark.settings

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import org.jarsi.ark.R

/**
 * Ensiasennuksen esittely: pyyhkäistävät sivut sovelluksen ideasta ja
 * tärkeimmistä ominaisuuksista sekä käyttöönoton kaksi askelta
 * (näppäimistön salliminen ja valinta oletukseksi) tilamerkkeineen.
 * Näytetään kerran; asetuksista pääsee katsomaan uudelleen.
 */
class IntroActivity : AppCompatActivity() {

    companion object {
        /** Esittely on näytetty; asetussivu ei avaa sitä enää itsestään. */
        const val PREF_SEEN = "esittely_nahty"

        private const val TYPE_PAGE = 0
        private const val TYPE_SETUP = 1
    }

    private data class Page(val icon: Int, val title: Int, val body: Int)

    private val pages = listOf(
        Page(R.drawable.ic_keyboard, R.string.esittely_tervetuloa_otsikko, R.string.esittely_tervetuloa_teksti),
        Page(0, R.string.esittely_kayttoonotto_otsikko, R.string.esittely_kayttoonotto_teksti),
        Page(R.drawable.ic_school, R.string.esittely_ehdotukset_otsikko, R.string.esittely_ehdotukset_teksti),
        Page(R.drawable.ic_tune, R.string.esittely_tyokalut_otsikko, R.string.esittely_tyokalut_teksti),
        Page(R.drawable.ic_check, R.string.esittely_valmis_otsikko, R.string.esittely_valmis_teksti),
    )
    private val setupIndex = 1

    private lateinit var pager: ViewPager2
    private lateinit var dotsRow: LinearLayout
    private lateinit var skipButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private var enableButton: MaterialButton? = null
    private var defaultButton: MaterialButton? = null

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(value: Int) = (value * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nähdyksi heti avauksesta: keskenkin jätetty esittely ei palaa
        // kiusaamaan joka käynnistyksellä.
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean(PREF_SEEN, true).apply()

        pager = ViewPager2(this).apply { adapter = PageAdapter() }
        dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        skipButton = textButton(R.string.esittely_ohita).apply {
            setOnClickListener { finish() }
        }
        nextButton = MaterialButton(this@IntroActivity).apply {
            setOnClickListener {
                if (pager.currentItem == pages.lastIndex) {
                    finish()
                } else {
                    pager.currentItem += 1
                }
            }
        }

        val bottomBar = FrameLayout(this).apply {
            setPadding(dp(16), dp(8), dp(16), dp(16))
            addView(
                skipButton,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.START or Gravity.CENTER_VERTICAL,
                ),
            )
            addView(
                dotsRow,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            addView(
                nextButton,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL,
                ),
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(pager, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                bottomBar,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        setContentView(root)

        buildDots()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = refreshChrome(position)
        })
        refreshChrome(0)
    }

    /** Alapalkin tila sivun mukaan: pisteet, Ohita ja Seuraava/Valmis. */
    private fun refreshChrome(position: Int) {
        val active = MaterialColors.getColor(
            pager, com.google.android.material.R.attr.colorPrimary
        )
        val inactive = MaterialColors.getColor(
            pager, com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        for (index in 0 until dotsRow.childCount) {
            val dot = dotsRow.getChildAt(index)
            (dot.background as GradientDrawable)
                .setColor(if (index == position) active else inactive)
            dot.alpha = if (index == position) 1f else 0.4f
        }
        val last = position == pages.lastIndex
        skipButton.visibility = if (last) View.INVISIBLE else View.VISIBLE
        nextButton.setText(
            if (last) R.string.esittely_valmis_nappi else R.string.esittely_seuraava
        )
    }

    private fun buildDots() {
        pages.forEach { _ ->
            val dot = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            }
            dotsRow.addView(
                dot,
                LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                },
            )
        }
    }

    // Käyttöönoton tila päivittyy, kun järjestelmän asetuksista tai
    // näppäimistövalitsimesta palataan tähän näkymään.
    override fun onResume() {
        super.onResume()
        refreshSetupState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshSetupState()
    }

    private fun imeEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun imeSelected(): Boolean = ImeSetup.isOwnIme(
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD),
        packageName,
    )

    /** Askelnapit saavat valmis-merkin; askel 2 aukeaa vasta 1:n jälkeen. */
    private fun refreshSetupState() {
        val enabled = imeEnabled()
        val selected = imeSelected()
        enableButton?.let { button ->
            button.icon = if (enabled) getDrawable(R.drawable.ic_check) else null
        }
        defaultButton?.let { button ->
            button.icon = if (selected) getDrawable(R.drawable.ic_check) else null
            button.isEnabled = enabled
        }
    }

    private fun textButton(textRes: Int) = MaterialButton(
        this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        setText(textRes)
        strokeWidth = 0
    }

    private inner class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int) =
            if (position == setupIndex) TYPE_SETUP else TYPE_PAGE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val context = parent.context
            val icon = ImageView(context).apply {
                setColorFilter(
                    MaterialColors.getColor(
                        parent, com.google.android.material.R.attr.colorPrimary
                    )
                )
            }
            val title = TextView(context).apply {
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, dp(12))
            }
            val body = TextView(context).apply {
                textSize = 16f
                gravity = Gravity.CENTER
                setLineSpacing(dp(4).toFloat(), 1f)
            }
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(32), dp(24), dp(32), dp(24))
                addView(icon, LinearLayout.LayoutParams(dp(96), dp(96)))
                addView(title)
                addView(body)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            if (viewType == TYPE_SETUP) {
                val enable = setupButton(R.string.esittely_askel_kayttoon) {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
                val select = setupButton(R.string.esittely_askel_vaihda) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as InputMethodManager
                    imm.showInputMethodPicker()
                }
                enableButton = enable
                defaultButton = select
                column.addView(
                    enable,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(24) },
                )
                column.addView(
                    select,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(8) },
                )
                refreshSetupState()
            }
            return object : RecyclerView.ViewHolder(column) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val page = pages[position]
            val column = holder.itemView as LinearLayout
            val icon = column.getChildAt(0) as ImageView
            val title = column.getChildAt(1) as TextView
            val body = column.getChildAt(2) as TextView
            if (page.icon != 0) {
                icon.visibility = View.VISIBLE
                icon.setImageResource(page.icon)
            } else {
                icon.visibility = View.GONE
            }
            title.setText(page.title)
            body.setText(page.body)
        }

        override fun getItemCount() = pages.size

        private fun setupButton(textRes: Int, onClick: () -> Unit) =
            MaterialButton(this@IntroActivity).apply {
                setText(textRes)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
                setOnClickListener { onClick() }
            }
    }
}
