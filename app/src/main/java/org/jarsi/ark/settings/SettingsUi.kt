package org.jarsi.ark.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import org.jarsi.ark.R

/**
 * Asetussivujen yhteinen runko: iso kutistuva otsikko (aktiviteetin
 * nimiö) ja sisältö sen alla. Yläreunan upotukset hoitaa sovelluspalkki;
 * sivujen ja alareunan upotukset varataan sisällölle tässä.
 */
object SettingsUi {

    fun install(
        activity: AppCompatActivity,
        content: View?,
        showBack: Boolean = true,
    ): ViewGroup {
        val root = LayoutInflater.from(activity)
            .inflate(R.layout.asetus_pohja, null) as ViewGroup
        root.findViewById<CollapsingToolbarLayout>(R.id.asetus_otsikko).title =
            activity.title
        if (showBack) {
            val toolbar = root.findViewById<MaterialToolbar>(R.id.asetus_tyokalupalkki)
            toolbar.setNavigationIcon(R.drawable.ic_back)
            toolbar.setNavigationOnClickListener { activity.finish() }
        }
        val container = root.findViewById<ViewGroup>(R.id.asetus_sisalto)
        // Sisältö täyttää kehyksen: muuten se venyy listan mittaiseksi ja
        // alareunan painike (esim. Tyhjennä kaikki) valuu ruudun alle.
        content?.let {
            container.addView(
                it,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        // Upotukset luetaan juuresta: sovelluspalkki ehtii kuluttaa ne
        // ennen sisältökehystä, jolloin alareunan painike jäisi
        // navigointipalkin alle. Juuren oma käsittely jatkuu normaalisti,
        // jotta yläreunan upotus menee yhä sovelluspalkille.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            container.setPadding(bars.left, 0, bars.right, bars.bottom)
            ViewCompat.onApplyWindowInsets(view, insets)
        }
        activity.setContentView(root)
        return container
    }
}
