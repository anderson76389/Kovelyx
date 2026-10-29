package com.nuvio.app.core.cloud

import com.kovelyx.cloud.media.DesktopProfileMediaFiles
import com.kovelyx.cloud.media.ProfileMediaFiles

internal actual object CloudflareProfileMediaPlatform {
    actual val files: ProfileMediaFiles by lazy { DesktopProfileMediaFiles() }
}
