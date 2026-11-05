#!/usr/bin/env python3
"""Inventory retired account-provider dependencies on all Kovelyx clients.

This is a whole-tree source inventory, not a certificate of runtime behavior.
The default Cloudflare path is separately validated by the upstream boundary tests.
Run with --strict only when the source/dependency migration is complete.
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path
import argparse
import re

ROOT = Path(__file__).resolve().parents[2]
PATTERN = re.compile(
    r"(?:supabase|io\.github\.jan\.supabase|libs\.supabase\.|SupabaseProvider|"
    r"SupabaseClient|\bPostgrest\b|\bSupabaseConfig\b|"
    r"\bSUPABASE_URL\b|\bSUPABASE_KEY\b|\bsupabase\.co\b|"
    r"\.rpc\(\s*[\"'](?:sync_|generate_sync_code|claim_sync_code|"
    r"get_sync_code|set_profile_pin|verify_profile_pin|clear_profile_pin))",
    re.IGNORECASE,
)

SOURCES = {
    "android": ROOT / "apps/mobile/composeApp/src",
    "windows": ROOT / "apps/desktop/composeApp/src",
    "android-tv": ROOT / "apps/tv/app/src",
}
BUILD_FILES = {
    "android": ROOT / "apps/mobile/composeApp/build.gradle.kts",
    "windows": ROOT / "apps/desktop/composeApp/build.gradle.kts",
    "android-tv": ROOT / "apps/tv/app/build.gradle.kts",
}
SKIP_COMPONENTS = {"test", "commonTest", "androidTest", "desktopTest", "iosTest", "build", ".gradle"}


def scan(platform: str) -> tuple[int, list[tuple[str, int]]]:
    found: list[tuple[str, int]] = []
    source = SOURCES[platform]
    for file in source.rglob("*"):
        if not file.is_file() or file.suffix not in (".kt", ".java"):
            continue
        if any(part in SKIP_COMPONENTS for part in file.relative_to(source).parts):
            continue
        count = len(PATTERN.findall(file.read_text(encoding="utf-8", errors="replace")))
        if count:
            found.append((file.relative_to(ROOT).as_posix(), count))
    build = BUILD_FILES[platform]
    count = len(PATTERN.findall(build.read_text(encoding="utf-8", errors="replace")))
    if count:
        found.append((build.relative_to(ROOT).as_posix(), count))
    return sum(n for _, n in found), sorted(found, key=lambda x: (-x[1], x[0]))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true",
                        help="Fail when a retired account provider is reintroduced")
    args = parser.parse_args()
    remaining = 0
    print("Audit des fournisseurs de compte retirés (production + Gradle) :")
    for platform in SOURCES:
        occurrences, files = scan(platform)
        remaining += occurrences
        print(f"\n{platform}: {len(files)} fichier(s), {occurrences} occurrence(s)")
        for path, count in files[:45]:
            print(f"  {count:4}  {path}")
        if len(files) > 45:
            print(f"  ... et {len(files)-45} autre(s) fichier(s)")
    print(f"\nTotal: {remaining} référence(s). La présence de code hérité ne signifie pas")
    print("que le mode Cloudflare l'exécute. Test sur appareils et trafic requis.")
    if args.strict and remaining:
        raise SystemExit("Un ancien fournisseur de compte reste référencé")


if __name__ == "__main__":
    main()
