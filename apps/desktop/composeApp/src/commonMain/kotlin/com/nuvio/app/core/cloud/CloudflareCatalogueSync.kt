package com.nuvio.app.core.cloud

import co.touchlab.kermit.Logger
import com.kovelyx.cloud.catalogues.*
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.sync.SyncClientIdentityStorage
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.CollectionSyncService
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsSyncService
import com.nuvio.app.features.home.SyncHomeCatalogPayload
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

object CloudflareCatalogueSync {
    private val log = Logger.withTag("KovelyxCatalogues")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cachedEngine = atomic<CatalogueSnapshotEngine?>(null)
    private fun engine(): CatalogueSnapshotEngine? {
        cachedEngine.value?.let { return it }
        val files = CloudflareProfileMediaPlatform.files ?: return null
        return CatalogueSnapshotEngine(object : CatalogueStateStore {
            override fun load(key: String) = SyncClientIdentityStorage.loadCloudflareCatalogues(key)
            override fun compareAndSave(key: String, expected: String?, next: String) =
                SyncClientIdentityStorage.compareAndSaveCloudflareCatalogues(key, expected, next)
        }, files::sha256, object : CatalogueRemote {
            override suspend fun putChunk(profile: String, kind: String, hash: String, chunk: CatalogueChunk) =
                CloudflareAccountClient.putCatalogueChunk(profile, kind, hash, chunk.index, chunk.text)
            override suspend fun readChunks(profile: String, kind: String, hash: String) =
                CloudflareAccountClient.synchronizeAll(profile, kind, hash).map { row ->
                    val payload = row["payload"]!!.jsonObject
                    CatalogueChunk(payload["index"]!!.jsonPrimitive.int, payload["text"]!!.jsonPrimitive.content)
                }
            override suspend fun publish(profile: String, kind: String, head: CatalogueHead) =
                CloudflareAccountClient.publishCatalogueHead(profile, kind, head)
        }).let { created ->
            if (cachedEngine.compareAndSet(null, created)) created else cachedEngine.value
        }
    }

    fun edited(kind: String) {
        if (!CloudflareAccountClient.enabled) return
        val account = AuthRepository.state.value as? AuthState.Authenticated ?: return
        if (account.isAnonymous) return
        val profileId = ProfileRepository.activeProfileId
        val uuid = ProfileRepository.state.value.profiles.firstOrNull { it.profileIndex == profileId }?.id
        runCatching {
            val engine = engine() ?: return
            engine.edited(account.userId, profileId, uuid, kind)
            if (kind == "collections") engine.edited(account.userId, profileId, uuid, "layout")
        }.onFailure { log.w { "Modification de catalogue conservée localement ; synchronisation différée" } }
        CloudflareDataSync.request(profileId)
    }

    suspend fun synchronize(profileId: Int, uuid: String, rows: List<JsonObject>): Boolean {
        val account = AuthRepository.state.value as? AuthState.Authenticated ?: return true
        val engine = engine() ?: return true
        fun current() = (AuthRepository.state.value as? AuthState.Authenticated)?.userId == account.userId &&
            CloudflareAccountClient.currentSession?.userId == account.userId && ProfileRepository.activeProfileId == profileId &&
            ProfileRepository.state.value.profiles.firstOrNull { it.profileIndex == profileId }?.id == uuid
        var ready = true
        for (kind in listOf("collections", "layout")) {
            try {
                val synchronized = engine.synchronize(account.userId, profileId, uuid, kind, readCatalogueHead(rows, kind),
                    current = {
                        check(current())
                        CollectionRepository.initialize()
                        if (kind == "collections") CollectionRepository.exportToJson() else {
                            HomeCatalogSettingsRepository.syncCollections(CollectionRepository.collections.value)
                            json.encodeToString(HomeCatalogSettingsRepository.exportToSyncPayload())
                        }
                    }, apply = { expected, text ->
                        check(current())
                        if (kind == "collections") {
                            val raw = json.parseToJsonElement(text)
                            if (raw.jsonArray.isNotEmpty()) require(CollectionRepository.validateJson(text).valid) { "Catalogue non compatible" }
                            val collections = json.decodeFromJsonElement<List<Collection>>(raw)
                            withContext(Dispatchers.Main.immediate) {
                                check(current())
                                if (CollectionRepository.exportToJson() != expected) false else {
                                    CollectionSyncService.isSyncingFromRemote = true
                                    try { CollectionRepository.applyFromRemote(collections, raw) }
                                    finally { CollectionSyncService.isSyncingFromRemote = false }
                                    HomeCatalogSettingsRepository.syncCollections(CollectionRepository.collections.value)
                                    true
                                }
                            }
                        } else {
                            val payload = json.decodeFromString<SyncHomeCatalogPayload>(text)
                            withContext(Dispatchers.Main.immediate) {
                                check(current())
                                if (json.encodeToString(HomeCatalogSettingsRepository.exportToSyncPayload()) != expected) false else {
                                    HomeCatalogSettingsSyncService.isSyncingFromRemote = true
                                    try { HomeCatalogSettingsRepository.applyFromRemote(payload) }
                                    finally { HomeCatalogSettingsSyncService.isSyncingFromRemote = false }
                                    true
                                }
                            }
                        }
                    }, isCurrent = ::current)
                if (!synchronized) ready = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                ready = false
                log.w { "Catalogue en attente ; définitions locales conservées" }
            }
        }
        return ready
    }
}
