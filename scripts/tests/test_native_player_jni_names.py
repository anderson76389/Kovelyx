#!/usr/bin/env python3
"""Assert that the desktop JNI exports and Kotlin class name stay synchronized.

The package rename must NOT break Windows, Linux, or macOS video playback.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
kotlin = root / "apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/features/player/desktop/NativePlayerBridge.kt"
source = kotlin.read_text(encoding="utf-8")
assert "package com.kovelyx.app.features.player.desktop" in source
assert "internal object NativePlayerBridge" in source
symbol = "Java_com_kovelyx_app_features_player_desktop_NativePlayerBridge"
old = "Java_com_nuvio_app_features_player_desktop_NativePlayerBridge"
for os, source_path in (
    ("Windows", "apps/desktop/composeApp/src/desktopMain/native/windows/player_bridge.cpp"),
    ("Linux", "apps/desktop/composeApp/src/desktopMain/native/linux/player_bridge.cpp"),
    ("macOS", "apps/desktop/composeApp/src/desktopMain/native/macos/player_bridge.mm"),
):
    code = (root / source_path).read_text(encoding="utf-8")
    assert symbol in code, f"{os} bridge is missing the active JNI entry points"
    assert old not in code, f"{os} bridge still exports the obsolete JNI class"
print("OK: Windows, Linux and macOS native player JNI packages match the Kotlin class")
