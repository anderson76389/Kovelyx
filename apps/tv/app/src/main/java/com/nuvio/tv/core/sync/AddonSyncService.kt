package com.nuvio.tv.core.sync

import android.content.Context
import com.kovelyx.cloud.addons.CloudAddon
import com.kovelyx.cloud.addons.AddonDefaultsPolicy
import com.kovelyx.cloud.addons.ADDON_DEFAULTS_VERSION
import com.kovelyx.cloud.addons.CloudAddonEngine
import com.kovelyx.cloud.addons.CloudAddonRow
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.AddonPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject

@Singleton
class AddonSyncService @Inject constructor(
    private val addonPreferences: AddonPreferences,
    private val profileManager: ProfileManager,
    @ApplicationContext context: Context
) {
    private val cloudMutex = Mutex()
    private val localOutboxLock = Any()
    private val cloudJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cloudStore = context.getSharedPreferences("kovelyx_cloud_addons", Context.MODE_PRIVATE)
    private val cloudEngine = CloudAddonEngine({ key -> cloudStore.getString(key, null) },
        { key, value -> cloudStore.edit().putString(key, value).apply() },
        AddonDefaultsPolicy { value -> MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } })

    suspend fun markLocalAddonsChanged() {
        val owner = CloudflareTvAccountClient.account?.id ?: return
        val activeId = profileManager.activeProfileId.value
        val effectiveId = if (profileManager.activeProfile?.usesPrimaryAddons == true) 1 else activeId
        // The snapshot call also makes local default migration durable before a queued push.
        addonPreferences.cloudSnapshot(effectiveId)
        if (CloudflareTvAccountClient.account?.id != owner || profileManager.activeProfileId.value != activeId) return
        synchronized(localOutboxLock) {
            cloudStore.edit().putString("$owner.local-addon-edit.$effectiveId", java.util.UUID.randomUUID().toString()).apply()
        }
    }

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
        val pendingKey = "${account.id}.local-addon-edit.$effectiveId"
        val pending = synchronized(localOutboxLock) { cloudStore.getString(pendingKey, "").orEmpty() }
        if (pending.isNotEmpty()) cloudEngine.edited("${account.id}.$uuid", captured)
        val row = (if (uuid == knownProfileId && knownRows != null) knownRows else CloudflareTvAccountClient.syncRows(uuid)).firstOrNull {
            it.optString("category") == "addons" && it.optString("itemKey") == "installed"
        }
        val remote = row?.let {
            val items = if (it.optBoolean("deleted")) emptyList() else
                cloudJson.decodeFromString<List<CloudAddon>>(it.getJSONObject("payload").getJSONArray("items").toString())
            CloudAddonRow(it.optInt("revision"), items, it.optJSONObject("payload")?.optInt("defaultsVersion") ?: 0)
        }
        check(valid())
        val synchronizedSuccessfully = cloudEngine.synchronize("${account.id}.$uuid", captured, remote, write = { items, revision ->
            check(valid())
            val payload = buildJsonObject { put("items", cloudJson.encodeToJsonElement(items)); put("defaultsVersion", ADDON_DEFAULTS_VERSION) }.toString()
            require(payload.length <= 32000) { "Configuration des extensions trop volumineuse" }
            CloudflareTvAccountClient.writeSync(uuid, "addons", "installed", JSONObject(payload), revision)
        }, apply = { items -> valid() && addonPreferences.applyCloudSnapshot(effectiveId, captured, items) })
        check(synchronizedSuccessfully) { "Extensions TV modifiées ; nouvelle tentative" }
        synchronized(localOutboxLock) {
            if (cloudStore.getString(pendingKey, "").orEmpty() == pending) cloudStore.edit().remove(pendingKey).apply()
        }
    }


    suspend fun pushToRemote(): Result<Unit> = runCatching {
        check(CloudflareTvAccountClient.enabled) { "Kovelyx Cloudflare non configuré" }
        synchronizeCloudAddons()
    }

    suspend fun getRemoteAddonUrls(): Result<List<String>> = runCatching {
        check(CloudflareTvAccountClient.enabled) { "Kovelyx Cloudflare non configuré" }
        synchronizeCloudAddons()
        addonPreferences.installedAddonUrls.first()
    }
}
