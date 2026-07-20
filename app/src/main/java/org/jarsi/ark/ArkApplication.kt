package org.jarsi.ark

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * Material You: asetussivut värjäytyvät käyttäjän taustakuvan mukaan
 * (Android 12+). Vanhemmilla laitteilla jää teeman oma väritys, eikä
 * näppäimistön piirtoon tämä vaikuta.
 */
class ArkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
