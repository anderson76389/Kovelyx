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
        DisplayRefreshSettings.initialize(preferences.getInt("hertz", 60)) {
            preferences.edit().putInt("hertz", it).apply()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val manager = activity.getSystemService(DisplayManager::class.java) ?: return
        fun apply() {
            if (activity.isDestroyed || activity.isFinishing) return
            val display = activity.window.decorView.display ?: return
            val current = display.mode
            fun android.view.Display.Mode.spec() = DisplayModeSpec(modeId, physicalWidth, physicalHeight, refreshRate)
            val modes = display.supportedModes.map { it.spec() }
            val selected = selectDisplayRefreshMode(modes, current.spec(), DisplayRefreshSettings.state.value.requested)
            val attributes = activity.window.attributes
            if (selected != null && (attributes.preferredDisplayModeId != selected.id || attributes.preferredRefreshRate != selected.hertz)) {
                attributes.preferredDisplayModeId = selected.id
                attributes.preferredRefreshRate = selected.hertz
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
        scope.launch { DisplayRefreshSettings.state.map { it.requested }.distinctUntilChanged().collect { apply() } }
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
