package com.kovelyx.tv.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val updateFilesMutex = Mutex()

    @Suppress("DEPRECATION")
    suspend fun cleanInstalledUpdateFiles(context: Context): Long = updateFilesMutex.withLock {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        UpdateCacheMaintenance.clean(File(context.cacheDir, "updates"), isAlreadyInstalled = { file ->
            val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (archive?.packageName == context.packageName) version(archive) <= version(installed) else null
        })
    }

    suspend fun download(
        url: String,
        destinationFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): Result<File> = updateFilesMutex.withLock {
        runCatching {
            // All published Kovelyx APKs have a matching GitHub .sha256 sidecar.
            // Verify it BEFORE opening Android Package Installer.
            val expectedSha256 = downloadChecksum(url, destinationFile.name)
            destinationFile.parentFile?.mkdirs()
            val temporary = File(destinationFile.parentFile, destinationFile.name + ".part")
            temporary.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Téléchargement Kovelyx refusé (HTTP ${response.code})." }
                    val body = response.body ?: error("Le fichier de mise à jour est vide.")
                    val total = body.contentLength().takeIf { it > 0L }
                    var downloaded = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var lastProgressBytes = 0L
                            val progressStepBytes = maxOf(1024L * 1024L, (total ?: 0L) / 100L)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                currentCoroutineContext().ensureActive()
                                if (read < 0) break
                                if (read == 0) continue
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                downloaded += read
                                if (downloaded - lastProgressBytes >= progressStepBytes) {
                                    lastProgressBytes = downloaded
                                    onProgress(downloaded, total)
                                }
                            }
                            if (downloaded > lastProgressBytes) onProgress(downloaded, total)
                            output.flush()
                        }
                    }
                    check(downloaded > 0 && (total == null || downloaded == total)) {
                        "Téléchargement incomplet : aucun APK ne sera installé."
                    }
                }
                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                check(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    "La vérification SHA-256 de l'APK Kovelyx a échoué."
                }
                if (destinationFile.exists()) destinationFile.delete()
                if (!temporary.renameTo(destinationFile)) {
                    temporary.copyTo(destinationFile, overwrite = true)
                    temporary.delete()
                }
                val finalApk = if (VerifiedAndroidPlatformBundle.isBundleName(destinationFile.name)) {
                    try {
                        VerifiedAndroidPlatformBundle.extractTvApk(
                            destinationFile, destinationFile.parentFile
                        )
                    } catch (error: Throwable) {
                        destinationFile.delete()
                        throw error
                    }.also { destinationFile.delete() }
                } else destinationFile
                // Only remove older disposable updates after the selected APK has
                // been checked; downloaded films and account data are untouched.
                finalApk.parentFile?.listFiles()?.forEach { old ->
                    if (old != finalApk && old.isFile &&
                        ((old.name.startsWith("Kovelyx-TV-") &&
                          old.name.endsWith(".apk", ignoreCase = true)) ||
                          VerifiedAndroidPlatformBundle.isBundleName(old.name))
                    ) old.delete()
                }
                finalApk
            } finally {
                temporary.delete()
            }
        }
    }

    private fun downloadChecksum(url: String, fileName: String): String {
        val downloadUri = java.net.URI(url)
        require(downloadUri.scheme == "https" &&
            downloadUri.host == "github.com" &&
            downloadUri.path.startsWith("/anderson76389/Kovelyx/releases/download/")) {
            "Cette mise à jour ne vient pas des Releases officielles Kovelyx."
        }
        if (VerifiedAndroidPlatformBundle.isBundleName(fileName)) {
            return fetchGitHubBundleDigest(downloadUri, fileName)
        }
        val request = Request.Builder().url(url + ".sha256").get().build()
        val line = okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Vérification SHA-256 indisponible (HTTP ${response.code})." }
            response.body?.string()?.trim()?.lineSequence()?.firstOrNull().orEmpty()
        }
        val match = Regex("^([a-fA-F0-9]{64})\\s+\\*?(.+)$").matchEntire(line)
            ?: error("Empreinte SHA-256 invalide.")
        check(match.groupValues[2].removePrefix("./") == fileName) {
            "L'empreinte SHA-256 ne correspond pas au fichier sélectionné."
        }
        return match.groupValues[1]
    }

    private fun fetchGitHubBundleDigest(uri: java.net.URI, name: String): String {
        val suffix = uri.path.removePrefix("/anderson76389/Kovelyx/releases/download/")
        val tag = suffix.substringBefore("/")
        require(name == "Kovelyx-Android-$tag.zip" && suffix.substringAfter("/") == name) {
            "Le pack Android ne correspond pas à la version sélectionnée."
        }
        val request = Request.Builder()
            .url("https://api.github.com/repos/anderson76389/Kovelyx/releases/tags/$tag")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Kovelyx-TV-Updater")
            .get().build()
        val responseData = okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Empreinte officielle du pack indisponible." }
            response.body?.string() ?: error("Réponse GitHub vide.")
        }
        val assets = JSONObject(responseData).getJSONArray("assets")
        val matches = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter { it.optString("name") == name }
        check(matches.size == 1) { "Pack Android absent ou dupliqué." }
        val digest = matches.single().optString("digest")
        check(digest.matches(Regex("sha256:[a-fA-F0-9]{64}"))) {
            "Empreinte SHA-256 officielle manquante."
        }
        return digest.removePrefix("sha256:")
    }
}
