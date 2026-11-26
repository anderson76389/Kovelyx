package com.kovelyx.tv.ui.screens.player

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.kovelyx.player.VlcPlaybackActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class VlcPlaybackTest {
    @Test(timeout = 60000)
    fun explicitInternalPlayerPlaysLocalClipToCompletion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "vlc-smoke.mp4")
        try {
            instrumentation.context.assets.open("media3-smoke.mp4.b64").use {
                file.writeBytes(Base64.decode(it.readBytes(), Base64.DEFAULT))
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(context, VlcPlaybackActivity::class.java).setData(uri)
            ActivityScenario.launchActivityForResult<VlcPlaybackActivity>(intent).use { scenario ->
                val result = scenario.result
                assertEquals(Activity.RESULT_OK, result.resultCode)
                assertEquals("playback_completion", result.resultData.getStringExtra("end_by"))
            }
        } finally {
            file.delete()
        }
    }
}
