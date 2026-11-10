#!/usr/bin/env python3
"""Rename owned UI symbols, leaving native ABI, storage keys and wire models intact."""
from pathlib import Path
import argparse
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
NAMES = ('LocalNuvioBottomNavigationOverlayPadding', 'LocalNuvioColors', 'LocalNuvioExtendedColors', 'LocalNuvioFocusRingStyle', 'LocalNuvioNavBarScrollState', 'LocalNuvioPlatformDensity', 'LocalNuvioTextStyles', 'LocalNuvioThemeTokens', 'LocalNuvioTypeScale', 'NuvioActionLabel', 'NuvioAddonGroupInfo', 'NuvioAnimatedWatchedBadge', 'NuvioAssMatroskaExtractor', 'NuvioAssSubtitleExtractorOutput', 'NuvioAssTrackOutput', 'NuvioAsyncImage', 'NuvioAvatarSizes', 'NuvioBackButton', 'NuvioBorderTokens', 'NuvioBottomSheetActionRow', 'NuvioBottomSheetDivider', 'NuvioBottomSheetDragHandle', 'NuvioBreakpointTokens', 'NuvioButton', 'NuvioButtonSizes', 'NuvioCardComponentTokens', 'NuvioCardDepthSurface', 'NuvioCardSizes', 'NuvioClassicNavigationBar', 'NuvioClassicNavigationBarScopeImpl', 'NuvioColorScheme', 'NuvioColorTokens', 'NuvioColors', 'NuvioComponentTokens', 'NuvioComponents', 'NuvioContinueWatchingActionSheet', 'NuvioContrastPair', 'NuvioControlsView', 'NuvioDesktopBaseHeightDp', 'NuvioDesktopBaseWidthDp', 'NuvioDesktopCatalogPosterScale', 'NuvioDesktopCatalogShelfPosterScale', 'NuvioDesktopFontScale', 'NuvioDesktopImageScaling', 'NuvioDesktopMaxUiScale', 'NuvioDesktopMinUiScale', 'NuvioDesktopVerticalScrollbar', 'NuvioDialog', 'NuvioDialogComponentTokens', 'NuvioDisabledColors', 'NuvioDropdownChip', 'NuvioDropdownOption', 'NuvioDropdownOptionsSheet', 'NuvioEffectTokens', 'NuvioEffects', 'NuvioElevationTokens', 'NuvioElevations', 'NuvioEpisodeInfo', 'NuvioEpisodesPanel', 'NuvioExoPlayerPerformanceHelper', 'NuvioExoPlayerPerformanceHelperTest', 'NuvioExtendedColors', 'NuvioFloatingPrompt', 'NuvioFocus', 'NuvioFocusColors', 'NuvioFocusRingStyle', 'NuvioFocusTokens', 'NuvioIconActionButton', 'NuvioIconSizes', 'NuvioIconTokens', 'NuvioInfoBadge', 'NuvioInlineMetadata', 'NuvioInputField', 'NuvioLayout', 'NuvioLayoutTokens', 'NuvioLoadingIndicator', 'NuvioMPVView', 'NuvioModalBottomSheet', 'NuvioMotion', 'NuvioMotionDurations', 'NuvioMotionEasings', 'NuvioMotionTokens', 'NuvioMpvSurfaceView', 'NuvioNativeBottomSheetDelegate', 'NuvioNativeModalBottomSheet', 'NuvioNavBarScrollState', 'NuvioNavHost', 'NuvioNavigateOptions', 'NuvioNavigationBar', 'NuvioNavigationBarScope', 'NuvioNavigationBarScopeImpl', 'NuvioNavigator', 'NuvioNetworkOfflineCard', 'NuvioOpacityTokens', 'NuvioPanelSpacing', 'NuvioPlayerComponentTokens', 'NuvioPlayerDesignTokens', 'NuvioPlayerSizes', 'NuvioPlayerState', 'NuvioPlayerWindow', 'NuvioPopUpToOptions', 'NuvioPosterCard', 'NuvioPosterShape', 'NuvioPosterZoomActionOverlay', 'NuvioPrimaryButton', 'NuvioPrimitives', 'NuvioProgressBar', 'NuvioRadii', 'NuvioRadiusTokens', 'NuvioRailSpacing', 'NuvioRippleConfiguration', 'NuvioRowComponentTokens', 'NuvioScreen', 'NuvioScreenHeader', 'NuvioScreenSpacing', 'NuvioScrollDefaults', 'NuvioSectionLabel', 'NuvioSelectionColors', 'NuvioSettingsComponentTokens', 'NuvioSettingsSizes', 'NuvioShapeTokens', 'NuvioShapes', 'NuvioShelfSectionHeader', 'NuvioSidebarComponentTokens', 'NuvioSidebarSizes', 'NuvioSizeTokens', 'NuvioSizes', 'NuvioSourceColors', 'NuvioSourcesPanel', 'NuvioSpacing', 'NuvioSpacingTokens', 'NuvioStatusColors', 'NuvioStatusModal', 'NuvioStreamInfo', 'NuvioStrokeTokens', 'NuvioStrokes', 'NuvioSurfaceCard', 'NuvioSurfaceColors', 'NuvioTabHome', 'NuvioTabLibrary', 'NuvioTabProfile', 'NuvioTabSearch', 'NuvioTextColors', 'NuvioTextField', 'NuvioTextStyleTokens', 'NuvioTextStyles', 'NuvioTheme', 'NuvioThemeTokens', 'NuvioToastController', 'NuvioToastHost', 'NuvioToastMessage', 'NuvioTokenPreviewSurface', 'NuvioTokens', 'NuvioTopBar', 'NuvioTrackInfo', 'NuvioTypeScale', 'NuvioTypeTokens', 'NuvioTypography', 'NuvioViewAllPill', 'NuvioViewAllPillSize', 'NuvioWatchedBadge', 'buildNuvioTextStyles', 'buildNuvioTypography', 'defaultNuvioThemeTokens', 'dismissNativeNuvioBottomSheet', 'dismissNuvioBottomSheet', 'isNuvioAccount', 'rememberNuvioNavBarScrollState', 'setNuvioPerformanceModeEnabled', 'usesNativeNuvioBottomSheet')
NAMES += ("NuvioSharedComponents", "NuvioLibmpvView")
MAP = {old: old.replace("Nuvio", "Kovelyx") for old in NAMES}
MAP.update({"nuvio-tab-" + item: "kovelyx-tab-" + item for item in ("home", "search", "library", "profile")})
PATTERN = re.compile(r"\b(?:" + "|".join(map(re.escape, sorted(MAP, key=len, reverse=True))) + r")\b")
SUFFIXES = (".kt", ".kts", ".java", ".swift", ".pbxproj", ".json", ".xml", ".plist", ".xcconfig", ".svg", ".properties", ".pro", ".def", ".sh", ".yml", ".yaml", ".py", ".txt")


def migrate(apply=False):
    paths = [p.decode() for p in subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).split(b"\0") if p]
    changes = []
    for name in paths:
        if not name.startswith(("apps/", "scripts/tests/", ".github/workflows/")) or not name.endswith(SUFFIXES):
            continue
        path = ROOT / name
        raw = path.read_bytes()
        if b"\0" in raw:
            continue
        try:
            old = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        new = PATTERN.sub(lambda match: MAP[match.group()], old)
        target = name
        for before, after in MAP.items():
            target = target.replace(before, after)
        if target != name and (ROOT / target).exists():
            raise RuntimeError("Rename collision: " + target)
        if new != old or target != name:
            changes.append((name, target, new))
    if apply:
        for before, after, content in changes:
            dest = ROOT / after
            dest.parent.mkdir(parents=True, exist_ok=True)
            (ROOT / before).rename(dest) if before != after else None
            dest.write_text(content)
    return {"changed_files": len(changes), "renamed_files": sum(a != b for a,b,_ in changes)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    print(json.dumps(migrate(parser.parse_args().apply)))
