#!/usr/bin/env python3
"""Check five settings navigation groups on Windows/Linux and Android mobile.

The organization is inspired by a user-supplied reference APK. This guard
preserves every local callback, feature flag and saved route, without importing
upstream code or making a runtime request to an upstream service.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

# Category order follows the user-facing sidebar, not alphabetical sorting.
GROUPS = (
    ("You", "settings_nav_group_you", ("onSwitchProfileClick", "onAccountClick")),
    ("Look", "settings_nav_group_look", ("onAppearanceClick", "onLayoutClick")),
    ("Watch", "settings_nav_group_watch", ("onContentDiscoveryClick", "onPlaybackClick")),
    ("Services", "settings_nav_group_services", ("onIntegrationsClick", "onTrackingClick")),
    ("System", "settings_nav_group_system", ("onNotificationsClick", "onAdvancedClick", "onCheckForUpdatesClick")),
)


def ensure(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def check_platform(platform: str) -> None:
    prefix = ROOT / "apps" / platform / "composeApp"
    settings = prefix / "src/commonMain/kotlin/com/kovelyx/app/features/settings"
    resources = prefix / "src/commonMain/composeResources"
    root = (settings / "SettingsRootPage.kt").read_text(encoding="utf-8")
    appearance = (settings / "AppearanceSettingsPage.kt").read_text(encoding="utf-8")
    model = (settings / "SettingsModels.kt").read_text(encoding="utf-8")
    screen = (settings / "SettingsScreen.kt").read_text(encoding="utf-8")
    search = (settings / "SettingsSearch.kt").read_text(encoding="utf-8")

    markers = [f"if (show{group}Section) {{" for group, _, _ in GROUPS]
    positions = [root.find(marker) for marker in markers]
    ensure(all(pos >= 0 for pos in positions) and positions == sorted(positions),
           f"{platform}: grouped settings are missing or out of order")
    ensure(root.count("if (showSystemSection)") >= 1, f"{platform}: missing System heading")

    for i, (group, title, actions) in enumerate(GROUPS):
        end = positions[i + 1] if i + 1 < len(GROUPS) else len(root)
        section = root[positions[i]:end]
        ensure(f"Res.string.{title}" in section, f"{platform}: missing heading {title}")
        found = [section.find("onClick = " + action) for action in actions]
        if group == "You":  # Switch profile is feature-gated.
            ensure("if (onSwitchProfileClick != null)" in section,
                   f"{platform}: missing profile switching guard")
        ensure(all(p >= 0 for p in found) and found == sorted(found),
               f"{platform}: incoherent {group} action order")
        if group == "System":
            for guard in ("showNotificationsEntry", "onCheckForUpdatesClick != null",
                          "onTestUpdateBannerClick != null"):
                ensure(guard in section, f"{platform}: missing system guard {guard}")
            ensure("onClick = onLicensesAttributionsClick" in section,
                   f"{platform}: licence route lost")
            ensure("onClick = onSupportersContributorsClick" in section,
                   f"{platform}: credits route lost")

    every_action = set(a for _, _, actions in GROUPS for a in actions)
    for action in every_action:
        ensure(root.count("onClick = " + action) == 1,
               f"{platform}: setting action missing/duplicated: {action}")

    ensure("Layout(" in model and "parentPage = Layout" in model,
           f"{platform}: layout page and nested routes absent")
    ensure("settingsCategoryForName(name: String)" in model,
           f"{platform}: persisted sidebar categories not migrated")
    ensure("SettingsPage.Appearance, SettingsPage.Layout -> appearanceSettingsContent(" in screen,
           f"{platform}: layout route not wired")
    ensure("layoutOnly = page == SettingsPage.Layout" in screen,
           f"{platform}: layout page not filtered")
    ensure("if (!layoutOnly)" in appearance and "if (layoutOnly)" in appearance,
           f"{platform}: layout and theme options are not separated")
    ensure("page = SettingsPage.Layout" in search,
           f"{platform}: new Layout page absent from parameter search")
    ensure("categoryLabels[page.category]" in search,
           f"{platform}: search ignores the destination group")

    for lang in ("values", "values-fr"):
        xml = (resources / lang / "settings_organization.xml").read_text(encoding="utf-8")
        for _, title, _ in GROUPS:
            ensure(f'name="{title}"' in xml, f"{platform}/{lang}: missing {title}")
        for key in ("settings_nav_layout_title", "settings_nav_layout_description"):
            ensure(f'name="{key}"' in xml, f"{platform}/{lang}: missing {key}")

    print(f"PASS {platform}: 5 groups, 11 destinations, preserved settings routes and layout")


def check_tv_consistency() -> None:
    path = ROOT / "apps/tv/app/src/main/java/com/kovelyx/tv/ui/screens/settings/SettingsCatalog.kt"
    tv = path.read_text(encoding="utf-8")
    for category in ("YOU", "LOOK", "WATCH", "SERVICES", "SYSTEM"):
        ensure(f"    {category}" in tv, f"Android TV: missing {category}")
    ensure("PLAYBACK(SettingsRailGroup.WATCH)" in tv, "Android TV: player misplaced")
    ensure("LAYOUT(SettingsRailGroup.LOOK)" in tv, "Android TV: layout misplaced")
    ensure("TRACKING(SettingsRailGroup.SERVICES)" in tv, "Android TV: tracking misplaced")
    print("PASS TV: native remote-driven rail already has the same five groups")


def main() -> None:
    for platform in ("desktop", "mobile"):
        check_platform(platform)
    mobile = (ROOT / "apps/mobile/composeApp/src/commonMain/kotlin/com/kovelyx/app/features/settings/SettingsScreen.kt").read_text(encoding="utf-8")
    ensure(mobile.count("showNotificationsEntry = SettingsPage.Notifications.isEnabledByPolicy()") == 2,
           "mobile: phone and tablet must respect notifications policy")
    check_tv_consistency()


if __name__ == "__main__":
    main()
