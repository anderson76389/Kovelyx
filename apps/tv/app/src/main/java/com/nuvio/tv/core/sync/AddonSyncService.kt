package com.nuvio.tv.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.kovelyx.cloud.addons.CloudAddon
import com.kovelyx.cloud.addons.CloudAddonEngine
import com.kovelyx.cloud.addons.CloudAddonRow
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.json.JSONObject
import android.util.Log
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.AddonPreferences
import com.nuvio.tv.data.remote.supabase.SupabaseAddon
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.addJsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AddonSyncService"

@Singleton
class AddonSyncService @Inject constructor(
    private val postgrest: Postgrest,
    private val authManager: AuthManager,
    private val addonPreferences: AddonPreferences,
    private val profileManager: ProfileManager,
    @ApplicationContext context: Context,
    private val syncClientIdentity: SyncClientIdentity
) {
    private val cloudMutex = Mutex()
    private val cloudJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cloudStore = context.getSharedPreferences("kovelyx_cloud_addons", Context.MODE_PRIVATE)
    private val cloudEngine = CloudAddonEngine({ key -> cloudStore.getString(key, null) },
        { key, value -> cloudStore.edit().putString(key, value).apply() })

    suspend fun synchronizeCloudAddons(knownProfileId: String? = null, knownRows: List<JSONObject>? = null) = cloudMutex.withLock {
        val account = CloudflareTvAccountClient.account ?: return@withLock
        val activeId = profileManager.activeProfileId.value
        val effectiveId = if (profileManager.activeProfile?.usesPrimaryAddons == true) 1 else activeId
        val profile = profileManager.profiles.value.firstOrNull { it.id == effectiveId } ?: return@withLock
        val profiles = if (profile.cloudProfileId == knownProfileId && knownProfileId != null)
            listOf(knownProfileId to profile.name) else CloudflareTvAccountClient.cloudProfiles()
        val matching = profiles.filter { it.second.equals(profile.name, true) }
        val uuid = if (profile.cloudProfileId != null) {
            profiles.firstOrNull { it.first == profile.cloudProfileId }?.first ?: return@withLock
        } else when {
            matching.size == 1 -> matching.single().first
            profiles.size == 1 && effectiveId == 1 -> profiles.single().first
            matching.isEmpty() && profiles.size < 8 -> CloudflareTvAccountClient.createCloudProfile(profile.name)
            else -> return@withLock
        }
        fun valid() = CloudflareTvAccountClient.account?.id == account.id && profileManager.activeProfileId.value == activeId
        check(valid())
        if (profile.cloudProfileId != uuid) profileManager.linkCloudProfile(effectiveId, uuid)
        val captured = addonPreferences.cloudSnapshot(effectiveId)
        val row = (if (uuid == knownProfileId && knownRows != null) knownRows else CloudflareTvAccountClient.syncRows(uuid)).firstOrNull {
            it.optString("category") == "addons" && it.optString("itemKey") == "installed"
        }
        val remote = row?.let {
            val items = if (it.optBoolean("deleted")) emptyList() else
                cloudJson.decodeFromString<List<CloudAddon>>(it.getJSONObject("payload").getJSONArray("items").toString())
            CloudAddonRow(it.optInt("revision"), items)
        }
        check(valid())
        cloudEngine.synchronize("${account.id}.$uuid", captured, remote, write = { items, revision ->
            check(valid())
            val payload = buildJsonObject { put("items", cloudJson.encodeToJsonElement(items)) }.toString()
            require(payload.length <= 32000) { "Configuration des extensions trop volumineuse" }
            CloudflareTvAccountClient.writeSync(uuid, "addons", "installed", JSONObject(payload), revision)
        }, apply = { items -> valid() && addonPreferences.applyCloudSnapshot(effectiveId, captured, items) })
    }

    private suspend fun <T> withJwtRefreshRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            if (!authManager.refreshSessionIfJwtExpired(e)) throw e
            block()
        }
    }

    /**
     * Push local addon URLs to Supabase via RPC.
     * Uses a SECURITY DEFINER function to handle RLS for linked devices.
     */
    suspend fun pushToRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        if (CloudflareTvAccountClient.enabled) return@withContext runCatching { synchronizeCloudAddons() }
        try {
            val activeProfile = profileManager.activeProfile
            val profileId = profileManager.activeProfileId.value
            Log.d(TAG, "pushToRemote: activeProfile=${activeProfile?.id} isPrimary=${activeProfile?.isPrimary} usesPrimaryAddons=${activeProfile?.usesPrimaryAddons} profileId=$profileId")

            if (activeProfile != null && !activeProfile.isPrimary && activeProfile.usesPrimaryAddons) {
                Log.d(TAG, "Profile ${activeProfile.id} uses primary addons, skipping push")
                return@withContext Result.success(Unit)
            }

            val localUrls = addonPreferences.installedAddonUrls.first()
            val userSetNames = addonPreferences.userSetNames.first()
            val enabledStates = addonPreferences.addonEnabledStates.first()
            Log.d(TAG, "pushToRemote: localUrls count=${localUrls.size} for profile $profileId")

            val params = buildJsonObject {
                put("p_addons", buildJsonArray {
                    localUrls.forEachIndexed { index, url ->
                        val canonicalUrl = canonicalizeUrl(url)
                        addJsonObject {
                            put("url", url)
                            put("sort_order", index)
                            put("enabled", enabledStates[canonicalUrl] ?: true)
                            val name = userSetNames[canonicalUrl] ?: userSetNames[url]
                            if (!name.isNullOrBlank()) {
                                put("name", name)
                            }
                        }
                    }
                })
                put("p_profile_id", profileId)
                putSyncOriginClientId(syncClientIdentity)
            }
            Log.d(TAG, "pushToRemote: calling RPC sync_push_addons with profile_id=$profileId")
            withJwtRefreshRetry {
                postgrest.rpc("sync_push_addons", params)
            }

            Log.d(TAG, "Pushed ${localUrls.size} addons to remote for profile $profileId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push addons to remote", e)
            Result.failure(e)
        }
    }

    suspend fun getRemoteAddonUrls(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (CloudflareTvAccountClient.enabled) return@withContext runCatching {
            synchronizeCloudAddons()
            addonPreferences.installedAddonUrls.first()
        }
        try {
            val effectiveUserId = authManager.getEffectiveUserId(fallbackToOwnIdOnFailure = false)
                ?: return@withContext Result.failure(
                    IllegalStateException("Unable to resolve sync owner for addon sync")
                )

            val activeProfile = profileManager.activeProfile
            val profileId = if (activeProfile != null && !activeProfile.isPrimary && activeProfile.usesPrimaryAddons) 1
                            else profileManager.activeProfileId.value

            val remoteAddons = withJwtRefreshRetry {
                postgrest.from("addons")
                    .select { filter {
                        eq("user_id", effectiveUserId)
                        eq("profile_id", profileId)
                    } }
                    .decodeList<SupabaseAddon>()
            }

            val nameMap = mutableMapOf<String, String>()
            val enabledMap = mutableMapOf<String, Boolean>()
            remoteAddons.forEach { addon ->
                val canonicalUrl = canonicalizeUrl(addon.url)
                if (!addon.name.isNullOrBlank()) {
                    nameMap[canonicalUrl] = addon.name
                }
                enabledMap[canonicalUrl] = addon.enabled
            }
            if (remoteAddons.isNotEmpty()) {
                addonPreferences.setUserSetNames(nameMap)
                addonPreferences.setAddonEnabledStates(enabledMap)
            }

            Result.success(
                remoteAddons
                .sortedBy { it.sortOrder }
                .map { it.url }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get remote addon URLs", e)
            Result.failure(e)
        }
    }

    private fun canonicalizeUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        val queryStart = trimmed.indexOf('?')
        val path = if (queryStart >= 0) trimmed.substring(0, queryStart) else trimmed
        val query = if (queryStart >= 0) trimmed.substring(queryStart) else ""
        val cleanPath = if (path.endsWith("/manifest.json", ignoreCase = true)) {
            path.dropLast("/manifest.json".length).trimEnd('/')
        } else {
            path.trimEnd('/')
        }
        return cleanPath + query
    }
}
