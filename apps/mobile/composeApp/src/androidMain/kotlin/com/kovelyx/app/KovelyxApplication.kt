package com.kovelyx.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.kovelyx.app.core.compat.KovelyxUpgradeWorkerFactory
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.kovelyx.app.core.poster.CustomPosterFallbackInterceptor

/**
 * Custom Application class that implements [SingletonImageLoader.Factory] to guarantee
 * the Coil ImageLoader (with [CustomPosterFallbackInterceptor]) is created before any
 * image request. Without this, Coil's auto-init ContentProvider creates the singleton
 * before MainActivity.onCreate, and interceptors registered later are ignored.
 */
class KovelyxApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        com.kovelyx.maintenance.android.AndroidMaintenance.startup(this)
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder().setWorkerFactory(KovelyxUpgradeWorkerFactory()).build()
    }

    // Initialize with the application-owned factory at startup: WorkManager may
    // need to resume historical download/notification work after an APK update.
    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this)
    }


    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                add(CustomPosterFallbackInterceptor())
                add(SvgDecoder.Factory())
                add(
                    KtorNetworkFetcherFactory(
                        cacheStrategy = { CacheControlCacheStrategy() },
                    ),
                )
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
