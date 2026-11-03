package com.nuvio.app.core.cloud

import android.content.Context
import com.kovelyx.cloud.media.AndroidProfileMediaFiles
import com.kovelyx.cloud.media.ProfileMediaFiles

internal actual object CloudflareProfileMediaPlatform {
    private var storage: ProfileMediaFiles? = null
    actual val files: ProfileMediaFiles? get() = storage
    fun initialize(context: Context) { if (storage == null) storage = AndroidProfileMediaFiles(context) }
}
