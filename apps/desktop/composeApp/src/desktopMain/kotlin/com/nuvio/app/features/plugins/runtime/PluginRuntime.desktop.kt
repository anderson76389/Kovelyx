package com.nuvio.app.features.plugins.runtime

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Chaque exécution QuickJS a son propre contexte natif, mais la création de
// dizaines de runtimes en parallèle sature Windows et déclenche le délai de
// sécurité des scrapers pendant le stress test. Garder des threads IO (les
// ponts JS peuvent attendre des I/O), avec une concurrence native bornée.
// La file d'attente est en dehors du withTimeout propre à chaque scraper.
private const val MAX_PARALLEL_DESKTOP_QUICKJS = 4
internal val pluginDispatcher: CoroutineDispatcher =
    Dispatchers.IO.limitedParallelism(MAX_PARALLEL_DESKTOP_QUICKJS)

internal fun QuickJs.configurePluginRuntime() = Unit
