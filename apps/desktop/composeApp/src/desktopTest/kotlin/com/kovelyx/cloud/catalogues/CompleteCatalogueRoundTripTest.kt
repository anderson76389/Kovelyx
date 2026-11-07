package com.kovelyx.cloud.catalogues

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class CompleteCatalogueRoundTripTest {
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private class Store : CatalogueStateStore {
        private val data = mutableMapOf<String, String>()
        override fun load(key: String) = data[key]
        override fun compareAndSave(key: String, expected: String?, next: String): Boolean {
            if (data[key] != expected) return false
            data[key] = next; return true
        }
    }
    @Test fun completePublicCollectionTransfersAllFoldersAndArtworkThenStaysIdle() = runBlocking {
        val relative = "tests/fixtures/ma-collection-complete.json.gz.b64"
        val fixture = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, relative) }.first { it.isFile }
        val original = GZIPInputStream(Base64.getMimeDecoder().decode(fixture.readText()).inputStream()).use { it.readBytes() }
        assertEquals("3278c178ab0fb831ec99af2aa7a270f5cc9ee8045da0045fa948557a7aaaadef", digest(original))
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val raw = json.parseToJsonElement(original.decodeToString()).jsonArray
        assertEquals(18, raw.size)
        assertEquals(756, raw.sumOf { it.jsonObject["folders"]!!.jsonArray.size })
        val decoded = json.decodeFromJsonElement<List<com.kovelyx.app.features.collection.Collection>>(raw)
        val payload = com.kovelyx.app.features.collection.CollectionJsonPreserver.merge(json, raw, decoded).toString()
        assertTrue(payload.encodeToByteArray().size <= CATALOGUE_MAX_BYTES, "Les valeurs par défaut ne doivent pas faire dépasser 4 Mo")
        var head: CatalogueHead? = null
        val chunks = mutableMapOf<String, MutableList<CatalogueChunk>>()
        var writes = 0; var reads = 0
        val remote = object : CatalogueRemote {
            override suspend fun putChunk(profile: String, kind: String, hash: String, chunk: CatalogueChunk) {
                writes++
                chunks.getOrPut(hash) { mutableListOf() }.apply { removeAll { it.index == chunk.index }; add(chunk) }
            }
            override suspend fun readChunks(profile: String, kind: String, hash: String): List<CatalogueChunk> { reads++; return chunks[hash].orEmpty() }
            override suspend fun publish(profile: String, kind: String, value: CatalogueHead) {
                assertEquals(head?.revision ?: 0, value.revision)
                head = value.copy(revision = value.revision + 1)
            }
        }
        class Device(var text: String) {
            val engine = CatalogueSnapshotEngine(Store(), ::digest, remote)
            suspend fun sync() = engine.synchronize("public-fixture", 1, "fixture-profile", "collections", head,
                current = { text }, apply = { expected, next -> if (text != expected) false else { text = next; true } }, isCurrent = { true })
        }
        val pc = Device(payload)
        assertTrue(pc.sync())
        val receiver = Device("[]")
        repeat(3) { receiver.sync() }
        val received = json.parseToJsonElement(receiver.text).jsonArray
        assertEquals(raw.map { it.jsonObject["id"] }, received.map { it.jsonObject["id"] })
        val receivedFolders = received.flatMap { it.jsonObject["folders"]!!.jsonArray }.associateBy { it.jsonObject["id"] }
        for (folder in raw.flatMap { it.jsonObject["folders"]!!.jsonArray }) {
            val actual = receivedFolders[folder.jsonObject["id"]]!!.jsonObject
            for (key in listOf("title", "coverImageUrl", "titleLogoUrl", "focusGifUrl", "heroBackdropUrl", "heroVideoUrl")) {
                folder.jsonObject[key]?.let { assertEquals(it, actual[key], "Dossier ou affiche perdue : $key") }
            }
        }
        val before = writes to reads
        assertTrue(receiver.sync())
        assertEquals(before, writes to reads, "Un catalogue inchangé ne doit pas être retransféré")
        assertTrue(head!!.utf8Bytes <= CATALOGUE_MAX_BYTES)
        assertTrue(head!!.parts > 100)
    }
}
