package com.nuvio.app.core.cloud

import com.nuvio.app.core.storage.DesktopStorage
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import java.util.Base64
import java.util.Locale

/**
 * Le jeton est protégé par Windows DPAPI (CryptProtectData, profil utilisateur).
 * Aucun secret n'est stocké en clair, et il n'y a pas de clé partagée dans l'EXE.
 * Sur Linux/macOS, la session reste volontairement en mémoire jusqu'à
 * l'intégration et la validation d'un coffre propre au système.
 */
internal actual object CloudSessionVault {
    private const val ENTRY = "dpapi_session"
    private const val UI_FORBIDDEN = 0x01
    private const val MAX_PAYLOAD = 20000

    private val onWindows: Boolean
        get() = System.getProperty("os.name").orEmpty()
            .lowercase(Locale.ROOT).contains("windows")

    private val store by lazy { DesktopStorage.store("kovelyx_cloud_secure_session_v1") }

    // JNA instancie ces interfaces et structures par réflexion : elles doivent
    // être visibles depuis son ClassLoader, sinon IllegalAccessException.
    interface Crypt32 : StdCallLibrary {
        fun CryptProtectData(
            input: DataBlob, description: WString?, entropy: Pointer?,
            reserved: Pointer?, prompt: Pointer?, flags: Int, output: DataBlob
        ): Boolean

        fun CryptUnprotectData(
            input: DataBlob, description: Pointer?, entropy: Pointer?,
            reserved: Pointer?, prompt: Pointer?, flags: Int, output: DataBlob
        ): Boolean
    }

    interface Kernel32 : StdCallLibrary {
        fun LocalFree(pointer: Pointer?): Pointer?
    }

    class DataBlob() : Structure() {
        @JvmField var cbData: Int = 0
        @JvmField var pbData: Pointer? = null
        override fun getFieldOrder(): List<String> = listOf("cbData", "pbData")

        constructor(bytes: ByteArray) : this() {
            cbData = bytes.size
            pbData = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
        }
    }

    private val crypt32 by lazy { Native.load("Crypt32", Crypt32::class.java) }
    private val kernel32 by lazy { Native.load("Kernel32", Kernel32::class.java) }

    private fun transform(input: ByteArray, protect: Boolean): ByteArray {
        val source = DataBlob(input)
        val output = DataBlob()
        val succeeded = if (protect) {
            crypt32.CryptProtectData(source, WString("Kovelyx Cloudflare"),
                null, null, null, UI_FORBIDDEN, output)
        } else {
            crypt32.CryptUnprotectData(source, null,
                null, null, null, UI_FORBIDDEN, output)
        }
        check(succeeded) { "Protection Windows DPAPI indisponible" }
        output.read()
        val nativePointer = checkNotNull(output.pbData)
        try {
            require(output.cbData in 32..(MAX_PAYLOAD + 1024)) {
                "Session Windows invalide"
            }
            return nativePointer.getByteArray(0, output.cbData)
        } finally {
            kernel32.LocalFree(nativePointer)
        }
    }

    // Primitive testable sans écrire dans le coffre du vrai utilisateur :
    // les tests CI Windows vérifient un cycle DPAPI natif et l'intégrité.
    internal fun protectForCurrentUser(data: ByteArray): ByteArray = transform(data, protect = true)
    internal fun unprotectForCurrentUser(data: ByteArray): ByteArray = transform(data, protect = false)

    @Synchronized
    actual fun save(payload: String) {
        if (!onWindows) return
        val source = payload.toByteArray(Charsets.UTF_8)
        require(source.size in 32..MAX_PAYLOAD)
        val encrypted = transform(source, protect = true)
        store.putString(ENTRY, Base64.getEncoder().encodeToString(encrypted))
    }

    @Synchronized
    actual fun load(): String? {
        if (!onWindows) return null
        val encoded = store.getString(ENTRY) ?: return null
        return try {
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size in 32..(MAX_PAYLOAD + 1024))
            val clear = transform(bytes, protect = false)
            require(clear.size in 32..MAX_PAYLOAD)
            String(clear, Charsets.UTF_8)
        } catch (_: Exception) {
            // Changement d'utilisateur Windows / sauvegarde DPAPI non récupérable.
            store.remove(ENTRY)
            null
        }
    }

    @Synchronized
    actual fun clear() {
        if (onWindows) store.remove(ENTRY)
    }
}
