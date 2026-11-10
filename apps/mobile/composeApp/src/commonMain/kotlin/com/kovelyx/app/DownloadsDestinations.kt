package com.kovelyx.app

import androidx.compose.runtime.Composable
import com.kovelyx.app.features.downloads.DownloadItem
import com.kovelyx.app.features.downloads.DownloadsScreen
import com.kovelyx.app.navigation.DownloadShowRoute
import com.kovelyx.app.navigation.DownloadsRoute
import com.kovelyx.app.navigation.KovelyxNavigator

@Composable
internal fun DownloadsDestination(
    route: DownloadsRoute,
    navController: KovelyxNavigator,
    useNativeNavigation: Boolean,
    onOpenDownload: (DownloadItem) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    DownloadsScreen(
        onBack = onBack,
        onOpenDownload = onOpenDownload,
        onNavigateToShow = if (useNativeNavigation) {
            { showId, title -> navController.navigate(DownloadShowRoute(showId, title)) }
        } else {
            null
        },
    )
}

@Composable
internal fun DownloadShowDestination(
    route: DownloadShowRoute,
    navController: KovelyxNavigator,
    onOpenDownload: (DownloadItem) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    DownloadsScreen(
        onBack = onBack,
        onOpenDownload = onOpenDownload,
        initialShowId = route.showId,
        onBackFromShow = onBack,
    )
}
