package com.kovelyx.maintenance.android

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kovelyx.maintenance.*
import com.kovelyx.maintenance.ui.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AndroidMaintenance {
    private fun workspace(context: Context) = File(context.noBackupFilesDir.canonicalFile, "maintenance")
    fun engine(context: Context): LocalMaintenance = LocalMaintenance(
        workspace(context),
        mapOf("preferences" to File(File(context.applicationInfo.dataDir).canonicalFile, "shared_prefs"),
            "datastore" to File(context.filesDir.canonicalFile, "datastore"),
            "mobile-media" to File(context.filesDir.canonicalFile, "kovelyx-profile-media"),
            "tv-media" to File(context.filesDir.canonicalFile, "kovelyx-tv-profile-backgrounds"),
            "cloud-media" to File(context.filesDir.canonicalFile, "kovelyx-cloud-profile-media")),
        { root, path -> when (root) {
            "preferences" -> '/' !in path && path.endsWith(".xml") && maintenancePreferenceAllowed(path)
            "datastore" -> '/' !in path && path.endsWith(".preferences_pb") && maintenancePreferenceAllowed(path)
            "mobile-media", "tv-media", "cloud-media" -> '/' !in path && path.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "webm", "heic", "heif", "avif")
            else -> false
        } },
        mapOf("images" to File(context.cacheDir.canonicalFile, "image_cache"),
            "video" to File(context.cacheDir.canonicalFile, "nuvio_vod_cache"),
            "torrent" to File(context.cacheDir.canonicalFile, "nuvio-engine/payload")),
        isSymbolicLink = { it.absoluteFile != it.canonicalFile },
    )
    // Changes to any account/session file between staging and process restart reject the restore.
    fun identity(context: Context): String {
        val digest = MessageDigest.getInstance("SHA-256")
        File(File(context.applicationInfo.dataDir).canonicalFile, "shared_prefs").listFiles().orEmpty()
            .filter { it.name.endsWith(".xml") && !maintenancePreferenceAllowed(it.name) &&
                (it.name.contains("auth", true) || it.name.contains("session", true)) }
            .sortedBy { it.name }.forEach { file ->
                digest.update(file.name.toByteArray()); file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
                }
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun startup(context: Context) {
        val instance = engine(context)
        if (instance.hasPendingRestore()) {
            // SharedPreferences normally prefers its .bak after a killed write. Recover it
            // before taking rollback copies, without initializing an in-memory preferences cache.
            val directory = File(File(context.applicationInfo.dataDir).canonicalFile, "shared_prefs")
            directory.listFiles().orEmpty().filter { it.name.endsWith(".xml.bak") && maintenancePreferenceAllowed(it.name.removeSuffix(".bak")) }.forEach { backup ->
                val target = File(directory, backup.name.removeSuffix(".bak"))
                require(backup.absoluteFile == backup.canonicalFile && target.absoluteFile == target.canonicalFile)
                check(backup.renameTo(target)) { "Récupération des préférences interrompues impossible" }
            }
        }
        val result = instance.startup(identity(context))
        if (result != null) File(workspace(context).apply { mkdirs() }, "notice").writeText(result)
    }
    private fun flushPreferences(context: Context) {
        File(File(context.applicationInfo.dataDir).canonicalFile, "shared_prefs").listFiles().orEmpty()
            .map { it.name.removeSuffix(".bak") }.filter { it.endsWith(".xml") && maintenancePreferenceAllowed(it) }.distinct()
            .forEach { check(context.getSharedPreferences(it.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().commit()) }
    }
    suspend fun report(context: Context): MaintenanceReport = withContext(Dispatchers.IO) {
        val engine = engine(context)
        val cached = engine.cacheSizes()
        val caches = engine.bytes(context.cacheDir.canonicalFile)
        val apk = (listOf(context.applicationInfo.sourceDir) + context.applicationInfo.splitSourceDirs.orEmpty())
            .sumOf { engine.bytes(File(it).canonicalFile) }
        MaintenanceReport(listOf(
            StorageLine("Application (APK)", apk),
            StorageLine("Données locales", engine.bytes(context.filesDir.canonicalFile) + engine.bytes(File(File(context.applicationInfo.dataDir).canonicalFile, "shared_prefs"))),
            StorageLine("Cache d'images", cached.getValue("images"), "images"),
            StorageLine("Cache vidéo", cached.getValue("video"), "video"),
            StorageLine("Cache torrent", cached.getValue("torrent"), "torrent"),
            StorageLine("Installateurs temporaires (nettoyage automatique)", engine.bytes(File(context.cacheDir.canonicalFile, "updates"))),
            StorageLine("Autres fichiers temporaires", (caches - cached.values.sum() - engine.bytes(File(context.cacheDir.canonicalFile, "updates"))).coerceAtLeast(0)),
            StorageLine("Sauvegardes en attente et état de maintenance", engine.bytes(workspace(context))),
        ), engine.hasPendingRestore(), File(workspace(context), "notice").takeIf { it.isFile }?.readText())
    }
    suspend fun export(context: Context, uri: Uri, password: CharArray, account: () -> String): String = withContext(Dispatchers.IO) {
        flushPreferences(context)
        val scope = maintenanceScope(context.packageName, account())
        val temp = File(workspace(context).apply { mkdirs() }, "export.kovbackup")
        try {
            temp.outputStream().use { engine(context).export(it, password, scope) }
            require(scope == maintenanceScope(context.packageName, account())) { "Le compte a changé" }
            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { out -> temp.inputStream().use { it.copyTo(out, 65536) } }
            "Sauvegarde chiffrée enregistrée"
        } finally { temp.delete() }
    }
    suspend fun restore(context: Context, uri: Uri, password: CharArray, account: () -> String): String = withContext(Dispatchers.IO) {
        flushPreferences(context)
        val scope = maintenanceScope(context.packageName, account())
        val identity = identity(context)
        checkNotNull(context.contentResolver.openInputStream(uri)).use { engine(context).stage(it, password, scope, identity) }
        if (scope != maintenanceScope(context.packageName, account()) || identity != identity(context)) {
            engine(context).cancelRestore(); error("Le compte a changé")
        }
        "Sauvegarde validée. Ferme complètement Kovelyx, puis relance l'application."
    }
}

@Composable
fun AndroidMaintenancePanel(account: () -> String, readSync: suspend () -> String) {
    val context = LocalContext.current.applicationContext
    var waiter by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { waiter?.complete(it) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { waiter?.complete(it) }
    MaintenancePanel(
        readStorage = { AndroidMaintenance.report(context) }, readSync = readSync,
        export = { password ->
            val response = CompletableDeferred<Uri?>(); waiter = response
            try {
                create.launch("Kovelyx-${System.currentTimeMillis()}.kovbackup")
                response.await()?.let { AndroidMaintenance.export(context, it, password, account) }
            } finally { waiter = null; response.cancel() }
        },
        restore = { password ->
            val response = CompletableDeferred<Uri?>(); waiter = response
            try {
                open.launch(arrayOf("application/octet-stream", "application/x-kovelyx-backup", "*/*"))
                response.await()?.let { AndroidMaintenance.restore(context, it, password, account) }
            } finally { waiter = null; response.cancel() }
        },
        clear = { key -> withContext(Dispatchers.IO) { AndroidMaintenance.engine(context).scheduleCacheClear(key) } },
        cancelRestore = { withContext(Dispatchers.IO) { AndroidMaintenance.engine(context).cancelRestore() } },
    )
}
