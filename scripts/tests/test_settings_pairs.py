#!/usr/bin/env python3
"""Guard the paired settings navigation shared by Windows/Linux and Android mobile.

The script checks user-visible structure only. Existing SettingsPage routes,
stored preferences, update behaviour and platform implementations are unchanged.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
PAIRS = [
    ("settings_org_root_experience", ("onAppearanceClick", "onPlaybackClick")),
    ("settings_org_root_content", ("onContentDiscoveryClick", "onIntegrationsClick")),
    ("settings_org_root_activity", ("onTrackingClick", "onNotificationsClick")),
    ("settings_org_root_system", ("onAdvancedClick", "onCheckForUpdatesClick")),
]


def ensure(condition: bool, explanation: str) -> None:
    if not condition:
        raise AssertionError(explanation)


def check_platform(platform: str) -> None:
    prefix = ROOT / "apps" / platform / "composeApp"
    root = (prefix / "src/commonMain/kotlin/com/kovelyx/app/features/settings/SettingsRootPage.kt").read_text()
    appearance = (prefix / "src/commonMain/kotlin/com/kovelyx/app/features/settings/AppearanceSettingsPage.kt").read_text()
    headings = [root.index("title = stringResource(Res.string." + key + ")") for key, _ in PAIRS]
    ensure(headings == sorted(headings), f"{platform}: sections are out of order")

    for idx, (key, actions) in enumerate(PAIRS):
        end = headings[idx + 1] if idx + 1 < len(headings) else root.index("    if (showAboutSection) {")
        section = root[headings[idx]:end]
        actual = re.findall(r"onClick\s*=\s*(on[A-Za-z]+Click)", section)
        ensure(tuple(actual[:2]) == actions, f"{platform}: incoherent pair {key}: {actual}")
        if key == "settings_org_root_system":
            ensure("if (onCheckForUpdatesClick != null)" in section,
                   f"{platform}: missing update availability guard")
            ensure("if (onTestUpdateBannerClick != null)" in section,
                   f"{platform}: debug update action not preserved")
        if key == "settings_org_root_activity":
            ensure("if (showNotificationsEntry)" in section,
                   f"{platform}: missing notifications feature guard")

    expected = [action for _, pair in PAIRS for action in pair]
    for action in expected:
        ensure(root.count("onClick = " + action) == 1,
               f"{platform}: callback lost or duplicated: {action}")
    about = root[root.index("    if (showAboutSection) {"):]
    ensure("onClick = onLicensesAttributionsClick" in about, f"{platform}: missing licenses")
    ensure("onClick = onSupportersContributorsClick" in about, f"{platform}: missing credits")
    ensure("onClick = onTestUpdateBannerClick" not in about,
           f"{platform}: debug action accidentally placed under licenses")

    # Within Appearance, keep home/reprise together and collections/posters together.
    home_actions = ["onHomescreenClick", "onContinueWatchingClick",
                    "onCollectionsClick", "onPosterCustomizationClick"]
    home_positions = [appearance.index("onClick = " + action) for action in home_actions]
    ensure(home_positions == sorted(home_positions),
           f"{platform}: incoherent home and collections order")

    for lang in ["values", "values-fr"]:
        xml = (prefix / f"src/commonMain/composeResources/{lang}/settings_organization.xml").read_text()
        for key, _ in PAIRS:
            ensure(f'name="{key}"' in xml, f"{platform}/{lang}: missing {key}")
    print(f"PASS {platform}: navigation paired, callbacks preserved, nested options coherent")


def main() -> None:
    for platform in ("desktop", "mobile"):
        check_platform(platform)
    mobile = (ROOT / "apps/mobile/composeApp/src/commonMain/kotlin/com/kovelyx/app/features/settings/SettingsScreen.kt").read_text()
    ensure(mobile.count("showNotificationsEntry = SettingsPage.Notifications.isEnabledByPolicy()") == 2,
           "mobile: phone and tablet must respect notifications availability")
    print("PASS Windows + Linux + Android mobile: same logical settings pairs")


if __name__ == "__main__":
    main()
