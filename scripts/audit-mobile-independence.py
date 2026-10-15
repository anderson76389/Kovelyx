#!/usr/bin/env python3
"""Garde-fou statique Android : aucune nouvelle dépendance à Nuvio hors comptes.

Ce contrôle ne prétend pas remplacer une analyse du trafic réseau de l'APK
sur téléphone. Il distingue les noms historiques de packages des URL
réellement codées dans les sources compilées.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MOBILE = ROOT / "apps/mobile"
SOURCES = MOBILE / "composeApp/src"
REPO_URL = "https://api.github.com/repos/anderson76389/Kovelyx"
ACCOUNT_PATH = "core/auth/DeviceLinkAuthRepository.kt"
LEGAL_PATH = "features/settings/LicensesAttributionsPage.kt"
SERVICE_URL = re.compile(r"https?://(?:[a-z0-9-]+\.)*nuvio\.tv(?:/[^\s\"'<>)]*)?", re.I)
UPSTREAM_URL = re.compile(r"https?://github\.com/NuvioMedia(?:/[^\s\"'<>)]*)?", re.I)
SOURCE_FOLDERS = {"androidMain", "androidFull", "androidPlaystore", "commonMain"}
violations: list[str] = []
accepted_accounts: list[str] = []
accepted_licences: list[str] = []
files_scanned = 0

for file in sorted(SOURCES.rglob("*.kt")):
    if not (set(file.relative_to(SOURCES).parts) & SOURCE_FOLDERS):
        continue
    files_scanned += 1
    relative = file.relative_to(ROOT).as_posix()
    data = file.read_text(encoding="utf-8")
    for pattern in (SERVICE_URL, UPSTREAM_URL):
        for match in pattern.finditer(data):
            url = match.group(0)
            if ACCOUNT_PATH in relative and url == "https://nuvio.tv/link":
                accepted_accounts.append(relative + " : " + url)
            elif LEGAL_PATH in relative and url == "https://github.com/NuvioMedia/NuvioMobile":
                accepted_licences.append(relative + " : " + url)
            else:
                violations.append(relative + " : " + url)

required = {
    "moteur Android embarqué": MOBILE / "composeApp/libs/lib-nuvio-engine-android-0.1.4.aar",
    "sources de sauvegarde moteur": ROOT / "vendor/engine",
    "métadonnées APK Android": MOBILE / "androidApp/build.gradle.kts",
}
for name, path in required.items():
    if not path.exists() or (path.is_file() and path.stat().st_size < 1024):
        violations.append("Dépendance locale introuvable : " + name + " (" + str(path) + ")")

updater = (SOURCES / "commonMain/kotlin/com/nuvio/app/features/updater/AppUpdaterRepository.kt")
if not updater.is_file() or REPO_URL not in updater.read_text(encoding="utf-8"):
    violations.append("Les mises à jour Android ne pointent pas vers les Releases Kovelyx.")

print(f"Sources Kotlin Android examinées : {files_scanned}")
print(f"Endpoint comptes conservé : {len(accepted_accounts)}")
for item in accepted_accounts:
    print("  COMPTE : " + item)
print(f"Attributions obligatoires conservées : {len(accepted_licences)}")
print(f"Références réseau Nuvio non autorisées : {len(violations)}")
for item in violations:
    print("  ECHEC : " + item)
print("Limite : contrôle statique, sans preuve de trafic réseau ni analyse des binaires natifs.")
sys.exit(1 if violations else 0)
