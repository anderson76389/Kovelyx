package com.kovelyx.player

import android.app.Activity
import android.content.ContentResolver
import android.os.ParcelFileDescriptor
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class VlcPlaybackActivity : Activity() {
    private lateinit var engine: LibVLC
    private lateinit var player: MediaPlayer
    private var localFile: ParcelFileDescriptor? = null
    private var completed = false
    private var resumeMs = 0L

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val uri = intent.data ?: run { finish(); return }
        val layout = VLCVideoLayout(this)
        setContentView(layout)
        val controls = android.widget.MediaController(this)
        controls.setAnchorView(layout)
        controls.setMediaPlayer(object : android.widget.MediaController.MediaPlayerControl {
            override fun start() = player.play()
            override fun pause() = player.pause()
            override fun getDuration() = player.length.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            override fun getCurrentPosition() = player.time.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            override fun seekTo(pos: Int) { player.setTime(pos.toLong()) }
            override fun isPlaying() = player.isPlaying
            override fun getBufferPercentage() = 0
            override fun canPause() = true
            override fun canSeekBackward() = player.isSeekable
            override fun canSeekForward() = player.isSeekable
            override fun getAudioSessionId() = 0
        })
        layout.setOnClickListener { controls.show() }
        engine = LibVLC(this, arrayListOf())
        player = MediaPlayer(engine)
        player.attachViews(layout, null, true, false)
        resumeMs = state?.getLong("position") ?: intent.getLongExtra("extra_position", 0L)
        val media = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val descriptor = try {
                contentResolver.openFileDescriptor(uri, "r")
            } catch (_: java.io.IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
            if (descriptor == null) {
                showPlaybackError()
                return
            }
            localFile = descriptor
            Media(engine, descriptor.fileDescriptor)
        } else {
            Media(engine, uri)
        }
        try {
            for (header in intent.getStringArrayExtra("headers").orEmpty()) {
                val value = header.substringAfter(':').trim()
                when (header.substringBefore(':').lowercase()) {
                    "user-agent" -> media.addOption(":http-user-agent=$value")
                    "referer" -> media.addOption(":http-referrer=$value")
                }
            }
            player.media = media
        } finally {
            media.release()
        }
        @Suppress("DEPRECATION")
        val subtitle = intent.getParcelableExtra<Uri>("subtitles_location")
        subtitle?.let { player.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle, it, true) }
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (resumeMs > 0 && player.isSeekable) {
                        player.setTime(resumeMs)
                        resumeMs = 0
                    }
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                MediaPlayer.Event.EndReached -> { completed = true; finish() }
                MediaPlayer.Event.EncounteredError -> showPlaybackError()
            }
        }
        player.play()
    }

    private fun showPlaybackError() {
        Toast.makeText(this, android.R.string.VideoView_error_text_unknown, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                if (event.repeatCount == 0) { if (player.isPlaying) player.pause() else player.play() }
            KeyEvent.KEYCODE_DPAD_LEFT -> player.setTime((player.time - 10000).coerceAtLeast(0))
            KeyEvent.KEYCODE_DPAD_RIGHT -> player.setTime(player.time + 10000)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onStop() {
        if (::player.isInitialized && player.isPlaying) player.pause()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::player.isInitialized) outState.putLong("position", player.time)
    }

    override fun finish() {
        if (::player.isInitialized) setResult(RESULT_OK, Intent().apply {
            putExtra("extra_position", player.time)
            putExtra("extra_duration", player.length)
            if (completed) putExtra("end_by", "playback_completion")
        })
        super.finish()
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            player.setEventListener(null)
            player.detachViews()
            player.release()
        }
        localFile?.close()
        if (::engine.isInitialized) engine.release()
        super.onDestroy()
    }
}
