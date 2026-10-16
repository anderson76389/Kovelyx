#!/usr/bin/env python3
"""Détecter les domaines Nuvio en clair dans les DLL, JAR et AAR embarqués.

Un résultat négatif ne prouve PAS que le binaire ne peut pas calculer une URL
dynamiquement ; un résultat positif doit être attribué à du code de compte
historique ou à un composant runtime (sans masquer la découverte).
"""
from __future__ import annotations

import io
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SCOPES = [
    ROOT / "apps/mobile/composeApp/libs",
    ROOT / "apps/tv/app/libs",
    ROOT / "apps/desktop/composeApp/libs",
    ROOT / "apps/desktop/composeApp/src/desktopMain/native/windows/engine",
]
# La plus grande partie des URL est conservée en ASCII ou UTF-8 dans les données
# binaires et le constant pool JVM ; la détection n'inclut pas le UTF-16.
UPSTREAM = re.compile(
    rb"(?:[a-z0-9-]+\.)*nuvio\.tv|"
    rb"(?:api\.github\.com/repos|github\.com|raw\.githubusercontent\.com)/NuvioMedia",
    re.I,
)
MAX_MEMBER_BYTES = 160 * 1024 * 1024
ALLOWED_EXTENSIONS = {".aar", ".jar", ".dll", ".so"}
hits: list[tuple[str, str]] = []
scanned_count = 0
errors: list[str] = []


def inspect_buffer(data: bytes, location: str, depth: int = 0) -> None:
    global scanned_count
    scanned_count += 1
    for match in UPSTREAM.finditer(data):
        value = match.group(0).decode("ascii", errors="replace")
        hits.append((location, value))
    if depth >= 2 or not zipfile.is_zipfile(io.BytesIO(data)):
        return
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for item in archive.infolist():
                if item.is_dir():
                    continue
                if item.file_size > MAX_MEMBER_BYTES:
                    errors.append(f"Membre trop volumineux, non contrôlé : {location}!{item.filename}")
                    continue
                # Ressources de licences non exécutables et images exclues
                if not item.filename.endswith((".jar", ".class", ".dex", ".so", ".dll", ".aar")):
                    continue
                inspect_buffer(archive.read(item), location + "!" + item.filename, depth + 1)
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as exc:
        errors.append(f"Archive non analysée : {location} : {type(exc).__name__}")


def main() -> int:
    count_files = 0
    for directory in SCOPES:
        if not directory.is_dir():
            errors.append(f"Dossier absent : {directory.relative_to(ROOT)}")
            continue
        for path in sorted(directory.iterdir()):
            if not path.is_file() or path.suffix.lower() not in ALLOWED_EXTENSIONS:
                continue
            count_files += 1
            inspect_buffer(path.read_bytes(), path.relative_to(ROOT).as_posix())
    print(f"Archives natives examinées : {count_files} ; composants internes : {scanned_count}")
    print(f"Occurrences de domaines NuvioMedia / nuvio.tv en clair : {len(hits)}")
    for location, value in hits[:80]:
        print(f"  A EXAMINER : {location} : {value}")
    for value in errors:
        print(f"  ERREUR : {value}")
    # Aucun endpoint Nuvio n'est attendu dans les bibliothèques natives des
    # trois clients hors comptes, dont le backend vit dans une autre couche.
    if errors or hits:
        print("NON CERTIFIÉ : présence d'une URL historique en binaire ou archive non analysée.")
        return 1
    print("OK : aucun domaine historique en clair dans les binaires inclus examinés.")
    print("Limites : adresses chiffrées/construites, bibliothèques système, SDK externes et MPV hors Git non inspectés.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
