package org.jarsi.ark.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        content?.let { container.addView(it) }
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        activity.setContentView(root)
        return container
    }
}
