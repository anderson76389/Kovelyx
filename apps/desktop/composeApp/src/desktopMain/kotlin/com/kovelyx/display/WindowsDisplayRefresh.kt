package com.kovelyx.display

import com.kovelyx.app.core.storage.DesktopStorage
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Observe at launch/focus; change the monitor only after an explicit range edit. */
internal class WindowsDisplayRefresh(private val window: Window) : AutoCloseable {
    private interface DisplayApi : StdCallLibrary {
        fun MonitorFromWindow(window: Pointer, flags: Int): Pointer?
        fun GetMonitorInfoW(monitor: Pointer, info: Pointer): Boolean
        fun EnumDisplaySettingsW(device: WString, index: Int, mode: Pointer): Boolean
        fun ChangeDisplaySettingsExW(device: WString, mode: Pointer?, window: Pointer?, flags: Int, parameter: Pointer?): Int
    }
    private val api: DisplayApi? = if (System.getProperty("os.name").startsWith("Windows", true))
        runCatching { Native.load("user32", DisplayApi::class.java, W32APIOptions.UNICODE_OPTIONS) }.getOrNull() else null
    private var currentDevice: String? = null
    private var observedMode: DisplayModeSpec? = null
    private var cachedModes = emptyList<Pair<DisplayModeSpec, Memory>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // EnumDisplaySettingsW can enumerate hundreds of monitor modes. Never run that
    // synchronous native work on the Swing/Compose event dispatch thread.
    private data class RefreshRequest(val windowPointer: Pointer, val changeMode: Boolean)
    private val requests = Channel<RefreshRequest>(Channel.CONFLATED)
    private val pendingExplicitModeChange = AtomicBoolean(false)
    private val settle = Timer(150) { requestRefresh() }.apply { isRepeats = false }
    private val movement = object : ComponentAdapter() {
        override fun componentMoved(event: ComponentEvent) { settle.restart() }
    }
    private val focus = object : WindowAdapter() {
        override fun windowGainedFocus(event: WindowEvent) { requestRefresh() }
    }

    init {
        if (api != null) {
            val store = DesktopStorage.store("kovelyx_display_refresh")
            val range = store.getString("range")?.split(':')
            DisplayRefreshSettings.initialize(range?.getOrNull(0)?.toIntOrNull(), range?.getOrNull(1)?.toIntOrNull(),
                store.getString("hertz")?.toIntOrNull() ?: 60) { minimum, maximum ->
                store.putString("range", "$minimum:$maximum")
            }
            // Windows mode changes affect the entire desktop and can trigger a black
            // screen / GPU driver resynchronization. Existing preferences are NOT consent.
            // Only a separate, explicit opt-in enables ChangeDisplaySettingsExW.
            DisplayRefreshSettings.configureSystemModeConsent(
                required = true,
                granted = store.getBoolean("allow_system_mode_switch") == true,
                persist = { allowed -> store.putBoolean("allow_system_mode_switch", allowed) },
            )
            window.addComponentListener(movement)
            window.addWindowFocusListener(focus)
            // A single background worker coalesces move/focus events. It owns all
            // cached native mode structures, so monitor changes cannot race.
            scope.launch(Dispatchers.IO) {
                for (request in requests) apply(request.windowPointer, pendingExplicitModeChange.getAndSet(false))
            }
            scope.launch {
                DisplayRefreshSettings.state.map { it.minimum to it.maximum }
                    .distinctUntilChanged().drop(1).collect { requestRefresh(changeMode = true) }
            }
            settle.start()
        }
    }

    private fun modeMemory() = Memory(220).apply { clear(); setShort(68, 220.toShort()) }
    private fun Memory.spec(id: Int = -1) = DisplayModeSpec(id, getInt(172), getInt(176), when (val rate = getInt(184)) { 23, 29, 47, 59, 95, 119, 143 -> (rate + 1) / 1.001f; else -> rate.toFloat() })
    private fun current(device: String): Memory? = modeMemory().takeIf { api?.EnumDisplaySettingsW(WString(device), -1, it) == true }

    private fun requestRefresh(changeMode: Boolean = false) {
        if (api == null || !window.isDisplayable) return
        // AWT native peer access belongs to the EDT. The expensive Win32
        // enumeration is performed later by a single IO worker.
        val pointer = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return
        if (changeMode) pendingExplicitModeChange.set(true)
        requests.trySend(RefreshRequest(pointer, changeMode))
    }

    private fun apply(windowPointer: Pointer, changeMode: Boolean = false) {
        val api = api ?: return
        runCatching {
            val monitor = api.MonitorFromWindow(windowPointer, 2) ?: return
            val info = Memory(104).apply { clear(); setInt(0, 104) }
            if (!api.GetMonitorInfoW(monitor, info)) return
            val device = info.getWideString(40)
            val before = current(device) ?: return
            val currentMode = before.spec()
            if (device != currentDevice || currentMode != observedMode) {
                val modes = mutableListOf<Pair<DisplayModeSpec, Memory>>()
                var index = 0
                while (index < 2048) {
                    val memory = modeMemory()
                    if (!api.EnumDisplaySettingsW(WString(device), index, memory)) break
                    if (memory.getInt(168) >= 32 && memory.getInt(184) > 1 && (memory.getInt(180) and 2) == 0) modes += memory.spec(index) to memory
                    index++
                }
                cachedModes = modes
                currentDevice = device
                observedMode = currentMode
            }
            val modes = cachedModes
            val range = DisplayRefreshSettings.state.value
            val candidates = displayRefreshCandidates(modes.map { it.first }, currentMode, range.minimum, range.maximum)
            for (choice in if (changeMode && range.systemModeConsentGranted) candidates else emptyList()) {
                if (nominalRefreshRate(currentMode.hertz) == nominalRefreshRate(choice.hertz)) break
                val memory = modes.first { it.first.id == choice.id }.second
                memory.setInt(72, 0x00400000) // DM_DISPLAYFREQUENCY only: preserve resolution, position and colour depth.
                if (api.ChangeDisplaySettingsExW(WString(device), memory, null, 2, null) == 0 &&
                    api.ChangeDisplaySettingsExW(WString(device), memory, null, 4, null) == 0) {
                    break
                }
            }
            val actual = current(device)?.spec() ?: currentMode
            DisplayRefreshSettings.displayChanged(modes.map { it.first }
                .filter { it.width == currentMode.width && it.height == currentMode.height }
                .mapNotNull { nominalRefreshRate(it.hertz) }.toSet(), actual.hertz)
        }
    }

    override fun close() {
        settle.stop()
        requests.close()
        scope.cancel()
        window.removeComponentListener(movement)
        window.removeWindowFocusListener(focus)
    }
}
