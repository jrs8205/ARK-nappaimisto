package org.jarsi.ark.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.jarsi.ark.R
import org.jarsi.ark.engine.WordTools
import org.jarsi.ark.keyboard.CursorIndex
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Käännösnäkymä Google Kääntäjän tapaan — oma työkalu, joka ei kirjoita
 * kenttään mitään itsestään. Ylhäällä on monirivinen kirjoitusalue, joka
 * kasvaa tekstin mukana, ja sen alla käännös samalla tavalla väljänä.
 * Kopiointinappi vie käännöksen leikepöydälle, Lisää-pikanappi suoraan
 * kenttään ja ✨ hakee laadukkaamman käännöksen AI-palvelulta.
 * Kielikoodia napauttamalla kieli vaihtuu ladattujen joukossa, ⇄ kääntää
 * suunnan ja ✕ tyhjentää. Tekstiä napauttamalla kursori siirtyy;
 * pitkä painallus valitsee sanan kahvoineen.
 */
class TranslateBarView(context: Context) : LinearLayout(context) {

    /** Kielivalinnan rivi: koodi, suomenkielinen nimi ja onko malli laitteella. */
    data class LanguageChoice(val code: String, val name: String, val downloaded: Boolean)

    interface Listener {
        /** Käyttäjä valitsi lähdekielen listasta. */
        fun onPickSource(code: String)

        fun onSwap()

        /** Käyttäjä valitsi kohdekielen listasta. */
        fun onPickTarget(code: String)

        fun onClear()

        /** Käyttäjä napautti lähdetekstiä: kursori kohtaan [position]. */
        fun onCursorTap(position: Int)

        /** Käyttäjä napautti käännöstä: sitä muokataan kohdasta [position]. */
        fun onTranslationTap(position: Int)

        /** Käyttäjä kopioi valitun lähdetekstin. */
        fun onCopy(text: String)

        /** Käyttäjä liittää leikepöydän tekstin käännösriville. */
        fun onPaste()

        /** Käyttäjä pyysi paremman käännöksen AI-palvelulta. */
        fun onAiTranslate()

        /** Käyttäjä kopioi käännöksen leikepöydälle. */
        fun onCopyTranslation()

        /** Käyttäjä vie näkyvän käännöksen kenttään. */
        fun onInsert()

        /** Käyttäjä antoi luvan kieliparin mallien lataukseen. */
        fun onDownloadModels()
    }

    var listener: Listener? = null

    /** Onko leikepöydällä liitettävää tekstiä; palvelu päivittää. */
    var pasteAvailable = false

    /** ✨ näytetään vain, kun valitulla AI-palvelulla on API-avain. */
    var aiEnabled = true
        set(value) {
            field = value
            refreshActionRow()
        }

    // Kieliparin mallit puuttuvat: toimintorivillä näkyy vain Lataa-nappi,
    // kunnes käyttäjä antaa luvan kertalataukselle.
    private var modelsMissing = false

    fun setModelsMissing(missing: Boolean) {
        modelsMissing = missing
        refreshActionRow()
    }

    private fun refreshActionRow() {
        downloadLabel.visibility = if (modelsMissing) VISIBLE else GONE
        aiLabel.visibility = if (!modelsMissing && aiEnabled) VISIBLE else GONE
        copyIcon.visibility = if (modelsMissing) GONE else VISIBLE
        insertLabel.visibility = if (modelsMissing) GONE else VISIBLE
    }

    private var theme = KeyboardTheme.load(context)
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    // Näytetyn tekstin kursorikohta napautuksen takaisinmappausta varten;
    // -1 kun alueella ei ole kohdistusta (vihje näkyvissä tai kirjoitus
    // kohdistuu toiseen alueeseen).
    private var shownCursor = -1
    private var shownTranslationCursor = -1

    // Kursori vilkkuu kuten tekstikentässä: puoli sekuntia näkyvissä ja
    // puoli piilossa; siirto tuo sen aina heti näkyviin. Kohdistus on
    // kerrallaan vain toisessa alueessa, joten sama tahti riittää molemmille.
    private var cursorVisible = true
    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (!hasCursor() || !isShown) return
            cursorVisible = !cursorVisible
            bufferView.invalidate()
            translationView.invalidate()
            postDelayed(this, CURSOR_BLINK_MS)
        }
    }

    private fun hasCursor(): Boolean = shownCursor >= 0 || shownTranslationCursor >= 0

    private fun restartBlink() {
        cursorVisible = true
        removeCallbacks(blinkRunnable)
        postDelayed(blinkRunnable, CURSOR_BLINK_MS)
    }

    // Kursori piirretään kapeana pystyviivana, joka vie vain parin pisteen
    // raon — kokonainen merkki työntäisi kirjaimet erilleen kuin välilyönti.
    // [accent] erottaa käännösalueen kursorin, joka seuraa käännöksen väriä.
    private inner class CursorSpan(private val accent: Boolean) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            fm?.let {
                val metrics = paint.fontMetricsInt
                it.top = metrics.top
                it.ascent = metrics.ascent
                it.descent = metrics.descent
                it.bottom = metrics.bottom
            }
            return dp(2)
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            if (!cursorVisible) return
            val oldColor = paint.color
            // Tekstin väri: tummassa teemassa valkoinen kuten kentän kursori,
            // vaaleassa tumma, jotta viiva näkyy aina.
            paint.color = if (accent) theme.accent else theme.text
            canvas.drawRect(x, y + paint.ascent(), x + dp(2), y + paint.descent(), paint)
            paint.color = oldColor
        }
    }

    private val cursorSpan = CursorSpan(accent = false)
    private val translationCursorSpan = CursorSpan(accent = true)

    // Kielipillerit Google Kääntäjän tapaan: koko kielinimet leveissä
    // pilleritaustoissa suunnanvaihdon molemmin puolin.
    private val sourceLabel = TextView(context).apply {
        textSize = 15f
        gravity = Gravity.CENTER
        maxLines = 1
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { showLanguageMenu(it, sourceSide = true) }
    }

    // Ikonit ovat vektoreita eivätkä tekstimerkkejä, jotta ne piirtyvät
    // samannäköisinä kaikilla laitteilla emoji-fontista riippumatta.
    private val swapLabel = ImageView(context).apply {
        setImageResource(R.drawable.ic_swap)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        contentDescription = context.getString(R.string.cd_vaihda_suunta)
        setOnClickListener { listener?.onSwap() }
    }

    private val targetLabel = TextView(context).apply {
        textSize = 15f
        gravity = Gravity.CENTER
        maxLines = 1
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { showLanguageMenu(it, sourceSide = false) }
    }

    // Valitut kielikoodit; pillerit näyttävät nimet mutta lista vertaa koodiin.
    private var sourceCode = ""
    private var targetCode = ""

    // Valittavat kielet listavalikkoon; palvelu päivittää ladatut tiedot.
    private var languageChoices: List<LanguageChoice> = emptyList()
    private var languagePopup: PopupWindow? = null

    fun setLanguageChoices(choices: List<LanguageChoice>) {
        languageChoices = choices
    }

    /**
     * Kielilista avautuu napautetun kielen ylle: ladatut kielet ensin ja
     * lataamattomat latausikonilla — valinta kysyy latausluvan erikseen.
     */
    private fun showLanguageMenu(anchor: View, sourceSide: Boolean) {
        dismissLanguagePopup()
        val choices = languageChoices
        if (choices.isEmpty()) return
        val selected = if (sourceSide) sourceCode else targetCode
        val list = LinearLayout(context).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(theme.specialKey)
            }
        }
        choices.forEach { choice ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(9), dp(16), dp(9))
                setOnClickListener {
                    dismissLanguagePopup()
                    if (sourceSide) {
                        listener?.onPickSource(choice.code)
                    } else {
                        listener?.onPickTarget(choice.code)
                    }
                }
            }
            val current = choice.code.equals(selected, ignoreCase = true)
            row.addView(
                TextView(context).apply {
                    text = choice.name
                    textSize = 15f
                    if (current) setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (current) theme.accent else theme.text)
                },
                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            if (!choice.downloaded) {
                row.addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_download)
                        setColorFilter(theme.hint)
                    },
                    LayoutParams(dp(18), dp(18)).apply { marginStart = dp(12) },
                )
            }
            list.addView(
                row,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val scroll = ScrollView(context).apply { addView(list) }
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(dp(240), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(dp(320), View.MeasureSpec.AT_MOST),
        )
        val popupWidth = scroll.measuredWidth
        val popupHeight = scroll.measuredHeight
        val anchorLocation = IntArray(2)
        anchor.getLocationInWindow(anchorLocation)
        val x = anchorLocation[0].coerceIn(
            dp(4),
            (resources.displayMetrics.widthPixels - popupWidth - dp(4)).coerceAtLeast(dp(4)),
        )
        languagePopup = PopupWindow(scroll, popupWidth, popupHeight).apply {
            isClippingEnabled = false
            // Sulkeutuu myös listan ulkopuolisesta napautuksesta.
            isOutsideTouchable = true
            isFocusable = false
            setBackgroundDrawable(GradientDrawable())
            showAtLocation(
                this@TranslateBarView,
                Gravity.NO_GRAVITY,
                x,
                popupTop(anchorLocation[1], popupHeight),
            )
        }
    }

    private fun dismissLanguagePopup() {
        languagePopup?.dismiss()
        languagePopup = null
    }

    // Näytetty lähdeteksti ja valittu väli (lähdetekstin indeksejä)
    // kopiointia varten; valinta piirretään korostustaustalla.
    private var shownText = ""
    private var shownHint = ""
    private var shownCursorTarget = 0

    // Näytetty käännös vihjeineen ja kursorikohtineen; -1 = ei kohdistusta.
    private var shownTranslation = ""
    private var shownTranslationHint = ""
    private var translationCursorTarget = -1
    private var selection: IntRange? = null
    private var selectionPopup: PopupWindow? = null
    private var popupAnchorX = 0

    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false

    // Tekstialueen ihannekorkeus; ahtaassa tilassa emo antaa vähemmän.
    private val areaHeight = idealAreaHeight()

    // Käännösalueen napautus: vieritykseksi tunnistettu liike ei siirrä kursoria.
    private var translationDownX = 0f
    private var translationDownY = 0f
    private var translationScrolled = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressRunnable = Runnable { onBufferLongPressed() }

    // Valintakahvan raahaus: 0 = ei mikään, 1 = alkukahva, 2 = loppukahva.
    private var draggingHandle = 0
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    @SuppressLint("ClickableViewAccessibility")
    private val bufferView = object : TextView(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawSelectionHandles(canvas, this)
        }
    }.apply {
        textSize = LARGE_TEXT_SP
        // Teksti rivittyy vapaasti kuten Google Kääntäjässä; alue kasvaa
        // sisällön mukana CappedScrollView'n ylärajaan asti. Oikeaan
        // reunaan jää tilaa kulman tyhjennysnapille.
        gravity = Gravity.TOP
        minimumHeight = areaHeight
        setPadding(dp(16), dp(10), dp(44), dp(10))
        setOnTouchListener { view, event ->
            val textView = view as TextView
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    longPressFired = false
                    draggingHandle = hitHandle(textView, event.x, event.y)
                    if (draggingHandle != 0) {
                        // Kahvan veto ei saa käynnistää vieritystä eikä valikkoa.
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        dismissSelectionPopup()
                    } else {
                        view.postDelayed(
                            longPressRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong(),
                        )
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (draggingHandle != 0) {
                        dragHandleTo(textView, event.x, event.y)
                    } else if (abs(event.x - downX) > touchSlop ||
                        abs(event.y - downY) > touchSlop
                    ) {
                        view.removeCallbacks(longPressRunnable)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(longPressRunnable)
                    if (draggingHandle != 0) {
                        draggingHandle = 0
                        showSelectionPopup()
                    } else if (!longPressFired) {
                        if (selection != null) {
                            clearSelection()
                            render()
                        }
                        onBufferTapped(textView, event.x, event.y)
                        view.performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPressRunnable)
                    draggingHandle = 0
                }
            }
            // Kosketus kuluu tässä, ettei vieritys nappaa napautusta;
            // liike jää vierityksen siepattavaksi paitsi kahvaa raahatessa.
            draggingHandle != 0 ||
                event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_DOWN
        }
    }

    /**
     * Tekstialue vierii sisältönsä yli. Korkeus on enintään ihanne,
     * mutta ahtaassa tilassa emo antaa vähemmän — silloin alue kutistuu
     * eikä työnnä alempia rivejä ulos näytöltä.
     */
    private inner class CappedScrollView : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val limit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                areaHeight
            } else {
                minOf(areaHeight, MeasureSpec.getSize(heightMeasureSpec))
            }
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST),
            )
        }
    }

    /**
     * Näkymä ei veny sisältöään korkeammaksi, vaikka emo tarjoaisi
     * enemmän: ylimääräinen tila kuuluu näppäimistölle.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val spec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, spec)
    }

    // Aluekorkeuden ihanne: näkymä täyttää tilan tilariviin asti eikä
    // taustasovelluksesta jää kaistaa näkyviin. Korkeus ei elä tekstin
    // mukana — kasvu työntäisi näppäimistön navigointipalkin alle, joten
    // pitkä teksti vierii alueen sisällä. Suhteellinen mitta skaalautuu
    // kaikille laitteille.
    private fun idealAreaHeight(): Int =
        maxOf(dp(120), (resources.displayMetrics.heightPixels * 0.17f).roundToInt())

    private val sourceScroll = CappedScrollView().apply {
        isVerticalScrollBarEnabled = false
        addView(
            bufferView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private val clearLabel = ImageView(context).apply {
        setImageResource(R.drawable.ic_close)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        visibility = GONE
        contentDescription = context.getString(R.string.cd_tyhjenna_rivi)
        setOnClickListener { listener?.onClear() }
    }

    // Tyhjennys kelluu lähdetekstin oikeassa yläkulmassa kuten Googlessa.
    private val sourceFrame = FrameLayout(context)

    // Ohut viiva erottaa lähdetekstin ja käännöksen ilman taustalaatikkoa.
    private val divider = View(context)

    // Käännösalue: monirivinen kuten kirjoitusaluekin. Kenttä ei muutu
    // ennen kuin käännös kopioidaan tai viedään pikanapilla. Käännöstä
    // napauttamalla sitä voi korjata paikallaan ennen vientiä.
    @SuppressLint("ClickableViewAccessibility")
    private val translationView = TextView(context).apply {
        textSize = LARGE_TEXT_SP
        gravity = Gravity.TOP
        minimumHeight = areaHeight
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    translationDownX = event.x
                    translationDownY = event.y
                    translationScrolled = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.x - translationDownX) > touchSlop ||
                        abs(event.y - translationDownY) > touchSlop
                    ) {
                        // Vieritys ei saa siirtää kursoria sormen noustessa.
                        translationScrolled = true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!translationScrolled) {
                        onTranslationTapped(view as TextView, event.x, event.y)
                        view.performClick()
                    }
                }
            }
            // Sama kulutusmalli kuin lähdealueessa: napautus jää tänne,
            // liike jää vierityksen siepattavaksi.
            event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_DOWN
        }
    }

    private val translationScroll = CappedScrollView().apply {
        isVerticalScrollBarEnabled = false
        addView(
            translationView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private val aiLabel = ImageView(context).apply {
        setImageResource(R.drawable.ic_sparkle)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        contentDescription = context.getString(R.string.cd_ai_kaannos)
        setOnClickListener { listener?.onAiTranslate() }
    }

    private val copyIcon = ImageView(context).apply {
        setImageResource(R.drawable.ic_clipboard)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        contentDescription = context.getString(R.string.cd_kopioi_kaannos)
        setOnClickListener { listener?.onCopyTranslation() }
    }

    private val insertLabel = TextView(context).apply {
        text = context.getString(R.string.kaannos_vie)
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(12), dp(10), dp(14), dp(10))
        setOnClickListener { listener?.onInsert() }
    }

    private val downloadLabel = TextView(context).apply {
        text = context.getString(R.string.kaannos_lataa_mallit)
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(12), dp(10), dp(14), dp(10))
        visibility = GONE
        setOnClickListener { listener?.onDownloadModels() }
    }

    // Toimintorivi käännöksen alla: ✨, kopiointi ja vienti kenttään.
    private val actionRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(44)
        addView(View(context), LayoutParams(0, 1, 1f))
        addView(aiLabel, LayoutParams(dp(44), dp(44)))
        addView(copyIcon, LayoutParams(dp(44), dp(44)))
        addView(insertLabel)
        addView(downloadLabel)
    }

    // Kielirivi alimpana näppäimistön yläpuolella kuten Google Kääntäjässä.
    private val languageRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(4))
        addView(sourceLabel, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            swapLabel,
            LayoutParams(dp(44), dp(44)).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            },
        )
        addView(targetLabel, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    init {
        orientation = VERTICAL
        // Rako alareunaan erottaa kielirivin alla olevasta työkalurivistä,
        // etteivät painallukset osu väärään riviin.
        setPadding(0, dp(6), 0, dp(10))
        val rowParams =
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        // Tekstialueet ovat näkymän joustava osa: ne kasvavat ihanteeseensa
        // ja kutistuvat tasan, kun näppäimistö vie enemmän tilaa.
        fun areaParams() = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        )
        sourceFrame.addView(
            sourceScroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        sourceFrame.addView(
            clearLabel,
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END),
        )
        addView(sourceFrame, areaParams())
        addView(
            divider,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
            },
        )
        addView(translationScroll, areaParams())
        addView(actionRow, rowParams)
        addView(languageRow, rowParams)
    }

    fun applySettings(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(theme.background)
        // Kielipillerit saavat korttitaustan; itse tekstialueet ovat
        // paljaita ja käännös erottuu vain värillä ja erotinviivalla.
        fun pill() = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(theme.specialKey)
        }
        sourceLabel.background = pill()
        targetLabel.background = pill()
        sourceLabel.setTextColor(theme.text)
        targetLabel.setTextColor(theme.text)
        swapLabel.setColorFilter(theme.text)
        clearLabel.setColorFilter(theme.hint)
        aiLabel.setColorFilter(theme.text)
        insertLabel.setTextColor(theme.accent)
        downloadLabel.setTextColor(theme.accent)
        copyIcon.setColorFilter(theme.text)
        divider.setBackgroundColor(
            (theme.hint and 0x00FFFFFF) or (DIVIDER_ALPHA shl 24)
        )
    }

    /** Iso teksti lyhyille, pienempi pitkille — kuten Google Kääntäjässä. */
    private fun textSizeFor(text: String): Float = when {
        text.length <= 60 -> LARGE_TEXT_SP
        text.length <= 160 -> 21f
        else -> 17f
    }

    /**
     * Näyttää käännöksen tai [hint]-vihjeen sen puuttuessa. [cursor] on
     * kohta käännöstekstissä, kun käyttäjä muokkaa käännöstä paikallaan;
     * -1 piirtää käännöksen ilman kursoria.
     */
    fun setTranslation(text: String, hint: String, cursor: Int = -1) {
        shownTranslation = text
        shownTranslationHint = hint
        translationCursorTarget = cursor
        renderTranslation()
    }

    private fun renderTranslation() {
        val text = shownTranslation
        translationView.textSize = textSizeFor(text)
        if (text.isEmpty()) {
            // Kursori jää näkyviin, jos käyttäjä pyyhki korjatun käännöksen
            // tyhjäksi — kirjoitus jatkuu yhä tälle alueelle.
            val focused = translationCursorTarget >= 0
            shownTranslationCursor = if (focused) 0 else -1
            translationView.text = if (focused) {
                SpannableStringBuilder(CURSOR_PLACEHOLDER + shownTranslationHint).apply {
                    setSpan(
                        translationCursorSpan,
                        0,
                        CURSOR_PLACEHOLDER.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            } else {
                SpannableStringBuilder(shownTranslationHint)
            }
            translationView.setTextColor(theme.hint)
            if (focused) restartBlink()
        } else {
            val position = translationCursorTarget.takeIf { it >= 0 }?.coerceIn(0, text.length)
            shownTranslationCursor = position ?: -1
            translationView.text = if (position == null) {
                SpannableStringBuilder(text)
            } else {
                SpannableStringBuilder(text).apply {
                    insert(position, CURSOR_PLACEHOLDER)
                    setSpan(
                        translationCursorSpan,
                        position,
                        position + CURSOR_PLACEHOLDER.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            // Käännös erottuu lähdetekstistä korostevärillä kuten Googlessa.
            translationView.setTextColor(theme.accent)
            if (position != null) {
                restartBlink()
                scrollIntoView(translationView, translationScroll, position)
            }
        }
        val alpha = if (text.isEmpty()) 0.4f else 1f
        insertLabel.alpha = alpha
        copyIcon.alpha = alpha
    }

    /** Napautus käännöksessä: kursori tekstin kohtaan, paikkamerkki huomioiden. */
    private fun onTranslationTapped(view: TextView, x: Float, y: Float) {
        if (shownTranslation.isEmpty()) return
        val offset = view.getOffsetForPosition(x, y)
        if (offset < 0) return
        val position = CursorIndex.toText(offset, shownTranslationCursor)
        listener?.onTranslationTap(position.coerceIn(0, shownTranslation.length))
    }

    fun setLanguages(
        sourceCode: String,
        sourceName: String,
        targetCode: String,
        targetName: String,
    ) {
        this.sourceCode = sourceCode
        this.targetCode = targetCode
        sourceLabel.text = sourceName
        targetLabel.text = targetName
    }

    /**
     * Näyttää keskeneräisen tekstin kursoreineen tai [hint]-vihjeen, kun
     * rivi on tyhjä. [cursor] on kohta lähdetekstissä; -1 jättää kursorin
     * pois, kun kirjoitus kohdistuu käännösalueeseen.
     */
    fun setBuffer(text: String, cursor: Int, hint: String) {
        if (text != shownText) clearSelection()
        shownText = text
        shownHint = hint
        shownCursorTarget = cursor
        render()
    }

    private fun render() {
        bufferView.textSize = textSizeFor(shownText)
        val focused = shownCursorTarget >= 0
        if (shownText.isEmpty()) {
            // Kursori vilkkuu vihjeen edessä heti avattaessa, jotta alue
            // näyttää aktiiviselta ja kirjoittamaan voi ryhtyä suoraan.
            shownCursor = if (focused) 0 else -1
            bufferView.text = if (focused) {
                SpannableStringBuilder(CURSOR_PLACEHOLDER + shownHint).apply {
                    setSpan(
                        cursorSpan,
                        0,
                        CURSOR_PLACEHOLDER.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            } else {
                SpannableStringBuilder(shownHint)
            }
            bufferView.setTextColor(theme.hint)
            clearLabel.visibility = GONE
            if (focused) restartBlink()
        } else {
            val position = if (focused) shownCursorTarget.coerceIn(0, shownText.length) else -1
            shownCursor = position
            bufferView.text = SpannableStringBuilder(shownText).apply {
                if (position >= 0) {
                    insert(position, CURSOR_PLACEHOLDER)
                    setSpan(
                        cursorSpan,
                        position,
                        position + CURSOR_PLACEHOLDER.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                selection?.let { sel ->
                    // Paikkamerkki siirtää näyttöindeksejä kursorin kohdalla.
                    setSpan(
                        BackgroundColorSpan(selectionColor()),
                        displayIndex(sel.first, boundaryAfter = false),
                        displayIndex(sel.last + 1, boundaryAfter = true),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            bufferView.setTextColor(theme.text)
            clearLabel.visibility = VISIBLE
            if (position >= 0) {
                restartBlink()
                // Kahvaa raahatessa näkymä ei saa hyppiä kursorin perässä.
                if (draggingHandle == 0) scrollIntoView(bufferView, sourceScroll, position)
            }
        }
    }

    private fun displayIndex(sourceIndex: Int, boundaryAfter: Boolean): Int =
        CursorIndex.toDisplay(sourceIndex, shownCursor, boundaryAfter)

    private fun selectionColor(): Int =
        (theme.accent and 0x00FFFFFF) or (SELECTION_ALPHA shl 24)

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        // Vilkutus pyörii vain rivin ollessa esillä.
        if (isVisible && hasCursor()) {
            restartBlink()
            bufferView.invalidate()
            translationView.invalidate()
        } else if (!isVisible) {
            removeCallbacks(blinkRunnable)
            clearSelection()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blinkRunnable)
        dismissSelectionPopup()
        dismissLanguagePopup()
        super.onDetachedFromWindow()
    }

    /** Vierittää pystysuunnassa niin, että kursoririvi pysyy näkyvissä. */
    private fun scrollIntoView(view: TextView, scroll: ScrollView, position: Int) {
        view.post {
            val layout = view.layout ?: return@post
            val clamped = position.coerceIn(0, layout.text.length)
            val line = layout.getLineForOffset(clamped)
            val top = layout.getLineTop(line) + view.totalPaddingTop
            val bottom = layout.getLineBottom(line) + view.totalPaddingTop
            val viewport = scroll.height
            if (viewport <= 0) return@post
            val scrollY = scroll.scrollY
            if (top < scrollY) {
                scroll.smoothScrollTo(0, top)
            } else if (bottom > scrollY + viewport) {
                scroll.smoothScrollTo(0, bottom - viewport)
            }
        }
    }

    private fun onBufferTapped(view: TextView, x: Float, y: Float) {
        // Tyhjällä rivillä kursori on jo alussa; napautus ei siirrä mitään.
        if (shownText.isEmpty()) return
        val offset = view.getOffsetForPosition(x, y)
        if (offset < 0) return
        // Näytetyssä tekstissä on kursorin paikkamerkki; poistetaan sen vaikutus.
        val position = CursorIndex.toText(offset, shownCursor)
        listener?.onCursorTap(position.coerceIn(0, shownText.length))
    }

    /** Näyttöindeksin piste (x, rivin alareuna) lähdetekstin kohdalle;
     *  paikkamerkki siirtää indeksejä kursorin kohdalla. [boundaryAfter]
     *  koskee valinnan loppurajaa, joka osuu paikkamerkin jälkeen. */
    private fun handlePoint(
        text: TextView,
        sourceIndex: Int,
        boundaryAfter: Boolean,
    ): Pair<Float, Float>? {
        val layout = text.layout ?: return null
        val clamped = displayIndex(sourceIndex, boundaryAfter).coerceIn(0, layout.text.length)
        val line = layout.getLineForOffset(clamped)
        val x = layout.getPrimaryHorizontal(clamped) + text.totalPaddingLeft
        val y = layout.getLineBottom(line).toFloat() + text.totalPaddingTop
        return x to y
    }

    private fun hitHandle(text: TextView, x: Float, y: Float): Int {
        val sel = selection ?: return 0
        val start = handlePoint(text, sel.first, false) ?: return 0
        val end = handlePoint(text, sel.last + 1, true) ?: return 0
        val slop = dp(24)
        fun near(p: Pair<Float, Float>) =
            abs(x - p.first) <= slop && abs(y - p.second) <= slop
        val toStart = abs(x - start.first) + abs(y - start.second)
        val toEnd = abs(x - end.first) + abs(y - end.second)
        return when {
            near(start) && toStart <= toEnd -> 1
            near(end) -> 2
            else -> 0
        }
    }

    private fun dragHandleTo(text: TextView, x: Float, y: Float) {
        val sel = selection ?: return
        val offset = text.getOffsetForPosition(x, y)
        if (offset < 0) return
        val position = CursorIndex.toText(offset, shownCursor).coerceIn(0, shownText.length)
        val updated = if (draggingHandle == 1) {
            minOf(position, sel.last)..sel.last
        } else {
            sel.first..maxOf(position - 1, sel.first)
        }
        if (updated != sel) {
            selection = updated
            render()
        }
    }

    private fun drawSelectionHandles(canvas: Canvas, text: TextView) {
        val sel = selection ?: return
        val start = handlePoint(text, sel.first, false) ?: return
        val end = handlePoint(text, sel.last + 1, true) ?: return
        handlePaint.color = theme.accent
        val radius = dp(5).toFloat()
        canvas.drawCircle(start.first, start.second + dp(3), radius, handlePaint)
        canvas.drawCircle(end.first, end.second + dp(3), radius, handlePaint)
    }

    /** Pitkä painallus maalaa sanan ja avaa Kopioi-valikon sen ylle. */
    private fun onBufferLongPressed() {
        if (shownText.isEmpty()) {
            // Tyhjälläkin rivillä voi liittää leikepöydän tekstin.
            if (pasteAvailable) {
                longPressFired = true
                val location = IntArray(2)
                bufferView.getLocationInWindow(location)
                popupAnchorX = location[0] + downX.roundToInt()
                showSelectionPopup()
            }
            return
        }
        val offset = bufferView.getOffsetForPosition(downX, downY)
        if (offset < 0) return
        val position = CursorIndex.toText(offset, shownCursor)
        val range = WordTools.wordRangeAt(shownText, position) ?: return
        longPressFired = true
        selection = range
        bufferView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        render()
        val location = IntArray(2)
        bufferView.getLocationInWindow(location)
        popupAnchorX = location[0] + downX.roundToInt()
        showSelectionPopup()
    }

    private fun showSelectionPopup() {
        dismissSelectionPopup()
        val sel = selection
        if (sel == null && !pasteAvailable) return

        val container = LinearLayout(context).apply {
            orientation = HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(theme.specialKey)
            }
        }
        fun addItem(label: String, onClick: () -> Unit) {
            container.addView(
                TextView(context).apply {
                    text = label
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(theme.text)
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    setOnClickListener { onClick() }
                },
                LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        if (sel != null) {
            addItem(context.getString(R.string.kaannos_kopioi)) {
                selection?.let { s ->
                    listener?.onCopy(shownText.substring(s.first, s.last + 1))
                }
                clearSelection()
                render()
            }
            val allSelected = sel.first == 0 && sel.last >= shownText.length - 1
            if (!allSelected) {
                addItem(context.getString(R.string.kaannos_valitse_kaikki)) {
                    selection = 0 until shownText.length
                    render()
                    showSelectionPopup()
                }
            }
        }
        if (pasteAvailable) {
            addItem(context.getString(R.string.leike_liita)) {
                dismissSelectionPopup()
                clearSelection()
                render()
                listener?.onPaste()
            }
        }

        container.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED,
        )
        val popupWidth = container.measuredWidth
        val popupHeight = container.measuredHeight
        val barLocation = IntArray(2)
        getLocationInWindow(barLocation)
        val x = (popupAnchorX - popupWidth / 2)
            .coerceIn(dp(4), (resources.displayMetrics.widthPixels - popupWidth - dp(4)).coerceAtLeast(dp(4)))
        selectionPopup = PopupWindow(container, popupWidth, popupHeight).apply {
            isClippingEnabled = false
            showAtLocation(
                this@TranslateBarView,
                Gravity.NO_GRAVITY,
                x,
                popupTop(barLocation[1], popupHeight),
            )
        }
    }

    /**
     * Valikon yläreuna: ensisijaisesti ankkurin yläpuolelle, mutta ei
     * koskaan tilarivin alle. Käännösnäkymä ulottuu lähelle näytön
     * yläreunaa, joten yläpuolella ei aina ole tilaa — silloin valikko
     * avautuu ankkurin alapuolelle.
     */
    private fun popupTop(anchorTop: Int, popupHeight: Int): Int {
        val minTop = statusBarTop() + dp(4)
        val above = anchorTop - popupHeight - dp(6)
        return if (above >= minTop) above else (anchorTop + dp(6)).coerceAtLeast(minTop)
    }

    private fun statusBarTop(): Int =
        ViewCompat.getRootWindowInsets(this)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: 0

    private fun clearSelection() {
        selection = null
        dismissSelectionPopup()
    }

    private fun dismissSelectionPopup() {
        selectionPopup?.dismiss()
        selectionPopup = null
    }

    private companion object {
        // Leveydetön merkki kantaa kursoripiirron; itse teksti ei muutu.
        const val CURSOR_PLACEHOLDER = "\u200B"

        // Sama tahti kuin Androidin tekstikenttien kursorilla.
        const val CURSOR_BLINK_MS = 500L

        // Valinnan korostustaustan läpinäkyvyys korostusväristä.
        const val SELECTION_ALPHA = 0x55

        // Tekstialueiden peruskoko lyhyille teksteille.
        const val LARGE_TEXT_SP = 26f

        // Erotinviivan läpinäkyvyys vihjeväristä.
        const val DIVIDER_ALPHA = 0x66
    }
}
