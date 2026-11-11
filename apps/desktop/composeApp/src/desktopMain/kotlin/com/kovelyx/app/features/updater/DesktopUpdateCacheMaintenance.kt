package com.kovelyx.app.features.updater

import java.io.File

internal object DesktopUpdateCacheMaintenance {
    fun clean(directory: File, currentVersion: String): Long =
        UpdateCacheMaintenance.clean(directory, isAlreadyInstalled = { file ->
            // Public assets use -vVERSION; older jpackage artifacts also used -VERSION.
            val raw = Regex("-(v?[0-9]+\\.[0-9]+\\.[0-9]+(?:-(?:alpha|beta|rc)(?:\\.[0-9]+)?)?)(?:-[A-Za-z0-9_]+)?\\.(?:msi|exe|zip|AppImage|deb|rpm)$", RegexOption.IGNORE_CASE)
                .find(file.name)?.groupValues?.get(1)
            val version = VersionUtils.parse(raw)
            val current = VersionUtils.parse(currentVersion)
            if (version != null && current != null) version <= current else null
        })
}
