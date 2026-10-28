package com.nuvio.app.features.collection

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.cloud.CloudflareProfileRecords
import com.nuvio.app.features.profiles.ProfileRepository
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

object CollectionSyncService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("CollectionSyncService")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val PUSH_DEBOUNCE_MS = 1500L

    @Volatile
    var isSyncingFromRemote: Boolean = false

    private var pushJob: Job? = null
    private var observeJob: Job? = null

    fun startObserving() {
        if (observeJob?.isActive == true) return
        observeLocalChangesAndPush()
    }

    suspend fun pullFromServer(profileId: Int) {
        pullFromServerChecked(profileId)
    }

    /** Un import destructif ne doit jamais continuer si le chargement du compte a échoué. */
    suspend fun pullFromServerChecked(profileId: Int): Boolean {
        if (ProfileRepository.activeProfileId != profileId) return false
        return runCatching {
            val rows = CloudflareProfileRecords.records(profileId)
            if (ProfileRepository.activeProfileId != profileId) return@runCatching false
            val manifest = CloudflareProfileRecords.lookup(rows, "catalogues", "collections-manifest")
                ?: return@runCatching true
            val metadata = manifest["payload"]?.jsonObject
                ?: error("Manifest des collections D1 invalide")
            val version = metadata["version"]?.jsonPrimitive?.content.orEmpty()
            val count = metadata["chunks"]?.jsonPrimitive?.intOrNull ?: -1
            require(version.matches(Regex("[0-9a-f]{1,16}")) && count in 1..256) {
                "Collections Cloudflare : fragments invalides"
            }
            val remoteText = (0 until count).joinToString("") { index ->
                CloudflareProfileRecords.lookup(rows, "catalogues", "collections-$version-$index")
                    ?.get("payload")?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: error("Fragment D1 manquant : $index")
            }
            val remoteCollectionsJson = json.parseToJsonElement(remoteText)
            val remoteJson = remoteCollectionsJson.toString()
            val localJson = CollectionRepository.exportToJson()
            if (ProfileRepository.activeProfileId != profileId) return@runCatching false
            if (remoteJson == localJson) {
                log.d { "pullFromServer — remote matches local" }
                return@runCatching true
            }
            val remoteCollections = runCatching {
                json.decodeFromString<List<Collection>>(remoteJson)
            }.getOrNull()
            if (remoteCollections == null) {
                log.w { "pullFromServer — failed to parse remote collections JSON" }
                return@runCatching false
            }
            if (ProfileRepository.activeProfileId != profileId) return@runCatching false
            isSyncingFromRemote = true
            try {
                CollectionRepository.applyFromRemote(remoteCollections, remoteCollectionsJson)
            } finally {
                isSyncingFromRemote = false
            }
            log.i { "pullFromServer — applied " + remoteCollections.size + " collections from remote" }
            true
        }.getOrElse { error ->
            isSyncingFromRemote = false
            log.e(error) { "pullFromServer — FAILED" }
            false
        }
    }

    fun triggerPush() {
        pushJob?.cancel()
        pushJob = scope.launch {
            val profileId = ProfileRepository.activeProfileId
            delay(500)
            if (ProfileRepository.activeProfileId != profileId) return@launch
            if (isSyncingFromRemote) return@launch
            val authState = AuthRepository.state.value
            if (authState !is AuthState.Authenticated || authState.isAnonymous) return@launch
            pushToRemote(profileId)
        }
    }

    private suspend fun pushToRemote(profileId: Int) {
        runCatching {
            if (ProfileRepository.activeProfileId != profileId) return@runCatching
            val collectionsJson = CollectionRepository.exportToJson()
            if (ProfileRepository.activeProfileId != profileId) return@runCatching
            val jsonElement = runCatching {
                json.parseToJsonElement(collectionsJson)
            }.getOrDefault(JsonArray(emptyList()))

            // Commit immutable fragments before changing the manifest. The old
            // manifest stays readable until the complete new version is uploaded.
            val version = kotlin.random.Random.nextLong().toULong().toString(16)
            val chunks = collectionsJson.chunked(6000)
            require(chunks.size in 1..256) { "Collections Cloudflare trop volumineuses" }
            chunks.forEachIndexed { index, chunk ->
                CloudflareProfileRecords.write(profileId, "catalogues", "collections-$version-$index",
                    buildJsonObject { put("text", chunk) })
            }
            CloudflareProfileRecords.write(profileId, "catalogues", "collections-manifest",
                buildJsonObject {
                    put("version", version)
                    put("chunks", chunks.size)
                })
            log.d { "pushToRemote — success" }
        }.onFailure { e ->
            log.e(e) { "pushToRemote — FAILED" }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeLocalChangesAndPush() {
        observeJob = scope.launch {
            CollectionRepository.localChangeEvents
                .debounce(PUSH_DEBOUNCE_MS)
                .collect {
                    val profileId = ProfileRepository.activeProfileId
                    if (isSyncingFromRemote) return@collect
                    val authState = AuthRepository.state.value
                    if (authState !is AuthState.Authenticated || authState.isAnonymous) return@collect
                    pushToRemote(profileId)
                }
        }
    }
}
