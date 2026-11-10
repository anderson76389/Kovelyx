package com.kovelyx.app.features.library

import com.kovelyx.app.core.ui.KovelyxToastController
import com.kovelyx.app.features.tracking.TrackingLibraryTab
import com.kovelyx.app.features.tracking.TrackingMembershipApplyResult
import com.kovelyx.app.features.tracking.TrackingProviderRegistry
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tracking_list_status_rewritten
import org.jetbrains.compose.resources.getString

internal suspend fun showTrackingMembershipRewriteFeedback(result: TrackingMembershipApplyResult) {
    val rewrite = result.rewrites.firstOrNull() ?: return
    val providerName = TrackingProviderRegistry.authProvider(rewrite.providerId)
        ?.descriptor
        ?.displayName
        ?: rewrite.providerId.storageId.replaceFirstChar { char -> char.titlecase() }
    val tabs = TrackingProviderRegistry.libraryProvider(rewrite.providerId)?.snapshot()?.tabs.orEmpty()
    val requestedTitle = tabs.statusTitle(rewrite.requestedListKey, providerName)
    val resolvedTitle = tabs.statusTitle(rewrite.resolvedListKey, providerName)
    KovelyxToastController.show(
        getString(
            Res.string.tracking_list_status_rewritten,
            providerName,
            resolvedTitle,
            requestedTitle,
        ),
    )
}

private fun List<TrackingLibraryTab>.statusTitle(
    key: String,
    providerName: String,
): String = firstOrNull { tab -> tab.key == key }
    ?.title
    ?.removePrefix("$providerName ")
    ?: key.substringAfterLast(':')
