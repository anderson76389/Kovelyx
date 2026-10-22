package com.nuvio.tv.core.sync

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.SavedLibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Cache de bibliothèque propre au compte D1, séparé des profils invités et du
 * DataStore/Supabase historique de la TV. Aucun titre invité n'est exporté.
 *
 * Seule une association non ambiguë (établie par l'écran de compte) peut
 * activer le cache. Les écritures utilisent les révisions CAS et restent
 * en attente en cas de conflit, d'absence de réseau ou de changement de compte.
 */
internal object CloudflareTvLibraryStore {
    data class View(
        val ownerId: String,
        val localProfileId: Int,
        val cloudProfileId: String,
        val items: Map<String, SavedLibraryItem> = emptyMap(),
        val revisions: Map<String, Int> = emptyMap(),
        val pendingDeletes: Map<String, Boolean> = emptyMap(),
        val conflicts: Int = 0
    )

    private const val PREFS = "kovelyx_tv_cloud_library_v1"
    private const val MAX_ROWS = 10000
    private val mutex = Mutex()
    private val _state = MutableStateFlow<View?>(null)
    val state: StateFlow<View?> = _state

    private fun key(item: SavedLibraryItem): String = key(item.id, item.type)
    private fun key(id: String, type: String) = type.trim().lowercase() + ":" + id.trim()

    fun reset() {
        _state.value = null
    }

    fun isBound(localProfileId: Int): Boolean {
        val acc = CloudflareTvAccountClient.account ?: return false
        return _state.value?.let {
            it.ownerId == acc.id && it.localProfileId == localProfileId
        } == true
    }

    fun contains(localProfileId: Int, id: String, type: String): Boolean =
        isBound(localProfileId) &&
            _state.value?.items?.containsKey(key(id, type)) == true

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // Conserver exactement l'ancienne clé pour ne jamais perdre les favoris
    // ni les mutations en attente lors de l'installation du correctif.
    private fun storageKey(view: View): String =
        digest(view.ownerId + "|" + view.cloudProfileId)

    // Chaque association vérifiée par /v1/profiles reste cantonnée au même
    // compte et au même profil local. Aucun couplage par position de liste.
    internal fun bindingKey(ownerId: String, localProfileId: Int): String =
        "binding_" + digest(ownerId + "|" + localProfileId)

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Cache local seulement, accessible après vérification de la session Better Auth. */
    suspend fun restoreVerifiedBinding(context: Context, localProfileId: Int): Boolean =
        withContext(Dispatchers.IO) {
            val account = CloudflareTvAccountClient.account ?: return@withContext false
            if (localProfileId !in 1..6) return@withContext false
            val cloudId = preferences(context)
                .getString(bindingKey(account.id, localProfileId), null)
                ?: return@withContext false
            if (!cloudId.matches(Regex("[0-9a-fA-F-]{36}"))) return@withContext false
            mutex.withLock {
                if (CloudflareTvAccountClient.account != account) return@withLock false
                val cached = _state.value
                if (cached?.ownerId != account.id || cached?.localProfileId != localProfileId ||
                    cached?.cloudProfileId != cloudId) {
                    _state.value = restore(context, account.id, localProfileId, cloudId)
                }
                true
            }
        }

    fun forgetBinding(context: Context, ownerId: String, localProfileId: Int) {
        preferences(context).edit().remove(bindingKey(ownerId, localProfileId)).apply()
        val current = _state.value
        if (current?.ownerId == ownerId && current.localProfileId == localProfileId) reset()
    }

    private fun safePoster(value: String?): String? =
        value?.takeIf {
            it.length <= 1024 && it.startsWith("https://image.tmdb.org/t/p/") &&
                '?' !in it && '#' !in it
        }

    private fun itemJson(item: SavedLibraryItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("type", item.type)
        .put("name", item.name.take(300))
        .put("savedAtEpochMs", item.addedAt)
        .apply {
            safePoster(item.poster)?.let { put("poster", it) }
            safePoster(item.background)?.let { put("banner", it) }
        }

    private fun decodeItem(data: JSONObject, expectedKey: String): SavedLibraryItem {
        val id = data.getString("id").trim()
        val type = data.getString("type").trim()
        val name = data.getString("name").take(300)
        require(id.isNotBlank() && type.isNotBlank() && name.isNotBlank())
        require(key(id, type) == expectedKey) { "Clé du catalogue incohérente" }
        return SavedLibraryItem(
            id = id, type = type, name = name,
            poster = safePoster(data.optString("poster").takeIf { it.isNotBlank() }),
            posterShape = PosterShape.POSTER,
            background = safePoster(data.optString("banner").takeIf { it.isNotBlank() }),
            description = null, releaseInfo = null, imdbRating = null,
            genres = emptyList(), addonBaseUrl = null,
            addedAt = data.optLong("savedAtEpochMs", 0)
        )
    }

    private fun valid(view: View): Boolean {
        val acc = CloudflareTvAccountClient.account
        return acc != null && acc.id == view.ownerId &&
            _state.value?.ownerId == view.ownerId &&
            _state.value?.cloudProfileId == view.cloudProfileId &&
            _state.value?.localProfileId == view.localProfileId
    }

    private fun save(context: Context, view: View) {
        if (!valid(view)) return
        val data = JSONObject()
        val records = JSONObject()
        for ((key, item) in view.items) records.put(key, itemJson(item))
        val revisions = JSONObject()
        for ((key, revision) in view.revisions) revisions.put(key, revision)
        val pending = JSONObject()
        for ((key, deleted) in view.pendingDeletes) pending.put(key, deleted)
        data.put("owner", view.ownerId)
            .put("profile", view.cloudProfileId)
            .put("items", records)
            .put("revisions", revisions)
            .put("pending", pending)
        check(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(storageKey(view), data.toString()).commit()) {
            "Sauvegarde de bibliothèque TV non confirmée"
        }
    }

    private fun restore(context: Context, owner: String, local: Int, cloud: String): View {
        val empty = View(owner, local, cloud)
        val text = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(storageKey(empty), null) ?: return empty
        return try {
            val parsed = JSONObject(text)
            require(parsed.optString("owner") == owner && parsed.optString("profile") == cloud)
            val entries = parsed.optJSONObject("items") ?: JSONObject()
            val revisions = parsed.optJSONObject("revisions") ?: JSONObject()
            val pending = parsed.optJSONObject("pending") ?: JSONObject()
            require(entries.length() <= MAX_ROWS && revisions.length() <= MAX_ROWS && pending.length() <= MAX_ROWS)
            val items = mutableMapOf<String, SavedLibraryItem>()
            val revs = mutableMapOf<String, Int>()
            val changes = mutableMapOf<String, Boolean>()
            for (key in entries.keys()) {
                if (key.length in 1..200) {
                    runCatching { decodeItem(entries.getJSONObject(key), key) }
                        .getOrNull()?.let { items[key] = it }
                }
            }
            for (key in revisions.keys()) {
                val revision = revisions.optInt(key, -1)
                if (key.length in 1..200 && revision >= 0) revs[key] = revision
            }
            for (key in pending.keys()) {
                if (key.length in 1..200) changes[key] = pending.optBoolean(key)
            }
            empty.copy(items = items, revisions = revs, pendingDeletes = changes)
        } catch (error: Exception) {
            Log.w("KovelyxCloudTvLibrary", "Cache cloud illisible, aucun invité effacé", error)
            empty
        }
    }

    suspend fun bind(context: Context, localProfileId: Int, cloudProfileId: String) {
        val acc = checkNotNull(CloudflareTvAccountClient.account)
        require(localProfileId in 1..6)
        require(cloudProfileId.matches(Regex("[0-9a-fA-F-]{36}")))
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(CloudflareTvAccountClient.account == acc) { "Compte TV changé" }
                // L'association actuelle a été vérifiée par /v1/profiles.
                check(preferences(context).edit()
                    .putString(bindingKey(acc.id, localProfileId), cloudProfileId).commit()) {
                    "Impossible de sauvegarder l'association de profil TV"
                }
                val current = _state.value
                if (current?.ownerId != acc.id || current.cloudProfileId != cloudProfileId ||
                    current.localProfileId != localProfileId) {
                    _state.value = restore(context, acc.id, localProfileId, cloudProfileId)
                }
            }
        }
        refresh(context, localProfileId)
    }

    /**
     * Fusion additive des changements D1 : une suppression est reconnue uniquement
     * lorsqu'aucune modification TV en attente n'utilise cette même clé.
     * En cas de CAS 409, les données TV restent en attente sur disque.
     */
    suspend fun refresh(context: Context, localProfileId: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val old = _state.value ?: return@withLock
            if (old.localProfileId != localProfileId || !valid(old)) return@withLock
            val remote = CloudflareTvAccountClient.readLibrary(old.cloudProfileId)
            if (!valid(old)) return@withLock
            val items = old.items.toMutableMap()
            val revisions = old.revisions.toMutableMap()
            val pending = old.pendingDeletes.toMutableMap()
            var conflicts = 0
            val remoteByKey = linkedMapOf<String, JSONObject>()
            for (row in remote) {
                val id = row.getString("itemKey")
                val revision = row.getInt("revision")
                require(id.length in 1..200 && revision > 0)
                require(id !in remoteByKey) { "Doublon dans les données cloud" }
                remoteByKey[id] = row
            }
            for ((id, row) in remoteByKey) {
                val remoteRev = row.getInt("revision")
                if (id in pending) {
                    val pendingDelete = pending[id] == true
                    // La requête TV peut avoir été acceptée par D1 juste avant
                    // une coupure réseau : reconnaître le résultat exact évite
                    // un conflit permanent après redémarrage.
                    val sameResult = row.getBoolean("deleted") == pendingDelete &&
                        (pendingDelete || items[id]?.let { local ->
                            row.optJSONObject("payload")?.optJSONObject("item")?.toString() ==
                                itemJson(local).toString()
                        } == true)
                    if (sameResult) {
                        pending.remove(id)
                        revisions[id] = remoteRev
                    } else if ((revisions[id] ?: 0) != remoteRev) {
                        conflicts++
                    }
                    continue
                }
                if (row.getBoolean("deleted")) items.remove(id)
                else items[id] = decodeItem(row.getJSONObject("payload").getJSONObject("item"), id)
                revisions[id] = remoteRev
            }
            var updated = old.copy(items = items, revisions = revisions, pendingDeletes = pending, conflicts = conflicts)
            _state.value = updated
            save(context, updated)
            for ((id, deleted) in pending.toMap()) {
                if (!valid(updated)) return@withLock
                val currentRev = revisions[id] ?: 0
                val serverRev = remoteByKey[id]?.getInt("revision") ?: 0
                if (serverRev != currentRev) continue // 409 implicite : pas d'écrasement
                try {
                    val item = items[id]
                    if (!deleted && item == null) continue
                    val rev = CloudflareTvAccountClient.writeLibrary(
                        updated.cloudProfileId, id, item?.let(::itemJson), currentRev, deleted
                    )
                    revisions[id] = rev
                    pending.remove(id)
                    updated = updated.copy(revisions = revisions.toMap(), pendingDeletes = pending.toMap())
                    if (!valid(updated)) return@withLock
                    _state.value = updated
                    save(context, updated)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    Log.w("KovelyxCloudTvLibrary", "Écriture conservée en attente (réseau/conflit)", error)
                }
            }
        }
    }

    suspend fun toggle(context: Context, localProfileId: Int, item: SavedLibraryItem) {
        require(item.id.isNotBlank() && item.type.isNotBlank())
        val id = key(item)
        require(id.length in 1..200)
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = checkNotNull(_state.value) { "Profil Cloudflare TV non associé" }
                check(current.localProfileId == localProfileId && valid(current)) { "Profil cloud changé" }
                val items = current.items.toMutableMap()
                val deleting = items.remove(id) != null
                if (!deleting) items[id] = item.copy(
                    poster = safePoster(item.poster), background = safePoster(item.background)
                )
                val pending = current.pendingDeletes + (id to deleting)
                val updated = current.copy(items = items, pendingDeletes = pending)
                _state.value = updated
                save(context, updated)
            }
        }
        // Le changement local est déjà durable même si le serveur refuse.
        try {
            refresh(context, localProfileId)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Log.w("KovelyxCloudTvLibrary", "Synchronisation reportée", error)
        }
    }
}
