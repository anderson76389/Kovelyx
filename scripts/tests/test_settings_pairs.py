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

    # All platforms group library-related layout options together.
    layout_start = appearance.index("if (layoutOnly)")
    layout = appearance[layout_start:]
    layout_actions = ("onHomescreenClick", "onCollectionsClick",
                      "onContinueWatchingClick", "onPosterCustomizationClick")
    layout_positions = [layout.find("onClick = " + action) for action in layout_actions]
    ensure(all(p >= 0 for p in layout_positions)
           and layout_positions == sorted(layout_positions),
           f"{platform}: home, collections, progress and posters are out of order")
    if platform == "desktop":
        ensure("onClick = onHoverPreviewClick" in layout,
               "desktop: hover preview must be in the Layout page")
        ensure("onClick = onHoverPreviewClick" not in appearance[:layout_start],
               "desktop: hover preview still exposed in the appearance group")
        ensure("    HoverPreview(" in model and "parentPage = Layout" in model,
               "desktop: hover preview back navigation lost")

    for lang in ("values", "values-fr"):
        xml = (resources / lang / "settings_organization.xml").read_text(encoding="utf-8")
        for _, title, _ in GROUPS:
            ensure(f'name="{title}"' in xml, f"{platform}/{lang}: missing {title}")
        for key in ("settings_nav_layout_title", "settings_nav_layout_description"):
            ensure(f'name="{key}"' in xml, f"{platform}/{lang}: missing {key}")

    discovery = (settings / "ContentDiscoverySettingsPage.kt").read_text(encoding="utf-8")
    ensure(discovery.count("SettingsSection(") == 1 and "kovelyx_media_group_catalogue" in discovery
           and "onPlaybackTopicClick" not in discovery,
           f"{platform}: Discovery must contain only catalogue and search settings")
    for callback in ("onAddonsClick", "onCollectionsClick", "onMediaServersClick", "onPluginsClick"):
        ensure(f"onClick = {callback}," in discovery,
               f"{platform}: missing a real Content Discovery navigation callback: {callback}")
    ensure("if (showPluginsEntry)" in discovery,
           f"{platform}: Content Discovery plugins must respect the platform policy")
    ensure("SearchHistoryRepository::setEnabled" in discovery,
           f"{platform}: discovery search-history switch is no longer connected")
    ensure(screen.count("onCollectionsClick = onCollectionsClick,") >= 2,
           f"{platform}: collections must work in phone/window and tablet layouts")
    media_routes = (
        "MediaPlayer", "MediaStreamSelection", "MediaUpNext", "MediaAudio", "MediaSubtitles",
    )
    for route in media_routes:
        ensure(f"SettingsPage.{route}" in screen,
               f"{platform}: Media destination not handled in SettingsScreen: {route}")
        ensure(f"    {route}(" in model and "parentPage = Playback" in model.split(f"    {route}(", 1)[1].split("),", 1)[0],
               f"{platform}: Media page {route} absent or not nested under Playback")
    ensure("topic = page," in screen,
           f"{platform}: Media destination does not filter the actual existing playback controls")
    ensure("SettingsPage.ContentDiscovery -> contentDiscoveryContent(" in screen,
           f"{platform}: Media/Content Discovery route is disconnected")
    playback = (settings / "PlaybackSettingsPage.kt").read_text(encoding="utf-8")
    ensure("PlaybackSettingsSection(" in playback and
           "settings_playback_section_player" in playback,
           f"{platform}: Playback must remain a separate usable Media route")
    for old, group in (("MediaSkipSegments", "MediaUpNext"), ("MediaVideo", "MediaAudio"),
                       ("MediaP2P", "MediaStreamSelection")):
        ensure(f"topic == SettingsPage.{old} || topic == SettingsPage.{group}" in playback,
               f"{platform}: {old} controls must be reachable through {group}")
        ensure(f"SettingsPage.{old}" in screen,
               f"{platform}: previous {old} route must still be accepted")
    for action in ("SubtitleAppearancePreview(subtitleStyle", "SubtitleFontPickerDialog(",
                   "compose_player_text_opacity", "kovelyx_subtitle_reset_appearance"):
        ensure(action in playback, f"{platform}: subtitle appearance action missing: {action}")

    for lang in ("values", "values-fr"):
        xml = (resources / lang / "settings_organization.xml").read_text(encoding="utf-8")
        # The visible Media row and page heading must match the supplied Nuvio screenshot.
        expected_sources_title = "Contenu et découverte" if lang == "values-fr" else "Content and discovery"
        ensure(f'<string name="settings_org_sources_title">{expected_sources_title}</string>' in xml,
               f"{platform}/{lang}: Media > Content Discovery title does not match screenshot")
        for key in (
            "kovelyx_media_group_catalogue",
            "kovelyx_subtitle_preview_sample",
        ):
            ensure(f'name="{key}"' in xml,
                   f"{platform}/{lang}: missing localized Content Discovery label: {key}")

    print(f"PASS {platform}: five Media destinations, catalogue and search controls, subtitle customization")

    print(f"PASS {platform}: 5 groups, 11 destinations, preserved settings routes and layout")


def check_tv_consistency() -> None:
    path = ROOT / "apps/tv/app/src/main/java/com/kovelyx/tv/ui/screens/settings/SettingsCatalog.kt"
    tv = path.read_text(encoding="utf-8")
    for category in ("YOU", "LOOK", "WATCH", "SERVICES", "SYSTEM"):
        ensure(f"    {category}" in tv, f"Android TV: missing {category}")
    ensure("PLAYBACK(SettingsRailGroup.WATCH)" in tv, "Android TV: player misplaced")
    ensure("LAYOUT(SettingsRailGroup.LOOK)" in tv, "Android TV: layout misplaced")
    ensure("TRACKING(SettingsRailGroup.SERVICES)" in tv, "Android TV: tracking misplaced")
    subtitles = path.with_name("PlaybackSubtitleSettings.kt").read_text(encoding="utf-8")
    ensure("SubtitleAppearancePreview(style)" in subtitles and "SubtitleFontStore.resolve" in subtitles,
           "Android TV: live preview must use the player subtitle style and installed fonts")
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
