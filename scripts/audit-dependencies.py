#!/usr/bin/env python3
"""Rapport statique de références extérieures dans les sources Kovelyx (sans clés secrètes)."""
from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
PLATFORMS = {"TV": ROOT / "apps/tv", "Android": ROOT / "apps/mobile", "Windows": ROOT / "apps/desktop"}
EXTENSIONS = {".kt", ".kts", ".java", ".swift", ".xml", ".json", ".yml", ".yaml", ".toml", ".properties", ".sh", ".py", ".gradle", ".xcconfig", ".plist", ".ts", ".tsx", ".js"}
LINK_RE = re.compile(r"https?://([A-Za-z0-9.-]+)(?::\d+)?", re.I)
UPSTREAM_RE = re.compile(r"(?i)nuvio\.tv|nuviomedia|nuvio://")
SKIP_DIRS = {".git", ".gradle", "build", ".idea", "node_modules", ".swiftpm", "Pods", ".kotlin"}
MAX_SOURCE_KB = 1024

def inspect(path: Path):
    domains: Counter[str] = Counter()
    dependent_locations: list[str] = []
    files = 0
    for file in path.rglob("*"):
        if not file.is_file() or file.suffix.lower() not in EXTENSIONS:
            continue
        if SKIP_DIRS.intersection(file.relative_to(path).parts):
            continue
        if file.stat().st_size > MAX_SOURCE_KB * 1024:
            continue
        files += 1
        try:
            contents = file.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        rel = file.relative_to(ROOT).as_posix()
        for line_num, line in enumerate(contents.splitlines(), 1):
            for match in LINK_RE.finditer(line):
                domains[match.group(1).lower()] += 1
            if UPSTREAM_RE.search(line):
                dependent_locations.append(f"`{rel}:{line_num}`")
    return files, domains, dependent_locations

def main():
    report = ["# Audit statique des références réseau", "",
              "Rapport **indicatif** ; il ne certifie ni la compilation ni l'indépendance. "
              "Certaines URLs peuvent appartenir aux documents, aux tests ou être construites dynamiquement.",
              ""]
    for title, directory in PLATFORMS.items():
        report.append(f"## {title}")
        if not directory.is_dir():
            report.extend(["", "Sources non importées.", ""])
            continue
        files, domains, upstream = inspect(directory)
        report.extend(["", f"Fichiers texte examinés : **{files}** ; références statiques à Nuvio/NuvioMedia : **{len(upstream)}**.", ""])
        report.extend(["| Hôte extrait | Occurrences |", "| --- | ---: |"])
        for host, occurrences in domains.most_common(30):
            report.append(f"| `{host}` | {occurrences} |")
        report.extend(["", "### Références à vérifier en priorité", ""])
        if upstream:
            report.extend(f"- {loc}" for loc in upstream[:80])
            if len(upstream) > 80:
                report.append(f"- …et {len(upstream)-80} autres correspondances (plafond d'affichage).")
        else:
            report.append("Aucune chaîne statique correspondante dans les extensions examinées.")
        report.append("")
    report.extend(["## Dépendances natives manquantes", ""])
    for name, directory in PLATFORMS.items():
        if not directory.is_dir():
            continue
        for filename in (".kovelyx-gitlinks.txt", ".kovelyx-binaries-a-sauvegarder.txt"):
            file = directory / filename
            if file.is_file():
                lines = file.read_text(encoding="utf-8", errors="replace").splitlines()
                if filename.endswith("gitlinks.txt") and not lines:
                    continue
                report.append(f"- **{name} / {filename}** :")
                report.extend(f"  - `{line}`" for line in lines if line and not line.startswith("#"))
    report.extend(["", "## Limites", "", 
      "- Un hôte présent n'est pas nécessairement un appel actif à l'exécution.",
      "- Les URLs construites à l'exécution, les données téléchargées, les secrets et services Docker nécessitent un audit séparé.",
      "- Les références à Nuvio doivent être remplacées au bon niveau : ne pas les supprimer aveuglément.",
      "- Les mises à jour des sources nécessitent de réexécuter l'audit.",
      ""])
    destination = ROOT / "docs" / "AUDIT_DEPENDANCES.md"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("\n".join(report), encoding="utf-8")
    print(f"Rapport écrit : {destination} ({len(report)} lignes)")

if __name__ == "__main__":
    main()
