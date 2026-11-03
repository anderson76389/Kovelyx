package com.nuvio.app.core.cloud

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
internal data class PendingCloudflareDelete(
    val ownerId: String,
    val cloudProfileId: String,
    val profileIndex: Int,
    val category: String,
    val itemKey: String,
    val createdAtMs: Long,
    val nonce: Long
) {
    fun sameItem(other: PendingCloudflareDelete): Boolean =
        ownerId == other.ownerId && cloudProfileId == other.cloudProfileId &&
            category == other.category && itemKey == other.itemKey
}

/** Durable, account/profile-scoped outbox. A stale response cannot acknowledge a newer deletion. */
internal class CloudflareDeleteOutbox(
    private val load: () -> String?,
    private val save: (String) -> Unit
) {
    private val lock = SynchronizedObject()
    private val json = Json { ignoreUnknownKeys = true }
    private fun entries(): List<PendingCloudflareDelete> =
        load()?.takeIf { it.isNotBlank() }?.let { json.decodeFromString(it) } ?: emptyList()

    fun record(change: PendingCloudflareDelete) = synchronized(lock) {
        val pending = entries().filterNot { it.sameItem(change) } + change
        save(json.encodeToString(pending))
    }

    fun pending(ownerId: String, cloudProfileId: String): List<PendingCloudflareDelete> = synchronized(lock) {
        entries().filter { it.ownerId == ownerId && it.cloudProfileId == cloudProfileId }
    }

    fun acknowledge(change: PendingCloudflareDelete) = synchronized(lock) {
        save(json.encodeToString(entries().filterNot { it == change }))
    }
}
