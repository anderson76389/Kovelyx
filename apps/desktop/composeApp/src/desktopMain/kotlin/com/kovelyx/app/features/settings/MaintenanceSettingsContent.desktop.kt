package com.kovelyx.app.features.settings

import androidx.compose.runtime.Composable
import com.kovelyx.app.core.auth.AuthRepository
import com.kovelyx.app.core.auth.AuthState
import com.kovelyx.app.core.cloud.CloudflareConfig
import com.kovelyx.app.core.cloud.CloudflareDataSync
import com.kovelyx.app.core.storage.DesktopStorage
import com.kovelyx.maintenance.*
import com.kovelyx.maintenance.ui.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal object DesktopMaintenance {
    private val root get() = DesktopStorage.rootDir.toFile().canonicalFile
    private val cache get() = DesktopStorage.cacheDir.toFile().canonicalFile
    private val workspace get() = File(root, "maintenance")
    fun engine() = LocalMaintenance(workspace,
        mapOf("preferences" to root, "profile-media" to File(root, "profile-media"),
            "cloud-media" to File(root, "cloud-profile-media")),
        { type, path -> when (type) {
            "preferences" -> '/' !in path && path.endsWith(".properties") && maintenancePreferenceAllowed(path)
            "profile-media", "cloud-media" -> '/' !in path && path.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "webm", "heic", "heif", "avif")
            else -> false
        } },
        mapOf("images" to File(cache, "image_cache"), "gifs" to File(cache, "gif-cache"),
            "torrent" to File(cache, "nuvio-engine/payload")))
    private fun account(): String = when (val auth = AuthRepository.state.value) {
        is AuthState.Authenticated -> if (auth.isAnonymous) "guest" else CloudflareConfig.URL + "|" + auth.userId
        AuthState.Unauthenticated -> "guest"
        else -> error("Compte en cours de chargement")
    }
    private fun scope() = maintenanceScope("desktop", account())
    private fun identity(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val session = File(System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home"), "Kovelyx/cloud-session-v1.bin")
        val files = root.listFiles().orEmpty().filter { it.name.endsWith(".properties") && (it.name.contains("auth", true) || it.name.contains("session", true)) } + session
        files.sortedBy { it.path }.filter(File::isFile).forEach { file ->
            digest.update(file.name.toByteArray())
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun startup() {
        val result = engine().startup(identity())
        if (result != null) File(workspace.apply { mkdirs() }, "notice").writeText(result)
    }
    private suspend fun choose(save: Boolean): File? = suspendCancellableCoroutine { continuation ->
        SwingUtilities.invokeLater {
            if (!continuation.isActive) return@invokeLater
            val picker = JFileChooser().apply {
                dialogTitle = if (save) "Créer une sauvegarde Kovelyx" else "Restaurer une sauvegarde Kovelyx"
                fileFilter = FileNameExtensionFilter("Sauvegarde Kovelyx chiffrée", "kovbackup")
                if (save) selectedFile = File("Kovelyx-${System.currentTimeMillis()}.kovbackup")
            }
            val result = if (save) picker.showSaveDialog(null) else picker.showOpenDialog(null)
            var file = picker.selectedFile?.canonicalFile?.takeIf { result == JFileChooser.APPROVE_OPTION }
            if (save && file != null && !file.name.endsWith(".kovbackup", true)) file = File(file.parentFile, file.name + ".kovbackup")
            if (save && file?.exists() == true && JOptionPane.showConfirmDialog(null, "Remplacer cette sauvegarde ?", "Kovelyx", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) file = null
            if (continuation.isActive) continuation.resume(file)
        }
    }
    suspend fun report(): MaintenanceReport = withContext(Dispatchers.IO) {
        val engine = engine()
        val sizes = engine.cacheSizes()
        val updates = engine.bytes(File(root, "updates"))
        val maintenance = engine.bytes(workspace)
        val data = (engine.bytes(root) - updates - maintenance).coerceAtLeast(0)
        val installed = System.getProperty("compose.application.resources.dir")?.let(::File)?.parentFile
        MaintenanceReport(listOfNotNull(
            installed?.takeIf { it.isDirectory }?.let { StorageLine("Application (fichiers du paquet)", engine.bytes(it.canonicalFile)) },
            StorageLine("Données locales", data),
            StorageLine("Cache d'images", sizes.getValue("images"), "images"),
            StorageLine("Cache GIF", sizes.getValue("gifs"), "gifs"),
            StorageLine("Cache torrent", sizes.getValue("torrent"), "torrent"),
            StorageLine("Installateurs temporaires (nettoyage automatique)", updates),
            StorageLine("Autres fichiers temporaires", (engine.bytes(cache) - sizes.values.sum()).coerceAtLeast(0)),
            StorageLine("Sauvegardes en attente et état de maintenance", maintenance),
        ), engine.hasPendingRestore(), File(workspace, "notice").takeIf { it.isFile }?.readText())
    }
    suspend fun export(password: CharArray): String? {
        val initialScope = scope()
        val file = choose(true) ?: return null
        return withContext(Dispatchers.IO) {
            require(scope() == initialScope)
            val temporary = Files.createTempFile(file.parentFile.toPath(), ".kovelyx-backup-", ".tmp")
            try {
                Files.newOutputStream(temporary).use { engine().export(it, password, initialScope) }
                require(scope() == initialScope)
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                "Sauvegarde chiffrée enregistrée"
            } finally { Files.deleteIfExists(temporary) }
        }
    }
    suspend fun restore(password: CharArray): String? {
        val initialScope = scope()
        val file = choose(false) ?: return null
        return withContext(Dispatchers.IO) {
            require(scope() == initialScope)
            val identity = identity()
            file.inputStream().use { engine().stage(it, password, initialScope, identity) }
            if (scope() != initialScope || identity != identity()) {
                engine().cancelRestore(); error("Le compte a changé")
            }
            "Sauvegarde validée. Ferme complètement Kovelyx, puis relance l'application."
        }
    }
}

@Composable
internal actual fun MaintenanceSettingsContent() {
    MaintenancePanel(readStorage = DesktopMaintenance::report, readSync = { CloudflareDataSync.diagnosticText() },
        export = DesktopMaintenance::export, restore = DesktopMaintenance::restore,
        clear = { key -> withContext(Dispatchers.IO) { DesktopMaintenance.engine().scheduleCacheClear(key) } },
        cancelRestore = { withContext(Dispatchers.IO) { DesktopMaintenance.engine().cancelRestore() } })
}
