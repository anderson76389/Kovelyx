package com.nuvio.tv.core.sync

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample

internal const val CLOUDFLARE_FOREGROUND_PULL_INTERVAL_MS = 60_000L
internal const val CLOUDFLARE_SNAPSHOT_INTERVAL_MS = 10_000L

/** Continuous playback must not postpone uploads forever, or upload every position tick. */
@OptIn(FlowPreview::class)
internal fun <T> Flow<T>.cloudflareSnapshotUpdates(): Flow<T> =
    distinctUntilChanged().sample(CLOUDFLARE_SNAPSHOT_INTERVAL_MS)
