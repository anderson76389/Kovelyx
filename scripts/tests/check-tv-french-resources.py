#!/usr/bin/env python3
"""Vérifie les ressources françaises Android TV, sans prétendre certifier l'UI.

Contrôles : XML, clés et doublons, chaînes simples, pluriels, arguments de
formatage, et noms hérités visibles hors véritables références de compte.
Les textes Kotlin calculés et les réponses des API demandent des tests UI.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "apps/tv/app/src/main/res"
# N'interprète pas le texte ordinaire « 10% de » comme un paramètre formaté.
FORMAT = re.compile(r"%(?:[1-9]\d*\$)?[-+#0,(]*\d*(?:\.\d+)?[sdfoxXeEgGaAcCbBhHn]")
LEGACY_ACCOUNT_OR_FILE_KEYS = {
    "trakt_watch_progress_dialog_subtitle",
    "trakt_watch_progress_source_nuvio",
    "trakt_library_source_nuvio",
    "trakt_library_source_nuvio_selected",
    "trakt_watch_progress_nuvio_selected",
    "library_source_nuvio",
    "account_sign_in_description",
    "collections_file_description",
}


def extract(path: Path) -> tuple[dict[str, str], dict[str, dict[str, str]]]:
    document = ET.parse(path)
    root = document.getroot()
    if root.tag != "resources":
        raise ValueError(f"Racine XML inattendue : {path}")
    strings: dict[str, str] = {}
    plurals: dict[str, dict[str, str]] = {}
    for node in root:
        if node.tag not in ("string", "plurals"):
            continue
        key = node.attrib.get("name")
        if not key:
            raise ValueError(f"Ressource sans clé dans {path}")
        table = strings if node.tag == "string" else plurals
        if key in table:
            raise ValueError(f"Clé en double : {key} dans {path}")
        if node.tag == "string":
            strings[key] = "".join(node.itertext())
        else:
            forms: dict[str, str] = {}
            for item in node.findall("item"):
                quantity = item.attrib.get("quantity")
                if not quantity or quantity in forms:
                    raise ValueError(f"Quantité plurielle invalide : {key}/{quantity}")
                forms[quantity] = "".join(item.itertext())
            plurals[key] = forms
    return strings, plurals


def check() -> int:
    original, original_plurals = extract(BASE / "values/strings.xml")
    translated, translated_plurals = extract(BASE / "values-fr/strings.xml")
    failures: list[str] = []
    for name, source, target in (
        ("chaînes", original, translated),
        ("pluriels", original_plurals, translated_plurals),
    ):
        for key in sorted(source.keys() - target.keys()):
            failures.append(f"{name} : manquant en français : {key}")
        for key in sorted(target.keys() - source.keys()):
            failures.append(f"{name} : absent de la référence : {key}")

    for key in original.keys() & translated.keys():
        if sorted(FORMAT.findall(original[key])) != sorted(FORMAT.findall(translated[key])):
            failures.append(f"Arguments de formatage incompatibles : {key}")
        if "nuvio" in translated[key].lower() and key not in LEGACY_ACCOUNT_OR_FILE_KEYS:
            failures.append(f"Ancienne marque visible hors compte et compatibilité : {key}")

    for key in original_plurals.keys() & translated_plurals.keys():
        source_forms = original_plurals[key]
        french_forms = translated_plurals[key]
        for quantity in sorted(source_forms.keys() - french_forms.keys()):
            failures.append(f"Forme plurielle manquante : {key}/{quantity}")
        for quantity in source_forms.keys() & french_forms.keys():
            if sorted(FORMAT.findall(source_forms[quantity])) != sorted(FORMAT.findall(french_forms[quantity])):
                failures.append(f"Arguments de pluriel incompatibles : {key}/{quantity}")

    print(f"TV : {len(translated)}/{len(original)} chaînes ; {len(translated_plurals)}/{len(original_plurals)} pluriels.")
    print(f"Erreurs XML, couverture, formatage, marque : {len(failures)}.")
    for failure in failures[:120]:
        print("  ECHEC : " + failure)
    if not failures:
        print("OK : couverture des ressources TV et des pluriels vérifiée.")
    print("Limites : textes dynamiques et comportement réel non vérifiés.")
    return 1 if failures else 0


if __name__ == "__main__":
    try:
        sys.exit(check())
    except (ValueError, ET.ParseError, OSError) as error:
        print("Erreur des ressources Android TV : " + str(error), file=sys.stderr)
        sys.exit(1)
