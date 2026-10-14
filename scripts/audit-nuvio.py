#!/usr/bin/env python3
"""Auditer les véritables liens Nuvio sans confondre les espaces de noms Kotlin."""
from pathlib import Path
from collections import defaultdict, Counter
import re

ROOT = Path(__file__).resolve().parents[1]
DIRS = ["apps/tv", "apps/mobile", "apps/desktop"]
EXT = {".kt",".kts",".java",".xml",".json",".properties",".yml",".yaml",".swift",".sh",".toml",".gradle",".plist"}
SKIP = {"build",".git",".gradle","node_modules",".idea",".kotlin"}
PATTERN = re.compile(r"(?i)(?<!com\.)nuvio\.tv|nuviomedia|nuvio://|nuvio-(?:engine|account)|NUVIO_SUPABASE|NUVIO_DISCORD")
VARIABLE = re.compile(r"\bNUVIO_[A-Z_]+\b")
GROUPS = ["Connexions et services", "Mises à jour et releases", "Liens annexes", "Archives et tests", "Configuration générale"]

def group(path, line):
    p = path.lower()
    if "/test" in p or "/.github/" in p or p.endswith("/store.json"):
        return "Archives et tests"
    if "update" in p or "release" in p or "github" in line.lower():
        return "Mises à jour et releases"
    if any(s in p for s in ("/auth/","login","server","supabase","support")) or any(
        s in line.lower() for s in ("tv-login","api.nuvio","supabase","supporters")):
        return "Connexions et services"
    if "https:" in line or "http:" in line or "nuvio://" in line:
        return "Liens annexes"
    return "Configuration générale"

def main():
    hits = defaultdict(list)
    names = Counter()
    vars = Counter()
    binaries = []
    seen = 0
    for d in DIRS:
        for path in sorted((ROOT/d).rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(ROOT).as_posix()
            if any(v in SKIP for v in path.parts):
                continue
            if "nuvio" in path.name.lower() and path.suffix in {".aar",".jar",".dll",".dylib",".so"}:
                binaries.append(rel)
            if path.suffix.lower() not in EXT or path.stat().st_size > 2000000:
                continue
            try:
                contents = path.read_text(encoding="utf-8")
            except (OSError, UnicodeError):
                continue
            seen += 1
            for i, line in enumerate(contents.splitlines(), 1):
                if "com.nuvio" in line or "nuvio.composeapp" in line:
                    names[d] += 1
                for m in VARIABLE.finditer(line):
                    vars[m.group(0)] += 1
                if PATTERN.search(line):
                    # Avoid publishing full secrets or API query values.
                    clean = " ".join(line.strip().split())
                    clean = clean.split("?")[0]
                    clean = re.sub(r"(?i)(key|token|secret|password)\s*[:=]\s*[^,\s;]+", r"\1=[REDACTED]", clean)
                    hits[group(rel, line)].append((rel,i,clean[:175]))
    report = [
        "# Audit Nuvio des trois applications", "",
        "Rapport statique généré sur le dépôt Kovelyx. Une occurrence ne prouve pas un appel réseau ;",
        "un symbole de package historique ne prouve pas une dépendance de service.", "",
        "Fichiers inspectés : " + str(seen), "",
        "## Inventaire des références à traiter", ""
    ]
    for category in GROUPS:
        entries = hits[category]
        report += ["### " + category + " (" + str(len(entries)) + " correspondances)", ""]
        for path, n, snippet in entries[:80]:
            report.append("- " + path + ":" + str(n) + " : " + snippet.replace("|","/"))
        if len(entries) > 80:
            report.append("- Autres correspondances non détaillées : " + str(len(entries)-80))
        report.append("")
    report += ["## Binaires hérités", ""]
    report += ["- " + path for path in binaries] or ["Aucun nom de binaire Nuvio."]
    report += ["", "## Références de packages Kotlin historiques", ""]
    report += ["- " + k + " : " + str(v) + " lignes." for k,v in names.items()]
    report += ["", "## Variables héritées (ne pas renommer sans migrer les consommateurs)", ""]
    report += ["- " + k + " : " + str(v) for k,v in vars.most_common(40)]
    report += ["", "## Limites et vérifications indispensables", "",
        "- Tester les trois applications en surveillant les appels réseau réels et sans accès aux domaines Nuvio.",
        "- Migrer ou rendre facultatives les fonctions Supabase (compte, authentification, synchronisation).",
        "- Conserver les sources et licences des bibliothèques MPV, libass et moteurs livrés sous forme binaire.",
        "- Migrer les URL de support et connexion sans inventer de faux services.",
        "- Tester la signature, les identifiants d'application, les mises à jour et la conservation des données.",
        "- Ne pas supprimer les mentions d'attribution/licence Nuvio obligatoires.",
        "", "Absence de résultat dans cet audit ne constituerait pas une garantie 100 % indépendante.", ""]
    out = ROOT/"docs"/"AUDIT_NUVIO.md"
    out.write_text("\n".join(report), encoding="utf-8")
    print("Fichiers :",seen)
    for key in GROUPS:
        print(key,":",len(hits[key]))
    print("Binaires :",len(binaries))

if __name__ == "__main__":
    main()
