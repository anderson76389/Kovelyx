package com.kovelyx.cloud.media

import kotlinx.coroutines.test.runTest

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ProfileMediaEngineTest {
    private val v1 = "a".repeat(64)
    private val v2 = "b".repeat(64)
    private class Files : ProfileMediaFiles {
        val states = mutableMapOf<String, ProfileMediaState>()
        var saved = 0
        var digest = "a".repeat(64)
        override fun loadState(key: String) = states[key]
        override fun saveState(key: String, state: ProfileMediaState) { states[key] = state }
        override fun exists(uri: String) = true
        override fun sha256(bytes: ByteArray) = digest
        override suspend fun encodeLocal(uri: String, background: Boolean) = ProfileImageBytes("image/gif", byteArrayOf(1,2,3))
        override suspend fun saveDownloaded(key: String, version: String, image: ProfileImageBytes): String {
            saved++; return "file:cache-$key-$version"
        }
    }
    private class Remote : ProfileMediaRemote {
        var downloads = 0
        var uploads = 0
        var deletes = 0
        var offline = false
        var expected: String? = null
        var onUpload: (() -> Unit)? = null
        override suspend fun upload(profileId: String, kind: String, expected: String?, image: ProfileImageBytes): String {
            uploads++; this.expected = expected
            if (offline) error("offline")
            assertEquals("image/gif", image.mime)
            onUpload?.invoke()
            return "b".repeat(64)
        }
        override suspend fun download(profileId: String, kind: String, version: String): ProfileImageBytes {
            downloads++; return ProfileImageBytes("image/png", byteArrayOf(1,2,3))
        }
        override suspend fun remove(profileId: String, kind: String, expected: String?) { deletes++; this.expected = expected }
    }

    @Test fun `a remote picture is downloaded once then reused from private cache`() = runTest {
        val files = Files(); val remote = Remote(); val engine = ProfileMediaEngine(files, remote)
        val first = engine.synchronize("alice", "profile", "avatar", null, v1)
        assertEquals(first, engine.synchronize("alice", "profile", "avatar", first, v1))
        assertEquals(1, remote.downloads)
        assertEquals(1, files.saved)
        engine.synchronize("bob", "profile", "avatar", null, v1)
        assertEquals(2, remote.downloads)
    }
    @Test fun `an unknown old local file cannot replace an existing cloud picture`() = runTest {
        val files = Files(); val remote = Remote(); val engine = ProfileMediaEngine(files, remote)
        val result = engine.synchronize("alice", "profile", "avatar", "file:old", v1)
        assertTrue(result?.startsWith("file:cache-") == true)
        assertEquals(0, remote.uploads)
        assertEquals(1, remote.downloads)
    }

    @Test fun `an edited photo survives offline failure and is retried after recreation`() = runTest {
        val files = Files(); val remote = Remote(); val engine = ProfileMediaEngine(files, remote)
        engine.edited("alice", "profile", "avatar", "file:my-photo")
        remote.offline = true
        assertTrue(runCatching { engine.synchronize("alice", "profile", "avatar", "file:my-photo", v1) }.isFailure)
        assertEquals(true, files.loadState("alice.profile.avatar")?.pending)
        remote.offline = false
        assertEquals("file:my-photo", ProfileMediaEngine(files, remote).synchronize("alice", "profile", "avatar", "file:my-photo", v1))
        assertEquals(false, files.loadState("alice.profile.avatar")?.pending)
        assertEquals(v1, remote.expected)
    }
    @Test fun `a newer edit made during upload remains pending`() = runTest {
        val files = Files(); val remote = Remote(); val engine = ProfileMediaEngine(files, remote)
        engine.edited("alice", "profile", "background", "file:first")
        remote.onUpload = { engine.edited("alice", "profile", "background", "file:newer") }
        assertEquals("file:newer", engine.synchronize("alice", "profile", "background", "file:first", v1))
        assertEquals(true, files.loadState("alice.profile.background")?.pending)
    }
    @Test fun `an automatic first import does not overwrite a cloud photo established by another device`() = runTest {
        val files = Files(); val remote = Remote(); val engine = ProfileMediaEngine(files, remote)
        remote.offline = true
        assertTrue(runCatching { engine.synchronize("alice", "profile", "avatar", "file:old-local", null) }.isFailure)
        assertEquals(true, files.loadState("alice.profile.avatar")?.automatic)
        remote.offline = false
        val result = engine.synchronize("alice", "profile", "avatar", "file:old-local", v1)
        assertTrue(result?.startsWith("file:cache-") == true)
        assertEquals(1, remote.uploads)
        assertEquals(1, remote.downloads)
    }

    @Test fun `explicit clearing removes the remote picture with its expected version`() = runTest {
        val files = Files(); val remote = Remote(); val engine = ProfileMediaEngine(files, remote)
        engine.edited("alice", "profile", "avatar", null)
        assertEquals(null, engine.synchronize("alice", "profile", "avatar", null, v1))
        assertEquals(1, remote.deletes)
        assertEquals(v1, remote.expected)
    }
    @Test fun `a mismatching digest does not replace an existing local picture`() = runTest {
        val files = Files(); files.digest = "c".repeat(64)
        val engine = ProfileMediaEngine(files, Remote())
        assertTrue(runCatching { engine.synchronize("alice", "profile", "avatar", "file:old", v1) }.isFailure)
        assertEquals(0, files.saved)
    }
}
