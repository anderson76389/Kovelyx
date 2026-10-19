package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import com.nuvio.tv.R
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.LibrarySyncService
import com.nuvio.tv.core.sync.CloudflareTvLibraryStore
import com.nuvio.tv.core.sync.CloudflareTvProfileBinding
import com.nuvio.tv.core.tracking.TrackingListManager
import com.nuvio.tv.core.tracking.TrackingLibraryProviderRegistry
import com.nuvio.tv.core.tracking.TrackingMembershipApplyResult
import com.nuvio.tv.core.tracking.TrackingProviderId
import com.nuvio.tv.core.tracking.LOCAL_LIBRARY_LIST_KEY
import com.nuvio.tv.core.tracking.TrackingMembershipMutationException
import com.nuvio.tv.core.tracking.dispatchTrackingMembershipChanges
import com.nuvio.tv.core.tracking.TrackingRefreshIntent
import com.nuvio.tv.core.tracking.effectiveLibrarySourceMode
import com.nuvio.tv.core.tracking.providerId
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.TraktSettingsDataStore
import com.nuvio.tv.data.mediaserver.ServerItemRef
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.ListMembershipChanges
import com.nuvio.tv.domain.model.ListMembershipSnapshot
import com.nuvio.tv.domain.model.SavedLibraryItem
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.domain.model.LibraryListPrivacy
import com.nuvio.tv.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val libraryPreferences: LibraryPreferences,
    private val traktSettingsDataStore: TraktSettingsDataStore,
    private val librarySyncService: LibrarySyncService,
    private val authManager: AuthManager,
    private val metaRepository: MetaRepository,
    private val trackingProviders: TrackingLibraryProviderRegistry,
    private val profileManager: ProfileManager,
) : LibraryRepository {

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hydratedLogoIds = mutableSetOf<String>()
    private var syncJob: Job? = null
    private val _isSyncingFromRemote = MutableStateFlow(false)
    var isSyncingFromRemote: Boolean
        get() = _isSyncingFromRemote.value
        set(value) { _isSyncingFromRemote.value = value }
    @Volatile
    var hasCompletedInitialPull = false

    init {
        if (CloudflareTvAccountClient.enabled) {
            // Démarrage et changement de profil fonctionnent aussi sans ouvrir
            // l'écran Compte. Ne jamais transférer les données du mode invité.
            syncScope.launch {
                combine(
                    authManager.authState,
                    profileManager.activeProfileId,
                    profileManager.profiles
                ) { state, localId, locals -> Triple(state, localId, locals) }
                    .collectLatest { (auth, localId, locals) ->
                        val cloud = CloudflareTvAccountClient.account
                        if (auth !is AuthState.FullAccount || cloud?.id != auth.userId) {
                            CloudflareTvLibraryStore.reset()
                            return@collectLatest
                        }
                        try {
                            val remote = CloudflareTvAccountClient.getCloudProfiles()
                            val bindings = CloudflareTvProfileBinding.resolve(
                                locals.map { CloudflareTvProfileBinding.Local(it.id, it.name) },
                                remote.map { CloudflareTvProfileBinding.Remote(it.id, it.name) }
                            )
                            val uuid = bindings[localId]
                            if (uuid == null) CloudflareTvLibraryStore.reset()
                            else CloudflareTvLibraryStore.bind(appContext, localId, uuid)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (error: Exception) {
                            // Un réseau indisponible n'efface ni la bibliothèque
                            // invitée ni le cache local du compte authentifié.
                            Log.w("CloudflareTvLibrary", "Lecture D1 reportée", error)
                        }
                    }
            }
        }
    }

    private fun triggerRemoteSync(profileId: Int) {
        // La synchronisation distante historique utilise Supabase.
        // Conserver les modifications uniquement sur l'appareil en mode D1.
        if (CloudflareTvAccountClient.enabled) return
        if (!hasCompletedInitialPull) return
        if (!authManager.isAuthenticated) return
        syncJob?.cancel()
        syncJob = syncScope.launch {
            delay(500)
            librarySyncService.pushToRemote(profileId)
        }
    }

    private val providerConnections = combine(
        trackingProviders.providers().map { provider ->
            provider.isAuthenticated.map { authenticated -> provider.providerId to authenticated }
        }
    ) { states -> states.toMap() }

    override val sourceMode: Flow<LibrarySourceMode> = combine(
        traktSettingsDataStore.librarySourceMode,
        providerConnections
    ) { mode, connections ->
        effectiveLibrarySourceMode(mode) { providerId -> connections[providerId] == true }
    }.distinctUntilChanged()

    override val isSyncing: Flow<Boolean> = sourceMode
        .flatMapLatest { mode ->
            mode.providerId
                ?.let(trackingProviders::provider)
                ?.isRefreshing
                ?: _isSyncingFromRemote
        }
        .distinctUntilChanged()

    override val libraryItems: Flow<List<LibraryEntry>> = sourceMode
        .flatMapLatest { mode ->
            val provider = mode.providerId?.let(trackingProviders::provider)
            if (provider != null) {
                provider.items
            } else {
                combine(
                    libraryPreferences.libraryItems,
                    CloudflareTvLibraryStore.state,
                    profileManager.activeProfileId
                ) { guestItems, cloud, selectedProfileId ->
                    val activeAccount = CloudflareTvAccountClient.account
                    // Aucune bibliothèque invitée n'est présentée comme appartenant à D1.
                    val items = if (CloudflareTvAccountClient.enabled && activeAccount != null) {
                        cloud?.takeIf {
                            it.ownerId == activeAccount.id &&
                                it.localProfileId == selectedProfileId
                        }?.items?.values?.toList().orEmpty()
                    } else guestItems
                    items.map { saved ->
                        LibraryEntry(
                            id = saved.id, type = saved.type, name = saved.name,
                            poster = saved.poster, posterShape = saved.posterShape,
                            background = saved.background, logo = saved.logo,
                            description = saved.description, releaseInfo = saved.releaseInfo,
                            imdbRating = saved.imdbRating, genres = saved.genres,
                            addonBaseUrl = saved.addonBaseUrl, listedAt = saved.addedAt,
                            rawPosterUrl = saved.poster
                        )
                    }
                }
            }
        }
        .distinctUntilChanged()

    override val listTabs: Flow<List<LibraryListTab>> = sourceMode
        .flatMapLatest { mode ->
            mode.providerId
                ?.let(trackingProviders::provider)
                ?.tabs
                ?: flowOf(emptyList())
        }
        .distinctUntilChanged()

    override val membershipListTabs: Flow<List<LibraryListTab>> = combine(
        trackingProviders.providers().map { provider ->
            provider.isAuthenticated.flatMapLatest { authenticated ->
                if (authenticated) provider.tabs else flowOf(emptyList())
            }
        }
    ) { tabs -> tabs.flatMap { it } }
        .distinctUntilChanged()

    override fun isInLibrary(itemId: String, itemType: String): Flow<Boolean> {
        return sourceMode.flatMapLatest { mode ->
            val provider = mode.providerId?.let(trackingProviders::provider)
            if (provider != null) {
                provider.observeMembership(itemId, itemType)
                    .map { memberships -> memberships.isNotEmpty() }
            } else {
                if (CloudflareTvAccountClient.enabled && CloudflareTvAccountClient.account != null)
                    libraryItems.map { items ->
                        items.any { it.id == itemId && it.type.equals(itemType, ignoreCase = true) }
                    }
                else libraryPreferences.isInLibrary(itemId = itemId, itemType = itemType)
            }
        }.distinctUntilChanged()
    }

    override fun isInWatchlist(itemId: String, itemType: String): Flow<Boolean> {
        return sourceMode.flatMapLatest { mode ->
            val provider = mode.providerId?.let(trackingProviders::provider)
            if (provider != null) {
                combine(
                    provider.observeMembership(itemId, itemType),
                    provider.tabs
                ) { memberships, tabs ->
                    tabs.any { tab ->
                        tab.type == LibraryListTab.Type.WATCHLIST && tab.key in memberships
                    }
                }
            } else {
                if (CloudflareTvAccountClient.enabled && CloudflareTvAccountClient.account != null)
                    libraryItems.map { items ->
                        items.any { it.id == itemId && it.type.equals(itemType, ignoreCase = true) }
                    }
                else libraryPreferences.isInLibrary(itemId = itemId, itemType = itemType)
            }
        }.distinctUntilChanged()
    }

    override suspend fun toggleDefault(
        item: LibraryEntryInput,
        confirmedRemovalProviders: Set<TrackingProviderId>
    ): TrackingMembershipApplyResult {
        val provider = sourceMode.first().providerId?.let(trackingProviders::provider)
        if (provider != null) {
            if (item.isServerItem) throw IllegalStateException(appContext.getString(R.string.library_server_title_unsupported))
            val currentMembership = provider.getMembershipSnapshot(item).listMembership
            val changes = ListMembershipChanges(
                provider.toggledDefaultMembership(currentMembership)
            )
            val confirmation = provider.membershipRemovalConfirmation(item, changes)
                ?.takeUnless { required ->
                    required.providerId in confirmedRemovalProviders
                }
            if (confirmation != null) {
                return TrackingMembershipApplyResult(listOf(confirmation))
            }
            provider.applyMembershipChanges(
                item = item,
                changes = changes,
                destructiveRemovalConfirmed = provider.providerId in confirmedRemovalProviders
            )
            return TrackingMembershipApplyResult()
        }

        val profileId = profileManager.activeProfileId.value
        if (CloudflareTvAccountClient.enabled && CloudflareTvAccountClient.account != null) {
            CloudflareTvLibraryStore.toggle(appContext, profileId, item.toSavedLibraryItem())
            return TrackingMembershipApplyResult()
        }
        val isInLocal = libraryPreferences.containsItem(
            itemId = item.itemId,
            itemType = item.itemType,
            profileId = profileId
        )
        if (isInLocal) {
            libraryPreferences.removeItem(
                itemId = item.itemId,
                itemType = item.itemType,
                profileId = profileId
            )
        } else {
            libraryPreferences.addItem(
                item = item.toSavedLibraryItem(),
                profileId = profileId
            )
        }
        triggerRemoteSync(profileId)
        return TrackingMembershipApplyResult()
    }

    override suspend fun getMembershipSnapshot(item: LibraryEntryInput): ListMembershipSnapshot {
        val profileId = profileManager.activeProfileId.value
        val inLocal = if (CloudflareTvAccountClient.enabled &&
            CloudflareTvAccountClient.account != null) {
            CloudflareTvLibraryStore.contains(profileId, item.itemId, item.itemType)
        } else libraryPreferences.containsItem(
            itemId = item.itemId,
            itemType = item.itemType,
            profileId = profileId
        )

        val membership = mutableMapOf<String, Boolean>()
        membership[LOCAL_LIBRARY_LIST_KEY] = inLocal

        if (!item.isServerItem) {
            trackingProviders.providers().forEach { provider ->
                if (provider.isAuthenticated.first()) {
                    membership.putAll(provider.getMembershipSnapshot(item).listMembership)
                }
            }
        }

        return ListMembershipSnapshot(listMembership = membership)
    }

    override suspend fun applyMembershipChanges(
        item: LibraryEntryInput,
        changes: ListMembershipChanges,
        confirmedRemovalProviders: Set<TrackingProviderId>
    ): TrackingMembershipApplyResult {
        val desired = changes.desiredMembership
        val requestedChanges = trackingProviders.providers().mapNotNull { provider ->
            if (!provider.isAuthenticated.first()) return@mapNotNull null
            desired.filterKeys(provider::recognizesListKey)
                .takeIf { providerMembership -> providerMembership.isNotEmpty() }
                ?.let { provider to ListMembershipChanges(it) }
        }
        if (item.isServerItem && requestedChanges.any { (_, change) -> change.desiredMembership.values.any { it } }) {
            throw IllegalStateException(appContext.getString(R.string.library_server_title_unsupported))
        }
        val providerChanges = if (item.isServerItem) emptyList() else requestedChanges
        val requiredConfirmations = providerChanges.mapNotNull { (provider, providerChange) ->
            provider.membershipRemovalConfirmation(item, providerChange)
                ?.takeUnless { confirmation -> confirmation.providerId in confirmedRemovalProviders }
        }
        if (requiredConfirmations.isNotEmpty()) {
            return TrackingMembershipApplyResult(requiredConfirmations)
        }

        val profileId = profileManager.activeProfileId.value
        val localDesired = desired[LOCAL_LIBRARY_LIST_KEY] == true
        val cloudMode = CloudflareTvAccountClient.enabled && CloudflareTvAccountClient.account != null
        val currentlyInLocal = if (cloudMode)
            CloudflareTvLibraryStore.contains(profileId, item.itemId, item.itemType)
        else libraryPreferences.containsItem(
            itemId = item.itemId,
            itemType = item.itemType,
            profileId = profileId
        )
        if (localDesired != currentlyInLocal) {
            if (cloudMode) {
                CloudflareTvLibraryStore.toggle(appContext, profileId, item.toSavedLibraryItem())
            } else {
            if (localDesired) {
                libraryPreferences.addItem(
                    item = item.toSavedLibraryItem(),
                    profileId = profileId
                )
            } else {
                libraryPreferences.removeItem(
                    itemId = item.itemId,
                    itemType = item.itemType,
                    profileId = profileId
                )
            }
            triggerRemoteSync(profileId)
            }
        }

        val failures = dispatchTrackingMembershipChanges(
            item = item,
            providerChanges = providerChanges,
            confirmedRemovalProviders = confirmedRemovalProviders
        )
        if (failures.isNotEmpty()) {
            throw TrackingMembershipMutationException(failures)
        }
        return TrackingMembershipApplyResult()
    }

    override suspend fun createPersonalList(name: String, description: String?, privacy: LibraryListPrivacy, source: LibrarySourceMode) {
        selectedListManager(source).createList(name, description, privacy)
    }

    override suspend fun updatePersonalList(
        listId: String,
        name: String,
        description: String?,
        privacy: LibraryListPrivacy,
        source: LibrarySourceMode
    ) {
        selectedListManager(source, listOf(listId)).updateList(listId, name, description, privacy)
    }

    override suspend fun deletePersonalList(listId: String, source: LibrarySourceMode) {
        selectedListManager(source, listOf(listId)).deleteList(listId)
    }

    override suspend fun reorderPersonalLists(orderedListIds: List<String>, source: LibrarySourceMode) {
        selectedListManager(source, orderedListIds).reorderLists(orderedListIds)
    }

    private suspend fun selectedListManager(
        source: LibrarySourceMode,
        keys: List<String> = emptyList()
    ): TrackingListManager {
        val profileId = profileManager.activeProfileId.value
        check(sourceMode.first() == source) { "Library source changed" }
        val provider = source.providerId?.let(trackingProviders::provider)
            ?: throw IllegalStateException("This library does not support list management")
        check(provider.isAuthenticated.first()) { "Connect the library account first" }
        require(keys.all(provider::recognizesListKey)) { "Invalid list for the library provider" }
        check(profileManager.activeProfileId.value == profileId) { "Profile changed" }
        return provider.listManager ?: throw IllegalStateException("This provider does not support list management")
    }

    override suspend fun refreshNow() {
        val providerId = sourceMode.first().providerId
        if (providerId != null) {
            trackingProviders.provider(providerId)?.refresh(TrackingRefreshIntent.USER_INITIATED)
        } else if (CloudflareTvAccountClient.enabled && CloudflareTvAccountClient.account != null) {
            CloudflareTvLibraryStore.refresh(appContext, profileManager.activeProfileId.value)
        }
    }


    private fun LibraryEntryInput.toSavedLibraryItem(): SavedLibraryItem {
        return SavedLibraryItem(
            id = itemId,
            type = itemType,
            name = title,
            poster = poster,
            posterShape = posterShape,
            background = background,
            description = description,
            releaseInfo = releaseInfo,
            imdbRating = imdbRating,
            genres = genres,
            addonBaseUrl = addonBaseUrl,
            logo = logo
        )
    }

    private suspend fun hydrateLibraryLogos(items: List<LibraryEntry>) {
        items.take(20).forEach { entry ->
            hydratedLogoIds.add(entry.id)
            runCatching {
                val result = metaRepository.getMetaFromPrimaryAddon(entry.type, entry.id)
                    .firstOrNull { it is NetworkResult.Success }
                val logo = (result as? NetworkResult.Success)?.data?.logo
                if (logo != null) {
                    libraryPreferences.updateLogo(entry.id, entry.type, logo)
                }
            }.onFailure { Log.w("LibraryRepo", "Logo hydration failed for ${entry.id}", it) }
        }
    }
}

private val LibraryEntryInput.isServerItem: Boolean
    get() = ServerItemRef.isServerId(itemId)
