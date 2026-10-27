package com.nuvio.tv.domain.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.core.tracking.LOCAL_LIBRARY_LIST_KEY
import com.nuvio.tv.core.tracking.TrackingProviderId

@Composable
fun LibraryListTab.localizedMembershipTitle(): String {
    val provider = when (TrackingProviderId.fromStorage(trackingProviderId)) {
        TrackingProviderId.TRAKT -> stringResource(R.string.trakt_name)
        TrackingProviderId.MDBLIST -> stringResource(R.string.mdblist_name)
        null -> null
    }
    return provider?.let { "$it · ${localizedTitle()}" } ?: localizedTitle()
}

@Composable
fun LibraryListTab.localizedTitle(): String {
    return when {
        key == LOCAL_LIBRARY_LIST_KEY -> title
        type == LibraryListTab.Type.WATCHLIST -> stringResource(R.string.library_watchlist)
        else -> title
    }
}
