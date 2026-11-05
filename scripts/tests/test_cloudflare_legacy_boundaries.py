#!/usr/bin/env python3
"""Non-regression: Kovelyx Cloudflare must not trigger legacy profile RPCs.

Static source checks, NOT a substitute for executing the apps on real devices.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
PLATFORMS = ("desktop", "mobile")


class CloudflareLegacyBoundaryTests(unittest.TestCase):
    def test_retired_background_catalog_is_deleted_from_both_clients(self):
        for platform in PLATFORMS:
            with self.subTest(platform=platform):
                source = (
                    ROOT / f"apps/{platform}/composeApp/src/commonMain/kotlin/"
                    "com/nuvio/app/features/membership/ProfileBackgroundRepository.kt"
                ).read_text(encoding="utf-8")
                self.assertNotIn("Supabase", source)
                self.assertNotIn(".rpc(", source)
                self.assertNotIn("downloadAuthenticated", source)
                for function in ("ensureLoaded", "loadSelectedAndPreload", "preloadLandscapeImages", "invalidate"):
                    self.assertIn("fun " + function, source)

    def test_legacy_membership_rpc_is_not_used_for_cloudflare_accounts(self):
        for platform in PLATFORMS:
            with self.subTest(platform=platform):
                source = (
                    ROOT / f"apps/{platform}/composeApp/src/commonMain/kotlin/"
                    "com/nuvio/app/features/membership/MemberAccessRepository.kt"
                ).read_text(encoding="utf-8")
                self.assertIn("import com.nuvio.app.core.cloud.CloudflareAccountClient", source)
                for function in ("ensureStarted", "loadAccess", "hydrateCachedAccess"):
                    match = re.search(
                        rf"fun {function}\([^)]*\)\s*\{{([\s\S]{{0,320}})",
                        source,
                    )
                    self.assertIsNotNone(match, f"{platform}: {function} missing")
                    self.assertIn("if (CloudflareAccountClient.enabled)", match.group(1))

    def test_oauth_defaults_do_not_use_the_old_app_scheme(self):
        for platform in PLATFORMS:
            with self.subTest(platform=platform):
                source = (ROOT / f"apps/{platform}/composeApp/build.gradle.kts").read_text(
                    encoding="utf-8"
                )
                self.assertNotIn('"nuvio://auth/trakt"', source)
                self.assertNotIn('"nuvio://auth/simkl"', source)
                self.assertIn('"kovelyx://auth/trakt"', source)

    def test_app_identifies_itself_as_kovelyx_in_custom_server_requests(self):
        for platform in PLATFORMS:
            with self.subTest(platform=platform):
                source = (
                    ROOT / f"apps/{platform}/composeApp/src/commonMain/kotlin/"
                    "com/nuvio/app/core/network/ServerDiscovery.kt"
                ).read_text(encoding="utf-8")
                self.assertRegex(source, r'"User-Agent"\s+to\s+"Kovelyx')


    def test_tv_private_tokens_cannot_be_sent_to_obsolete_database(self):
        for relative in (
            "core/sync/ProviderCredentialSyncService.kt",
            "core/sync/MediaServerSyncService.kt",
        ):
            with self.subTest(service=relative):
                source = (ROOT / "apps/tv/app/src/main/java/com/nuvio/tv" / relative).read_text(encoding="utf-8")
                self.assertNotIn("Postgrest", source)
                self.assertNotIn(".rpc(", source)
                self.assertIn("Result.success(false)", source)

    def test_tv_profile_pin_and_identity_use_cloudflare_only(self):
        profile = (
            ROOT / "apps/tv/app/src/main/java/com/nuvio/tv/core/sync/ProfileSyncService.kt"
        ).read_text(encoding="utf-8")
        self.assertNotIn("Postgrest", profile)
        self.assertNotIn(".rpc(", profile)
        for feature in ("cloudProfilePinStates", "setCloudProfilePin", "clearCloudProfilePin", "verifyCloudProfilePin"):
            self.assertIn("CloudflareTvAccountClient." + feature, profile)
        settings = (
            ROOT / "apps/tv/app/src/main/java/com/nuvio/tv/core/sync/ProfileSettingsSyncService.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("if (CloudflareTvAccountClient.enabled) return@collect", settings)

    def test_tv_cloud_profile_deletion_requires_remote_ack_before_local_erase(self):
        client = (
            ROOT / "apps/tv/app/src/main/java/com/nuvio/tv/core/auth/CloudflareTvAccountClient.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("suspend fun deleteCloudProfile(profileId: String)", client)
        vm = (
            ROOT / "apps/tv/app/src/main/java/com/nuvio/tv/ui/screens/profile/ProfileSelectionViewModel.kt"
        ).read_text(encoding="utf-8")
        remote = vm.index("CloudflareTvAccountClient.deleteCloudProfile(uuid)")
        local = vm.index("profileManager.deleteProfile(id)", remote)
        self.assertLess(remote, local)
        self.assertIn("catch (error: Exception)", vm)


if __name__ == "__main__":
    unittest.main()
