#!/usr/bin/env python3
"""Garde-fou des ressources françaises Android TV Kovelyx.

N'atteste pas de la qualité linguistique ni du fonctionnement des écrans ;
vérifie les clés, la validité XML et les arguments interpolés.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "apps/tv/app/src/main/res"
FORMAT = re.compile(r"%(?:[1-9][0-9]*\\$)[-+# 0,(]*[0-9]*(?:\\.[0-9]+)?[a-zA-Z]")


def extract(path: Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    seen: dict[str, str] = {}
    for child in root:
        if child.tag != "string":
            continue
        name = child.get("name")
        if not name:
            raise ValueError(f"Chaîne sans nom dans {path}")
        if name in seen:
            raise ValueError(f"Clé en double : {name} dans {path}")
        seen[name] = "".join(child.itertext())
    return seen


def main() -> int:
    original = extract(BASE / "values/strings.xml")
    french = extract(BASE / "values-fr/strings.xml")
    missing = sorted(original.keys() - french.keys())
    extra = sorted(french.keys() - original.keys())
    mismatches = [
        name for name in original.keys() & french.keys()
        if sorted(FORMAT.findall(original[name])) != sorted(FORMAT.findall(french[name]))
    ]
    print(f"Android TV : {len(french)} traductions / {len(original)} chaînes par défaut.")
    for title, entries in (("Manquantes", missing), ("En trop", extra), ("Arguments incompatibles", sorted(mismatches))):
        print(f"{title} : {len(entries)}")
        for name in entries[:50]:
            print("  " + name)
    if missing or extra or mismatches:
        return 1
    print("OK : ressources françaises Android TV complètes, XML valide, aucune clé en double.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, ET.ParseError, OSError) as error:
        print("Erreur de ressources Android TV : " + str(error), file=sys.stderr)
        sys.exit(1)
