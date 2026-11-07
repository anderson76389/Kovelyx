#!/usr/bin/env python3
"""Idempotent Kovelyx source-namespace migration.

Rewrites first-party Kotlin/Java package declarations, imports, references,
generated-code paths, and qualified test names consistently. It never renames
an installed Android applicationId, a user data key, a deeplink scheme, or a
native engine ABI. Keep the result as a separate PR until all builds pass.
"""
from __future__ import annotations

import argparse
import json
import subprocess
from collections import Counter
from pathlib import Path

REPLACEMENTS = (
    ("com.nuvio.android", "com.kovelyx.android"),
    ("com.nuvio.app", "com.kovelyx.app"),
    ("com.nuvio.tv", "com.kovelyx.tv"),
    ("com/nuvio/android", "com/kovelyx/android"),
    ("com/nuvio/app", "com/kovelyx/app"),
    ("com/nuvio/tv", "com/kovelyx/tv"),
)
TEXT_EXTENSIONS = frozenset({
    ".kt", ".kts", ".java", ".xml", ".pro", ".properties", ".gradle",
    ".json", ".toml", ".swift", ".m", ".mm", ".h", ".hpp", ".c", ".cc",
    ".cpp", ".cfg", ".txt", ".sh", ".py", ".yml", ".yaml", ".plist",
    ".def", ".map", ".md", ".html", ".xcconfig", ".js",
})
TARGETS = ("apps/mobile/", "apps/desktop/", "apps/tv/", "scripts/")
SKIP_PREFIX = ("apps/mobile/.gradle/", "apps/desktop/.gradle/", "apps/tv/.gradle/", "scripts/migrations/")


def tracked_files(root: Path) -> list[str]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=root)
    return [item.decode("utf-8") for item in raw.split(b"\0") if item]


def target(path: str) -> bool:
    return (path.startswith(TARGETS) and not path.startswith(SKIP_PREFIX)
            and Path(path).suffix.lower() in TEXT_EXTENSIONS)


def transform(content: str) -> tuple[str, int]:
    total = 0
    for old, new in REPLACEMENTS:
        total += content.count(old)
        content = content.replace(old, new)
    return content, total


def rename_path(path: str) -> str:
    for old, new in REPLACEMENTS[3:]:
        path = path.replace(old, new)
    return path


def migrate(root: Path, apply: bool) -> dict:
    tracked = tracked_files(root)
    candidates = [path for path in tracked if target(path)]
    renames = {path: rename_path(path) for path in candidates if rename_path(path) != path}
    originals = set(tracked)
    destinations = set()
    for source, destination in renames.items():
        if destination in destinations:
            raise RuntimeError(f"Duplicate target path: {destination}")
        destinations.add(destination)
        if destination in originals and destination not in renames:
            raise RuntimeError(f"Refusing to overwrite existing file: {destination}")

    changes = []
    by_area: Counter[str] = Counter()
    for path in candidates:
        data = (root / path).read_bytes()
        if b"\0" in data:
            continue
        try:
            original = data.decode("utf-8")
        except UnicodeDecodeError:
            continue
        updated, replacements = transform(original)
        destination = renames.get(path, path)
        if updated != original or destination != path:
            changes.append((path, destination, updated if replacements else None))
            area = path.split("/")[1] if path.startswith("apps/") else "shared-build-tools"
            by_area[area] += 1

    if apply:
        for source, destination, content in changes:
            src, dst = root / source, root / destination
            if source != destination:
                dst.parent.mkdir(parents=True, exist_ok=True)
                src.rename(dst)
            if content is not None:
                dst.write_text(content, encoding="utf-8", newline="")

    return {
        "mode": "applied" if apply else "preview",
        "changed_files": len(changes),
        "renamed_files": len(renames),
        "per_area": dict(sorted(by_area.items())),
        "preserved_contracts": [
            "existing Android applicationId and APK update identity",
            "Cloudflare and profile data",
            "nuvio:// compatibility aliases and OAuth callbacks",
            "native engine ABI/binary names and stored cache keys",
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    print(json.dumps(migrate(args.root.resolve(), args.apply), indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
