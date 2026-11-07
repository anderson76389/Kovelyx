package com.kovelyx.tv.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted on-device Better Auth bearer session. The symmetric key never leaves
 * Android Keystore, and the encrypted preferences are private to the TV app.
 * Corrupted/restored backups are rejected instead of leaking a plaintext token.
 */
internal class CloudflareTvSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "kovelyx_cloudflare_session", Context.MODE_PRIVATE
    )

    fun save(account: CloudflareTvAccountClient.Account, cloudUrl: String) {
        val plain = JSONObject()
            .put("id", account.id)
            .put("email", account.email)
            .put("token", account.bearerToken)
            .put("cloudUrl", cloudUrl)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(plain)
        check(preferences.edit().putString(PREF_KEY, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()) {
            "Impossible de sauvegarder la session Kovelyx"
        }
    }

    fun load(cloudUrl: String): CloudflareTvAccountClient.Account? {
        val stored = preferences.getString(PREF_KEY, null) ?: return null
        return try {
            val sealed = Base64.decode(stored, Base64.DEFAULT)
            require(sealed.size > IV_BYTES + 16)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, sealed.copyOfRange(0, IV_BYTES))
            )
            val data = JSONObject(
                cipher.doFinal(sealed.copyOfRange(IV_BYTES, sealed.size))
                    .toString(Charsets.UTF_8)
            )
            require(data.optString("cloudUrl") == cloudUrl)
            val id = data.getString("id")
            val email = data.getString("email")
            val token = data.getString("token")
            require(id.isNotBlank() && email.isNotBlank() && token.length >= 16)
            CloudflareTvAccountClient.Account(id, email, token)
        } catch (e: Exception) {
            // Includes authentication-tag failures after an Android backup restore.
            Log.w(TAG, "Session chiffrée invalide : nouvelle connexion nécessaire")
            clear()
            null
        }
    }

    fun clear() {
        preferences.edit().remove(PREF_KEY).commit()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val TAG = "KovelyxTvAuth"
        const val KEY_ALIAS = "kovelyx.tv.cloudflare.bearer.v1"
        const val PREF_KEY = "encrypted_bearer_v1"
        const val IV_BYTES = 12
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
