package com.nuvio.app.core.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nuvio.app.core.auth.AuthStorage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM AndroidKeyStore : aucune clé AES, aucun token ni mot de passe
 * n'est placé en clair dans SharedPreferences ni inclus dans une URL.
 */
internal actual object CloudSessionVault {
    private const val PREFS = "kovelyx_cloud_secure_session_v1"
    private const val ENTRY = "sealed_session"
    private const val ALIAS = "kovelyx_compose_cloud_aes_v1"
    private const val MAX_PLAINTEXT_BYTES = 20000

    private fun preferences() = checkNotNull(AuthStorage.cloudAppContext) {
        "Stockage Android non initialisé"
    }.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized
    actual fun save(payload: String) {
        val source = payload.toByteArray(Charsets.UTF_8)
        require(source.size in 32..MAX_PLAINTEXT_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, checkNotNull(key(true)))
        val result = cipher.iv + cipher.doFinal(source)
        check(preferences().edit().putString(
            ENTRY, Base64.encodeToString(result, Base64.NO_WRAP)
        ).commit()) { "Impossible de protéger la session Kovelyx" }
    }

    @Synchronized
    actual fun load(): String? {
        val prefs = preferences()
        val stored = prefs.getString(ENTRY, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size in 29..(MAX_PLAINTEXT_BYTES + 32))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, checkNotNull(key(false)),
                GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val result = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
            require(result.size in 32..MAX_PLAINTEXT_BYTES)
            String(result, Charsets.UTF_8)
        } catch (_: Exception) {
            // Une restauration Android sans la clé Keystore ne permet pas
            // de déchiffrer l'ancien jeton ; aucune connexion non vérifiée.
            prefs.edit().remove(ENTRY).commit()
            null
        }
    }

    @Synchronized
    actual fun clear() {
        check(preferences().edit().remove(ENTRY).commit()) {
            "Impossible d'effacer la session Kovelyx"
        }
    }
}
