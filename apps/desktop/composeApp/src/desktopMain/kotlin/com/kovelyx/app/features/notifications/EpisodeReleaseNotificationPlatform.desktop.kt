package com.kovelyx.app.features.notifications

import com.kovelyx.app.core.storage.DesktopStorage
import java.awt.SystemTray
import java.awt.TrayIcon
import java.time.LocalDateTime
import javax.imageio.ImageIO
import javax.swing.Timer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** System notifications while Kovelyx is open, including when minimized. */
internal actual object EpisodeReleaseNotificationPlatform {
    private var tray: TrayIcon? = null
    private var open: (String?) -> Unit = {}
    private var pendingLink: String? = null
    private var owner: String? = null
    private var profile = -1
    private var requests = emptyList<EpisodeReleaseNotificationRequest>()
    private val delivered by lazy { DesktopStorage.store("kovelyx_episode_notifications_delivered") }
    private val timer = Timer(30_000) { deliverDue() }

    fun bind(onOpen: (String?) -> Unit) { open = onOpen }
    fun close() {
        timer.stop()
        tray?.let { SystemTray.getSystemTray().remove(it) }
        tray = null
        requests = emptyList()
        open = {}
    }
    private fun ensureTray(): Boolean {
        if (tray != null) return true
        if (!SystemTray.isSupported()) return false
        val image = javaClass.classLoader.getResourceAsStream("icons/kovelyx.png")?.use { ImageIO.read(it) }
            ?: return false
        return runCatching {
            TrayIcon(image, "Kovelyx").also { icon ->
                icon.isImageAutoSize = true
                icon.addActionListener { open(pendingLink) }
                SystemTray.getSystemTray().add(icon)
                tray = icon
            }
            true
        }.getOrDefault(false)
    }
    actual suspend fun openSystemSettings(): Boolean = withContext(Dispatchers.IO) {
        if (!System.getProperty("os.name").startsWith("Windows", true)) return@withContext false
        runCatching { ProcessBuilder("explorer.exe", "ms-settings:notifications").start(); true }.getOrDefault(false)
    }
    actual suspend fun notificationsAuthorized(): Boolean = withContext(Dispatchers.Main.immediate) {
        SystemTray.isSupported()
    }
    actual suspend fun requestAuthorization(): Boolean = withContext(Dispatchers.Main.immediate) { ensureTray() }
    actual suspend fun scheduleEpisodeReleaseNotifications(requests: List<EpisodeReleaseNotificationRequest>) {
        withContext(Dispatchers.Main.immediate) {
            check(ensureTray()) { "Les notifications système ne sont pas disponibles." }
            owner = (com.kovelyx.app.core.auth.AuthRepository.state.value as? com.kovelyx.app.core.auth.AuthState.Authenticated)?.userId
            profile = com.kovelyx.app.features.profiles.ProfileRepository.activeProfileId
            this@EpisodeReleaseNotificationPlatform.requests = requests
            deliverDue()
            timer.start()
        }
    }
    actual suspend fun clearScheduledEpisodeReleaseNotifications() {
        withContext(Dispatchers.Main.immediate) { timer.stop(); requests = emptyList() }
    }
    actual suspend fun showTestNotification(request: EpisodeReleaseNotificationRequest) {
        withContext(Dispatchers.Main.immediate) {
            check(ensureTray()) { "Les notifications système ne sont pas disponibles." }
            show(request)
        }
    }
    private fun show(request: EpisodeReleaseNotificationRequest) {
        pendingLink = request.deepLinkUrl
        checkNotNull(tray).displayMessage(request.notificationTitle, request.notificationBody, TrayIcon.MessageType.INFO)
    }
    private fun deliverDue() {
        if ((com.kovelyx.app.core.auth.AuthRepository.state.value as? com.kovelyx.app.core.auth.AuthState.Authenticated)?.userId != owner ||
            com.kovelyx.app.features.profiles.ProfileRepository.activeProfileId != profile) return
        dueEpisodeReleaseRequests(requests, LocalDateTime.now()).forEach { request ->
            if (delivered.getString("$owner:${request.requestId}") == "sent") return@forEach
            show(request)
            delivered.putString("$owner:${request.requestId}", "sent")
        }
    }
}

internal fun dueEpisodeReleaseRequests(
    requests: List<EpisodeReleaseNotificationRequest>, now: LocalDateTime,
): List<EpisodeReleaseNotificationRequest> = requests.filter { request ->
    runCatching {
        val date = java.time.LocalDate.parse(request.releaseDateIso)
        date == now.toLocalDate() && !now.toLocalTime().isBefore(java.time.LocalTime.of(EpisodeReleaseNotificationHour, EpisodeReleaseNotificationMinute))
    }.getOrDefault(false)
}.distinctBy { it.requestId }
