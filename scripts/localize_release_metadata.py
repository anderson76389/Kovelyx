#!/usr/bin/env python3
"""Franciser uniquement les textes historiques de Releases Kovelyx connues.

Ne modifie jamais les fichiers binaires, les tags, les versions ou les liens.
Les sections Android/TV déjà en français et les preuves de compilation restent intactes.
"""
import json
import re
import sys
from pathlib import Path

VERSION = re.compile(r"^v\d+\.\d+\.\d+-(?:alpha|beta|rc)$")
OLD_WINDOWS_PREFIX = "## Kovelyx Windows — preview "
REPLACEMENTS = (
    ("**Recommended download:**", "**Téléchargement conseillé :**"),
    ("(one file to INSTALL).", "(installateur Windows en un seul fichier)."),
    (
        "The MSI is an alternative installer. The portable ZIP must retain the complete app and Java runtime folders.",
        "Le fichier MSI est un autre format d'installation. La version portable ZIP doit conserver tous les fichiers de l'application et le moteur Java fourni.",
    ),
    ("- Source Windows CI:", "- Compilation Windows :"),
    ("- Source commit:", "- Commit source :"),
    (
        "- SHA-256 checksums are provided for every file.",
        "- Une empreinte SHA-256 accompagne chaque fichier téléchargé.",
    ),
    (
        "- This build is **experimental, unsigned and not certified on real hardware**.",
        "- Cette version est **expérimentale, non signée et non certifiée sur un ordinateur réel**.",
    ),
    (
        "- Playback, extensions, tracking sign-in, and updates still need real-PC verification.",
        "- La lecture, les extensions, les connexions aux services de suivi et les mises à jour doivent encore être vérifiées sur un vrai PC.",
    ),
    ("- License and dependency notices:", "- Licences et dépendances :"),
    (
        "This is NOT a stable release. Do not ignore Windows security warnings.",
        "**Ce n'est PAS une version stable. Respectez les avertissements de sécurité de Windows.**",
    ),
)


def localize_release(release: dict) -> tuple[str, str]:
    tag = str(release.get("tag_name") or "")
    title = str(release.get("name") or "")
    body = str(release.get("body") or "")
    if not VERSION.fullmatch(tag):
        return title, body

    if body.startswith(OLD_WINDOWS_PREFIX + tag + "\n"):
        body = body.replace(
            OLD_WINDOWS_PREFIX + tag,
            "## Kovelyx Windows — préversion expérimentale " + tag,
            1,
        )
        for source, translation in REPLACEMENTS:
            body = body.replace(source, translation, 1)

    if title == f"Kovelyx Windows {tag} — test":
        title = f"Kovelyx Windows {tag} — préversion expérimentale"

    return title, body


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("Utilisation : localize_release_metadata.py entree.json notes.md titre.txt")
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    title, body = localize_release(data)
    Path(sys.argv[2]).write_text(body, encoding="utf-8")
    Path(sys.argv[3]).write_text(title, encoding="utf-8")


if __name__ == "__main__":
    main()
