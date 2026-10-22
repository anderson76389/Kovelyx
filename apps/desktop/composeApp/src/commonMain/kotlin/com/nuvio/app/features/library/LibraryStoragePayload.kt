package com.nuvio.app.features.library

import com.nuvio.app.features.library.sync.LibrarySyncKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class CloudLibraryCheckpoint(
    val userId: String,
    val profileId: String,
    val revisions: Map<String, Int> = emptyMap(),
)

@Serializable
internal data class StoredLibraryPayload(
    val items: List<LibraryItem> = emptyList(),
    val deltaCursorEventId: Long = 0L,
    val deltaInitialized: Boolean = false,
    val pendingUpsertKeys: List<LibrarySyncKey> = emptyList(),
    val pendingDeleteKeys: List<LibrarySyncKey> = emptyList(),
    val cloudCheckpoint: CloudLibraryCheckpoint? = null,
)

internal object LibraryStoragePayloadCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decode(payload: String): StoredLibraryPayload =
        runCatching {
            json.decodeFromString<StoredLibraryPayload>(payload)
        }.getOrDefault(StoredLibraryPayload())

    fun encode(
        snapshot: LibraryLocalSnapshot,
        checkpoint: CloudLibraryCheckpoint? = LibraryStorage.loadPayload(snapshot.token.profileId)
            ?.takeIf(String::isNotBlank)?.let(::decode)?.cloudCheckpoint,
    ): String =
        json.encodeToString(
            StoredLibraryPayload(
                items = snapshot.items.sortedByDescending(LibraryItem::savedAtEpochMs),
                deltaCursorEventId = snapshot.deltaCursorEventId,
                deltaInitialized = snapshot.deltaInitialized,
                pendingUpsertKeys = snapshot.pendingUpsertKeys,
                pendingDeleteKeys = snapshot.pendingDeleteKeys,
                cloudCheckpoint = checkpoint,
            ),
        )
}
