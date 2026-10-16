#!/usr/bin/env python3
"""Connecte, sur GitHub Actions uniquement, les builds Kovelyx au Supabase du propriétaire.

Deux variables du dépôt : KOVELYX_CLOUD_URL et KOVELYX_CLOUD_PUBLISHABLE_KEY.
Ne jamais utiliser sb_secret_* ou service_role dans une application installable.
En cas d'absence des deux variables, le mode invité existant reste inchangé.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {
    "desktop": ("apps/desktop/local.properties",),
    "mobile": ("apps/mobile/local.properties",),
    "tv": ("apps/tv/local.properties", "apps/tv/dev.properties"),
}

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--platform", required=True, choices=(*TARGETS, "all"))
args = parser.parse_args()
url = os.environ.get("KOVELYX_CLOUD_URL", "").strip().rstrip("/")
key = os.environ.get("KOVELYX_CLOUD_PUBLISHABLE_KEY", "").strip()

if not url and not key:
    print("Kovelyx Cloud non configure : comptes en mode invite, aucun ancien serveur force.")
    raise SystemExit(0)
if not url or not key:
    raise SystemExit("Kovelyx Cloud : configurer URL ET cle publique, ou aucune des deux.")
parts = urlsplit(url)
if (
    parts.scheme != "https"
    or not parts.hostname
    or not parts.hostname.endswith(".supabase.co")
    or parts.hostname == ".supabase.co"
    or parts.username or parts.password or parts.port
    or parts.path not in ("", "/")
    or parts.query or parts.fragment
):
    raise SystemExit("URL Supabase invalide. Utiliser https://<identifiant>.supabase.co")
if not key.startswith("sb_publishable_") or not key.removeprefix("sb_publishable_").replace("_", "").replace("-", "").isalnum():
    raise SystemExit("La cle doit etre une Publishable key sb_publishable_... (jamais sb_secret ou service_role).")
if "\n" in key or "\r" in key:
    raise SystemExit("Cle invalide.")
properties = {
    "NUVIO_SUPABASE_URL": url,
    "NUVIO_SUPABASE_ANON_KEY": key,
    "NUVIO_SUPABASE_FALLBACK_URL": "",
}
for platform in TARGETS if args.platform == "all" else (args.platform,):
    for relative in TARGETS[platform]:
        target = ROOT / relative
        lines = target.read_text(encoding="utf-8").splitlines() if target.exists() else []
        retained = [line for line in lines if line.split("=", 1)[0].strip() not in properties]
        target.write_text("\n".join(retained + [f"{name}={value}" for name, value in properties.items()]) + "\n", encoding="utf-8")
        os.chmod(target, 0o600)
    print("Configuration de comptes Kovelyx injectee pour " + platform + " (aucune valeur affichee).")
