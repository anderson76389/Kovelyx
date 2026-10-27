package com.kovelyx.app.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM key material is non-exportable from the Android Keystore. */
internal actual object CloudflareCredentialStore {
    private const val KEY_ALIAS = "kovelyx.cloudflare.mobile.auth.v1"
    private const val KEY = "token_encrypted_v1"
    private const val PREFS = "kovelyx_secure_cloudflare_auth"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private var appContext: Context? = null

    fun initialize(context: Context) { appContext = context.applicationContext }

    private fun prefs() = checkNotNull(appContext) {
        "Kovelyx AuthStorage must be initialized before Cloudflare credentials"
    }.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun aesKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    actual fun save(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey())
        val blob = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs().edit().putString(KEY, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()) {
            "Enregistrement sécurisé de la session impossible"
        }
    }

    actual fun load(): String? {
        val stored = prefs().getString(KEY, null) ?: return null
        return try {
            val blob = Base64.decode(stored, Base64.DEFAULT)
            require(blob.size > 28)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, aesKey(),
                GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            cipher.doFinal(blob.copyOfRange(12, blob.size)).toString(Charsets.UTF_8)
        } catch (error: Exception) {
            // Restores from Android backup cannot restore the original Keystore key.
            clear()
            null
        }
    }

    actual fun clear() { prefs().edit().remove(KEY).commit() }
}
