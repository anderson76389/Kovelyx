package com.nuvio.app.features.updater

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.updates_download_failed_http
import nuvio.composeapp.generated.resources.updates_downloaded_file_missing
import nuvio.composeapp.generated.resources.updates_empty_download_body
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AndroidAppUpdaterPlatform {
    private const val preferencesName = "nuvio_updater"
    private const val ignoredTagKey = "ignored_release_tag"
    private const val updateChannelKey = "update_channel"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getSupportedAbis(): List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()

    fun isDebugBuild(): Boolean {
        val context = appContext ?: return false
        return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    fun getIgnoredTag(): String? =
        preferences().getString(ignoredTagKey, null)

    fun setIgnoredTag(tag: String?) {
        preferences().edit().apply {
            if (tag == null) remove(ignoredTagKey) else putString(ignoredTagKey, tag)
        }.apply()
    }

    fun getUpdateChannel(): String? = preferences().getString(updateChannelKey, null)

    fun setUpdateChannel(channel: String) {
        preferences().edit().putString(updateChannelKey, channel).apply()
    }

    fun deleteDownloadedUpdate(path: String) {
        File(path).delete()
    }

    suspend fun downloadUpdateAsset(
        assetUrl: String,
        assetName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val context = requireContext()
            val safeName = assetName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val expectedSha256 = fetchReleaseChecksum(assetUrl, safeName)
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val destination = File(dir, safeName)
            val temporary = File(dir, "$safeName.part")
            temporary.delete()
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            try {
                val request = Request.Builder().url(assetUrl).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error(runBlocking { getString(Res.string.updates_download_failed_http, response.code) })
                    }
                    val body = response.body ?: error(runBlocking { getString(Res.string.updates_empty_download_body) })
                    val total = body.contentLength().takeIf { it > 0L }
                    var downloaded = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                currentCoroutineContext().ensureActive()
                                if (read < 0) break
                                if (read == 0) continue
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                downloaded += read.toLong()
                                onProgress(downloaded, total)
                            }
                            output.flush()
                        }
                    }
                    check(downloaded > 0 && (total == null || downloaded == total)) {
                        "Téléchargement incomplet : l’installation ne sera pas lancée."
                    }
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                check(sha256.equals(expectedSha256, ignoreCase = true)) {
                    "Empreinte SHA-256 différente de la Release officielle Kovelyx."
                }
                if (destination.exists()) destination.delete()
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                destination.absolutePath
            } finally {
                temporary.delete()
            }
        }
    }

    private fun fetchReleaseChecksum(assetUrl: String, safeName: String): String {
        val uri = java.net.URI(assetUrl)
        require(uri.scheme == "https" && uri.host == "github.com" &&
            uri.path.startsWith("/anderson76389/Kovelyx/releases/download/")) {
            "Fichier non officiel : cette mise à jour ne vient pas des Releases Kovelyx."
        }
        val request = Request.Builder().url(assetUrl + ".sha256").get().build()
        val line = httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Empreinte SHA-256 indisponible (HTTP ${response.code})." }
            response.body?.string()?.trim()?.lineSequence()?.firstOrNull().orEmpty()
        }
        val match = Regex("^([a-fA-F0-9]{64})\\s+\\*?(.+)$").matchEntire(line)
            ?: error("Empreinte SHA-256 invalide.")
        check(match.groupValues[2].removePrefix("./") == safeName) {
            "La vérification SHA-256 ne correspond pas à l’APK sélectionné."
        }
        return match.groupValues[1]
    }
    fun canRequestPackageInstalls(): Boolean {
        val context = appContext ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: SecurityException) {
            
                true
            }
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installDownloadedUpdate(path: String): Result<Unit> = runCatching {
        val context = requireContext()
        val apkFile = File(path)
        check(apkFile.exists()) { runBlocking { getString(Res.string.updates_downloaded_file_missing) } }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    private fun preferences() = requireContext().getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private fun requireContext(): Context =
        requireNotNull(appContext) { "AndroidAppUpdaterPlatform.initialize must be called before use." }
}
