package com.nuvio.app.features.profiles

import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal actual fun ProfileVideoBackdrop(url: String,modifier: Modifier) {
    AndroidView(
        modifier=modifier,
        factory={ context ->
            VideoView(context).apply {
                tag=url
                setVideoURI(Uri.parse(url))
                setOnPreparedListener { player ->
                    player.isLooping=true
                    player.setVolume(0f,0f)
                    start()
                }
                setOnErrorListener { _,_,_ -> true }
            }
        },
        update={ view ->
            if(view.tag!=url) {
                view.tag=url
                view.setVideoURI(Uri.parse(url))
                view.start()
            }
        },
        onRelease={ it.stopPlayback() }
    )
}
