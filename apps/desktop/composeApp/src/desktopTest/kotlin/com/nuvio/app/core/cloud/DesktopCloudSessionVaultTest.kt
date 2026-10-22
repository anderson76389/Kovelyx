package com.nuvio.app.core.cloud

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class DesktopCloudSessionVaultTest {
    @Test
    fun windows_dpapi_encrypts_decrypts_and_rejects_modified_ciphertext() {
        // Les builds Linux/macOS n'ont pas de magasin sécurisé permanent.
        if (!System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("windows")) return

        // Ne toucher à aucun compte, fichier ni token existant de l'utilisateur.
        val plain = ("Kovelyx test-only DPAPI session " + System.nanoTime())
            .toByteArray(Charsets.UTF_8)
        val ciphertext = CloudSessionVault.protectForCurrentUser(plain)
        assertFalse(ciphertext.contentEquals(plain), "DPAPI doit réellement chiffrer")
        assertContentEquals(plain, CloudSessionVault.unprotectForCurrentUser(ciphertext))

        val corrupted = ciphertext.copyOf()
        corrupted[corrupted.lastIndex] =
            (corrupted.last().toInt() xor 0x5A).toByte()
        assertFailsWith<IllegalStateException> {
            CloudSessionVault.unprotectForCurrentUser(corrupted)
        }
    }
}
