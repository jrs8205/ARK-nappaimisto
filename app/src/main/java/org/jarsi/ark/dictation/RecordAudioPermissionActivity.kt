package org.jarsi.ark.dictation

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Läpinäkyvä aktiviteetti, joka pyytää mikrofoniluvan sanelua varten,
 * koska näppäimistöpalvelu ei voi kysyä lupia suoraan. Sulkeutuu heti
 * vastauksen jälkeen; palvelu jatkaa sanelua kentän palautuessa.
 */
class RecordAudioPermissionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }
}
