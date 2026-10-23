package com.nuvio.app.core.auth

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Windows DPAPI encrypts under the current user's logon credentials. */
internal actual object CloudflareCredentialStore {
    private val isWindows: Boolean = System.getProperty("os.name")
        .startsWith("Windows", ignoreCase = true)
    private val storageFile: Path? = if (isWindows) {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
        Path.of(base, "Kovelyx", "cloud-session-v1.bin")
    } else null

    actual fun save(value: String) {
        val path = storageFile ?: return
        Files.createDirectories(path.parent)
        val ciphertext = Crypt32Util.cryptProtectData(value.toByteArray(Charsets.UTF_8))
        val temp = Files.createTempFile(path.parent, "cloud-session-", ".tmp")
        try {
            Files.write(temp, ciphertext)
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    actual fun load(): String? {
        val path = storageFile ?: return null
        if (!Files.isRegularFile(path)) return null
        return try {
            Crypt32Util.cryptUnprotectData(Files.readAllBytes(path))
                .toString(Charsets.UTF_8)
        } catch (error: Exception) {
            clear()
            null
        }
    }

    actual fun clear() { storageFile?.let { Files.deleteIfExists(it) } }
}
