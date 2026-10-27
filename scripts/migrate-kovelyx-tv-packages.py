#!/usr/bin/env python3
"""Migration TV : com.nuvio.tv -> com.kovelyx.tv, sans changer applicationId.

Déplace les sources Kotlin/Java (tests compris), adapte les manifestes, ProGuard,
Gradle et JNI Dolby Vision. Préserve com.nuvio.engine et les noms des anciennes
préférences : leur changement risquerait de casser le moteur ou d'effacer
l'historique utilisateur. N'efface ni les licences ni les attributions.
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TV = ROOT / "apps/tv"
MAIN_MANIFEST = TV / "app/src/main/AndroidManifest.xml"
APP_GRADLE = TV / "app/build.gradle.kts"
JNI = TV / "app/src/main/cpp/dovi_bridge.cpp"
OLD_PKG = "com.nuvio.tv"
NEW_PKG = "com.kovelyx.tv"
OLD_JNI = "Java_com_nuvio_tv_"
NEW_JNI = "Java_com_kovelyx_tv_"
OLD_CLASS = "NuvioApplication"
NEW_CLASS = "KovelyxApplication"

# Le code (pas les médias binaires, les clés de stockage ou les licences).
TEXT_EXTENSIONS = {".kt", ".java", ".xml", ".pro", ".kts", ".gradle", ".aidl", ".cpp", ".h"}
SKIPPED_DIRECTORIES = {"build", ".gradle", ".git", ".idea"}


def production_files():
    for file in sorted(TV.rglob("*")):
        if not file.is_file() or file.suffix not in TEXT_EXTENSIONS:
            continue
        relative = file.relative_to(TV)
        # Ne pas exclure les packages Kotlin core/build : seul le dossier
        # de sorties Gradle du module est généré.
        if relative.parts[0] in SKIPPED_DIRECTORIES:
            continue
        if len(relative.parts) > 1 and relative.parts[0] in {"app", "baselineprofile", "ffmpeg-decoder-downmix"} and relative.parts[1] in SKIPPED_DIRECTORIES:
            continue
        yield file


def rewrite(content: str, file: Path) -> str:
    content = content.replace(OLD_PKG, NEW_PKG)
    content = content.replace("com/nuvio/tv", "com/kovelyx/tv")
    content = content.replace(OLD_JNI, NEW_JNI)
    # L'application doit cesser de s'appeler NuvioApplication en interne.
    content = content.replace(OLD_CLASS, NEW_CLASS)
    return content


def destination(old: Path) -> Path:
    rel = old.relative_to(TV)
    parts = list(rel.parts)
    for marker in ("java", "kotlin"):
        if marker not in parts:
            continue
        idx = parts.index(marker)
        if parts[idx + 1:idx + 4] == ["com", "nuvio", "tv"]:
            parts[idx + 2] = "kovelyx"
            break
    name = parts[-1]
    if name == OLD_CLASS + ".kt" or name == OLD_CLASS + ".java":
        parts[-1] = name.replace(OLD_CLASS, NEW_CLASS)
    return TV.joinpath(*parts)


def migrate() -> tuple[int, int]:
    moved = 0
    rewritten = 0
    for old in list(production_files()):
        new = destination(old)
        original = old.read_text(encoding="utf-8")
        updated = rewrite(original, old)
        if new == old and updated == original:
            continue
        if new != old:
            if new.exists():
                raise RuntimeError("Destination existante, migration arrêtée : " + str(new))
            new.parent.mkdir(parents=True, exist_ok=True)
            new.write_text(updated, encoding="utf-8")
            old.unlink()
            moved += 1
        else:
            old.write_text(updated, encoding="utf-8")
            rewritten += 1
    # Adapter les chemins source TV codés dans les tests/outils sans altérer
    # le code mobile/Windows, les crédits, les préférences historiques ou les licences.
    for directory in (ROOT / "scripts",):
        if not directory.exists():
            continue
        for file in sorted(directory.rglob("*")):
            if not file.is_file() or file.name == Path(__file__).name or file.suffix not in {".py", ".yml", ".yaml", ".sh", ".ps1"}:
                continue
            old_text = file.read_text(encoding="utf-8")
            new_text = old_text.replace("apps/tv/app/src/main/java/com/nuvio/tv/", "apps/tv/app/src/main/java/com/kovelyx/tv/")
            new_text = new_text.replace("apps/tv/app/src/full/java/com/nuvio/tv/", "apps/tv/app/src/full/java/com/kovelyx/tv/")
            new_text = new_text.replace("apps/tv/app/src/test/java/com/nuvio/tv/", "apps/tv/app/src/test/java/com/kovelyx/tv/")
            if new_text != old_text:
                file.write_text(new_text, encoding="utf-8")
                rewritten += 1
    return moved, rewritten


def check() -> None:
    gradle = APP_GRADLE.read_text(encoding="utf-8")
    manifest = MAIN_MANIFEST.read_text(encoding="utf-8")
    jni = JNI.read_text(encoding="utf-8")
    assert 'namespace = "com.kovelyx.tv"' in gradle
    assert 'applicationId = "com.kovelyx.tv"' in gradle
    assert 'applicationId = "com.kovelyx.tv.playstore"' in gradle
    assert 'android:name=".KovelyxApplication"' in manifest
    assert 'android:scheme="kovelyx"' in manifest
    assert (TV / "app/src/main/java/com/kovelyx/tv/MainActivity.kt").exists()
    assert (TV / "app/src/main/java/com/kovelyx/tv/KovelyxApplication.kt").exists()
    assert OLD_JNI not in jni, "JNI Dolby Vision conserve des noms de classes de Nuvio"
    assert NEW_JNI in jni, "JNI Dolby Vision non mis à jour"
    proguard = (TV / "app/proguard-rules.pro").read_text(encoding="utf-8")
    assert "-keep class com.kovelyx.tv.core.player" in proguard or "-keep class com.kovelyx.tv.core.torrent" in proguard
    assert "-keep class com.nuvio.engine.**" in proguard, "Pont JNI natif du moteur original détruit"
    assert (TV / "app/libs/lib-nuvio-engine-android-0.1.4.aar").exists()
    obsolete_files = []
    obsolete_text = []
    for file in production_files():
        relative = file.relative_to(TV).as_posix()
        if "/com/nuvio/tv/" in "/" + relative:
            obsolete_files.append(relative)
        if file.suffix in {".kt", ".java"} and OLD_PKG in file.read_text(encoding="utf-8"):
            obsolete_text.append(relative)
    assert not obsolete_files, "Anciens chemins Kotlin/Java : " + str(obsolete_files[:12])
    assert not obsolete_text, "Anciens packages de production : " + str(obsolete_text[:12])
    print("OK : package TV Kovelyx, JNI Dolby Vision, manifestes, tests et identifiants d'installation.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Contrôle sans modifier le dépôt")
    args = parser.parse_args()
    if not args.check:
        moved, rewritten = migrate()
        print(f"Fichiers TV déplacés : {moved} ; fichiers réécrits : {rewritten}")
    check()
