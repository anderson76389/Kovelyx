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
assert "locals.size == 1 && remotes.size == 1" in profile_matcher
print("TV: session sécurisée, bibliothèque D1 distincte et association non ambiguë détectées")
print("NOTE: contrôle statique uniquement; la compilation, l'essai réel et les conflits D1 restent à valider")

backend = (ROOT / "services/kovelyx-cloudflare/src/index.mjs").read_text(encoding="utf-8")
contract = (ROOT / "services/kovelyx-cloudflare/src/contract.mjs").read_text(encoding="utf-8")
config = (ROOT / "services/kovelyx-cloudflare/wrangler.toml").read_text(encoding="utf-8")
assert '"/v1/sync"' in backend
assert '"/v1/profiles"' in backend
assert "KOVELYX_REGISTRATION_OPEN" in backend
assert '"watch_progress"' in contract
assert "# [[r2_buckets]]" in config, "R2 privé doit rester facultatif sans facturation surprise"
assert "\n[[r2_buckets]]" not in config, "R2 activé sans accord explicite"
print("Worker: D1/Better Auth présents, inscriptions contrôlées, R2 non activé")

print("LIMITES: ne valide pas les sessions de production, le transfert entre appareils,")
print("l'absence de trafic Nuvio des librairies natives ou toutes les fonctions UI.")
