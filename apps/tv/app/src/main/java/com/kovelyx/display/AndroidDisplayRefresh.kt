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
        var requestedRate = 0f
        val surfaces = mutableMapOf<android.view.SurfaceView, android.view.SurfaceHolder.Callback>()
        fun voteSurface(view: android.view.SurfaceView) {
            if (Build.VERSION.SDK_INT >= 30 && view.holder.surface.isValid) {
                runCatching { view.holder.surface.setFrameRate(requestedRate, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
            }
        }
        fun voteViews(view: android.view.View) {
            if (view is android.view.SurfaceView && !surfaces.containsKey(view)) {
                val callback = object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) = voteSurface(view)
                    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) = voteSurface(view)
                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) = Unit
                }
                surfaces[view] = callback
                view.holder.addCallback(callback)
                voteSurface(view)
            }
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) voteViews(view.getChildAt(index))
            }
        }
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
            if (requestedRate != refreshRate) {
                requestedRate = refreshRate
                surfaces.keys.forEach(::voteSurface)
            }
            surfaces.keys.filter { !it.isAttachedToWindow }.forEach { detached ->
                surfaces.remove(detached)?.let { detached.holder.removeCallback(it) }
            }
            applyAndroidViewFrameRate(activity.window.decorView, requestedRate)
            voteViews(activity.window.decorView)
            DisplayRefreshSettings.displayChanged(modes.filter { it.width == current.physicalWidth && it.height == current.physicalHeight }
                .mapNotNull { nominalRefreshRate(it.hertz) }.toSet(), display.refreshRate)
        }
        val layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener { apply() }
        activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
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
                activity.window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                surfaces.forEach { (view, callback) -> view.holder.removeCallback(callback) }
                surfaces.clear()
                scope.cancel()
            }
        })
    }
}

/** Android 15 does not inherit a ViewGroup vote in its child rendering views. */
internal fun applyAndroidViewFrameRate(view: android.view.View, rate: Float) {
    if (Build.VERSION.SDK_INT < 35) return
    if (view.requestedFrameRate != rate) view.setRequestedFrameRate(rate)
    if (view is android.view.ViewGroup) {
        for (index in 0 until view.childCount) applyAndroidViewFrameRate(view.getChildAt(index), rate)
    }
}
