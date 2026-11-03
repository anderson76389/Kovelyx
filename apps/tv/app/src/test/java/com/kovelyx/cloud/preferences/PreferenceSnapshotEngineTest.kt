package com.kovelyx.cloud.preferences

import com.kovelyx.cloud.catalogues.CatalogueStateStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class PreferenceSnapshotEngineTest {
    private suspend inline fun <reified T : Throwable> assertFailsWith(crossinline block: suspend () -> Unit) {
        try { block() } catch (error: Throwable) { if (error is T) return; throw error }
        fail("Expected " + T::class.simpleName)
    }

    private class Store : CatalogueStateStore {
        val data = mutableMapOf<String, String>()
        override fun load(key: String) = data[key]
        override fun compareAndSave(key: String, expected: String?, next: String): Boolean {
            if (data[key] != expected) return false
            data[key] = next; return true
        }
    }
    private val red = buildJsonObject { put("theme", "red") }
    private val blue = buildJsonObject { put("theme", "blue") }
    private class Remote(var row: PreferenceRow? = null) { var writes = 0; var lost = false; var conflict = false }
    private inner class Device(val remote: Remote, var local: JsonObject = JsonObject(emptyMap()), val owner: String = "owner", val profile: String = "profile") {
        val engine = PreferenceSnapshotEngine(Store())
        var active = true
        var failApply = false
        var duringPublish: (() -> Unit)? = null
        fun edit(value: JsonObject) { local = value; engine.edited(owner, profile, "visual") }
        suspend fun sync() = engine.synchronize(owner, profile, "visual", remote.row,
            current = { local }, apply = { expected, next ->
                if (local != expected) false else { local = next; if (failApply) error("Réception interrompue"); true }
            }, publish = { payload, revision ->
                if (remote.conflict) { remote.conflict = false; remote.row = PreferenceRow(blue, 1); error("Autre appareil publié en premier") }
                check(revision == (remote.row?.revision ?: 0))
                remote.row = PreferenceRow(payload, revision + 1); remote.writes++
                duringPublish?.also { duringPublish = null; it() }
                if (remote.lost) { remote.lost = false; error("Réponse perdue") }
            }, isCurrent = { active })
    }
    @Test fun desktopMobileAndTvReceiveThemeWithoutEchoWrites() = runBlocking {
        val remote = Remote()
        val pc = Device(remote, red); assertTrue(pc.sync())
        val phone = Device(remote); val tv = Device(remote)
        assertTrue(phone.sync()); assertTrue(tv.sync())
        assertEquals(red, phone.local); assertEquals(red, tv.local)
        phone.edit(blue); assertTrue(phone.sync()); assertTrue(pc.sync()); assertTrue(tv.sync())
        assertEquals(blue, pc.local); assertEquals(blue, tv.local)
        repeat(3) { assertTrue(pc.sync()); assertTrue(phone.sync()); assertTrue(tv.sync()) }
        assertEquals(2, remote.writes)
    }
    @Test fun firstEmptyDeviceCannotOverwriteAConcurrentConfiguredDevice() = runBlocking {
        val d = Device(Remote()); d.remote.conflict = true
        assertFailsWith<IllegalStateException> { d.sync() }
        assertTrue(d.sync()); assertEquals(blue, d.local); assertEquals(0, d.remote.writes)
    }
    @Test fun firstLocalEditWinsOlderCloudValue() = runBlocking {
        val d = Device(Remote(PreferenceRow(red, 3))); d.edit(blue)
        assertTrue(d.sync()); assertEquals(blue, d.remote.row!!.payload)
    }
    @Test fun emptyValueIsAnExplicitRemovalAndReachesNewDevice() = runBlocking {
        val d = Device(Remote(), red); d.sync(); d.edit(JsonObject(emptyMap())); d.sync()
        val next = Device(d.remote, blue); assertTrue(next.sync()); assertEquals(JsonObject(emptyMap()), next.local)
    }
    @Test fun lostPublicationResponseDoesNotRepeatWrite() = runBlocking {
        val d = Device(Remote(), red); d.remote.lost = true
        assertFailsWith<IllegalStateException> { d.sync() }
        assertTrue(d.sync()); assertEquals(1, d.remote.writes)
    }
    @Test fun editDuringPublicationRemainsPending() = runBlocking {
        val d = Device(Remote(), red); d.duringPublish = { d.edit(blue) }
        assertFalse(d.sync()); assertTrue(d.sync()); assertEquals(blue, d.remote.row!!.payload)
    }
    @Test fun failedPartialReceptionCannotBeUploadedAsALocalChange() = runBlocking {
        val d = Device(Remote(), red); d.sync()
        d.remote.row = PreferenceRow(blue, 2); d.failApply = true
        assertFailsWith<IllegalStateException> { d.sync() }
        d.local = JsonObject(emptyMap()) // a partial local import must never become authoritative
        d.failApply = false
        assertTrue(d.sync()); assertEquals(blue, d.local); assertEquals(1, d.remote.writes)
    }
    @Test fun switchedAccountCannotPublish() = runBlocking {
        val d = Device(Remote(), red); d.active = false
        assertFailsWith<IllegalStateException> { d.sync() }; assertEquals(0, d.remote.writes)
    }
    @Test fun checkpointAndPendingChangesSurviveRestart() = runBlocking {
        val store = Store(); val engine = PreferenceSnapshotEngine(store)
        engine.edited("owner", "profile", "visual")
        var published: JsonObject? = null
        val restarted = PreferenceSnapshotEngine(store)
        assertTrue(restarted.synchronize("owner", "profile", "visual", PreferenceRow(red, 2),
            current = { blue }, apply = { _, _ -> throw AssertionError("Une modification locale ne doit pas être écrasée") },
            publish = { payload, revision -> assertEquals(2, revision); published = payload }, isCurrent = { true }))
        assertEquals(blue, published)
    }
}
