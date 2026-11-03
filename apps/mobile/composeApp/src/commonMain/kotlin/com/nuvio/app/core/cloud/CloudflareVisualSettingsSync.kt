package com.nuvio.app.core.cloud

import com.kovelyx.cloud.catalogues.CatalogueStateStore
import com.kovelyx.cloud.preferences.*
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.sync.SyncClientIdentityStorage
import com.nuvio.app.core.ui.*
import com.nuvio.app.core.poster.CustomPosterUrlRepository
import com.nuvio.app.core.poster.CustomPosterUrlStorage
import com.nuvio.app.features.collection.CollectionMobileSettingsRepository
import com.nuvio.app.features.collection.CollectionMobileSettingsStorage
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.MetaScreenSettingsStorage
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.settings.ThemeSettingsStorage
import com.nuvio.app.features.home.HomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Visual preferences only: never sends player hardware options or service credentials. */
object CloudflareVisualSettingsSync {
    internal val engine = PreferenceSnapshotEngine(object : CatalogueStateStore {
        override fun load(key: String) = SyncClientIdentityStorage.loadCloudflareCatalogues(key)
        override fun compareAndSave(key: String, expected: String?, next: String) =
            SyncClientIdentityStorage.compareAndSaveCloudflareCatalogues(key, expected, next)
    })
    @kotlin.concurrent.Volatile private var applying = false
    private fun uuid(profileId: Int) = ProfileRepository.state.value.profiles.firstOrNull { it.profileIndex == profileId }?.id
    private fun snapshot(): Map<String, JsonObject> {
        ThemeSettingsRepository.ensureLoaded(); MetaScreenSettingsRepository.ensureLoaded()
        PosterCardStyleRepository.ensureLoaded(); CardDepthStyleRepository.ensureLoaded()
        CustomPosterUrlRepository.ensureLoaded(); CollectionMobileSettingsRepository.ensureLoaded()
        fun text(value: String?) = buildJsonObject { put("value", value.orEmpty()) }
        return linkedMapOf(
            "visual_theme_v1" to ThemeSettingsStorage.exportToSyncPayload(),
            "visual_meta_v1" to text(MetaScreenSettingsStorage.loadPayload()),
            "visual_posters_v1" to text(PosterCardStyleStorage.loadPayload()),
            "visual_depth_v1" to text(CardDepthStyleStorage.loadPayload()),
            "visual_collection_v1" to text(CollectionMobileSettingsStorage.loadPayload()),
            "visual_custom_poster_v1" to buildJsonObject {
                put("pattern", CustomPosterUrlStorage.loadPattern().orEmpty())
                put("screens", JsonArray(CustomPosterUrlStorage.loadEnabledScreens().orEmpty().sorted().map(::JsonPrimitive)))
                put("allScreens", CustomPosterUrlStorage.loadEnabledScreens() == null)
            }
        )
    }
    fun edited() {
        if (!CloudflareAccountClient.enabled || applying) return
        val account = AuthRepository.state.value as? AuthState.Authenticated ?: return
        if (account.isAnonymous) return
        val profileId = ProfileRepository.activeProfileId
        val id = uuid(profileId) ?: return
        // The engine detects changed features against their durable baseline.
        // A first edit before initial sync must also win over an older remote value.
        val current = snapshot()
        val before = observed
        observed = current
        current.filter { before?.get(it.key) != it.value }.keys.forEach { engine.edited(account.userId, id, it) }
        CloudflareDataSync.request(profileId)
    }
    private var observed: Map<String, JsonObject>? = null
    fun observeBaseline() { observed = snapshot() }

    suspend fun synchronize(profileId: Int, id: String, rows: List<JsonObject>): Boolean {
        val owner = (AuthRepository.state.value as? AuthState.Authenticated)?.userId ?: return true
        fun current() = ProfileRepository.activeProfileId == profileId && uuid(profileId) == id &&
            CloudflareAccountClient.currentSession?.userId == owner &&
            (AuthRepository.state.value as? AuthState.Authenticated)?.userId == owner
        var ready = true
        for (feature in snapshot().keys) {
            val ok = engine.synchronize(owner, id, feature, preferenceRow(rows, feature),
                current = { check(current()); snapshot().getValue(feature) },
                apply = { expected, next -> withContext(Dispatchers.Main.immediate) {
                    check(current())
                    if (snapshot()[feature] != expected) false else {
                        applying = true
                        try { apply(feature, next); observed = snapshot() } finally { applying = false }
                        true
                    }
                } },
                publish = { value, revision -> check(current()); CloudflareAccountClient.writeSync(id, "profile_settings", feature, value, revision) },
                isCurrent = ::current)
            if (!ok) ready = false
        }
        observed = snapshot()
        return ready
    }
    private fun apply(feature: String, value: JsonObject) {
        val text = value["value"]?.jsonPrimitive?.content.orEmpty()
        when (feature) {
            "visual_theme_v1" -> { ThemeSettingsStorage.replaceFromSyncPayload(value); ThemeSettingsRepository.onProfileChanged() }
            "visual_meta_v1" -> { if (text.isNotBlank()) Json.parseToJsonElement(text).jsonObject
                MetaScreenSettingsStorage.savePayload(text); MetaScreenSettingsRepository.onProfileChanged() }
            "visual_posters_v1" -> { PosterCardStyleStorage.savePayload(text); PosterCardStyleRepository.onProfileChanged() }
            "visual_depth_v1" -> { CardDepthStyleStorage.savePayload(text); CardDepthStyleRepository.onProfileChanged() }
            "visual_collection_v1" -> { CollectionMobileSettingsStorage.savePayload(text); CollectionMobileSettingsRepository.onProfileChanged() }
            "visual_custom_poster_v1" -> {
                CustomPosterUrlStorage.savePattern(value["pattern"]?.jsonPrimitive?.content?.ifBlank { null })
                CustomPosterUrlStorage.saveEnabledScreens(if (value["allScreens"]?.jsonPrimitive?.booleanOrNull == true) null
                    else value["screens"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.toSet())
                CustomPosterUrlRepository.onProfileChanged()
            }
        }
        HomeRepository.applyCurrentSettings()
    }
}
