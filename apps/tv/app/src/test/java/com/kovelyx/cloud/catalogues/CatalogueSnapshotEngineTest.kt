package com.kovelyx.cloud.catalogues

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*


class CatalogueSnapshotEngineTest {
    private suspend inline fun <reified T : Throwable> assertFailsWith(crossinline block: suspend () -> Unit) {
        try { block() } catch (error: Throwable) { if (error is T) return; throw error }
        fail("Expected " + T::class.simpleName)
    }
    private class Store : CatalogueStateStore {
        val data = mutableMapOf<String, String>()
        override fun load(key: String) = data[key]
        override fun compareAndSave(key: String, expected: String?, next: String): Boolean {
            if (data[key] != expected) return false
            data[key] = next
            return true
        }
    }
    // Deterministic test digest: production supplies SHA-256 through native storage.
    private fun digest(bytes: ByteArray): String {
        var a = 1469598103934665603L
        bytes.forEach { a = (a xor it.toLong()) * 1099511628211L }
        return a.toULong().toString(16).padStart(16, '0').repeat(4)
    }
    private inner class Remote : CatalogueRemote {
        var head: CatalogueHead? = null
        val pieces = mutableMapOf<String, MutableList<CatalogueChunk>>()
        var losePublishResponse = false
        var reads = 0
        var writes = 0
        var duringWrite: (() -> Unit)? = null
        override suspend fun putChunk(profile: String, kind: String, hash: String, chunk: CatalogueChunk) {
            writes++
            val list = pieces.getOrPut(hash) { mutableListOf() }
            list.removeAll { it.index == chunk.index }; list.add(chunk)
            duringWrite?.also { duringWrite = null; it() }
        }
        override suspend fun readChunks(profile: String, kind: String, hash: String): List<CatalogueChunk> {
            reads++; return pieces[hash].orEmpty()
        }
        override suspend fun publish(profile: String, kind: String, head: CatalogueHead) {
            check(head.revision == (this.head?.revision ?: 0))
            this.head = head.copy(revision = head.revision + 1)
            if (losePublishResponse) { losePublishResponse = false; error("Publication reçue, réponse perdue") }
        }
    }
    private val one = """[{"id":"one","title":"Ma collection","folders":[{"id":"f","title":"Films","sources":[{"provider":"addon","addonId":"kaptain","catalogId":"films","type":"movie"}]}]}]"""
    private val two = """[{"id":"two","title":"Autre appareil","folders":[]}]"""
    private inner class Device(val remote: Remote, var text: String = "[]", val kind: String = "collections") {
        val engine = CatalogueSnapshotEngine(Store(), ::digest, remote)
        var exports = 0
        var applies = 0
        var active = true
        fun edit(next: String) { text = next; engine.edited("owner", 1, "uuid", kind) }
        suspend fun sync(): Boolean = engine.synchronize("owner", 1, "uuid", kind, remote.head,
            current = { exports++; text }, apply = { expected, next ->
                if (text != expected) false else { applies++; text = next; true }
            }, isCurrent = { active })
    }

    @Test fun missingLocalCatalogueIsRecoveredEvenWhenCloudRevisionIsUnchanged() = runBlocking {
        val remote = Remote()
        val pc = Device(remote, one); assertTrue(pc.sync())
        val phone = Device(remote); assertTrue(phone.sync())
        assertTrue(phone.text.contains("Ma collection"))
        val writes = remote.writes
        phone.text = "[]" // restored checkpoint with missing/reset local storage
        assertTrue(phone.sync())
        assertTrue(phone.text.contains("Ma collection"))
        assertEquals(writes, remote.writes)
        phone.edit("[]") // intentional removal must still win
        assertTrue(phone.sync())
        assertTrue(pc.sync()); assertEquals("[]", pc.text)
    }

    @Test fun unicodeBoundaryPreservesExactText() {
        val text = "x".repeat(5999) + "😀" + "é".repeat(6000)
        val parts = splitCatalogue(text)
        assertEquals(text, parts.joinToString("") { it.text })
        assertTrue(parts.all { it.text.length <= 6000 && it.text.last().code !in 0xD800..0xDBFF })
    }

    @Test fun importTransfersDefinitionsAndIdleDoesNoExtraWork() = runBlocking {
        val remote = Remote(); val pc = Device(remote, one); assertTrue(pc.sync())
        val tv = Device(remote); assertTrue(tv.sync())
        assertEquals(Json.parseToJsonElement(one), Json.parseToJsonElement(tv.text))
        val reads = remote.reads; val writes = remote.writes; val exports = tv.exports
        repeat(10) { assertTrue(tv.sync()) }
        assertEquals(reads, remote.reads); assertEquals(writes, remote.writes); assertEquals(exports + 10, tv.exports) // One local integrity check per poll, no network transfer.
    }

    @Test fun initialMigrationKeepsEachDevicesCollectionsThenSyncsDeletion() = runBlocking {
        val remote = Remote(); val pc = Device(remote, one); assertTrue(pc.sync())
        val tv = Device(remote, two)
        assertFalse(tv.sync()) // Union must first be published on the next pass.
        assertTrue(tv.sync()); assertTrue(pc.sync())
        assertEquals(setOf("one", "two"), Json.parseToJsonElement(pc.text).jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet())
        pc.edit("[]"); assertTrue(pc.sync()); assertTrue(tv.sync())
        assertEquals("[]", tv.text)
    }

    @Test fun missingOrCorruptChunkNeverReplacesLocalCollection() = runBlocking {
        val remote = Remote(); assertTrue(Device(remote, one).sync())
        val tv = Device(remote, two)
        remote.pieces[remote.head!!.sha256] = mutableListOf(CatalogueChunk(0, "[]"))
        assertFailsWith<IllegalArgumentException> { tv.sync() }
        assertEquals(two, tv.text); assertEquals(0, tv.applies)
    }

    @Test fun localEditDuringUploadIsRetriedWithoutLosingIt() = runBlocking {
        val remote = Remote(); val pc = Device(remote, one)
        remote.duringWrite = { pc.edit(two) }
        assertFalse(pc.sync()); assertEquals(two, pc.text)
        assertTrue(pc.sync())
        val tv = Device(remote); assertTrue(tv.sync())
        assertEquals(Json.parseToJsonElement(two), Json.parseToJsonElement(tv.text))
    }

    @Test fun lostPublishResponseDoesNotResurrectReplacedInitialCollection() = runBlocking {
        val remote = Remote(); val pc = Device(remote, one)
        remote.losePublishResponse = true
        assertFailsWith<IllegalStateException> { pc.sync() }
        pc.edit(two)
        assertTrue(pc.sync())
        val tv = Device(remote); assertTrue(tv.sync())
        assertEquals(Json.parseToJsonElement(two), Json.parseToJsonElement(tv.text))
    }

    @Test fun accountChangeBeforeReceiveDoesNotApplyOtherOwnersData() = runBlocking {
        val remote = Remote(); assertTrue(Device(remote, one).sync())
        val tv = Device(remote, two); tv.active = false
        assertFailsWith<IllegalStateException> { tv.sync() }
        assertEquals(two, tv.text); assertEquals(0, tv.applies)
    }

    @Test fun layoutEditPreservesUnknownFieldsAndUnavailableCatalogues() = runBlocking {
        val remote = Remote()
        val pc = Device(remote, """{"items":[{"addon_id":"a","type":"movie","catalog_id":"x","enabled":true,"order":0,"future_option":true},{"addon_id":"b","type":"series","catalog_id":"y","order":1}],"future_root":"preserved"}""", "layout")
        assertTrue(pc.sync())
        val tv = Device(remote, """{"items":[]}""", "layout"); assertTrue(tv.sync())
        tv.edit("""{"items":[{"addon_id":"a","type":"movie","catalog_id":"x","enabled":false,"order":0}]}""")
        assertTrue(tv.sync())
        val published = remote.readChunks("uuid", "layout", remote.head!!.sha256).joinToString("") { it.text }
        val obj = Json.parseToJsonElement(published).jsonObject
        assertEquals("preserved", obj["future_root"]!!.jsonPrimitive.content)
        assertEquals(2, obj["items"]!!.jsonArray.size)
        assertEquals(false, obj["items"]!!.jsonArray[0].jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertEquals(true, obj["items"]!!.jsonArray[0].jsonObject["future_option"]!!.jsonPrimitive.boolean)
    }

    @Test fun sameSourceWithDesktopDecorativeIdAndTvEncodingIsNotDuplicated() {
        val desktop = """[{"id":"one","title":"Collection","folders":[{"id":"f","title":"Films","sources":[{"id":"src-legacy","name":"Films","title":"Liste","provider":"addon","addonId":"kaptain","type":"movie","catalogId":"films","genre":null}]}]}]"""
        val tv = """[{"id":"one","title":"Collection","folders":[{"id":"f","title":"Films","sources":[{"provider":"addon","addonId":"kaptain","type":"movie","catalogId":"films"}]}]}]"""
        val tmdbDesktop = """[{"id":"one","title":"Collection","folders":[{"id":"f","title":"Films","sources":[{"id":"src-legacy","name":"Top 10","title":"Liste","genre":"Top 10 Movies","provider":"tmdb","tmdbSourceType":"LIST","tmdbId":123,"mediaType":"MOVIE","sortBy":"original","filters":{"year":null}}]}]}]"""
        val tmdbTv = """[{"id":"one","title":"Collection","folders":[{"id":"f","title":"Films","sources":[{"provider":"tmdb","tmdbSourceType":"LIST","tmdbId":123,"mediaType":"MOVIE","sortBy":"original","filters":{}}]}]}]"""
        for ((remote, local) in listOf(desktop to tv, tmdbDesktop to tmdbTv)) {
            for (preferLocal in listOf(true, false)) {
                val merged = Json.parseToJsonElement(mergeInitialCatalogue("collections", remote, local, preferLocal)).jsonArray
                assertEquals(1, merged[0].jsonObject["folders"]!!.jsonArray[0].jsonObject["sources"]!!.jsonArray.size)
            }
        }
    }

    @Test fun distinctNestedSourceTitlesAreNotDeduplicated() {
        val a = """[{"id":"one","folders":[{"id":"f","sources":[{"provider":"tmdb","filters":{"title":"A"}}]}]}]"""
        val b = a.replace("\"A\"", "\"B\"")
        val merged = Json.parseToJsonElement(mergeInitialCatalogue("collections", a, b, false)).jsonArray
        assertEquals(2, merged[0].jsonObject["folders"]!!.jsonArray[0].jsonObject["sources"]!!.jsonArray.size)
    }

    @Test fun oversizedOrCredentialPayloadNeverUploads() = runBlocking {
        for (payload in listOf("""[{"api_key":"private"}]""", "[\"" + "x".repeat(CATALOGUE_MAX_BYTES) + "\"]")) {
            val remote = Remote(); val pc = Device(remote, payload)
            assertFailsWith<IllegalArgumentException> { pc.sync() }
            assertEquals(0, remote.writes)
        }
    }
}
