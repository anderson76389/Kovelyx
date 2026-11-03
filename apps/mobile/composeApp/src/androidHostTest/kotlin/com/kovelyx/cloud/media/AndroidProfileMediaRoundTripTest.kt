package com.kovelyx.cloud.media

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.nuvio.app.features.profiles.normalizedAvatarUrl
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidProfileMediaRoundTripTest {
    private class DeviceContext(base: Context, private val device: String) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(super.getFilesDir(), device).also { it.mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("$device-$name", mode)
    }

    @Test
    fun animatedGifAboveTwoMiBReachesBothProfileImageSlotsWithoutFlattening() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as Context
        val senderContext = DeviceContext(app, "gif-sender-${UUID.randomUUID()}")
        val receiverContext = DeviceContext(app, "gif-receiver-${UUID.randomUUID()}")
        val senderFiles = AndroidProfileMediaFiles(senderContext)
        val receiverFiles = AndroidProfileMediaFiles(receiverContext)
        val gif = java.util.Base64.getDecoder().decode("R0lGODlhAgACAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAAAgACAAAIBgABCAQQEAAh+QQBFAABACwAAAAAAgACAIEAAP8AAAAAAAAAAAAIBgABCAQQEAA7")
        val bytes = java.io.ByteArrayOutputStream().apply {
            write(gif, 0, gif.size - 1)
            write(byteArrayOf(0x21, 0xfe.toByte()))
            repeat(8600) { write(255); write(ByteArray(255) { 65 }) }
            write(0); write(0x3b)
        }.toByteArray()
        assertTrue(bytes.size > 2 * 1024 * 1024)
        val stored = mutableMapOf<String, ProfileImageBytes>()
        val remote = object : ProfileMediaRemote {
            override suspend fun upload(profileId: String, kind: String, expected: String?, image: ProfileImageBytes): String {
                stored[kind] = image
                return senderFiles.sha256(image.bytes)
            }
            override suspend fun download(profileId: String, kind: String, version: String) = requireNotNull(stored[kind])
            override suspend fun remove(profileId: String, kind: String, expected: String?) = Unit
        }
        val sender = ProfileMediaEngine(senderFiles, remote)
        val receiver = ProfileMediaEngine(receiverFiles, remote)
        for (kind in listOf("avatar", "background")) {
            val folder = File(senderContext.filesDir, "kovelyx-profile-media").also { it.mkdirs() }
            val source = File(folder, "$kind.gif").also { it.writeBytes(bytes) }
            val uri = Uri.fromFile(source).toString()
            sender.edited("account", "profile", kind, uri)
            sender.synchronize("account", "profile", kind, uri, null)
            val uploaded = requireNotNull(stored[kind])
            assertEquals("image/gif", uploaded.mime)
            assertArrayEquals(bytes, uploaded.bytes)
            val version = senderFiles.sha256(bytes)
            val copied = requireNotNull(receiver.synchronize("account", "profile", kind, null, version))
            assertEquals(copied, normalizedAvatarUrl(copied))
            val downloaded = File(requireNotNull(Uri.parse(copied).path))
            assertArrayEquals(bytes, downloaded.readBytes())
            val drawable = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(downloaded))
            assertTrue("The synced file must still decode as an animation", drawable is android.graphics.drawable.AnimatedImageDrawable)
            val restarted = ProfileMediaEngine(AndroidProfileMediaFiles(receiverContext), remote)
            assertEquals(copied, restarted.synchronize("account", "profile", kind, copied, version))
        }
    }

    @Test
    fun pickedImageReachesAnotherDeviceAndRemainsDisplayableAfterRestart() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as Context
        val senderContext = DeviceContext(app, "sender-${UUID.randomUUID()}")
        val receiverContext = DeviceContext(app, "receiver-${UUID.randomUUID()}")
        val senderFiles = AndroidProfileMediaFiles(senderContext)
        val receiverFiles = AndroidProfileMediaFiles(receiverContext)
        val stored = mutableMapOf<String, ProfileImageBytes>()
        var downloads = 0
        val remote = object : ProfileMediaRemote {
            override suspend fun upload(profileId: String, kind: String, expected: String?, image: ProfileImageBytes): String {
                stored[kind] = image
                return senderFiles.sha256(image.bytes)
            }
            override suspend fun download(profileId: String, kind: String, version: String): ProfileImageBytes {
                downloads++
                return requireNotNull(stored[kind])
            }
            override suspend fun remove(profileId: String, kind: String, expected: String?) { stored.remove(kind) }
        }
        val sender = ProfileMediaEngine(senderFiles, remote)
        val receiver = ProfileMediaEngine(receiverFiles, remote)
        val received = mutableMapOf<String, String>()
        for ((kind, color) in listOf("avatar" to Color.BLUE, "background" to Color.GREEN)) {
            val dir = File(senderContext.filesDir, "kovelyx-profile-media").also { it.mkdirs() }
            val source = File(dir, "$kind.png")
            val bitmap = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(color)
                source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            val picked = Uri.fromFile(source).toString()
            sender.edited("account", "profile", kind, picked)
            sender.synchronize("account", "profile", kind, picked, null)
            assertArrayEquals(source.readBytes(), requireNotNull(stored[kind]).bytes)
            val version = senderFiles.sha256(requireNotNull(stored[kind]).bytes)
            val copied = requireNotNull(receiver.synchronize("account", "profile", kind, null, version))
            assertNotEquals(picked, copied)
            assertTrue(receiverFiles.exists(copied))
            assertEquals(copied, normalizedAvatarUrl(copied))
            val decoded = requireNotNull(BitmapFactory.decodeFile(Uri.parse(copied).path))
            try { assertEquals(2048, decoded.width); assertEquals(1024, decoded.height) }
            finally { decoded.recycle() }
            received[kind] = copied
            val restarted = ProfileMediaEngine(AndroidProfileMediaFiles(receiverContext), remote)
            assertEquals(copied, restarted.synchronize("account", "profile", kind, copied, version))
        }
        assertEquals(2, downloads)
        assertNotEquals(received["avatar"], received["background"])
    }
}
