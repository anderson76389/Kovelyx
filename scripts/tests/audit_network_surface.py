#!/usr/bin/env python3
"""Inventory compile-time URL host categories and Android permissions.

This deliberately NEVER emits URL/path/query values, tokens or private domains.
It is a static source inventory: configurable addons, native binaries,
generated URLs and device runtime traffic require separate device captures.
"""
from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PLATFORMS = {
    "tv": [ROOT / "apps/tv/app/src/main"],
    "mobile": [ROOT / "apps/mobile/composeApp/src", ROOT / "apps/mobile/androidApp/src/main"],
    "windows": [ROOT / "apps/desktop/composeApp/src", ROOT / "apps/desktop/androidApp/src/main"],
    "worker": [ROOT / "services/kovelyx-cloudflare/src"],
}
# Match hostnames only; never capture query strings or path segments.
HOST = re.compile(r"https?://([A-Za-z0-9._-]+)", re.I)
SOURCE_EXTENSIONS = {".kt", ".java", ".js", ".mjs", ".xml", ".kts"}
ANDROID = "{http://schemas.android.com/apk/res/android}"


def category(host: str) -> str:
    host = host.lower()
    mapping = {
        "cloudflare": ("cloudflare.com", "cloudflare.net", "workers.dev"),
        "github": ("github.com", "githubusercontent.com"),
        "metadata": ("themoviedb.org", "tmdb.org", "imdb.com", "thetvdb.com"),
        "tracking": ("trakt.tv", "mdblist.com", "simkl.com"),
        "telemetry": ("sentry.io", "ingest.sentry.io"),
        "google": ("google.com", "googleapis.com", "gstatic.com", "youtube.com"),
        "subtitle": ("opensubtitles.com", "opensubtitles.org"),
        "debrid": ("real-debrid.com", "alldebrid.com", "premiumize.me"),
        "example_or_local": (".invalid", ".example", "localhost", "127.0.0.1"),
    }
    for group, endings in mapping.items():
        if any(host == suffix or host.endswith("." + suffix) or host.endswith(suffix)
               for suffix in endings):
            return group
    return "other_or_custom"


def inventory() -> dict:
    counts: dict[str, dict[str, int]] = {}
    files: dict[str, int] = {}
    for platform, roots in PLATFORMS.items():
        counter = Counter()
        scanned = 0
        for root in roots:
            for path in root.rglob("*"):
                if not path.is_file() or path.suffix.lower() not in SOURCE_EXTENSIONS:
                    continue
                if any(p in ("build", ".gradle", "generated", "test", "androidTest")
                       for p in path.parts):
                    continue
                if path.stat().st_size > 1_000_000:
                    continue
                scanned += 1
                for host in HOST.findall(path.read_text(encoding="utf-8", errors="replace")):
                    counter[category(host)] += 1
        counts[platform] = dict(sorted(counter.items()))
        files[platform] = scanned
    manifests = {
        "tv": ROOT / "apps/tv/app/src/main/AndroidManifest.xml",
        "mobile": ROOT / "apps/mobile/androidApp/src/main/AndroidManifest.xml",
        "windows_android_target": ROOT / "apps/desktop/androidApp/src/main/AndroidManifest.xml",
    }
    perms = {}
    for name, path in manifests.items():
        doc = ET.parse(path).getroot()
        app = doc.find("application")
        perms[name] = {
            "permissions": sorted({
                el.attrib.get(ANDROID + "name", "")
                for el in doc.findall("uses-permission")
            }),
            "allow_backup": app.attrib.get(ANDROID + "allowBackup"),
            "cleartext_traffic_allowed": app.attrib.get(ANDROID + "usesCleartextTraffic"),
        }
    return {
        "scan": "first-party-source-literals-only",
        "source_files": files,
        "url_host_categories": counts,
        "android_manifest": perms,
        "limitations": [
            "no DNS, packet capture or real device traffic inspected",
            "no configured user addon URLs, magnet links or runtime endpoints inspected",
            "hardcoded source categories are counts, not an egress allowlist",
            "Android cleartext permission preserves optional HTTP/local addons",
        ],
    }


if __name__ == "__main__":
    print(json.dumps(inventory(), indent=2, ensure_ascii=False, sort_keys=True))
