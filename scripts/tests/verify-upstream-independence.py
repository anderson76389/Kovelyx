#!/usr/bin/env python3
"""Barrière de code contre les dépendances réseau Nuvio dans les clients Kovelyx.

Un succès est un constat STATIQUE, jamais une preuve d'absence de trafic réel :
les SDK, les bibliothèques natives, les URLs calculées et les valeurs dynamiques
nécessitent des tests supplémentaires avec l'amont réellement inaccessible.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]

# Uniquement les sources de production susceptibles d'être incluses dans nos builds.
RUNTIME = (
    "apps/desktop/composeApp/src/commonMain",
    "apps/desktop/composeApp/src/desktopMain",
    "apps/mobile/composeApp/src/commonMain",
    "apps/mobile/composeApp/src/androidMain",
    "apps/mobile/composeApp/src/androidFull",
    "apps/tv/app/src/main/java",
    "apps/tv/app/src/full/java",
)

BUILD_FILES = (
    "apps/desktop/composeApp/build.gradle.kts",
    "apps/mobile/composeApp/build.gradle.kts",
    "apps/tv/app/build.gradle.kts",
    "apps/desktop/settings.gradle.kts",
    "apps/mobile/settings.gradle.kts",
    "apps/tv/settings.gradle.kts",
)

UPSTREAM_SERVICE = re.compile(
    r'https?://(?:[a-z0-9-]+\.)*nuvio\.tv(?:/[\w./?%=&+-]*)?', re.I
)
UPSTREAM_REPO = re.compile(
    r'https?://(?:www\.)?github\.com/NuvioMedia(?:/[\w./?%=&+-]*)?', re.I
)
UPSTREAM_API = re.compile(
    r'https?://api\.github\.com/repos/NuvioMedia(?:/[\w./?%=&+-]*)?', re.I
)
UPSTREAM_UPDATE_LITERAL = re.compile(
    r'\b(?:owner|repo)\s*=\s*"(?:NuvioMedia|NuvioMobile|NuvioTV|NuvioDesktop)"'
)

# Aucun serveur de comptes officiel Nuvio n'est permis dans les clients actifs.
ACCOUNT_ALLOW: set[tuple[str, str]] = set()

# Référence de REJET uniquement : ce serveur historique est explicitement refusé
# dans isOfficial() ; cette constante ne déclenche aucune requête.
REJECT_ONLY_ALLOW = {
    (
        "apps/desktop/composeApp/src/commonMain/kotlin/"
        "com/nuvio/app/core/network/ServerDiscovery.kt",
        "https://api.nuvio.tv",
    ),
}

# Les notices de licences/crédits doivent rester intactes.
LEGAL_FILE_NAMES = ("license", "licence", "attribution", "provenance")
violations: list[str] = []
account_exceptions: list[str] = []
rejection_only_exceptions: list[str] = []
checked_files = 0

candidate_files: list[Path] = []
for relative in RUNTIME:
    source_dir = ROOT / relative
    if not source_dir.exists():
        violations.append(f"Dossier actif absent : {relative}")
        continue
    candidate_files.extend(source_dir.rglob("*.kt"))
    candidate_files.extend(source_dir.rglob("*.java"))
candidate_files.extend(ROOT / name for name in BUILD_FILES)

for file in sorted(set(candidate_files)):
    if not file.is_file():
        violations.append(f"Fichier de build absent : {file.relative_to(ROOT)}")
        continue
    checked_files += 1
    relative = file.relative_to(ROOT).as_posix()
    if any(s in file.name.lower() for s in LEGAL_FILE_NAMES):
        continue
    for line_no, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
        if line.lstrip().startswith(("//", "*", "#")):
            continue
        for regex in (UPSTREAM_SERVICE, UPSTREAM_REPO, UPSTREAM_API):
            for match in regex.finditer(line):
                url = match.group(0)
                if (relative, url) in ACCOUNT_ALLOW:
                    account_exceptions.append(f"{relative}:{line_no}")
                elif (relative, url) in REJECT_ONLY_ALLOW and "canonicalOfficialBackend" in line:
                    rejection_only_exceptions.append(f"{relative}:{line_no}")
                else:
                    violations.append(f"{relative}:{line_no} : URL interdite {url}")
        if "NUVIO_SUPABASE_" in line:
            violations.append(f"{relative}:{line_no} : configuration du compte amont interdite")
        if UPSTREAM_UPDATE_LITERAL.search(line):
            violations.append(f"{relative}:{line_no} : ancienne source de mise à jour")

# Les anciens flux IPA Nuvio ne sont pas des Releases Kovelyx :
# leur présence dans les sous-projets est une régression.
for upstream_feed in ("apps/mobile/store.json", "apps/desktop/store.json"):
    if (ROOT / upstream_feed).is_file():
        violations.append(f"Flux IPA Nuvio hérités toujours publiés : {upstream_feed}")

print(f"Sources de production et build inspectés : {checked_files}")
print(f"Exceptions temporaires pour le compte historique : {len(account_exceptions)}")
for item in account_exceptions:
    print(f"  COMPTE SEULEMENT : {item}")
print(f"Références d'identification d'URL interdites, sans appel réseau : {len(rejection_only_exceptions)}")
for item in rejection_only_exceptions:
    print(f"  REJET UNIQUEMENT : {item}")
print(f"Connexions anciennes codées en dur hors comptes : {len(violations)}")
for item in violations[:120]:
    print(f"  ECHEC : {item}")
print("Ce contrôle n'analyse pas les DLL/AAR, les connexions DNS ni les services calculés à l'exécution.")
sys.exit(1 if violations else 0)
