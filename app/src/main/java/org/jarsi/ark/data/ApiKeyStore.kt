package org.jarsi.ark.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API-avaimen säilytys levyllä salattuna: salausavain elää Android
 * Keystoressa eikä koskaan poistu laitteelta, joten asetuksiin
 * tallentuu vain salattu muoto. Aiemmin avoimena tallennettu avain
 * salataan ensimmäisellä luvulla. Jos laitteen Keystore ei toimi,
 * avain säilytetään entiseen tapaan avoimena, jotta toiminto ei
 * lakkaa toimimasta millään laitteella.
 */
object ApiKeyStore {

    /** Tallennuspaikat eri palveluiden avaimille. */
    enum class Slot(val plainPref: String, val encryptedPref: String) {
        CLAUDE("claude_api_avain", "claude_api_avain_salattu"),
        OPENAI("openai_api_avain", "openai_api_avain_salattu"),
    }

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "ark_api_avain"
    private const val GCM_TAG_BITS = 128

    // Purku tapahtuu jokaisella korjausnäkymän avauksella; välimuisti
    // säästää Keystore-kutsut yhteen per prosessi.
    private val cached = java.util.concurrent.ConcurrentHashMap<Slot, String>()

    /** Avain käyttöä varten tai null; siirtää avoimen avaimen salatuksi. */
    fun read(prefs: SharedPreferences, slot: Slot = Slot.CLAUDE): String? {
        cached[slot]?.let { return it }
        prefs.getString(slot.encryptedPref, null)?.let { encrypted ->
            val plain = decrypt(encrypted)
            if (plain != null) cached[slot] = plain
            return plain
        }
        val plain = prefs.getString(slot.plainPref, null)?.trim().orEmpty()
        if (plain.isEmpty()) return null
        save(prefs, plain, slot)
        return cached[slot]
    }

    /** Onko avain asetettu (ilman purkua, kun salattu muoto riittää). */
    fun exists(prefs: SharedPreferences, slot: Slot = Slot.CLAUDE): Boolean =
        prefs.getString(slot.encryptedPref, null) != null ||
            prefs.getString(slot.plainPref, null)?.isNotBlank() == true

    /** Tallentaa avaimen salattuna; tyhjä arvo poistaa avaimen kokonaan. */
    fun save(prefs: SharedPreferences, plainKey: String, slot: Slot = Slot.CLAUDE) {
        val trimmed = plainKey.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(slot.encryptedPref).remove(slot.plainPref).apply()
            cached.remove(slot)
            return
        }
        val encrypted = encrypt(trimmed)
        prefs.edit().apply {
            if (encrypted != null) {
                putString(slot.encryptedPref, encrypted)
                remove(slot.plainPref)
            } else {
                // Varareitti laitteille, joiden Keystore on rikki.
                putString(slot.plainPref, trimmed)
                remove(slot.encryptedPref)
            }
        }.apply()
        cached[slot] = trimmed
    }

    private fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    private fun decrypt(encoded: String): String? = try {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // GCM-alustusvektori on aina 12 tavua datan alussa.
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, data, 0, 12),
        )
        String(cipher.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
