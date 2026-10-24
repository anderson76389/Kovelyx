package com.nuvio.tv.updater

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun download(
        url: String,
        destinationFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): Result<File> = runCatching {
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
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
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
            destinationFile
        } finally {
            temporary.delete()
        }
    }

    private fun downloadChecksum(url: String, fileName: String): String {
        val downloadUri = java.net.URI(url)
        require(downloadUri.scheme == "https" &&
            downloadUri.host == "github.com" &&
            downloadUri.path.startsWith("/anderson76389/Kovelyx/releases/download/")) {
            "Cette mise à jour ne vient pas des Releases officielles Kovelyx."
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
}
