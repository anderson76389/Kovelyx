package com.nuvio.tv.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Coffre de session Better Auth lié à cette TV. La clé AES n'est jamais exportée
 * du magasin AndroidKeyStore. Mot de passe et jeton en clair ne sont pas stockés.
 */
internal object TvCloudSessionVault {
    private const val PREFS = "kovelyx_cloud_tv_session_v1"
    private const val ENTRY = "sealed_session"
    private const val ALIAS = "kovelyx_tv_better_auth_aes_v1"
    private const val MAX_LENGTH = 16000

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadKey(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized
    fun save(context: Context, account: CloudflareTvAccountClient.Account) {
        val payload = JSONObject()
            .put("server", com.nuvio.tv.BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/'))
            .put("id", account.id)
            .put("email", account.email)
            .put("token", account.bearerToken)
            .toString().toByteArray(Charsets.UTF_8)
        require(payload.size in 32..MAX_LENGTH) { "Session Kovelyx invalide" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, checkNotNull(loadKey(true)))
        val sealed = cipher.iv + cipher.doFinal(payload)
        check(preferences(context).edit().putString(ENTRY, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()) {
            "Sauvegarde sécurisée de session impossible"
        }
    }

    @Synchronized
    fun read(context: Context): CloudflareTvAccountClient.Account? {
        val data = preferences(context).getString(ENTRY, null) ?: return null
        return try {
            val bytes = Base64.decode(data, Base64.NO_WRAP)
            require(bytes.size in 29..(MAX_LENGTH + 32))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, checkNotNull(loadKey(false)), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val decrypted = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
            val parsed = JSONObject(String(decrypted, Charsets.UTF_8))
            require(parsed.optString("server") == com.nuvio.tv.BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/'))
            val id = parsed.optString("id")
            val email = parsed.optString("email")
            val token = parsed.optString("token")
            require(id.isNotBlank() && email.contains('@') && token.length >= 16)
            CloudflareTvAccountClient.Account(id, email, token)
        } catch (_: Exception) {
            // Sauvegarde Android restaurée sans la clé Keystore ou jeton corrompu.
            clear(context)
            null
        }
    }

    @Synchronized
    fun clear(context: Context) {
        check(preferences(context).edit().remove(ENTRY).commit()) { "Effacement de session impossible" }
    }
}
