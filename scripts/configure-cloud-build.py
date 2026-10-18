#!/usr/bin/env python3
"""Préparer les builds Kovelyx pour un Worker Cloudflare déjà déployé.

Configuration publique : KOVELYX_CLOUDFLARE_URL (GitHub Actions Variable).
NE JAMAIS brancher le Worker Cloudflare dans Supabase SDK : protocole différent.
Les anciennes valeurs Supabase de CI sont ignorées, afin de conserver le mode invité.
"""
import argparse
import ipaddress
import os
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {
    "desktop": ("apps/desktop/local.properties",),
    "mobile": ("apps/mobile/local.properties",),
    "tv": ("apps/tv/local.properties",),
}
parser = argparse.ArgumentParser()
parser.add_argument("--platform", choices=(*TARGETS, "all"), required=True)
args = parser.parse_args()
url = os.getenv("KOVELYX_CLOUDFLARE_URL", "").strip().rstrip("/")
if not url:
    print("Cloudflare non configuré : mode invité conservé.")
    raise SystemExit(0)
parts = urlsplit(url)
host = parts.hostname or ""
try:
    ipaddress.ip_address(host)
    raise SystemExit("IP directe refusée : utilisez HTTPS public.")
except ValueError:
    pass
if (parts.scheme != "https" or not host or "." not in host or
    parts.port is not None or parts.path not in ("", "/") or
    parts.query or parts.fragment or parts.username or parts.password or
    host in ("localhost", "nuvio.tv", "api.nuvio.tv") or
    host.endswith((".local", ".internal", ".supabase.co", ".nuvio.tv"))):
    raise SystemExit("Worker Cloudflare : fournir une URL HTTPS publique sans chemin ni identifiant.")
for platform in TARGETS if args.platform == "all" else (args.platform,):
    for relative in TARGETS[platform]:
        dest = ROOT / relative
        lines = dest.read_text(encoding="utf-8").splitlines() if dest.exists() else []
        # Ne pas écraser les réglages locaux des autres services.
        lines = [line for line in lines if line.split("=",1)[0].strip() != "KOVELYX_CLOUD_URL"]
        dest.write_text("\n".join([*lines, "KOVELYX_CLOUD_URL=" + url]) + "\n", encoding="utf-8")
        os.chmod(dest, 0o600)
    print("Worker Cloudflare configuré pour " + platform + " : " + host)
