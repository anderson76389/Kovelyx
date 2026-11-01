package com.kovelyx.display

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

object AndroidDisplayRefresh {
    fun bind(activity: Activity) {
        if (Build.VERSION.SDK_INT < 23) return
        val preferences = activity.applicationContext.getSharedPreferences("kovelyx_display_refresh", 0)
        DisplayRefreshSettings.initialize(
            preferences.takeIf { it.contains("minimum_hertz") }?.getInt("minimum_hertz", 60),
            preferences.takeIf { it.contains("maximum_hertz") }?.getInt("maximum_hertz", 60),
            preferences.getInt("hertz", 60)
        ) { minimum, maximum ->
            preferences.edit().putInt("minimum_hertz", minimum).putInt("maximum_hertz", maximum).apply()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val manager = activity.getSystemService(DisplayManager::class.java) ?: return
        fun apply() {
            if (activity.isDestroyed || activity.isFinishing) return
            val display = activity.window.decorView.display ?: return
            val current = display.mode
            fun android.view.Display.Mode.spec() = DisplayModeSpec(modeId, physicalWidth, physicalHeight, refreshRate)
            val modes = display.supportedModes.map { it.spec() }
            val range = DisplayRefreshSettings.state.value
            val selected = displayRefreshCandidates(modes, current.spec(), range.minimum, range.maximum).firstOrNull()
            val attributes = activity.window.attributes
            val modeId = selected?.id ?: 0
            val refreshRate = selected?.hertz ?: 0f
            if (attributes.preferredDisplayModeId != modeId || attributes.preferredRefreshRate != refreshRate) {
                attributes.preferredDisplayModeId = modeId
                attributes.preferredRefreshRate = refreshRate
                activity.window.attributes = attributes
            }
            DisplayRefreshSettings.displayChanged(modes.filter { it.width == current.physicalWidth && it.height == current.physicalHeight }
                .mapNotNull { nominalRefreshRate(it.hertz) }.toSet(), current.refreshRate)
        }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = apply()
            override fun onDisplayRemoved(id: Int) = apply()
            override fun onDisplayChanged(id: Int) = apply()
        }
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        scope.launch { DisplayRefreshSettings.state.map { it.minimum to it.maximum }.distinctUntilChanged().collect { apply() } }
        activity.window.decorView.post { apply() }
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) apply()
            if (event == Lifecycle.Event.ON_DESTROY) {
                manager.unregisterDisplayListener(listener)
                scope.cancel()
            }
        })
    }
}
