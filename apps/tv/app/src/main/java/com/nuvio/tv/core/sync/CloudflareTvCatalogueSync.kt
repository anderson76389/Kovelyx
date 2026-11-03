package com.nuvio.tv.core.sync

import android.content.Context
import android.util.Log
import com.kovelyx.cloud.catalogues.*
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.json.JSONObject

@Singleton
class CloudflareTvCatalogueSync @Inject constructor(
    @ApplicationContext context: Context,
    private val profiles: ProfileManager,
    private val collections: CollectionsDataStore,
    private val layout: LayoutPreferenceDataStore,
    private val addons: AddonRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("kovelyx_cloud_catalogues", Context.MODE_PRIVATE)
    private val stateLock = Any()
    private val requests = Channel<Int>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = CatalogueSnapshotEngine(object : CatalogueStateStore {
        override fun load(key: String): String? = synchronized(stateLock) { prefs.getString(key, null) }
        override fun compareAndSave(key: String, expected: String?, next: String): Boolean = synchronized(stateLock) {
            if (prefs.getString(key, null) != expected) false
            else { prefs.edit().putString(key, next).apply(); true }
        }
    }, { bytes -> MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } },
        object : CatalogueRemote {
            override suspend fun putChunk(profile: String, kind: String, hash: String, chunk: CatalogueChunk) =
                CloudflareTvAccountClient.putCatalogueChunk(profile, kind, hash, chunk.index, chunk.text)
            override suspend fun readChunks(profile: String, kind: String, hash: String) =
                CloudflareTvAccountClient.syncRows(profile, kind, hash).map {
                    val payload = it.getJSONObject("payload")
                    CatalogueChunk(payload.getInt("index"), payload.getString("text"))
                }
            override suspend fun publish(profile: String, kind: String, head: CatalogueHead) =
                CloudflareTvAccountClient.publishCatalogueHead(profile, kind, head)
        })

    init {
        collections.captureCloudEdit = { profileId -> captureEdit("collections", profileId) }
        layout.captureCloudEdit = { profileId -> captureEdit("layout", profileId) }
        scope.launch {
            for (profileId in requests) {
                delay(900)
                val owner = CloudflareTvAccountClient.account?.id ?: continue
                var retry = 2_000L
                while (profiles.activeProfileId.value == profileId && CloudflareTvAccountClient.account?.id == owner) {
                    try { if (synchronize(profileId)) break }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { Log.w("KovelyxCatalogues", "Catalogues TV en attente ; données locales conservées") }
                    delay(retry)
                    retry = (retry * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    private fun captureEdit(kind: String, profileId: Int): () -> Unit {
        val owner = CloudflareTvAccountClient.account?.id ?: return {}
        val uuid = profiles.profiles.value.firstOrNull { it.id == profileId }?.cloudProfileId
        return {
            engine.edited(owner, profileId, uuid, kind)
            if (kind == "collections") engine.edited(owner, profileId, uuid, "layout")
            request(profileId)
        }
    }

    fun edited(kind: String) { captureEdit(kind, profiles.activeProfileId.value).invoke() }
    fun request(profileId: Int = profiles.activeProfileId.value) { requests.trySend(profileId) }

    suspend fun synchronize(profileId: Int, uuid: String? = null, knownRows: List<JSONObject>? = null): Boolean {
        val owner = CloudflareTvAccountClient.account?.id ?: return true
        val linkedUuid = uuid ?: profiles.profiles.value.firstOrNull { it.id == profileId }?.cloudProfileId ?: return false
        fun current() = CloudflareTvAccountClient.account?.id == owner && profiles.activeProfileId.value == profileId &&
            profiles.profiles.value.firstOrNull { it.id == profileId }?.cloudProfileId == linkedUuid
        if (!current()) return false
        val rows = (knownRows ?: CloudflareTvAccountClient.syncRows(linkedUuid)).map { json.parseToJsonElement(it.toString()).jsonObject }
        var ready = true
        for (kind in listOf("collections", "layout")) {
            var capturedLayout = LocalHomeCatalogSettingsState()
            val done = engine.synchronize(owner, profileId, linkedUuid, kind, readCatalogueHead(rows, kind),
                current = {
                    check(current())
                    if (kind == "collections") collections.exportCurrentProfileJson() ?: "[]" else {
                        capturedLayout = layout.getHomeCatalogSettingsState()
                        val installed = addons.getInstalledAddons().first().enabledAddons()
                        json.encodeToString(buildHomeCatalogSyncPayload(installed, collections.getCurrentCollections(), capturedLayout))
                    }
                }, apply = { expected, text ->
                    check(current())
                    if (kind == "collections") collections.applyCloudflareJson(profileId, expected, text, ::current)
                    else layout.applyCloudflareLayout(profileId, capturedLayout, json.decodeFromString<SyncHomeCatalogPayload>(text), ::current)
                }, isCurrent = ::current)
            if (!done) ready = false
        }
        return ready
    }
}
