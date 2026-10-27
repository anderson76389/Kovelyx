#!/usr/bin/env python3
"""Migration du code Android mobile de com.nuvio vers com.kovelyx.

Garde expressément les identifiants de stockage, les bibliothèques binaires
et les classes com.nuvio.engine (JNI du moteur précompilé) inchangés.
Idempotent: peut être relancé sans renommer une seconde fois.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MOBILE = ROOT / "apps/mobile"
SOURCE_EXTENSIONS = {".kt", ".java", ".xml", ".kts", ".pro", ".properties"}
LEGACY_PACKAGE = re.compile(r"(?<![\w.])com\.nuvio\.(app|android)\b")
LEGACY_PATH = re.compile(r"(?<![\w/])com/nuvio/(app|android)(?=/|$)")
PATH_IN_PROJECT = re.compile(
    r"(apps/mobile/[^\s\"']*?/(?:kotlin|java)/)com/nuvio/(app|android)(?=/|$)"
)


def rewrite(value: str) -> str:
    value = LEGACY_PACKAGE.sub(lambda m: "com.kovelyx." + m.group(1), value)
    value = LEGACY_PATH.sub(lambda m: "com/kovelyx/" + m.group(1), value)
    value = value.replace("Theme.Nuvio", "Theme.Kovelyx")
    value = value.replace("Theme_Nuvio", "Theme_Kovelyx")
    return value


def migrate_sources() -> tuple[int, int]:
    updated = 0
    moved = 0
    files = sorted(
        p for p in MOBILE.rglob("*")
        if p.is_file() and p.suffix in SOURCE_EXTENSIONS
        # Ne pas ignorer core/build : c'est un paquet Kotlin actif,
        # distinct des répertoires Gradle générés.
        and not p.relative_to(MOBILE).parts[0] in {".gradle", "build"}
        and not (len(p.relative_to(MOBILE).parts) > 1 and p.relative_to(MOBILE).parts[1] == "build")
    )
    for old in files:
        old_data = old.read_text(encoding="utf-8")
        new_data = rewrite(old_data)
        target = old
        parts = list(old.relative_to(MOBILE).parts)
        for marker in ("kotlin", "java"):
            if marker not in parts:
                continue
            index = parts.index(marker)
            if len(parts) > index + 3 and parts[index + 1:index + 3] == ["com", "nuvio"] and parts[index + 3] in ("app", "android"):
                parts[index + 2] = "kovelyx"
                target = MOBILE.joinpath(*parts)
                break
        if target != old:
            if target.exists():
                raise RuntimeError(f"Migration refusée, destination préexistante : {target}")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(new_data, encoding="utf-8")
            old.unlink()
            moved += 1
        elif new_data != old_data:
            old.write_text(new_data, encoding="utf-8")
            updated += 1
    return moved, updated


def migrate_audit_references() -> int:
    updated = 0
    for directory in (ROOT / "scripts", ROOT / ".github/workflows"):
        for file in sorted(directory.rglob("*")):
            if not file.is_file() or file.suffix not in {".py", ".yml", ".yaml", ".sh", ".ps1"}:
                continue
            data = file.read_text(encoding="utf-8")
            new = PATH_IN_PROJECT.sub(lambda m: m.group(1) + "com/kovelyx/" + m.group(2), data)
            if file.name == "audit-mobile-independence.py":
                new = new.replace("commonMain/kotlin/com/nuvio/app/", "commonMain/kotlin/com/kovelyx/app/")
            if file.name == "audit-kovelyx-android.py":
                new = new.replace("androidMain/kotlin/com/nuvio/app/", "androidMain/kotlin/com/kovelyx/app/")
                new = new.replace("com.nuvio.app.KovelyxApplication", "com.kovelyx.app.KovelyxApplication")
                new = new.replace("com.nuvio.app.NuvioApplication", "com.kovelyx.app.NuvioApplication")
            if new != data:
                file.write_text(new, encoding="utf-8")
                updated += 1
    return updated


def verify() -> None:
    manifest = (MOBILE / "androidApp/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    app_gradle = (MOBILE / "androidApp/build.gradle.kts").read_text(encoding="utf-8")
    compose_gradle = (MOBILE / "composeApp/build.gradle.kts").read_text(encoding="utf-8")
    assert 'applicationId = "com.kovelyx.mobile"' in app_gradle
    assert 'namespace = "com.kovelyx.android"' in app_gradle
    assert 'namespace = "com.kovelyx.app"' in compose_gradle
    assert "com.kovelyx.app.KovelyxApplication" in manifest
    assert "com.nuvio.app." not in manifest
    assert (MOBILE / "composeApp/src/androidMain/kotlin/com/kovelyx/app/MainActivity.kt").exists()
    assert (MOBILE / "composeApp/src/androidMain/kotlin/com/kovelyx/app/KovelyxApplication.kt").exists()
    assert not list(MOBILE.glob("**/kotlin/com/nuvio/app/*.kt"))
    assert not list(MOBILE.glob("**/kotlin/com/nuvio/android/*.kt"))
    stale = []
    for root in (MOBILE / "androidApp/src", MOBILE / "composeApp/src"):
        for file in root.rglob("*.kt"):
            if LEGACY_PACKAGE.search(file.read_text(encoding="utf-8")):
                stale.append(str(file.relative_to(ROOT)))
    if stale:
        raise RuntimeError("Anciens packages Android dans les sources : " + ", ".join(stale[:12]))
    assert (MOBILE / "composeApp/libs/lib-nuvio-engine-android-0.1.4.aar").exists()
    assert (MOBILE / "composeApp/libs/quickjs-kt-android-1.0.5-nuvio.aar").exists()
    print("OK : com.kovelyx dans les sources Android ; identité installable et moteur JNI conservés.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Contrôler seulement, sans modifier")
    args = parser.parse_args()
    if not args.check:
        moved, changed = migrate_sources()
        adjusted = migrate_audit_references()
        print(f"Migration : {moved} fichiers déplacés, {changed} réécrits, {adjusted} contrôles adaptés")
    verify()
