package com.nuvio.app.core.cloud

import com.kovelyx.cloud.media.ProfileMediaFiles

internal expect object CloudflareProfileMediaPlatform {
    val files: ProfileMediaFiles?
}
