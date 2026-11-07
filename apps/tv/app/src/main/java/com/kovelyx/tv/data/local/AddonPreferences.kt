package com.kovelyx.tv.data.local

import com.kovelyx.cloud.addons.CloudAddon
import com.kovelyx.cloud.addons.AddonDefaultsPolicy
import com.kovelyx.cloud.addons.canonicalAddonManifestUrl
import com.kovelyx.cloud.addons.OPENSUBTITLES_V3_MANIFEST
import java.security.MessageDigest
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.onStart
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kovelyx.tv.core.profile.ProfileManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class AddonPreferences @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "addon_preferences"
    }

    private fun effectiveProfileId(): Int {
        val active = profileManager.activeProfile
        return if (active != null && active.usesPrimaryAddons) 1 else profileManager.activeProfileId.value
    }

    private fun store(profileId: Int = effectiveProfileId()) =
        factory.get(profileId, FEATURE)

    private val effectiveProfileIdFlow: Flow<Int> = combine(
        profileManager.activeProfileId,
        profileManager.profiles
    ) { activeProfileId, profiles ->
        val activeProfile = profiles.firstOrNull { it.id == activeProfileId }
        if (activeProfile?.usesPrimaryAddons == true) 1 else activeProfileId
    }.distinctUntilChanged()

    private val gson = Gson()
    private val orderedUrlsKey = stringPreferencesKey("installed_addon_urls_ordered")
    private val legacyUrlsKey = stringSetPreferencesKey("installed_addon_urls")
    private val userSetNamesKey = stringPreferencesKey("addon_user_set_names")
    private val addonEnabledStatesKey = stringPreferencesKey("installed_addon_enabled_states")
    private val manifestSuffix = "/manifest.json"
    private val defaultsMigratedKey = booleanPreferencesKey("kovelyx_defaults_v1")
    private val defaultsPolicy = AddonDefaultsPolicy { value -> MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } }

    private fun canonicalizeUrl(url: String): String {
        val manifest = canonicalAddonManifestUrl(url)
        val path = manifest.substringBefore('?').removeSuffix(manifestSuffix)
        return path + if ('?' in manifest) "?${manifest.substringAfter('?')}" else ""
    }

    val installedAddonUrls: Flow<List<String>> = effectiveProfileIdFlow.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.onStart { ensureMigrated(pid) }.map { preferences ->
            val json = preferences[orderedUrlsKey]
            if (json != null) {
                parseUrlList(json)
            } else {
                val legacySet = preferences[legacyUrlsKey] ?: getDefaultAddons()
                legacySet.toList()
            }
        }
    }

    val addonEnabledStates: Flow<Map<String, Boolean>> = effectiveProfileIdFlow.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { preferences ->
            preferences[addonEnabledStatesKey]
                ?.let(::parseEnabledStateMap)
                .orEmpty()
        }
    }

    suspend fun ensureMigrated(profileId: Int = effectiveProfileId()) {
        val ds = store(profileId)
        ds.edit { prefs ->
            val original = getCurrentList(prefs)
            val items = original.map { CloudAddon(canonicalAddonManifestUrl(it)) }
            val migrated = if (prefs[defaultsMigratedKey] != true) defaultsPolicy.seed(items) else defaultsPolicy.clean(items)
            val urls = migrated.map { canonicalizeUrl(it.url) }
            prefs[orderedUrlsKey] = gson.toJson(urls)
            prefs.remove(legacyUrlsKey)
            val names = prefs[userSetNamesKey]?.let(::parseNameMap).orEmpty()
            val enabled = getCurrentEnabledStates(prefs)
            if (names.keys.any(defaultsPolicy::retired)) prefs[userSetNamesKey] = gson.toJson(names.filterKeys { !defaultsPolicy.retired(it) })
            if (enabled.keys.any(defaultsPolicy::retired)) prefs[addonEnabledStatesKey] = gson.toJson(enabled.filterKeys { !defaultsPolicy.retired(it) })
            prefs[defaultsMigratedKey] = true
        }
    }

    suspend fun addAddon(url: String): Boolean {
           val active = profileManager.activeProfile
           if (active != null && !active.isPrimary && active.usesPrimaryAddons) return false
        var changed = false
        store().edit { preferences ->
            val current = getCurrentList(preferences)
            val normalizedUrl = canonicalizeUrl(url)
            if (defaultsPolicy.retired(normalizedUrl)) return@edit
            if (current.any { canonicalizeUrl(it) == normalizedUrl }) return@edit
            preferences[orderedUrlsKey] = gson.toJson(current + normalizedUrl)
            val states = getCurrentEnabledStates(preferences).toMutableMap()
            states[normalizedUrl] = true
            preferences[addonEnabledStatesKey] = gson.toJson(states)
            changed = true
        }
        return changed
    }

    suspend fun removeAddon(url: String): Boolean {
           val active = profileManager.activeProfile
           if (active != null && !active.isPrimary && active.usesPrimaryAddons) return false
        var changed = false
        store().edit { preferences ->
            val current = getCurrentList(preferences).toMutableList()
            val normalizedUrl = canonicalizeUrl(url)

            val indexToRemove = current.indexOfFirst {
                canonicalizeUrl(it) == normalizedUrl
            }
            if (indexToRemove != -1) {
                current.removeAt(indexToRemove)
            }
            if (indexToRemove == -1) return@edit
            preferences[orderedUrlsKey] = gson.toJson(current)
            val states = getCurrentEnabledStates(preferences).toMutableMap()
            states.remove(normalizedUrl)
            preferences[addonEnabledStatesKey] = gson.toJson(states)
            changed = true
        }
        return changed
    }

    suspend fun setAddonOrder(urls: List<String>): Boolean {
            val active = profileManager.activeProfile
            if (active != null && !active.isPrimary && active.usesPrimaryAddons) return false
        var changed = false
        store().edit { preferences ->
            val orderedUrls = urls.map(::canonicalizeUrl)
            val currentUrls = getCurrentList(preferences).map(::canonicalizeUrl)
            if (orderedUrls == currentUrls) return@edit
            preferences[orderedUrlsKey] = gson.toJson(orderedUrls)
            val currentStates = getCurrentEnabledStates(preferences)
            preferences[addonEnabledStatesKey] = gson.toJson(
                orderedUrls.associateWith { url -> currentStates[url] ?: true }
            )
            changed = true
        }
        return changed
    }

    suspend fun setAddonEnabled(url: String, enabled: Boolean): Boolean {
        val active = profileManager.activeProfile
        if (active != null && !active.isPrimary && active.usesPrimaryAddons) return false
        var changed = false
        store().edit { preferences ->
            val states = getCurrentEnabledStates(preferences).toMutableMap()
            val normalizedUrl = canonicalizeUrl(url)
            if ((states[normalizedUrl] ?: true) == enabled) return@edit
            states[normalizedUrl] = enabled
            preferences[addonEnabledStatesKey] = gson.toJson(states)
            changed = true
        }
        return changed
    }

    suspend fun setAddonEnabledStates(states: Map<String, Boolean>) {
        val active = profileManager.activeProfile
        if (active != null && !active.isPrimary && active.usesPrimaryAddons) return
        store().edit { preferences ->
            preferences[addonEnabledStatesKey] = gson.toJson(
                states.mapKeys { (url, _) -> canonicalizeUrl(url) }
            )
        }
    }

    suspend fun cloudSnapshot(profileId: Int): List<CloudAddon> {
        ensureMigrated(profileId)
        return cloudSnapshot(store(profileId).data.first())
    }

    private fun cloudSnapshot(prefs: Preferences): List<CloudAddon> {
        val names = prefs[userSetNamesKey]?.let(::parseNameMap).orEmpty()
        val enabled = getCurrentEnabledStates(prefs)
        return getCurrentList(prefs).map { raw ->
            val url = canonicalizeUrl(raw)
            val path = url.substringBefore('?').trimEnd('/')
            val query = url.substringAfter('?', "")
            CloudAddon(path + "/manifest.json" + if (query.isEmpty()) "" else "?$query",
                names[url].orEmpty(), enabled[url] ?: true)
        }
    }

    /** All fields change together; a concurrent local edit is never overwritten by a pull. */
    suspend fun applyCloudSnapshot(profileId: Int, expected: List<CloudAddon>, next: List<CloudAddon>): Boolean {
        var applied = false
        store(profileId).edit { prefs ->
            if (cloudSnapshot(prefs) != expected) return@edit
            prefs[orderedUrlsKey] = gson.toJson(defaultsPolicy.clean(next).map { canonicalizeUrl(it.url) })
            prefs[defaultsMigratedKey] = true
            prefs[userSetNamesKey] = gson.toJson(next.associate { canonicalizeUrl(it.url) to it.name })
            prefs[addonEnabledStatesKey] = gson.toJson(next.associate { canonicalizeUrl(it.url) to it.enabled })
            applied = true
        }
        return applied
    }

    private fun getCurrentList(preferences: Preferences): List<String> {
        val json = preferences[orderedUrlsKey]
        return if (json != null) {
            parseUrlList(json)
        } else {
            val legacySet = preferences[legacyUrlsKey] ?: getDefaultAddons()
            legacySet.toList()
        }
    }

    private fun parseUrlList(json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            (gson.fromJson<List<String>>(json, type) ?: getDefaultAddons().toList()).filterNot(defaultsPolicy::retired)
        } catch (e: Exception) {
            getDefaultAddons().toList()
        }
    }

    val userSetNames: Flow<Map<String, String>> = effectiveProfileIdFlow.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { preferences ->
            val json = preferences[userSetNamesKey]
            if (json != null) parseNameMap(json) else emptyMap()
        }
    }

    suspend fun setUserSetNames(names: Map<String, String>) {
        store().edit { preferences ->
            preferences[userSetNamesKey] = gson.toJson(
                names.mapKeys { (url, _) -> canonicalizeUrl(url) }
            )
        }
    }

    private fun parseNameMap(json: String): Map<String, String> {
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val parsed: Map<String, String> = gson.fromJson(json, type) ?: emptyMap()
            parsed.mapKeys { (url, _) -> canonicalizeUrl(url) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun getCurrentEnabledStates(preferences: Preferences): Map<String, Boolean> {
        val json = preferences[addonEnabledStatesKey] ?: return emptyMap()
        return parseEnabledStateMap(json)
    }

    private fun parseEnabledStateMap(json: String): Map<String, Boolean> {
        return try {
            val type = object : TypeToken<Map<String, Boolean>>() {}.type
            val parsed: Map<String, Boolean> = gson.fromJson(json, type) ?: emptyMap()
            parsed.mapKeys { (url, _) -> canonicalizeUrl(url) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun getDefaultAddons(): Set<String> = setOf(
        OPENSUBTITLES_V3_MANIFEST.removeSuffix(manifestSuffix)
    )
}
