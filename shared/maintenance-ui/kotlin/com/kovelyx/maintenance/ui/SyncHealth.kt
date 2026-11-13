package com.kovelyx.maintenance.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

/** Session diagnostics: account/profile scoped, bounded, and free of URLs or exception text. */
object SyncHealth {
    private data class Health(val success: Long = 0, val queued: Boolean = false, val running: Boolean = false, val failed: Boolean = false, val generation: Long = 0)
    private val states = MutableStateFlow<Map<String, Health>>(emptyMap())
    private fun change(key: String, update: (Health) -> Health) {
        states.update { previous -> (previous - key).toList().takeLast(47).toMap() + (key to update(previous[key] ?: Health())) }
    }
    fun queued(key: String) = change(key) { it.copy(queued = true, generation = it.generation + 1) }
    fun started(key: String): Long {
        var generation = 0L
        change(key) { generation = it.generation; it.copy(running = true) }
        return generation
    }
    fun succeeded(key: String, generation: Long) = change(key) {
        it.copy(success = Clock.System.now().toEpochMilliseconds(), queued = it.generation != generation, running = false, failed = false)
    }
    fun failed(key: String) = change(key) { it.copy(queued = true, running = false, failed = true) }
    fun cancelled(key: String) = change(key) { it.copy(running = false) }
    fun describe(key: String): String {
        val value = states.value[key] ?: Health()
        val last = if (value.success == 0L) "Aucune réussite observée depuis le lancement" else
            "Dernière réussite il y a ${((Clock.System.now().toEpochMilliseconds() - value.success) / 1000).coerceAtLeast(0)} s"
        return listOf(last, when {
            value.running -> "Synchronisation en cours"
            value.failed -> "Échec temporaire : reprise automatique, données locales conservées"
            value.queued -> "Modifications ou demande locale en attente"
            else -> "Aucune demande en attente observée dans cette session"
        }).joinToString("\n")
    }
}
