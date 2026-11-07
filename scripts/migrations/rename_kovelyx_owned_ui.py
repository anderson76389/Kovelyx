#!/usr/bin/env python3
"""Rename app-owned UI/Swift symbols without changing native ABI or persistent IDs.

Only tracked app source/resource references are updated. No native engine names,
Android applicationId, preferences, deeplinks, remote service URLs, or licenses.
"""
from __future__ import annotations
import argparse
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STEMS = (
    "NuvioDialog",
    "NuvioScrollDefaults",
    "NuvioTopBar",
    "NuvioNavHost",
    "NuvioAssMatroskaExtractor",
    "NuvioExoPlayerPerformanceHelper",
    "NuvioMpvSurfaceView",
    "NuvioNavigator",
    "NuvioControlsView",
    "NuvioEpisodesPanel",
    "NuvioMPVView",
    "NuvioPlayerDesignTokens",
    "NuvioPlayerState",
    "NuvioPlayerWindow",
    "NuvioSharedComponents",
    "NuvioSourcesPanel",
    "NuvioTabHome",
    "NuvioTabSearch",
    "NuvioTabLibrary",
    "NuvioTabProfile",
)
SUFFIXES = (
    ".kt", ".kts", ".java", ".swift", ".pbxproj", ".json", ".xml",
    ".plist", ".xcconfig", ".svg", ".properties", ".pro", ".def",
    ".sh", ".yml", ".yaml", ".md",
)
NAMES = tuple((old, old.replace("Nuvio", "Kovelyx", 1)) for old in STEMS)

def migrate(apply: bool) -> dict:
    files = [x.decode() for x in subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).split(b"\0") if x]
    selected = [p for p in files if p.startswith(("apps/mobile/", "apps/desktop/", "apps/tv/"))
                and p.endswith(SUFFIXES)]
    changes = []
    paths = set(files)
    for path in selected:
        raw = (ROOT / path).read_bytes()
        if b"\0" in raw:
            continue
        try:
            original = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        current, target = original, path
        for a, b in NAMES:
            current = current.replace(a, b)
            target = target.replace(a, b)
        if target in paths and target != path:
            raise RuntimeError(f"Destination already exists: {target}")
        if target != path or current != original:
            changes.append((path, target, current))
    if apply:
        for old, new, content in changes:
            source = ROOT / old
            dest = ROOT / new
            if old != new:
                dest.parent.mkdir(parents=True, exist_ok=True)
                source.rename(dest)
            dest.write_text(content, encoding="utf-8")
    return {"changed_files": len(changes),
            "renamed_files": sum(a != b for a,b,_ in changes),
            "mode": "applied" if apply else "preview",
            "preserved": ["Android package IDs", "storage keys", "native engine ABI",
                          "authorization and deeplink compatibility"]}

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    print(json.dumps(migrate(args.apply), indent=2))
