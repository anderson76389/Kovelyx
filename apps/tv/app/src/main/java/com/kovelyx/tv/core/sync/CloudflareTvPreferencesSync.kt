package com.kovelyx.tv.core.sync

import android.content.Context
import androidx.datastore.preferences.core.*
import com.kovelyx.cloud.catalogues.CatalogueStateStore
import com.kovelyx.cloud.preferences.*
import com.kovelyx.tv.BuildConfig
import com.kovelyx.tv.core.auth.CloudflareTvAccountClient
import com.kovelyx.tv.core.plugin.PluginManager
import com.kovelyx.tv.core.profile.ProfileManager
import com.kovelyx.tv.data.local.PluginDataStore
import com.kovelyx.tv.data.local.ProfileDataStoreFactory
import com.kovelyx.tv.domain.model.RemotePluginInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.json.JSONObject

@Singleton
class CloudflareTvPreferencesSync @Inject constructor(
    @ApplicationContext context: Context,
    private val profiles: ProfileManager,
    private val factory: ProfileDataStoreFactory,
    private val plugins: PluginDataStore,
    private val manager: Provider<PluginManager>,
) {
    private val prefs = context.getSharedPreferences("kovelyx_cloud_preferences", Context.MODE_PRIVATE)
    private val lock = Any()
    private val engine = PreferenceSnapshotEngine(object : CatalogueStateStore {
        override fun load(key: String) = synchronized(lock) { prefs.getString(key, null) }
        override fun compareAndSave(key: String, expected: String?, next: String) = synchronized(lock) {
            if (prefs.getString(key, null) != expected) false else { prefs.edit().putString(key, next).apply(); true }
        }
    })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Int>(Channel.CONFLATED)
    @Volatile private var applying = false
    private val pluginFeature = "plugin_repositories_v1"
    init {
        scope.launch {
            profiles.activeProfileId.collectLatest { pid ->
                factory.get(pid, "theme_settings").data.map { theme(it) }.distinctUntilChanged().drop(1).collect {
                    if (!applying) edited("visual_theme_v1", pid)
                }
            }
        }
        scope.launch {
            for (pid in requests) {
                delay(900)
                try { synchronize(pid) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Main D1 worker also retries while the profile is active. */ }
            }
        }
    }
    fun edited(feature: String = pluginFeature, pid: Int = profiles.activeProfileId.value) {
        if (!CloudflareTvAccountClient.enabled || applying) return
        val owner = CloudflareTvAccountClient.account?.id ?: return
        val uuid = profiles.profiles.value.firstOrNull { it.id == pid }?.cloudProfileId ?: return
        engine.edited(owner, uuid, feature)
        requests.trySend(pid)
    }
    private fun theme(preferences: Preferences): JsonObject = buildJsonObject {
        fun text(key: String) { preferences[stringPreferencesKey(key)]?.let { value ->
            put(key, buildJsonObject { put("type", "string"); put("value", value) })
        } }
        text("selected_theme"); text("custom_theme_colors")
        preferences[booleanPreferencesKey("amoled_mode")]?.let { value ->
            put("amoled_enabled", buildJsonObject { put("type", "boolean"); put("value", value) })
        }
    }
    private suspend fun pluginSnapshot(): JsonObject {
        val byId = plugins.repositories.first().associateBy { it.id }
        return buildJsonObject {
        put("repositories", JsonArray(plugins.repositories.first().map { repo -> buildJsonObject {
            put("url", repo.url); put("name", repo.name); put("repo_type", repo.type.name)
        } }))
        put("disabled", JsonArray(plugins.scrapers.first().filter { !it.enabled }.map { JsonPrimitive(byId[it.repositoryId]?.url.orEmpty() + "|" + it.filename) }))
        put("enabled", plugins.pluginsEnabled.first()); put("group", plugins.groupStreamsByRepository.first())
    }
    }
    suspend fun synchronize(pid: Int, linked: String? = null, knownRows: List<JSONObject>? = null): Boolean {
        val owner = CloudflareTvAccountClient.account?.id ?: return true
        val uuid = linked ?: profiles.profiles.value.firstOrNull { it.id == pid }?.cloudProfileId ?: return false
        fun current() = CloudflareTvAccountClient.account?.id == owner && profiles.activeProfileId.value == pid &&
            profiles.profiles.value.firstOrNull { it.id == pid }?.cloudProfileId == uuid
        val rows = (knownRows ?: CloudflareTvAccountClient.syncRows(uuid)).map { Json.parseToJsonElement(it.toString()).jsonObject }
        val features = if (BuildConfig.FLAVOR.contains("full", true) && profiles.activeProfile?.usesPrimaryPlugins != true)
            listOf("visual_theme_v1", pluginFeature) else listOf("visual_theme_v1")
        var ready = true
        for (feature in features) {
            suspend fun snapshot() = if (feature == pluginFeature) pluginSnapshot() else theme(factory.get(pid, "theme_settings").data.first())
            val row = preferenceRow(rows, feature)
            val done = engine.synchronize(owner, uuid, feature, row, current = { check(current()); snapshot() },
                apply = { expected, next ->
                    check(current())
                    if (snapshot() != expected) false else {
                        applying = true
                        try {
                            if (feature == pluginFeature) {
                                val pluginManager = manager.get()
                                val repos = next["repositories"]?.jsonArray.orEmpty().map { it.jsonObject }
                                require(repos.size <= 100)
                                pluginManager.isSyncingFromRemote = true
                                try {
                                    if (repos.isEmpty()) plugins.repositories.first().forEach { check(current()); pluginManager.removeRepository(it.id) }
                                    else pluginManager.reconcileWithRemoteRepoUrls(repos.map { RemotePluginInfo(it["url"]!!.jsonPrimitive.content, it["repo_type"]?.jsonPrimitive?.content) })
                                    check(current())
                                    val installed = plugins.repositories.first().map { it.url }.toSet()
                                    check(repos.all { it["url"]!!.jsonPrimitive.content in installed }) { "Module indisponible : reprise nécessaire" }
                                    val disabled = next["disabled"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.toSet()
                                    val byId = plugins.repositories.first().associateBy { it.id }
                                    plugins.saveScrapers(plugins.scrapers.first().map { it.copy(enabled = it.manifestEnabled && (byId[it.repositoryId]?.url.orEmpty() + "|" + it.filename) !in disabled) })
                                    plugins.setPluginsEnabled(next["enabled"]?.jsonPrimitive?.booleanOrNull ?: true)
                                    plugins.setGroupStreamsByRepository(next["group"]?.jsonPrimitive?.booleanOrNull ?: false)
                                } finally { pluginManager.isSyncingFromRemote = false }
                            } else {
                                factory.get(pid, "theme_settings").edit { values ->
                                    check(current())
                                    for (key in listOf("selected_theme", "custom_theme_colors")) {
                                        val localKey = stringPreferencesKey(key)
                                        val value = next[key]?.jsonObject?.get("value")?.jsonPrimitive?.content
                                        if (value == null) values.remove(localKey) else values[localKey] = value
                                    }
                                    val amoled = next["amoled_enabled"]?.jsonObject?.get("value")?.jsonPrimitive?.booleanOrNull
                                    if (amoled == null) values.remove(booleanPreferencesKey("amoled_mode")) else values[booleanPreferencesKey("amoled_mode")] = amoled
                                }
                            }
                            check(current()); true
                        } finally { applying = false }
                    }
                }, publish = { value, revision ->
                    check(current())
                    // Retain mobile-only theme fields when this TV changes its common theme.
                    val payload = if (feature == "visual_theme_v1") JsonObject((row?.payload.orEmpty() - setOf("selected_theme", "custom_theme_colors", "amoled_enabled")) + value) else value
                    CloudflareTvAccountClient.writeSync(uuid, "profile_settings", feature, JSONObject(payload.toString()), revision)
                }, isCurrent = ::current)
            if (!done) ready = false
        }
        return ready
    }
}
