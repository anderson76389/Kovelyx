#!/usr/bin/env python3
"""Contrôle préventif : URLs Nuvio codées dans les sources Kotlin actives de l'APK TV.

Les packages com.nuvio.tv et licences historiques ne sont pas des appels
réseau. Ne couvre ni code natif ni domaines construits dynamiquement.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "apps/tv/app/src"
ACTIVE = [APP / "main/java", APP / "full/java"]
OLD_SERVICE = re.compile(r"https?://(?:[a-z0-9-]+\\.)*nuvio\\.tv(?:/|\\b)", re.I)
OLD_RELEASE = re.compile(r"https?://(?:www\\.)?github\\.com/NuvioMedia(?:/|\\b)", re.I)
LEGAL_FILES = ("license", "licence", "attribution", "provenance")
violations: list[str] = []
total = 0

for directory in ACTIVE:
    for path in sorted(directory.rglob("*.kt")):
        total += 1
        relative = path.relative_to(ROOT).as_posix()
        # Les URL de provenance des logiciels dérivés doivent être conservées.
        if any(part in path.name.lower() for part in LEGAL_FILES):
            continue
        content = path.read_text(encoding="utf-8")
        for number, line in enumerate(content.splitlines(), 1):
            if OLD_SERVICE.search(line) or OLD_RELEASE.search(line):
                violations.append(f"{relative}:{number}")

print(f"Kovelyx TV : {total} sources Kotlin actives examinées.")
print(f"URLs Nuvio/NuvioMedia hors crédits et licences : {len(violations)}")
for item in violations:
    print("NON CONFORME : " + item)
print("Limite : analyse statique, sans inspection du réseau réel, des dépendances natives ni des comptes historiques.")
sys.exit(1 if violations else 0)
