package com.kovelyx.display

import com.nuvio.app.core.storage.DesktopStorage
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Temporary mode of this window's monitor; never writes Windows display settings to the registry. */
internal class WindowsDisplayRefresh(private val window: Window) : AutoCloseable {
    private interface DisplayApi : StdCallLibrary {
        fun MonitorFromWindow(window: Pointer, flags: Int): Pointer?
        fun GetMonitorInfoW(monitor: Pointer, info: Pointer): Boolean
        fun EnumDisplaySettingsW(device: WString, index: Int, mode: Pointer): Boolean
        fun ChangeDisplaySettingsExW(device: WString, mode: Pointer?, window: Pointer?, flags: Int, parameter: Pointer?): Int
    }
    private val api: DisplayApi? = if (System.getProperty("os.name").startsWith("Windows", true))
        runCatching { Native.load("user32", DisplayApi::class.java, W32APIOptions.UNICODE_OPTIONS) }.getOrNull() else null
    private data class Original(val bytes: ByteArray, val applied: DisplayModeSpec)
    private val originals = mutableMapOf<String, Original>()
    private var currentDevice: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settle = Timer(150) { apply() }.apply { isRepeats = false }
    private val movement = object : ComponentAdapter() {
        override fun componentMoved(event: ComponentEvent) { settle.restart() }
    }
    private val focus = object : WindowAdapter() {
        override fun windowGainedFocus(event: WindowEvent) { apply() }
    }

    init {
        if (api != null) {
            val store = DesktopStorage.store("kovelyx_display_refresh")
            DisplayRefreshSettings.initialize(store.getString("hertz")?.toIntOrNull() ?: 60) {
                store.putString("hertz", it.toString())
            }
            window.addComponentListener(movement)
            window.addWindowFocusListener(focus)
            scope.launch { DisplayRefreshSettings.state.map { it.requested }.distinctUntilChanged().collect { apply() } }
            settle.start()
        }
    }

    private fun modeMemory() = Memory(220).apply { clear(); setShort(68, 220.toShort()) }
    private fun Memory.spec(id: Int = -1) = DisplayModeSpec(id, getInt(172), getInt(176), when (val rate = getInt(184)) { 59 -> 59.94f; 119 -> 119.88f; else -> rate.toFloat() })
    private fun current(device: String): Memory? = modeMemory().takeIf { api?.EnumDisplaySettingsW(WString(device), -1, it) == true }

    private fun apply() {
        val api = api ?: return
        if (!window.isDisplayable) return
        runCatching {
            val monitor = api.MonitorFromWindow(Native.getWindowPointer(window), 2) ?: return
            val info = Memory(104).apply { clear(); setInt(0, 104) }
            if (!api.GetMonitorInfoW(monitor, info)) return
            val device = info.getWideString(40)
            if (device != currentDevice) {
                currentDevice?.let(::restore)
                currentDevice = device
            }
            val before = current(device) ?: return
            val modes = mutableListOf<Pair<DisplayModeSpec, Memory>>()
            var index = 0
            while (index < 2048) {
                val memory = modeMemory()
                if (!api.EnumDisplaySettingsW(WString(device), index, memory)) break
                if (memory.getInt(168) >= 32) modes += memory.spec(index) to memory
                index++
            }
            val currentMode = before.spec()
            val requested = DisplayRefreshSettings.state.value.requested
            val candidates = listOfNotNull(
                selectDisplayRefreshMode(modes.map { it.first }, currentMode, requested),
                selectDisplayRefreshMode(modes.map { it.first }, currentMode, 60)
            ).distinctBy { it.id }
            for (choice in candidates) {
                if (nominalRefreshRate(currentMode.hertz) == nominalRefreshRate(choice.hertz)) break
                val memory = modes.first { it.first.id == choice.id }.second
                memory.setInt(72, 0x00400000) // DM_DISPLAYFREQUENCY only: preserve resolution, position and colour depth.
                if (api.ChangeDisplaySettingsExW(WString(device), memory, null, 2, null) == 0 &&
                    api.ChangeDisplaySettingsExW(WString(device), memory, null, 4, null) == 0) {
                    val original = originals[device]?.bytes ?: before.getByteArray(0, 220)
                    originals[device] = Original(original, choice)
                    break
                }
            }
            val actual = current(device)?.spec() ?: currentMode
            DisplayRefreshSettings.displayChanged(modes.map { it.first }
                .filter { it.width == currentMode.width && it.height == currentMode.height }
                .mapNotNull { nominalRefreshRate(it.hertz) }.toSet(), actual.hertz)
        }
    }

    private fun restore(device: String) {
        val original = originals.remove(device) ?: return
        val actual = current(device)?.spec() ?: return
        // A user or another app may have changed the monitor since our request.
        if (actual.width != original.applied.width || actual.height != original.applied.height ||
            nominalRefreshRate(actual.hertz) != nominalRefreshRate(original.applied.hertz)) return
        val memory = modeMemory().apply { write(0, original.bytes, 0, original.bytes.size) }
        api?.ChangeDisplaySettingsExW(WString(device), memory, null, 4, null)
    }

    override fun close() {
        settle.stop()
        scope.cancel()
        window.removeComponentListener(movement)
        window.removeWindowFocusListener(focus)
        originals.keys.toList().forEach { runCatching { restore(it) } }
    }
}
