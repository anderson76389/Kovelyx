package com.kovelyx.app.core.compat

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.kovelyx.app.features.downloads.DownloadsTransferWorker
import com.kovelyx.app.features.notifications.EpisodeReleaseNotificationWorker

/**
 * WorkManager persists a worker's fully-qualified class name in its local database.
 * Before the Kovelyx package migration, installed copies saved these two historical
 * class names. Resolve them to the current workers so pending downloads and episode
 * reminders survive an in-place APK update rather than silently failing at startup.
 *
 * Historical class names are data-compatibility identifiers, not upstream calls.
 */
internal class KovelyxUpgradeWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        DownloadsTransferWorker::class.java.name,
        "com.nuvio.app.features.downloads.DownloadsTransferWorker" ->
            DownloadsTransferWorker(appContext, workerParameters)
        EpisodeReleaseNotificationWorker::class.java.name,
        "com.nuvio.app.features.notifications.EpisodeReleaseNotificationWorker" ->
            EpisodeReleaseNotificationWorker(appContext, workerParameters)
        else -> null
    }
}
