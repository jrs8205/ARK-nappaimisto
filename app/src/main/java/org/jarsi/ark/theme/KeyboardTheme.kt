package org.jarsi.ark.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import org.jarsi.ark.R

/**
 * Näppäimistön väriteema. Värit määritellään kertaalleen colors.xml:ssä
 * Material 3 -rooleittain; asetussivu käyttää samoja värejä teemansa kautta.
 */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val specialKey: Int,
    val keyPressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val accentText: Int,
) {
    companion object {
        /**
         * Lataa teeman järjestelmän tumman tilan mukaan. Vanhoilla
         * Android-versioilla (ennen 10:tä) ei ole järjestelmän tummaa tilaa,
         * joten niillä käytetään aina tummaa teemaa.
         */
        fun load(context: Context): KeyboardTheme {
            val light = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            return if (light) {
                KeyboardTheme(
                    background = context.getColor(R.color.vaalea_tausta),
                    key = context.getColor(R.color.vaalea_nappain),
                    specialKey = context.getColor(R.color.vaalea_erikoisnappain),
                    keyPressed = context.getColor(R.color.vaalea_painettu),
                    text = context.getColor(R.color.vaalea_teksti),
                    hint = context.getColor(R.color.vaalea_vihje),
                    accent = context.getColor(R.color.vaalea_korostus),
                    accentText = context.getColor(R.color.vaalea_korostusteksti),
                )
            } else {
                KeyboardTheme(
                    background = context.getColor(R.color.tumma_tausta),
                    key = context.getColor(R.color.tumma_nappain),
                    specialKey = context.getColor(R.color.tumma_erikoisnappain),
                    keyPressed = context.getColor(R.color.tumma_painettu),
                    text = context.getColor(R.color.tumma_teksti),
                    hint = context.getColor(R.color.tumma_vihje),
                    accent = context.getColor(R.color.tumma_korostus),
                    accentText = context.getColor(R.color.tumma_korostusteksti),
                )
            }
        }
    }
}
