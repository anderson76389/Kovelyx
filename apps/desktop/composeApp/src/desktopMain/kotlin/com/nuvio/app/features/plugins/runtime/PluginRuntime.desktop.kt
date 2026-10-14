package com.nuvio.app.features.plugins.runtime

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// QuickJS may block threads while running native bytecode and promise callbacks.
// Dispatchers.IO has capacity for the bounded concurrent plugin runtimes.
internal val pluginDispatcher: CoroutineDispatcher = Dispatchers.IO

internal fun QuickJs.configurePluginRuntime() = Unit
