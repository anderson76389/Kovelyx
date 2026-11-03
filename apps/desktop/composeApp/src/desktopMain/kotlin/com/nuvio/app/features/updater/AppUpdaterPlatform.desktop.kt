package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.updates_download_failed
import nuvio.composeapp.generated.resources.updates_download_failed_http
import nuvio.composeapp.generated.resources.updates_downloaded_file_missing
import nuvio.composeapp.generated.resources.updates_empty_download_body
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

private const val desktopUpdaterPreferencesName = "kovelyx_updater"
private const val ignoredTagKey = "ignored_release_tag"
private const val updateChannelKey = "update_channel"

private val desktopUpdaterHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(60))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

actual object AppUpdaterPlatform {
    private val currentOs: DesktopUpdaterOs = DesktopUpdaterOs.current()
    private val store = DesktopStorage.store(desktopUpdaterPreferencesName)
    actual val isDebugBuild: Boolean = false

    private val isPortableWindows: Boolean by lazy {
        if (currentOs != DesktopUpdaterOs.WINDOWS) false else {
            val launcher = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }
                ?: ProcessHandle.current().info().command().orElse(null)
            launcher != null &&
                File(launcher).parentFile?.resolve("Kovelyx.portable")?.isFile == true
        }
    }
    actual val hasSingleUpdateChannel: Boolean = true

    // Linux ships four package formats and the running app is the only place
    // that can tell which one it was installed from, so the format is resolved
    // once and then drives both the asset choice and the install command.
    private val linuxInstallMethod: LinuxInstallMethod by lazy {
        if (currentOs == DesktopUpdaterOs.LINUX) detectLinuxInstallMethod() else LinuxInstallMethod.UNKNOWN
    }

    // A Flatpak cannot install anything for itself: the sandbox has no write
    // access to /app and the manifest grants no talk-name for the host Flatpak
    // service, so the update belongs to the user's store, not to this dialog.
    actual val isSupported: Boolean
        get() = currentOs != DesktopUpdaterOs.UNKNOWN && linuxInstallMethod != LinuxInstallMethod.FLATPAK

    actual val releaseSource: AppUpdateReleaseSource = AppUpdateReleaseSource(
        owner = "anderson76389",
        repo = "Kovelyx",
        channelBranch = null,
        includePrereleases = true,
        userAgent = "Kovelyx-Windows",
    )

    actual val assetSelector: AppUpdateAssetSelector
        get() = if (isPortableWindows) {
            AppUpdateAssetSelector(
                fileExtensions = listOf(".zip"),
                preferredNameFragments = listOf("Kovelyx-Windows-Portable-"),
                requiredNamePrefixes = listOf("Kovelyx-Windows-Portable-"),
            )
        } else {
            currentOs.assetSelector(linuxInstallMethod)
        }

    actual val currentVersionName: String = AppVersionConfig.DESKTOP_VERSION_NAME

    actual fun getSupportedAbis(): List<String> = emptyList()

    actual fun getIgnoredTag(): String? = store.getString(ignoredTagKey)

    actual fun setIgnoredTag(tag: String?) {
        store.putString(ignoredTagKey, tag)
    }

    actual fun getUpdateChannel(): String? = store.getString(updateChannelKey)

    actual fun setUpdateChannel(channel: String) {
        store.putString(updateChannelKey, channel)
    }

    actual fun deleteDownloadedUpdate(path: String) {
        File(path).delete()
    }

    actual suspend fun downloadUpdateAsset(
        assetUrl: String,
        assetName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            clearDir(updatesDir())
            val safeName = assetName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destination = File(updatesDir(), safeName)
            val tempFile = File(updatesDir(), "$safeName.part")

            val releaseAsset = URI(assetUrl)
            check(isOfficialKovelyxReleaseAsset(releaseAsset, safeName)) {
                "Le fichier de mise à jour ne provient pas des Releases officielles de Kovelyx."
            }
            val expectedSha256 = fetchReleaseChecksum(releaseAsset, safeName)
            val digest = MessageDigest.getInstance("SHA-256")
            val request = HttpRequest.newBuilder()
                .uri(releaseAsset)
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build()
            val response = desktopUpdaterHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in 200..299) {
                error(runBlocking { getString(Res.string.updates_download_failed_http, response.statusCode()) })
            }

            val totalBytes = response.headers().firstValue("Content-Length").orElse(null)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            var downloadedBytes = 0L
            try {
                response.body()?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloadedBytes += read.toLong()
                            onProgress(downloadedBytes, totalBytes)
                        }
                        output.flush()
                    }
                } ?: error(runBlocking { getString(Res.string.updates_empty_download_body) })

                if (totalBytes != null && downloadedBytes != totalBytes) {
                    error(runBlocking { getString(Res.string.updates_download_failed) })
                }
                val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                check(checksum.equals(expectedSha256, ignoreCase = true)) {
                    "Empreinte SHA-256 invalide : mise à jour annulée."
                }

                if (!tempFile.renameTo(destination)) {
                    tempFile.copyTo(destination, overwrite = true)
                    tempFile.delete()
                }
                destination.absolutePath
            } catch (t: Throwable) {
                if (tempFile.exists()) tempFile.delete()
                throw t
            }
        }
    }

    actual fun canInstallDownloadedUpdate(): Boolean = true

    actual fun openInstallPermissionSettings() = Unit

    actual fun installDownloadedUpdate(path: String): Result<Unit> = runCatching {
        val updateFile = File(path)
        check(updateFile.exists()) { runBlocking { getString(Res.string.updates_downloaded_file_missing) } }

        val shouldExit = if (isPortableWindows) {
            launchPortableUpdate(updateFile)
            true
        } else {
            launchInstaller(updateFile)
            currentOs == DesktopUpdaterOs.WINDOWS ||
                (currentOs == DesktopUpdaterOs.LINUX && linuxInstallMethod == LinuxInstallMethod.APP_IMAGE)
        }
        if (shouldExit) scheduleAppExit()
    }

    private fun fetchReleaseChecksum(assetUrl: URI, assetName: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI(assetUrl.toASCIIString() + ".sha256"))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val response = desktopUpdaterHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Empreinte de mise à jour indisponible sur GitHub." }
        val line = response.body().trim().lineSequence().firstOrNull().orEmpty()
        val match = Regex("^([A-Fa-f0-9]{64})\\s+\\*?(.*)$").matchEntire(line)
            ?: error("Fichier de vérification de mise à jour invalide.")
        val checksumFilename = match.groupValues[2].removePrefix("./")
        check(checksumFilename == assetName) {
            "La vérification SHA-256 ne correspond pas au fichier de mise à jour."
        }
        return match.groupValues[1]
    }

    private fun updatesDir(): File =
        File(DesktopStorage.rootDir.resolve("updates").also { it.createDirectories() }.toUri())

    private fun clearDir(dirPath: File) {
        if (dirPath.exists() and dirPath.isDirectory) {
            dirPath.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
        }
    }

    private fun launchPortableUpdate(zipFile: File) {
        check(zipFile.name.endsWith(".zip", ignoreCase = true)) {
            "Archive de mise à jour portable invalide."
        }
        ZipFile(zipFile).use { archive ->
            val names = archive.entries().asSequence().map { it.name }.toList()
            check(names.any { it.endsWith("/Kovelyx.portable") } &&
                names.any { it.endsWith("/Kovelyx.exe") } &&
                names.any { it.contains("/runtime/") }) {
                "Archive portable incomplète : mise à jour refusée."
            }
        }
        val launcher = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }
            ?: ProcessHandle.current().info().command().orElse(null)
            ?: error("Lanceur Kovelyx introuvable.")
        val installDir = File(launcher).parentFile
            ?: error("Dossier portable introuvable.")
        check(File(installDir, "Kovelyx.portable").isFile && installDir.canWrite()) {
            "Ce dossier portable ne peut pas être mis à jour automatiquement."
        }
        val resource = AppUpdaterPlatform::class.java.getResourceAsStream(
            "/updater/apply-portable-update.ps1",
        ) ?: error("Script de mise à jour portable manquant.")
        val script = File(updatesDir(), "apply-portable-update.ps1")
        resource.use { input -> script.outputStream().use { output -> input.copyTo(output) } }
        ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", script.absolutePath,
            "-ZipPath", zipFile.absolutePath,
            "-InstallDir", installDir.absolutePath,
            "-AppProcessId", ProcessHandle.current().pid().toString(),
            "-LogPath", File(updatesDir(), "portable-update.log").absolutePath,
        ).start()
    }

    private fun launchInstaller(updateFile: File) {
        // The official MSI is the same Windows product as the EXE installer.
        // Run it after Kovelyx exits so the user does not have to follow a
        // second interactive installation wizard for each update.
        if (currentOs == DesktopUpdaterOs.WINDOWS &&
            updateFile.extension.equals("msi", ignoreCase = true)
        ) {
            launchSilentWindowsInstaller(updateFile)
            return
        }
        val command = when (currentOs) {
            DesktopUpdaterOs.WINDOWS -> windowsInstallerCommand(updateFile)
            DesktopUpdaterOs.MACOS -> listOf("open", updateFile.absolutePath)
            DesktopUpdaterOs.LINUX -> linuxInstallerCommand(
                method = linuxInstallMethod,
                updateFile = updateFile,
                appImagePath = System.getenv(appImageEnvName),
                currentPid = ProcessHandle.current().pid(),
            )
            DesktopUpdaterOs.UNKNOWN -> error("Les mises à jour de Kovelyx ne sont pas disponibles sur ce système.")
        }
        ProcessBuilder(command).start()
    }

    private fun launchSilentWindowsInstaller(msiFile: File) {
        check(msiFile.isFile && msiFile.extension.equals("msi", ignoreCase = true)) {
            "Installateur MSI Kovelyx introuvable."
        }
        val launcher = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }
            ?: ProcessHandle.current().info().command().orElse(null)
            ?: error("Impossible de retrouver le lanceur Kovelyx.")
        check(File(launcher).isFile && File(launcher).name.equals("Kovelyx.exe", ignoreCase = true)) {
            "L'installation silencieuse est réservée à Kovelyx installé sur Windows."
        }

        val resource = AppUpdaterPlatform::class.java.getResourceAsStream(
            "/updater/apply-installed-windows-update.ps1",
        ) ?: error("Assistant de mise à jour Windows introuvable.")
        val script = File(updatesDir(), "apply-installed-windows-update.ps1")
        resource.use { input -> script.outputStream().use { output -> input.copyTo(output) } }

        ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", script.absolutePath,
            "-MsiPath", msiFile.absolutePath,
            "-CurrentLauncher", launcher,
            "-AppProcessId", ProcessHandle.current().pid().toString(),
            "-LogPath", File(updatesDir(), "windows-update.log").absolutePath,
        ).start()
    }

    private fun scheduleAppExit() {
        thread(name = "kovelyx-updater-exit", isDaemon = true) {
            Thread.sleep(500)
            exitProcess(0)
        }
    }
}

private enum class DesktopUpdaterOs {
    WINDOWS,
    MACOS,
    LINUX,
    UNKNOWN;

    fun assetSelector(linuxInstallMethod: LinuxInstallMethod): AppUpdateAssetSelector {
        val archFragments = desktopArchitectureFragments()
        return when (this) {
            WINDOWS -> AppUpdateAssetSelector(
                // Prefer the single-file installer; never accidentally choose Linux/ARM or checksum files.
                fileExtensions = listOf(".exe", ".msi"),
                preferredNameFragments = listOf("Kovelyx-Windows-v") + archFragments + listOf("Kovelyx-Windows-Setup-", "windows"),
                fallbackNameFragments = listOf("universal", "all"),
                requiredNamePrefixes = listOf("Kovelyx-Windows-"),
            )
            MACOS -> AppUpdateAssetSelector(
                fileExtensions = listOf(".dmg", ".pkg"),
                preferredNameFragments = archFragments + listOf("macos", "mac", "darwin"),
                fallbackNameFragments = listOf("universal", "all"),
            )
            LINUX -> AppUpdateAssetSelector(
                fileExtensions = linuxUpdateFileExtensions(linuxInstallMethod),
                preferredNameFragments = archFragments + listOf("linux"),
                fallbackNameFragments = listOf("universal", "all"),
            )
            UNKNOWN -> AppUpdateAssetSelector(fileExtensions = emptyList())
        }
    }

    companion object {
        fun current(): DesktopUpdaterOs {
            val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
            return when {
                osName.contains("win") -> WINDOWS
                osName.contains("mac") -> MACOS
                osName.contains("linux") -> LINUX
                else -> UNKNOWN
            }
        }
    }
}

private fun desktopArchitectureFragments(): List<String> {
    val arch = System.getProperty("os.arch").orEmpty().lowercase(Locale.ROOT)
    return when {
        arch == "aarch64" || arch == "arm64" -> listOf("arm64", "aarch64")
        arch == "x86" || arch == "i386" || arch == "i686" -> listOf("x86", "i386", "i686")
        arch.contains("64") -> listOf("x64", "x86_64", "amd64")
        else -> emptyList()
    }
}

internal fun windowsInstallerCommand(updateFile: File): List<String> {
    if (!updateFile.extension.equals("msi", ignoreCase = true)) {
        return listOf(updateFile.absolutePath)
    }

    return listOf("msiexec", "/i", updateFile.absolutePath)
}

internal enum class LinuxInstallMethod {
    APP_IMAGE,
    FLATPAK,
    RPM,
    DEB,
    UNKNOWN,
}

internal const val appImageEnvName = "APPIMAGE"
private const val flatpakIdEnvName = "FLATPAK_ID"
private const val flatpakInfoPath = "/.flatpak-info"
private const val jpackageAppPathProperty = "jpackage.app-path"
private const val javaExecutableName = "java"
private const val packageQueryTimeoutSeconds = 3L
private const val appImageExitWaitTicks = 100

// A release carries every Linux format at once, so the extension list has to be
// the one the running install can actually consume. An unresolved install keeps
// the previous list, leaving source and tarball builds no worse off than before.
internal fun linuxUpdateFileExtensions(method: LinuxInstallMethod): List<String> = when (method) {
    LinuxInstallMethod.APP_IMAGE -> listOf(".AppImage")
    LinuxInstallMethod.RPM -> listOf(".rpm")
    LinuxInstallMethod.DEB -> listOf(".deb")
    LinuxInstallMethod.FLATPAK -> emptyList()
    LinuxInstallMethod.UNKNOWN -> listOf(".deb", ".AppImage")
}

internal fun resolveLinuxInstallMethod(
    appImagePath: String?,
    appImageExists: Boolean,
    flatpakId: String?,
    flatpakInfoExists: Boolean,
    launcherPath: String?,
    isOwnedByRpm: (String) -> Boolean,
    isOwnedByDpkg: (String) -> Boolean,
): LinuxInstallMethod {
    if (!appImagePath.isNullOrBlank() && appImageExists) return LinuxInstallMethod.APP_IMAGE
    if (!flatpakId.isNullOrBlank() || flatpakInfoExists) return LinuxInstallMethod.FLATPAK

    val path = launcherPath?.takeIf { it.isNotBlank() } ?: return LinuxInstallMethod.UNKNOWN
    if (isOwnedByRpm(path)) return LinuxInstallMethod.RPM
    if (isOwnedByDpkg(path)) return LinuxInstallMethod.DEB
    return LinuxInstallMethod.UNKNOWN
}

private fun detectLinuxInstallMethod(): LinuxInstallMethod {
    val appImagePath = System.getenv(appImageEnvName)
    return resolveLinuxInstallMethod(
        appImagePath = appImagePath,
        appImageExists = !appImagePath.isNullOrBlank() && File(appImagePath).exists(),
        flatpakId = System.getenv(flatpakIdEnvName),
        flatpakInfoExists = File(flatpakInfoPath).exists(),
        launcherPath = linuxLauncherPath(),
        isOwnedByRpm = { path -> packageQuerySucceeds(listOf("rpm", "-qf", path)) },
        isOwnedByDpkg = { path -> packageQuerySucceeds(listOf("dpkg", "-S", path)) },
    )
}

private fun linuxLauncherPath(): String? = linuxLauncherPathFrom(
    jpackageAppPath = System.getProperty(jpackageAppPathProperty),
    processCommand = ProcessHandle.current().info().command().orElse(null),
)

// jpackage records the launcher it started, which is the only path that belongs
// to this app's package. Without it the process command is the JVM itself, and
// the JVM belongs to the distribution's own java package -- asking rpm or dpkg
// who owns that would report a packaged install that is not Nuvio, so a source
// or tarball run is left unresolved instead.
internal fun linuxLauncherPathFrom(
    jpackageAppPath: String?,
    processCommand: String?,
): String? {
    jpackageAppPath?.takeIf { it.isNotBlank() }?.let { return it }

    return processCommand?.takeIf { it.isNotBlank() && File(it).name != javaExecutableName }
}

private fun packageQuerySucceeds(command: List<String>): Boolean = runCatching {
    val process = ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    if (!process.waitFor(packageQueryTimeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching false
    }
    process.exitValue() == 0
}.getOrDefault(false)

internal fun linuxInstallerCommand(
    method: LinuxInstallMethod,
    updateFile: File,
    appImagePath: String?,
    currentPid: Long,
): List<String> {
    if (method == LinuxInstallMethod.APP_IMAGE && !appImagePath.isNullOrBlank()) {
        return listOf("sh", "-c", appImageReplaceScript(updateFile.absolutePath, appImagePath, currentPid))
    }

    return listOf("xdg-open", updateFile.absolutePath)
}

// An AppImage has no installer: updating one means replacing the file that is
// running. The copy waits for this process to exit first, both because the
// running image is still mounted from that file and because a half-written one
// would leave nothing to start. When the image sits somewhere this user cannot
// write, the download is handed to the desktop rather than failing in silence.
internal fun appImageReplaceScript(
    downloadedPath: String,
    appImagePath: String,
    currentPid: Long,
): String {
    val downloaded = singleQuote(downloadedPath)
    val target = singleQuote(appImagePath)
    val stage = singleQuote("$appImagePath.kovelyx-staged-$currentPid")
    val backup = singleQuote("$appImagePath.kovelyx-previous")
    return buildString {
        // Stage an executable in the SAME folder as the target for atomic rename.
        // The existing AppImage remains intact if copying or renaming fails.
        append("if cp -f -- $downloaded $stage && chmod +x -- $stage; then ")
        append("i=0; ")
        append("while [ \$i -lt $appImageExitWaitTicks ] && kill -0 $currentPid 2>/dev/null; do ")
        append("sleep 0.1; i=\$((i+1)); ")
        append("done; ")
        append("if mv -f -- $target $backup; then ")
        append("if mv -f -- $stage $target; then rm -f -- $downloaded; exec $target; ")
        append("else mv -f -- $backup $target; xdg-open $downloaded; fi; ")
        append("else rm -f -- $stage; xdg-open $downloaded; fi; ")
        append("else rm -f -- $stage; xdg-open $downloaded; fi")
    }
}

private fun singleQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"


internal fun isOfficialKovelyxReleaseAsset(uri: URI, name: String): Boolean {
    if (uri.scheme != "https" || uri.host != "github.com" || uri.userInfo != null ||
        uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null
    ) return false
    val prefix = "/anderson76389/Kovelyx/releases/download/"
    val path = uri.path ?: return false
    if (!path.startsWith(prefix)) return false
    val remainder = path.removePrefix(prefix)
    if (remainder.count { it == '/' } != 1) return false
    val version = remainder.substringBefore('/')
    val remoteName = remainder.substringAfter('/')
    return version.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+(?:-(alpha|beta|rc)(?:\\.[0-9]+)?)?")) &&
        remoteName == name &&
        name.startsWith("Kovelyx-") &&
        listOf(".exe", ".msi", ".AppImage", ".deb", ".rpm", ".zip").any {
            name.endsWith(it, ignoreCase = true)
        }
}
