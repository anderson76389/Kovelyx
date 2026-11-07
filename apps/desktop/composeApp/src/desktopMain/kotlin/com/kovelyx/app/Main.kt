package com.kovelyx.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.configureSwingGlobalsForCompose
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.kovelyx.app.core.build.KovelyxIntegrationCredentials
import com.kovelyx.app.core.storage.DesktopStorage
import com.kovelyx.app.core.deeplink.handleAppUrl
import com.kovelyx.app.core.diagnostics.SentryInitializer
import com.kovelyx.app.core.ui.NuvioTheme
import com.kovelyx.app.core.ui.ProvideDesktopWindowInsets
import com.kovelyx.app.features.discordrpc.DiscordPresenceManager
import com.kovelyx.app.features.p2p.P2pStreamingEngine
import com.kovelyx.app.features.plugins.configureDesktopQuickJsLibrary
import com.kovelyx.app.features.player.PlatformPlayerSurface
import com.kovelyx.app.features.player.desktop.DesktopAppFullscreenController
import com.kovelyx.app.features.player.desktop.DesktopHostOs
import com.kovelyx.app.features.player.desktop.DesktopWindowGeometry
import com.kovelyx.app.features.player.desktop.DesktopWindowModeStorage
import com.kovelyx.app.features.player.desktop.NativePlayerBridge
import com.kovelyx.app.features.player.desktop.applyNativeDesktopWindowChrome
import com.kovelyx.app.features.player.desktop.configureMacosWindowBeforePeer
import com.kovelyx.app.features.player.desktop.installDesktopAppFullscreenShortcuts
import com.kovelyx.app.features.player.desktop.registerDesktopAppFullscreenToggle
import com.kovelyx.app.features.player.desktop.trackMaximizedBoundsForCurrentScreen
import com.kovelyx.app.features.profiles.ProfileRepository
import com.kovelyx.app.features.settings.AppIconOption
import com.kovelyx.app.features.settings.AppIconRepository
import com.kovelyx.app.features.settings.applyDesktopRendererPreference
import com.kovelyx.app.features.settings.transparentPreviewResource
import java.awt.Desktop
import java.net.URI
import javax.imageio.ImageIO
import java.awt.Color as AwtColor
import javax.swing.JComponent

private val KovelyxDesktopNativeBackground = AwtColor(0x0D, 0x0D, 0x0D)
private const val MacosDarkAquaAppearance = "NSAppearanceNameDarkAqua"

// Use the Windows system browser for all external authorization and website links.
private val kovelyxDesktopUriHandler = object : UriHandler {
    override fun openUri(uri: String) {
        val target = URI(uri.trim())
        val scheme = target.scheme?.lowercase()
        require(scheme == "https" || scheme == "http" || scheme == "mailto") {
            "Protocole de lien externe non autorisé"
        }
        val openedByDesktop = runCatching {
            if (!Desktop.isDesktopSupported()) return@runCatching false
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return@runCatching false
            desktop.browse(target)
            true
        }.getOrDefault(false)
        if (openedByDesktop) return
        if (DesktopHostOs.current == DesktopHostOs.WINDOWS && (scheme == "https" || scheme == "http")) {
            ProcessBuilder("rundll32.exe", "url.dll,FileProtocolHandler", target.toString()).start()
            return
        }
        throw IllegalStateException("Impossible d'ouvrir le navigateur par défaut")
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val instance = DesktopSingleInstance(DesktopStorage.rootDir.resolve("launch-mailbox"))
    if (!instance.acquireOrForward(args) { forwarded ->
        java.awt.EventQueue.invokeLater {
            handleDesktopLaunchArgs(forwarded)
            java.awt.Window.getWindows().firstOrNull { it.isDisplayable && it.isVisible }?.let { existing ->
                if (existing is java.awt.Frame) existing.extendedState = existing.extendedState and java.awt.Frame.ICONIFIED.inv()
                existing.toFront()
                existing.requestFocus()
            }
        }
    }) return
    Runtime.getRuntime().addShutdownHook(Thread { instance.close() })
    DesktopCollectionImportBridge.start()
    DesktopCollectionImportBridge.registerWindowsProtocol()

    // On Linux, initialize GTK BEFORE AWT/Compose/Skia to prevent GdkDisplayManager
    // type registration conflict (Skiko partially loads GDK without full GTK init).
    if (System.getProperty("os.name", "").lowercase().contains("linux")) {
        runCatching { NativePlayerBridge.initGtkEarly() }
    }
    // Load personal OAuth application keys before initializing integrations.
    // Nothing is uploaded to the repository, and the app can run without keys.
    val integrationStore = DesktopStorage.store("kovelyx_integrations")
    KovelyxIntegrationCredentials.initializeDesktop(
        clientId = integrationStore.getString("trakt_client_id").orEmpty(),
        clientSecret = integrationStore.getString("trakt_client_secret").orEmpty(),
        mdbListId = integrationStore.getString("mdblist_client_id").orEmpty(),
        onSave = { traktId, traktSecret, mdbListId ->
            integrationStore.putString("trakt_client_id", traktId)
            integrationStore.putString("trakt_client_secret", traktSecret)
            integrationStore.putString("mdblist_client_id", mdbListId)
        },
    )
    applyDesktopRendererPreference()
    SentryInitializer.start()
    configureDesktopQuickJsLibrary()
    configureDesktopChrome()
    configureLinuxSwingGlobalsBeforeAwt()
    installDesktopOpenUriHandler()
    handleDesktopLaunchArgs(args)
    // Load cached profile data synchronously so the profile color is available
    // on the very first Compose frame (matching Android's SharedPreferences behavior).
    ProfileRepository.loadCachedProfiles()
    AppIconRepository.ensureLoaded()
    DiscordPresenceManager.start()

    application {
        val appIconState by AppIconRepository.state.collectAsState()
        val smokePlayerUrl = (
            System.getProperty("nuvio.desktop.smokePlayerUrl")
                ?: System.getenv("NUVIO_DESKTOP_SMOKE_PLAYER_URL")
            )
            ?.takeIf { it.isNotBlank() }
        val wasFullscreenOnLastExit = remember { DesktopWindowModeStorage.loadWasFullscreen() }
        val wasMaximizedOnLastExit = remember { DesktopWindowModeStorage.loadWasMaximized() }
        val savedGeometry = remember { DesktopWindowModeStorage.loadWindowedGeometry() }
        val restoresMaximizedWindowPlacement = DesktopHostOs.current != DesktopHostOs.MACOS
        val initialPlacement = when {
            wasFullscreenOnLastExit && DesktopHostOs.current != DesktopHostOs.WINDOWS -> {
                WindowPlacement.Fullscreen
            }
            wasMaximizedOnLastExit == false && savedGeometry != null -> {
                WindowPlacement.Floating
            }
            restoresMaximizedWindowPlacement -> {
                WindowPlacement.Maximized
            }
            else -> WindowPlacement.Floating
        }
        val isStartingMaximizedOrFullscreen =
            initialPlacement == WindowPlacement.Maximized || initialPlacement == WindowPlacement.Fullscreen
        val maxScreenBounds = remember {
            runCatching {
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            }.getOrNull()
        }
        val initialWidth = when {
            isStartingMaximizedOrFullscreen && maxScreenBounds != null -> maxScreenBounds.width.dp
            savedGeometry != null -> savedGeometry.width.dp
            else -> 1280.dp
        }
        val initialHeight = when {
            isStartingMaximizedOrFullscreen && maxScreenBounds != null -> maxScreenBounds.height.dp
            savedGeometry != null -> savedGeometry.height.dp
            else -> 820.dp
        }
        val windowState = rememberWindowState(
            width = initialWidth,
            height = initialHeight,
            position = savedGeometry?.let { WindowPosition.Absolute(x = it.x.dp, y = it.y.dp) }
                ?: WindowPosition.PlatformDefault,
            // Windows fullscreen is emulated natively (see DesktopAppFullscreenController)
            // rather than driven by WindowPlacement, so it's restored separately below.
            placement = initialPlacement,
        )
        val fullscreenController = remember { DesktopAppFullscreenController() }

        SwingWindow(
            onCloseRequest = {
                DesktopCollectionImportBridge.stop()
                P2pStreamingEngine.shutdown()
                DiscordPresenceManager.shutdown()
                SentryInitializer.close()
                exitApplication()
            },
            title = if (smokePlayerUrl == null) "Kovelyx" else "Kovelyx Player Smoke",
            state = windowState,
            // Compose needs a typed DrawableResource here. The Windows-specific
            // Kovelyx icon is loaded from the packaged PNG in the LaunchedEffect below.
            icon = painterResource(appIconState.selected.transparentPreviewResource),
            init = ::configureMacosWindowBeforePeer,
        ) {
            DisposableEffect(window) {
                com.kovelyx.app.features.notifications.EpisodeReleaseNotificationPlatform.bind { link ->
                    window.isVisible = true
                    window.extendedState = window.extendedState and java.awt.Frame.ICONIFIED.inv()
                    window.toFront()
                    window.requestFocus()
                    link?.takeIf { it != "kovelyx://" }?.let(::handleAppUrl)
                }
                val refresh = com.kovelyx.display.WindowsDisplayRefresh(window)
                onDispose { refresh.close(); com.kovelyx.app.features.notifications.EpisodeReleaseNotificationPlatform.close() }
            }
            SideEffect {
                window.background = KovelyxDesktopNativeBackground
                window.rootPane.background = KovelyxDesktopNativeBackground
                window.contentPane.background = KovelyxDesktopNativeBackground
                (window.contentPane as? JComponent)?.isOpaque = true
            }
            LaunchedEffect(window, appIconState.selected) {
                val backgroundSuffix = "-transparent"
                val iconPath = if (DesktopHostOs.current == DesktopHostOs.WINDOWS && appIconState.selected == AppIconOption.ORIGINAL) {
                    "icons/kovelyx.png"
                } else {
                    "icons/app-icon-${appIconState.selected.key}$backgroundSuffix.png"
                }
                Thread.currentThread().contextClassLoader.getResourceAsStream(iconPath)?.use { stream ->
                    ImageIO.read(stream)?.let { image ->
                        window.iconImages = listOf(image)
                    }
                }
            }

            LaunchedEffect(window) {
                applyNativeDesktopWindowChrome(window)
                installLinuxExtendedMouseButtons()
                // Windows fullscreen is emulated natively and isn't reflected by
                // WindowPlacement, so it must be re-applied once the window peer exists.
                fullscreenController.applyRestoredFullscreenState(window, windowState, wasFullscreenOnLastExit)
            }
            LaunchedEffect(windowState) {
                // Covers OS-driven placement changes too (e.g. the native macOS
                // green-button fullscreen toggle), not just our own shortcuts.
                if (DesktopHostOs.current != DesktopHostOs.WINDOWS) {
                    snapshotFlow { windowState.placement }
                        .collect { placement ->
                            DesktopWindowModeStorage.saveWasFullscreen(placement == WindowPlacement.Fullscreen)
                        }
                }
            }
            LaunchedEffect(windowState) {
                // Only persist geometry while windowed: fullscreen/native-Windows-fullscreen
                // coordinates aren't a meaningful "windowed position" to restore later.
                snapshotFlow { Triple(windowState.placement, windowState.position, windowState.size) }
                    .collect { (placement, position, size) ->
                        val isFullscreen = fullscreenController.isFullscreen(window, windowState)
                        if (!isFullscreen && restoresMaximizedWindowPlacement) {
                            DesktopWindowModeStorage.saveWasMaximized(placement == WindowPlacement.Maximized)
                        }
                        val isWindowed = placement == WindowPlacement.Floating && !isFullscreen
                        if (isWindowed && position.isSpecified) {
                            DesktopWindowModeStorage.saveWindowedGeometry(
                                DesktopWindowGeometry(
                                    x = position.x.value,
                                    y = position.y.value,
                                    width = size.width.value,
                                    height = size.height.value,
                                ),
                            )
                        }
                    }
            }
            DisposableEffect(window, windowState) {
                val unregisterFullscreenToggle = registerDesktopAppFullscreenToggle(
                    handler = { targetWindow ->
                        if (targetWindow == null || targetWindow === window) {
                            fullscreenController.toggle(window, windowState)
                            DesktopWindowModeStorage.saveWasFullscreen(
                                fullscreenController.isFullscreen(window, windowState),
                            )
                        }
                    },
                    isFullscreen = { targetWindow ->
                        (targetWindow == null || targetWindow === window) &&
                            fullscreenController.isFullscreen(window, windowState)
                    },
                )
                val uninstallFullscreenShortcuts = installDesktopAppFullscreenShortcuts(window)
                val untrackMaximizedBounds = window.trackMaximizedBoundsForCurrentScreen()
                onDispose {
                    fullscreenController.dispose(window)
                    uninstallFullscreenShortcuts()
                    untrackMaximizedBounds()
                    unregisterFullscreenToggle()
                }
            }

            CompositionLocalProvider(LocalUriHandler provides kovelyxDesktopUriHandler) {
            if (smokePlayerUrl == null) {
                ProvideDesktopWindowInsets(isFullscreen = windowState.placement == WindowPlacement.Fullscreen) {
                    var appReady by remember { mutableStateOf(false) }
                    App(
                        onAppReady = { ready -> appReady = ready },
                        desktopOverlay = { DesktopCollectionImportPrompt(isAppReady = appReady) },
                    )
                }
            } else {
                // Explicit CI smoke mode only: report actual surface progress from
                // the packaged EXE, rather than accepting a merely alive process.
                val smokeReport = remember {
                    System.getenv("KOVELYX_PLAYBACK_SMOKE_RESULT")?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
                }
                val smokeReported = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
                // The player surface reads LocalNuvioPlatformDensity, which only
                // NuvioTheme provides — the bare smoke harness must supply it too.
                NuvioTheme {
                    PlatformPlayerSurface(
                        sourceUrl = smokePlayerUrl,
                        modifier = Modifier.fillMaxSize(),
                        onControllerReady = {},
                        onSnapshot = { snapshot ->
                            if (snapshot.positionMs >= 800L && snapshot.durationMs >= 1800L &&
                                smokeReport != null && smokeReported.compareAndSet(false, true)
                            ) {
                                smokeReport.writeText("PASS position=${snapshot.positionMs} duration=${snapshot.durationMs}")
                            }
                        },
                        onError = { error ->
                            if (error != null && smokeReport != null && smokeReported.compareAndSet(false, true)) {
                                smokeReport.writeText("FAIL $error")
                            }
                        },
                    )
                }
            }
            }
        }
    }
}

private fun configureDesktopChrome() {
    if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
        System.setProperty("apple.awt.application.appearance", MacosDarkAquaAppearance)
    }
}

// application {} applies Compose's Swing globals, which on Linux include Skiko's
// display-scale detection (it sets sun.java2d.uiScale). AWT reads that property
// once, when its graphics environment starts, and installDesktopOpenUriHandler()
// starts it before application {} runs, which left the UI at 1x on HiDPI Linux
// desktops (#514). Apply the globals before anything touches AWT.
@OptIn(ExperimentalComposeUiApi::class)
private fun configureLinuxSwingGlobalsBeforeAwt() {
    if (DesktopHostOs.current != DesktopHostOs.LINUX) return
    if (System.getProperty("compose.application.configure.swing.globals") != "true") return
    // Skiko's detection overwrites sun.java2d.uiScale, so keep an explicitly
    // set scale (the documented #514 workaround) working by skipping it.
    configureSwingGlobalsForCompose(
        useAutoDpiOnLinux = System.getProperty("sun.java2d.uiScale") == null &&
            System.getProperty("skiko.linux.autodpi", "true") == "true",
    )
}

private fun installDesktopOpenUriHandler() {
    if (!Desktop.isDesktopSupported()) return
    val desktop = runCatching { Desktop.getDesktop() }.getOrNull() ?: return
    if (!desktop.isSupported(Desktop.Action.APP_OPEN_URI)) return

    runCatching {
        desktop.setOpenURIHandler { event ->
            event.uri
                ?.toString()
                ?.trim()
                ?.takeIf(::isDesktopAppUrl)
                ?.let(::handleAppUrl)
        }
    }
}

private fun handleDesktopLaunchArgs(args: Array<String>) {
    args.asSequence()
        .map(String::trim)
        .filter(::isDesktopAppUrl)
        .forEach(::handleAppUrl)
}

private fun isDesktopAppUrl(value: String): Boolean =
    value.startsWith("kovelyx://", ignoreCase = true) ||
        value.startsWith("nuvio://", ignoreCase = true) ||
        value.startsWith("stremio://", ignoreCase = true)
