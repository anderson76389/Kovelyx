#!/usr/bin/env python3
"""Audit de frontière Cloudflare/ancien backend, déterministe et sans secrets.

Un succès N'EST PAS une preuve de synchronisation complète : ce contrôle empêche
seulement des régressions connues (reprise Supabase/ancien lien Nuvio).
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

for app in ("desktop", "mobile"):
    root = ROOT / "apps" / app / "composeApp/src/commonMain/kotlin/com/nuvio/app"
    provider = (root / "core/network/SupabaseProvider.kt").read_text(encoding="utf-8")
    account = (root / "core/auth/AuthRepository.kt").read_text(encoding="utf-8")
    device = (root / "core/auth/DeviceLinkAuthRepository.kt").read_text(encoding="utf-8")
    library = (root / "features/library/LibraryRepository.kt").read_text(encoding="utf-8")
    assert "check(!CloudflareAccountClient.enabled)" in provider, f"{app}: fail-closed absent"
    assert "CloudflareAccountClient.signIn" in account, f"{app}: authentification D1 absente"
    assert "CloudflareAccountClient.signOut" in account, f"{app}: déconnexion D1 absente"
    assert "if (CloudflareAccountClient.enabled)" in device, f"{app}: ancien lien par code non bloqué"
    assert "synchronizeCloudflareLibrary" in library, f"{app}: bibliothèque D1 absente"
    assert "https://nuvio.tv/link" not in device, f"{app}: ancien lien Nuvio encore présent"
    watched_store = (root / "features/watched/WatchedStorage.kt").read_text(encoding="utf-8")
    assert "accountScopedWatchedStorageKey" in watched_store
    assert "WatchedRepository.clearLocalState()" in account, f"{app}: vue historique non purgée"
    watched_android = (ROOT / "apps" / app /
        "composeApp/src/androidMain/kotlin/com/nuvio/app/features/watched/WatchedStorage.android.kt").read_text(encoding="utf-8")
    assert "accountScopedWatchedStorageKey" in watched_android
    print(f"{app}: compte Cloudflare isolé du client Supabase et lien Nuvio désactivé")

tv_root = ROOT / "apps/tv/app/src/main/java/com/nuvio/tv"
tv = (tv_root / "core/auth/CloudflareTvAccountClient.kt").read_text(encoding="utf-8")
library_tv = (tv_root / "core/sync/LibrarySyncService.kt").read_text(encoding="utf-8")
assert "signIn(context: android.content.Context, email: String, password: String)" in tv
vault = (tv_root / "core/auth/TvCloudSessionVault.kt").read_text(encoding="utf-8")
assert "AndroidKeyStore" in vault and "AES/GCM/NoPadding" in vault
assert "restoreVerified" in tv and '"/v1/me"' in tv
assert "if (CloudflareTvAccountClient.enabled)" in library_tv
assert "Bibliothèque TV Cloudflare D1 non activée" in library_tv
tv_watch_engine = (tv_root / "core/sync/CloudflareTvWatchSyncService.kt").read_text(encoding="utf-8")
tv_watch_repository = (tv_root / "data/repository/WatchProgressRepositoryImpl.kt").read_text(encoding="utf-8")
tv_watch_mutations = (tv_root / "core/sync/WatchStateMutationStore.kt").read_text(encoding="utf-8")
tv_watch_startup = (tv_root / "core/sync/StartupSyncService.kt").read_text(encoding="utf-8")
tv_watch_factory = (tv_root / "data/local/ProfileDataStoreFactory.kt").read_text(encoding="utf-8")
assert "CloudflareTvAccountClient.readProgress" in tv_watch_engine
assert "CloudflareTvAccountClient.writeProgress" in tv_watch_engine
assert "CloudflareTvAccountClient.readWatched" in tv_watch_engine
assert "CloudflareTvAccountClient.writeWatched" in tv_watch_engine
assert "acknowledgeProgressUpserts" in tv_watch_engine and "acknowledgeWatchedUpserts" in tv_watch_engine
assert "pendingProgressDeleteTimes" in tv_watch_engine and "pendingWatchedDeleteTimes" in tv_watch_engine
assert "progress_delete_times_cloud" in tv_watch_mutations
assert "watched_delete_times_cloud" in tv_watch_mutations
assert "CloudflareTvWatchSyncService" in tv_watch_repository
assert "cloudTvWatchSyncService.sync" in tv_watch_repository
assert "cloudWatchSyncService.sync" in tv_watch_startup
assert "cloudWatchFeatures" in tv_watch_factory and "cloudProfileId" in tv_watch_factory
assert "CloudflareTvAccountClient.ownerId" in (
    tv_root / "data/local/WatchProgressPreferences.kt"
).read_text(encoding="utf-8")
assert "CloudflareTvAccountClient.ownerId" in (
    tv_root / "data/local/WatchedItemsPreferences.kt"
).read_text(encoding="utf-8")
print("TV : lectures et écritures D1 reprises/vus reliées au lecteur, stockage isolé par profil.")
print("Contrôle statique uniquement : la compilation et les appareils doivent encore confirmer.")

cloud_store = (tv_root / "core/sync/CloudflareTvLibraryStore.kt").read_text(encoding="utf-8")
profile_matcher = (tv_root / "core/sync/CloudflareTvProfileBinding.kt").read_text(encoding="utf-8")
tv_repo = (tv_root / "data/repository/LibraryRepositoryImpl.kt").read_text(encoding="utf-8")
tv_account = (tv_root / "ui/screens/account/AccountViewModel.kt").read_text(encoding="utf-8")
assert "readLibrary" in tv and "writeLibrary" in tv
assert "CloudflareTvLibraryStore.bind" in tv_repo
assert "restoreVerifiedBinding" in tv_repo
assert "forgetBinding" in tv_repo
assert "bindingKey(ownerId: String, localProfileId: Int)" in cloud_store
assert "sameResult" in cloud_store and "pending.remove(id)" in cloud_store
auth_tv = (tv_root / "core/auth/AuthManager.kt").read_text(encoding="utf-8")
assert "TvCloudSessionVault.read(appContext)" in auth_tv
assert "backoffMs" in auth_tv
assert "CloudflareTvLibraryStore.toggle" in tv_repo
assert "CloudflareTvProfileBinding.resolve" in tv_repo
assert "pendingDeletes" in cloud_store and "currentRev" in cloud_store
assert "cloud?.takeIf" in tv_repo and "guestItems" in tv_repo
assert "CloudflareTvLibraryStore.reset()" in tv_account
# Le diagnostic de reprises TV doit utiliser uniquement les routes D1
# du compte actif, sans écrire dans l'historique local de l'invité.
tv_screen = (tv_root / "ui/screens/account/AccountScreen.kt").read_text(encoding="utf-8")
assert 'readSyncCategory(profileId: String, category: String)' in tv
assert 'readSyncCategory(profileId, "watch_progress")' in tv
assert 'CloudflareTvAccountClient.readProgress(profile.id)' in tv_account
assert 'CloudflareTvAccountClient.readLibrary(profile.id)' in tv_account
assert 'CloudflareTvAccountClient.account == owner' in tv_account
assert 'cloudSyncPreview = emptyList()' in tv_account
assert 'refreshCloudflareSyncPreview()' in tv_screen
assert 'Aperçu en lecture seule du compte' in tv_screen
assert 'if (CloudflareTvAccountClient.enabled)' in tv_screen
print("TV: aperçu cloud D1 en lecture seule, données invitées préservées")
assert "locals.size == 1 && remotes.size == 1" in profile_matcher
for relative in (
    "core/sync/WatchProgressSyncService.kt",
    "core/sync/WatchedItemsSyncService.kt",
):
    source = (tv_root / relative).read_text(encoding="utf-8")
    assert "CloudflareTvAccountClient.enabled" in source, f"{relative}: Cloudflare guard absent"
    assert "check(!CloudflareTvAccountClient.enabled)" in source, f"{relative}: RPC Supabase non bloqué"
    for name in ("pushToRemote", "pullFromRemote", "syncDeltaFromRemote", "syncSnapshotFromRemote"):
        begin = source.find("suspend fun " + name + "(")
        assert begin >= 0, f"{relative}: {name} absent"
        end = source.find("return@withContext Result.failure(", begin)
        assert 0 <= end - begin < 600, f"{relative}: {name} non bloqué en mode Cloudflare"
# Garde-fou: tous les RPC herites doivent etre fermes pour un compte D1.
for legacy_service in (
    "AddonSyncService", "PluginSyncService", "CollectionSyncService",
    "HomeCatalogSettingsSyncService", "ProfileSettingsSyncService",
    "ProviderCredentialSyncService", "ProfileSyncService", "MediaServerSyncService",
):
    code = (tv_root / f"core/sync/{legacy_service}.kt").read_text(encoding="utf-8")
    assert "check(!CloudflareTvAccountClient.enabled)" in code, legacy_service
legacy_module = (tv_root / "core/di/SupabaseModule.kt").read_text(encoding="utf-8")
assert "supabase-disabled.invalid" in legacy_module
assert "autoLoadFromStorage = !legacyDisabled" in legacy_module
assert "autoSaveToStorage = !legacyDisabled" in legacy_module
assert "alwaysAutoRefresh = !legacyDisabled" in legacy_module
print("TV : anciens RPC fermes et sessions Supabase non restaurees")
print("TV: progression et historique Supabase bloqués pour les comptes Cloudflare")
print("TV: session sécurisée, bibliothèque D1 distincte et association non ambiguë détectées")
print("NOTE: contrôle statique uniquement; la compilation, l'essai réel et les conflits D1 restent à valider")

backend = (ROOT / "services/kovelyx-cloudflare/src/index.mjs").read_text(encoding="utf-8")
contract = (ROOT / "services/kovelyx-cloudflare/src/contract.mjs").read_text(encoding="utf-8")
config = (ROOT / "services/kovelyx-cloudflare/wrangler.toml").read_text(encoding="utf-8")
assert '"/v1/sync"' in backend
assert '"/v1/profiles"' in backend
assert "KOVELYX_REGISTRATION_OPEN" in backend
assert '"watch_progress"' in contract

watched_api = (ROOT / "services/kovelyx-cloudflare/src/watched.mjs").read_text(encoding="utf-8")
watched_migration = (ROOT / "services/kovelyx-cloudflare/migrations/0002_watched_items.sql").read_text(encoding="utf-8")
assert 'url.pathname === "/v1/watched"' in backend
assert "kv_watched_items" in watched_api and "revision=revision+1" in watched_api
assert "FOREIGN KEY(owner_id, profile_id)" in watched_migration
assert "CREATE TABLE IF NOT EXISTS kv_sync " not in watched_migration, "La migration ne doit pas rebatir les favoris"
for app in ("desktop", "mobile"):
    client = (ROOT / "apps" / app /
              "composeApp/src/commonMain/kotlin/com/nuvio/app/core/cloud/CloudflareAccountClient.kt").read_text(encoding="utf-8")
    assert '"/v1/watched"' in client and "readWatched" in client and "writeWatched" in client
tv_client = (tv_root / "core/auth/CloudflareTvAccountClient.kt").read_text(encoding="utf-8")
assert '"/v1/watched"' in tv_client
assert "readWatched" in tv_client and "writeWatched" in tv_client
print("Contrats prêts pour l'historique vu : Worker D1, Windows, Android, TV")
print("NOTE: ces méthodes ne sont pas encore branchées à tous les écrans ni aux mutations locales.")
assert "# [[r2_buckets]]" in config, "R2 privé doit rester facultatif sans facturation surprise"
assert "\n[[r2_buckets]]" not in config, "R2 activé sans accord explicite"
print("Worker: D1/Better Auth présents, inscriptions contrôlées, R2 non activé")

print("LIMITES: ne valide pas les sessions de production, le transfert entre appareils,")
print("l'absence de trafic Nuvio des librairies natives ou toutes les fonctions UI.")
