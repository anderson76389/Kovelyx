package com.nuvio.app.features.library

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.cloud.CloudflareAccountClient
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.tracking.ensureTrackingProvidersRegistered
import com.nuvio.app.features.library.sync.LibrarySyncAdapter
import com.nuvio.app.features.library.sync.SupabaseLibrarySyncAdapter
import com.nuvio.app.features.library.sync.consumeCursorPages
import com.nuvio.app.features.library.sync.libraryDeltaPageSize
import com.nuvio.app.features.library.sync.librarySnapshotPageSize
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.core.poster.CustomPosterUrlRepository
import com.nuvio.app.features.servers.ServerItemRef
import com.nuvio.app.features.tracking.TrackingLibraryProvider
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import com.nuvio.app.features.tracking.TrackingMembershipApplyResult
import com.nuvio.app.features.tracking.TrackingMembershipResolution
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.tracking.supportsContentType
import com.nuvio.app.features.tracking.effectiveLibrarySourceMode as resolveEffectiveLibrarySourceMode
import com.nuvio.app.features.tracking.providerId
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.library_local_tab_title
import nuvio.composeapp.generated.resources.library_server_title_unsupported
import nuvio.composeapp.generated.resources.library_other
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

object LibraryRepository {
    private const val pushDebounceMs = 500L

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("LibraryRepository")

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val localState = LibraryLocalState()
    private val loadLock = SynchronizedObject()
    private val nuvioSyncMutex = Mutex()
    private val persistenceLock = SynchronizedObject()
    private val lastPersistedRevisionByProfile = mutableMapOf<Int, Long>()
    internal var syncAdapter: LibrarySyncAdapter = SupabaseLibrarySyncAdapter

    init {
        ensureTrackingProvidersRegistered()
        syncScope.launch {
            TrackingProviderRegistry.connectedProviderIds.collectLatest {
                TrackingProviderRegistry.connectedLibraryProviders().forEach(TrackingLibraryProvider::prepare)
                activeLibraryProvider()?.let { provider ->
                    refreshLibraryProvider(
                        provider = provider,
                        reason = "connection state change",
                        intent = provider.connectionRefreshIntent,
                    )
                }
                publish()
            }
        }
        syncScope.launch {
            TrackingSettingsRepository.uiState
                .map { it.librarySourceMode }
                .distinctUntilChanged()
                .collectLatest {
                    publish()
                    activeLibraryProvider()?.let { provider ->
                        provider.prepare()
                        refreshLibraryProviderAsync(provider)
                    }
                }
        }
        TrackingProviderRegistry.libraryProviders().forEach { provider ->
            syncScope.launch {
                provider.changes.collectLatest {
                    if (TrackingProviderRegistry.isAuthenticated(provider.providerId)) {
                        publish()
                    }
                }
            }
        }
        syncScope.launch {
            kotlinx.coroutines.flow.combine(
                CustomPosterUrlRepository.pattern,
                CustomPosterUrlRepository.enabledScreens
            ) { _, _ -> Unit }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { publish() }
        }
    }

    fun ensureLoaded() {
        ensureTrackingProvidersRegistered()
        TrackingProviderRegistry.ensureLoaded()
        TrackingSettingsRepository.ensureLoaded()
        TrackingProviderRegistry.libraryProviders().forEach(TrackingLibraryProvider::ensureLoaded)
        while (true) {
            val activeProfileId = ProfileRepository.activeProfileId
            val snapshot = localState.snapshot()
            if (snapshot.hasLoaded && snapshot.token.profileId == activeProfileId) break
            loadFromDisk(activeProfileId)
        }
        TrackingProviderRegistry.connectedLibraryProviders().forEach(TrackingLibraryProvider::prepare)
        activeLibraryProvider()?.let(::refreshLibraryProviderAsync)
    }

    fun onProfileChanged(profileId: Int) {
        val current = localState.snapshot()
        if (profileId == current.token.profileId && current.hasLoaded) return

        if (!loadFromDisk(profileId)) return
        if (CloudflareAccountClient.enabled) {
            syncScope.launch {
                runCatching { synchronizeCloudflareLibrary(profileId) }
                    .onFailure { log.e(it) { "Cloudflare : synchronisation au changement de profil impossible" } }
            }
        }
        TrackingProviderRegistry.libraryProviders().forEach(TrackingLibraryProvider::onProfileChanged)
        TrackingProviderRegistry.connectedLibraryProviders().forEach(TrackingLibraryProvider::prepare)
        activeLibraryProvider()?.let(::refreshLibraryProviderAsync)
    }

    fun clearLocalState() {
        val transition = synchronized(loadLock) { localState.reset() }
        transition.detachedPushJob?.cancel()
        TrackingProviderRegistry.libraryProviders().forEach(TrackingLibraryProvider::clearLocalState)
        _uiState.value = LibraryUiState()
    }

    internal fun runAccountStorageWipe(wipeStorage: () -> Unit) {
        synchronized(loadLock) {
            val transition = localState.reset()
            transition.detachedPushJob?.cancel()
            synchronized(persistenceLock) {
                try {
                    wipeStorage()
                } finally {
                    lastPersistedRevisionByProfile.clear()
                }
            }
        }
    }

    private fun loadFromDisk(profileId: Int): Boolean {
        var shouldPublish = false
        val loaded = synchronized(loadLock) {
            if (ProfileRepository.activeProfileId != profileId) return@synchronized false
            val current = localState.snapshot()
            if (current.hasLoaded && current.token.profileId == profileId) {
                return@synchronized true
            }

            val transition = localState.beginProfileLoad(profileId)
            transition.detachedPushJob?.cancel()
            shouldPublish = completeLoadFromDisk(transition.snapshot.token)
            shouldPublish
        }
        if (shouldPublish) publish()
        return loaded
    }

    private fun completeLoadFromDisk(token: LibraryProfileToken): Boolean {
        val payload = LibraryStorage.loadPayload(token.profileId).orEmpty().trim()
        val storedPayload = if (payload.isNotEmpty()) {
            LibraryStoragePayloadCodec.decode(payload)
        } else {
            StoredLibraryPayload()
        }

        return localState.completeProfileLoad(
            token = token,
            activeProfileId = ProfileRepository.activeProfileId,
            items = storedPayload.items,
            deltaCursorEventId = storedPayload.deltaCursorEventId,
            deltaInitialized = storedPayload.deltaInitialized,
            pendingUpsertKeys = storedPayload.pendingUpsertKeys,
            pendingDeleteKeys = storedPayload.pendingDeleteKeys,
        ) != null
    }

    suspend fun pullFromServer(
        profileId: Int,
        refreshIntent: TrackingRefreshIntent = TrackingRefreshIntent.AUTOMATIC,
    ) {
        val operationToken = activeOperationToken(profileId) ?: run {
            log.d { "Skipping library pull for inactive profile $profileId" }
            return
        }

        activeLibraryProvider()?.let { provider ->
            refreshLibraryProvider(
                provider = provider,
                reason = "explicit pull",
                intent = refreshIntent,
            )
            if (!isActiveOperation(operationToken)) return
            publish()
            return
        }

        if (CloudflareAccountClient.enabled) {
            try {
                synchronizeCloudflareLibrary(profileId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "Bibliothèque Cloudflare : données locales préservées" }
            }
            return
        }

        nuvioSyncMutex.withLock {
            val serializedToken = activeOperationToken(profileId) ?: return@withLock
            val pullSnapshot = localState.markPullStarted(serializedToken) ?: return@withLock

            try {
                if (!pullSnapshot.deltaInitialized) {
                    val cursorBeforeSnapshot = syncAdapter.getDeltaCursor(profileId)
                    val serverItems = syncAdapter.pullSnapshot(
                        profileId = profileId,
                        pageSize = librarySnapshotPageSize,
                    )
                    val applyResult = localState.applyServerItems(
                        pullSnapshot = pullSnapshot,
                        serverItems = serverItems,
                        cursorEventId = cursorBeforeSnapshot,
                    ) ?: return@withLock
                    persist(applyResult.snapshot)
                    publish()
                    if (applyResult.preservedLocalItems) {
                        log.i {
                            "Merged pending local library changes during snapshot bootstrap " +
                                "profile=$profileId items=${applyResult.snapshot.items.size}"
                        }
                    }
                }
                pullLibraryDelta(serializedToken, profileId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "Failed to pull library from server" }
            }
        }
    }

    /**
     * Synchronisation de la bibliothèque locale avec D1, séparée des RPC Supabase.
     * Première liaison : import distant sans écraser ni téléverser les données
     * préexistantes. Les mutations locales suivantes utilisent un CAS par élément.
     */
    internal suspend fun synchronizeCloudflareLibrary(profileId: Int) {
        if (!CloudflareAccountClient.enabled) return
        val auth = AuthRepository.state.value as? AuthState.Authenticated ?: return
        if (auth.isAnonymous || CloudflareAccountClient.currentSession?.userId != auth.userId) return
        val remoteProfileId = ProfileRepository.state.value.profiles
            .firstOrNull { it.profileIndex == profileId }?.id
            ?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) } ?: return
        nuvioSyncMutex.withLock {
            val token = activeOperationToken(profileId) ?: return@withLock
            val rows = CloudflareAccountClient.syncCategory(remoteProfileId, "library")
            val remote = CloudflareLibraryCodec.decode(rows)
            val snapshot = localState.snapshot()
            if (snapshot.token != token || !localState.isCurrent(snapshot)) return@withLock
            val saved = LibraryStoragePayloadCodec.decode(
                LibraryStorage.loadPayload(profileId).orEmpty()
            )
            val checkpoint = saved.cloudCheckpoint?.takeIf {
                it.userId == auth.userId && it.profileId == remoteProfileId
            }
            val pendingDeletes = snapshot.pendingDeleteKeys.map {
                libraryItemKey(it.contentId,it.contentType)
            }.toSet()
            val remoteAdditions = remote.filter { (key,row) ->
                !row.deleted && key !in pendingDeletes
            }.values.mapNotNull(CloudLibraryRow::item)
            val confirmedRemoteDeletions = remote.filter { (key,row) ->
                row.deleted && checkpoint?.revisions?.containsKey(key) == true
            }.keys
            val merged = localState.applyCloudItems(
                snapshot, remoteAdditions, confirmedRemoteDeletions
            ) ?: return@withLock
            persist(merged)
            publish()

            val remoteRevisions = remote.mapValues { it.value.revision }
            if (checkpoint == null) {
                // Un ancien invité/compte peut posséder des favoris locaux.
                // Ne pas les attribuer au nouveau compte sans action explicite.
                val clean = localState.markPushCompleted(merged) ?: return@withLock
                persist(clean)
                saveCloudCheckpoint(clean.token, CloudLibraryCheckpoint(
                    userId = auth.userId, profileId = remoteProfileId,
                    revisions = remoteRevisions
                ))
                return@withLock
            }

            val localByKey = merged.items.associateBy {
                libraryItemKey(it.id,it.type)
            }
            val progress = checkpoint.revisions.toMutableMap()
            // D1 et le cache local peuvent changer pendant une requête. Le CAS
            // refuse de remplacer une modification distante non observée.
            suspend fun push(key: String, item: LibraryItem?, deleted: Boolean) {
                val known = progress[key] ?: 0
                val actual = remote[key]?.revision ?: 0
                check(known == actual) {
                    "Conflit cloud sur $key : modification locale préservée (aucun écrasement)"
                }
                check(CloudflareAccountClient.currentSession?.userId == auth.userId) {
                    "Le compte a changé durant la synchronisation"
                }
                val newRevision = CloudflareAccountClient.writeSync(
                    profileId = remoteProfileId,
                    category = "library",
                    itemKey = key,
                    payload = if (deleted) kotlinx.serialization.json.buildJsonObject {}
                        else CloudflareLibraryCodec.payload(checkNotNull(item)),
                    revision = actual,
                    deleted = deleted
                )
                progress[key] = newRevision
                // Sauvegarder chaque accusé de réception : une coupure entre
                // deux écritures ne doit pas provoquer un faux conflit au retour.
                saveCloudCheckpoint(token, CloudLibraryCheckpoint(
                    userId = auth.userId, profileId = remoteProfileId,
                    revisions = progress.toMap()
                ))
            }
            for (pending in merged.pendingUpsertKeys) {
                val key = libraryItemKey(pending.contentId,pending.contentType)
                val item = localByKey[key] ?: continue
                if (!ServerItemRef.isServerId(item.id))
                    push(key,item,deleted=false)
            }
            for (pending in merged.pendingDeleteKeys) {
                val key = libraryItemKey(pending.contentId,pending.contentType)
                if (remote[key]?.deleted == false)
                    push(key,null,deleted=true)
            }
            // Les modifications distantes sans conflit sont maintenant connues.
            val finalRevisions = remoteRevisions + progress
            val acknowledged = localState.markPushCompleted(merged)
            if (acknowledged != null) persist(acknowledged)
            saveCloudCheckpoint(token, CloudLibraryCheckpoint(
                userId = auth.userId, profileId = remoteProfileId,
                revisions = finalRevisions
            ))
            publish()
        }
    }

    private fun saveCloudCheckpoint(
        token: LibraryProfileToken,
        checkpoint: CloudLibraryCheckpoint
    ) {
        synchronized(persistenceLock) {
            val latest = localState.snapshot()
            if (latest.token != token) return@synchronized
            localState.runIfCurrent(latest) {
                LibraryStorage.savePayload(
                    token.profileId,
                    LibraryStoragePayloadCodec.encode(latest,checkpoint)
                )
                lastPersistedRevisionByProfile[token.profileId] = latest.revision
            }
        }
    }

    private suspend fun pullLibraryDelta(
        token: LibraryProfileToken,
        profileId: Int,
    ) {
        val initialSnapshot = localState.snapshot()
        if (initialSnapshot.token != token) return
        consumeCursorPages(
            initialCursor = initialSnapshot.deltaCursorEventId,
            pageSize = libraryDeltaPageSize,
            fetchPage = { cursor, limit ->
                if (isActiveOperation(token)) {
                    syncAdapter.pullDelta(
                        profileId = profileId,
                        sinceEventId = cursor,
                        limit = limit,
                    )
                } else {
                    emptyList()
                }
            },
            applyPage = { events, _ ->
                if (!isActiveOperation(token)) {
                    null
                } else {
                    localState.applyDeltaEvents(token, events)?.also { snapshot ->
                        persist(snapshot)
                        publish()
                    }?.deltaCursorEventId
                }
            },
        )
    }

    private fun activeOperationToken(profileId: Int): LibraryProfileToken? {
        if (ProfileRepository.activeProfileId != profileId) return null
        if (!loadFromDisk(profileId)) return null
        return localState.currentTokenIfLoaded(profileId)
            ?.takeIf { ProfileRepository.activeProfileId == profileId }
    }

    private fun isActiveOperation(token: LibraryProfileToken): Boolean =
        localState.isCurrent(token) && ProfileRepository.activeProfileId == token.profileId

    suspend fun toggleSaved(
        item: LibraryItem,
        confirmedRemovalProviders: Set<TrackingProviderId> = emptySet(),
    ): TrackingMembershipApplyResult {
        ensureLoaded()

        activeLibraryProvider()?.let { provider ->
            if (item.isServerItem) throw IllegalStateException(getString(Res.string.library_server_title_unsupported))
            val providerMembership = provider.membership(item)
            val desiredMembership = provider.toggledDefaultMembership(providerMembership)
            log.i {
                "toggleSaved routed to ${provider.providerId.storageId} library source " +
                    "item=${item.id} type=${item.type} profile=${localState.snapshot().token.profileId}"
            }
            return applyMembershipChanges(
                item = item,
                desiredMembership = desiredMembership,
                confirmedRemovalProviders = confirmedRemovalProviders,
                targetProviderIds = setOf(provider.providerId),
                updateLocal = false,
            )
        }

        return toggleLocalSavedInternal(item)
    }

    fun toggleLocalSaved(item: LibraryItem) {
        ensureLoaded()
        toggleLocalSavedInternal(item)
    }

    private fun toggleLocalSavedInternal(item: LibraryItem): TrackingMembershipApplyResult {
        val result = localState.toggle(
            item.copy(savedAtEpochMs = LibraryClock.nowEpochMs()),
        )
        if (result.isSaved) {
            log.i {
                "Saving local library item item=${item.id} type=${item.type} " +
                    "profile=${result.snapshot.token.profileId}"
            }
        } else {
            log.i {
                "Removing local library item id=${item.id} type=${item.type} " +
                    "profile=${result.snapshot.token.profileId}"
            }
        }
        persist(result.snapshot)
        publish()
        pushToServer(result.snapshot)
        return TrackingMembershipApplyResult()
    }

    fun save(item: LibraryItem) {
        ensureLoaded()
        val snapshot = localState.upsert(item.copy(savedAtEpochMs = LibraryClock.nowEpochMs()))
        log.i {
            "Saving local library item item=${item.id} type=${item.type} profile=${snapshot.token.profileId}"
        }
        persist(snapshot)
        publish()
        pushToServer(snapshot)
    }

    fun remove(id: String) {
        ensureLoaded()
        val result = localState.removeById(id)
        if (result.affectedCount > 0) {
            log.i {
                "Removing local library item id=$id profile=${result.snapshot.token.profileId} " +
                    "removed=${result.affectedCount}"
            }
            persist(result.snapshot)
            publish()
            pushToServer(result.snapshot)
        }
    }

    private fun remove(id: String, type: String) {
        ensureLoaded()
        val result = localState.remove(id, type)
        if (result.affectedCount > 0) {
            log.i {
                "Removing local library item id=$id type=$type profile=${result.snapshot.token.profileId}"
            }
            persist(result.snapshot)
            publish()
            pushToServer(result.snapshot)
        }
    }

    fun isSaved(id: String, type: String? = null): Boolean {
        ensureLoaded()

        activeLibraryProvider()?.let { provider -> return provider.contains(id, type) }

        return if (type != null) {
            localState.contains(id, type)
        } else {
            localState.containsId(id)
        }
    }

    fun savedItem(id: String): LibraryItem? {
        ensureLoaded()

        activeLibraryProvider()?.let { provider -> return provider.find(id) }

        return localState.findById(id)
    }

    fun libraryListTabs(item: LibraryItem? = null): List<TrackingLibraryTab> =
        libraryTabsWithLocal(
            TrackingProviderRegistry.connectedLibraryProviders()
                .flatMap { provider -> provider.snapshot().tabs },
        ).filter { tab ->
            item == null || (tab.supportsContentType(item.type) && (!item.isServerItem || tab.key == LOCAL_LIBRARY_LIST_KEY))
        }

    internal fun listManagementContext(): LibraryManagementContext? {
        val source = effectiveLibrarySourceMode()
        val provider = activeLibraryProvider(source) ?: return null
        if (provider.listManager == null) return null
        val account = TrackingProviderRegistry.authProvider(provider.providerId) ?: return null
        return LibraryManagementContext(ProfileRepository.activeProfileId, source, account.accountGeneration)
    }

    internal fun listManager(context: LibraryManagementContext): com.nuvio.app.features.tracking.TrackingListManager {
        check(context == listManagementContext()) { "Library account changed" }
        return requireNotNull(activeLibraryProvider(context.source)?.listManager)
    }

    suspend fun getMembershipSnapshot(item: LibraryItem): Map<String, Boolean> {
        ensureLoaded()
        val inLocal = localState.contains(item.id, item.type)
        val memberships = linkedMapOf<String, Boolean>()
        if (!item.isServerItem) {
            TrackingProviderRegistry.connectedLibraryProviders().forEach { provider ->
                memberships += provider.membership(item)
            }
        }
        return libraryMembershipWithLocal(inLocal = inLocal, providerMembership = memberships)
    }

    suspend fun applyMembershipChanges(
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
        confirmedRemovalProviders: Set<TrackingProviderId> = emptySet(),
    ): TrackingMembershipApplyResult = applyMembershipChanges(
        item = item,
        desiredMembership = desiredMembership,
        confirmedRemovalProviders = confirmedRemovalProviders,
        targetProviderIds = null,
        updateLocal = true,
    )

    private suspend fun applyMembershipChanges(
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
        confirmedRemovalProviders: Set<TrackingProviderId>,
        targetProviderIds: Set<TrackingProviderId>?,
        updateLocal: Boolean,
    ): TrackingMembershipApplyResult {
        ensureLoaded()
        val localDesired = desiredMembership[LOCAL_LIBRARY_LIST_KEY] == true
        val currentlyInLocal = localState.contains(item.id, item.type)
        val profileId = localState.snapshot().token.profileId
        val requestedChanges = TrackingProviderRegistry.connectedLibraryProviders()
            .filter { provider -> targetProviderIds == null || provider.providerId in targetProviderIds }
            .mapNotNull { provider ->
                val providerListKeys = provider.snapshot().tabs.mapTo(mutableSetOf(), TrackingLibraryTab::key)
                val providerMembership = desiredMembership.filterKeys(providerListKeys::contains)
                providerMembership.takeIf { membership -> membership.isNotEmpty() }?.let { membership ->
                    provider to membership
                }
            }
        if (item.isServerItem && requestedChanges.any { (_, membership) -> membership.values.any { it } }) {
            throw IllegalStateException(getString(Res.string.library_server_title_unsupported))
        }
        val providerChanges = if (item.isServerItem) emptyList() else requestedChanges
        val requiredConfirmations = providerChanges.mapNotNull { (provider, providerMembership) ->
            provider.membershipRemovalConfirmation(item, providerMembership)
                ?.takeUnless { confirmation -> confirmation.providerId in confirmedRemovalProviders }
        }
        log.i {
            "Applying library membership item=${item.id} type=${item.type} profile=$profileId " +
                "localDesired=$localDesired currentlyInLocal=$currentlyInLocal " +
                "connectedProviders=${TrackingProviderRegistry.connectedProviderIdsSnapshot()}"
        }
        if (requiredConfirmations.isNotEmpty()) {
            return TrackingMembershipApplyResult(
                requiredRemovalConfirmations = requiredConfirmations,
            )
        }
        if (updateLocal && localDesired != currentlyInLocal) {
            if (localDesired) {
                save(item)
            } else {
                remove(item.id, item.type)
            }
        }

        var firstFailure: Throwable? = null
        val resolutions = mutableListOf<TrackingMembershipResolution>()
        providerChanges.forEach { (provider, providerMembership) ->
            try {
                provider.applyMembership(
                    profileId = profileId,
                    item = item,
                    desiredMembership = providerMembership,
                    destructiveRemovalConfirmed = provider.providerId in confirmedRemovalProviders,
                )?.let(resolutions::add)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (firstFailure == null) firstFailure = error
                log.e(error) { "Failed to update ${provider.providerId.storageId} library membership" }
            }
        }
        publish()
        firstFailure?.let { throw it }
        return TrackingMembershipApplyResult(resolutions = resolutions)
    }

    suspend fun removeFromList(
        item: LibraryItem,
        listKey: String,
        confirmedRemovalProviders: Set<TrackingProviderId> = emptySet(),
    ): TrackingMembershipApplyResult {
        ensureLoaded()
        val targetProvider = TrackingProviderRegistry.connectedLibraryProviders()
            .firstOrNull { provider -> provider.snapshot().tabs.any { tab -> tab.key == listKey } }
        val currentMembership = if (listKey == LOCAL_LIBRARY_LIST_KEY) {
            mapOf(LOCAL_LIBRARY_LIST_KEY to localState.contains(item.id, item.type))
        } else {
            targetProvider?.membership(item).orEmpty()
        }
        val desiredMembership = libraryMembershipWithRemovedList(
            currentMembership = currentMembership,
            listKey = listKey,
        )
        return applyMembershipChanges(
            item = item,
            desiredMembership = desiredMembership,
            confirmedRemovalProviders = confirmedRemovalProviders,
            targetProviderIds = targetProvider?.let { provider -> setOf(provider.providerId) }.orEmpty(),
            updateLocal = listKey == LOCAL_LIBRARY_LIST_KEY,
        )
    }

    private fun pushToServer(
        snapshot: LibraryLocalSnapshot,
        delayMs: Long = pushDebounceMs,
    ) {
        if (CloudflareAccountClient.enabled) {
            if (!snapshot.hasPendingPush) return
            syncScope.launch {
                delay(delayMs)
                runCatching { synchronizeCloudflareLibrary(snapshot.token.profileId) }
                    .onFailure { log.e(it) { "Cloudflare : modification locale conservée, renvoi à réessayer" } }
            }
            return
        }
        if (!snapshot.hasPendingPush) return
        val authState = AuthRepository.state.value
        val profileId = snapshot.token.profileId
        if (authState !is AuthState.Authenticated) {
            log.w { "Skipping library push: auth state is ${authState::class.simpleName} profile=$profileId" }
            return
        }
        if (authState.isAnonymous) {
            log.w { "Skipping library push: anonymous auth user=${authState.userId} profile=$profileId" }
            return
        }
        val pushJob = syncScope.launch(start = CoroutineStart.LAZY) {
            delay(delayMs)
            nuvioSyncMutex.withLock {
                if (!localState.isCurrent(snapshot)) {
                    val current = localState.snapshot()
                    log.d {
                        "Skipping stale debounced library push scheduled=${snapshot.token} " +
                            "current=${current.token} scheduledRevision=${snapshot.revision} " +
                            "currentRevision=${current.revision}"
                    }
                    return@withLock
                }
                val currentAuthState = AuthRepository.state.value
                if (currentAuthState !is AuthState.Authenticated || currentAuthState.isAnonymous) {
                    return@withLock
                }
                runCatching {
                    val itemsByKey = snapshot.items.associateBy { item ->
                        libraryItemKey(item.id, item.type)
                    }
                    val upsertItems = snapshot.pendingUpsertKeys.mapNotNull { key ->
                        itemsByKey[libraryItemKey(key.contentId, key.contentType)]
                    }.filterNot { ServerItemRef.isServerId(it.id) }
                    syncAdapter.pushItems(profileId, upsertItems)
                    syncAdapter.deleteItems(profileId, snapshot.pendingDeleteKeys.filterNot { ServerItemRef.isServerId(it.contentId) })
                    localState.markPushCompleted(snapshot)?.let(::persist)
                    log.i {
                        "Library delta push completed profile=$profileId " +
                            "upserts=${upsertItems.size} deletes=${snapshot.pendingDeleteKeys.size}"
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    log.e(error) {
                        "Failed to push library delta profile=$profileId " +
                            "upserts=${snapshot.pendingUpsertKeys.size} deletes=${snapshot.pendingDeleteKeys.size}"
                    }
                }
            }
        }
        pushJob.invokeOnCompletion { localState.clearPushJob(pushJob) }

        val installResult = localState.installPushJob(snapshot, pushJob)
        if (!installResult.installed) {
            pushJob.cancel()
            return
        }
        installResult.detachedPushJob?.cancel()
        pushJob.start()
    }

    private fun publish() {
        val localSnapshot = localState.snapshot()
        val sourceMode = effectiveLibrarySourceMode()
        activeLibraryProvider(sourceMode)?.let { provider ->
            val providerSnapshot = provider.snapshot()
            val newUiState = LibraryUiState(
                sourceMode = sourceMode,
                items = providerSnapshot.items,
                sections = providerSnapshot.sections,
                isLoaded = providerSnapshot.hasLoaded,
                isLoading = providerSnapshot.isLoading,
                errorMessage = providerSnapshot.errorMessage,
            )
            localState.runIfTokenCurrent(localSnapshot.token) {
                _uiState.value = newUiState
            }
            return
        }

        val items = localSnapshot.items
            .sortedByDescending { it.savedAtEpochMs }
        val sections = items
            .groupBy { it.type }
            .map { (type, typeItems) ->
                LibrarySection(
                    type = type,
                    displayTitle = type.toLibraryDisplayTitle(),
                    items = typeItems.sortedByDescending { it.savedAtEpochMs },
                )
            }
            .sortedBy { it.displayTitle }

        val newUiState = LibraryUiState(
            sourceMode = LibrarySourceMode.LOCAL,
            items = items,
            sections = sections,
            isLoaded = localSnapshot.hasLoaded,
            isLoading = localSnapshot.isLoading,
            errorMessage = null,
        )
        localState.runIfCurrent(localSnapshot) {
            _uiState.value = newUiState
        }
    }

    private fun persist(snapshot: LibraryLocalSnapshot) {
        synchronized(persistenceLock) {
            val profileId = snapshot.token.profileId
            val lastPersistedRevision = lastPersistedRevisionByProfile[profileId] ?: Long.MIN_VALUE
            if (snapshot.revision <= lastPersistedRevision) return@synchronized
            localState.runIfCurrent(snapshot) {
                val payload = LibraryStoragePayloadCodec.encode(snapshot)
                LibraryStorage.savePayload(profileId, payload)
                lastPersistedRevisionByProfile[profileId] = snapshot.revision
            }
        }
    }

    private fun refreshLibraryProviderAsync(provider: TrackingLibraryProvider) {
        syncScope.launch {
            refreshLibraryProvider(
                provider = provider,
                reason = "background refresh",
                intent = TrackingRefreshIntent.AUTOMATIC,
            )
            publish()
        }
    }

    private suspend fun refreshLibraryProvider(
        provider: TrackingLibraryProvider,
        reason: String,
        intent: TrackingRefreshIntent,
    ) {
        log.i {
            "Tracking library refresh request provider=${provider.providerId.storageId} " +
                "reason=$reason intent=$intent"
        }
        try {
            provider.refresh(intent)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.e(error) {
                "Failed to refresh ${provider.providerId.storageId} library during $reason"
            }
        }
    }

    private fun selectedLibrarySourceMode(): LibrarySourceMode {
        TrackingSettingsRepository.ensureLoaded()
        return TrackingSettingsRepository.uiState.value.librarySourceMode
    }

    private fun effectiveLibrarySourceMode(): LibrarySourceMode =
        resolveEffectiveLibrarySourceMode(
            requestedSource = selectedLibrarySourceMode(),
            isProviderAuthenticated = { providerId ->
                TrackingProviderRegistry.libraryProvider(providerId) != null &&
                    TrackingProviderRegistry.isAuthenticated(providerId)
            },
        )

    private fun activeLibraryProvider(
        sourceMode: LibrarySourceMode = effectiveLibrarySourceMode(),
    ): TrackingLibraryProvider? =
        sourceMode.providerId?.let(TrackingProviderRegistry::libraryProvider)
}

internal const val LOCAL_LIBRARY_LIST_KEY = "local"

private val LibraryItem.isServerItem: Boolean
    get() = ServerItemRef.isServerId(id)
private const val DEFAULT_LOCAL_LIBRARY_TAB_TITLE = "Nuvio Library"
private const val DEFAULT_LIBRARY_OTHER_TITLE = "Other"

internal fun localLibraryListTab(): TrackingLibraryTab =
    TrackingLibraryTab(
        key = LOCAL_LIBRARY_LIST_KEY,
        title = localizedStringOrDefault(
            resource = Res.string.library_local_tab_title,
            fallback = DEFAULT_LOCAL_LIBRARY_TAB_TITLE,
        ),
        providerId = null,
        kind = TrackingLibraryTabKind.WATCHLIST,
    )

internal fun libraryTabsWithLocal(providerTabs: List<TrackingLibraryTab>): List<TrackingLibraryTab> =
    listOf(localLibraryListTab()) + providerTabs

internal fun libraryMembershipWithLocal(
    inLocal: Boolean,
    providerMembership: Map<String, Boolean> = emptyMap(),
): Map<String, Boolean> =
    linkedMapOf<String, Boolean>(LOCAL_LIBRARY_LIST_KEY to inLocal).apply {
        putAll(providerMembership)
    }

internal fun libraryMembershipWithRemovedList(
    currentMembership: Map<String, Boolean>,
    listKey: String,
): Map<String, Boolean> =
    currentMembership.toMutableMap().apply {
        this[listKey] = false
    }

internal fun String.toLibraryDisplayTitle(): String {
    val normalized = trim()
    if (normalized.isBlank()) return localizedLibraryOtherTitle()

    return normalized
        .split('-', '_', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { char -> char.uppercase() }
        }
        .ifBlank { localizedLibraryOtherTitle() }
}

private fun localizedLibraryOtherTitle(): String =
    localizedStringOrDefault(
        resource = Res.string.library_other,
        fallback = DEFAULT_LIBRARY_OTHER_TITLE,
    )

private fun localizedStringOrDefault(resource: StringResource, fallback: String): String =
    runCatching { runBlocking { getString(resource) } }
        .getOrDefault(fallback)
