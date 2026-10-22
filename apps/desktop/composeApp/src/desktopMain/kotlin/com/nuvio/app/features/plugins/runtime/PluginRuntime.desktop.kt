package com.nuvio.app.features.plugins.runtime

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

// QuickJS uses native execution contexts that need deterministic thread
// affinity across coroutine suspensions (not provided by Dispatchers.IO).
// A dedicated JVM worker prevents thread migration when JS promises complete.
// Calls are cooperatively interleaved while awaiting I/O; the shared scraper
// semaphore still limits the number of active runtimes.
internal val pluginDispatcher: CoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kovelyx-quickjs-worker").apply { isDaemon = true }
    }.asCoroutineDispatcher()

internal fun QuickJs.configurePluginRuntime() = Unit
