#!/usr/bin/env python3
"""Owner-side restore rehearsal for an encrypted Kovelyx D1 backup.

Runs only on your own computer with the *offline* recovery private key.
Never connects to Cloudflare, uploads the key, or writes to production D1.
The decrypted SQL exists only in a mode-0700 temporary directory, and is
replayed in an isolated in-memory SQLite database by backup_validation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from backup_validation import validate_export

ENCRYPTED = "kovelyx-d1.sql.cms"
CERT = ROOT.parents[1] / ".github" / "cloudflare-backup-recipient.pem"


def sha256(path: Path) -> str:
    hash_value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            hash_value.update(block)
    return hash_value.hexdigest()


def verify_artifact(artifact: Path, key: Path, cert: Path = CERT) -> dict:
    artifact = artifact.resolve(strict=True)
    key = key.resolve(strict=True)
    cert = cert.resolve(strict=True)
    if not key.is_file() or not cert.is_file():
        raise ValueError("Clé privée ou certificat public de récupération absent.")
    if not artifact.is_dir():
        raise ValueError("Choisir le dossier contenant le CMS chiffré et manifest.json.")
    manifest = json.loads((artifact / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("format") != "CMS-AES-256-GCM-RSA4096":
        raise ValueError("Format de sauvegarde inattendu.")
    if manifest.get("validation_status") != "passed":
        raise ValueError("Cette sauvegarde n'a pas été validée à la création.")
    encrypted = artifact / ENCRYPTED
    if encrypted.stat().st_size == 0:
        raise ValueError("Fichier chiffré vide.")
    if sha256(encrypted) != manifest.get("encrypted_sha256"):
        raise ValueError("Empreinte du fichier chiffré incohérente.")
    if sha256(cert) != manifest.get("certificate_sha256"):
        raise ValueError("Le certificat de récupération ne correspond pas à la sauvegarde.")
    with tempfile.TemporaryDirectory(prefix="kovelyx-d1-restore-check-") as td:
        directory = Path(td)
        sql = directory / "restored-d1.sql"
        # Never forward OpenSSL stdout/stderr: they can contain key paths,
        # decrypted fragments, or implementation details.
        process = subprocess.run(
            ["openssl", "cms", "-decrypt", "-binary", "-inform", "DER",
             "-in", str(encrypted), "-recip", str(cert),
             "-inkey", str(key), "-out", str(sql)],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            timeout=120, check=False,
        )
        if process.returncode != 0:
            raise ValueError("Déchiffrement impossible : mauvaise clé ou copie endommagée.")
        if sha256(sql) != manifest.get("sql_sha256"):
            raise ValueError("L'empreinte du SQL restauré ne correspond pas au manifeste.")
        outcome = validate_export(sql)
        expected_counts = manifest.get("counts", {}).get("tables")
        if not isinstance(expected_counts, dict) or outcome["tables"] != expected_counts:
            raise ValueError("Les nombres de lignes après restauration diffèrent du manifeste.")
        for name in ("verified_media", "verified_catalogues", "legacy_inline_catalogues"):
            if outcome[name] != manifest["counts"].get(name):
                raise ValueError("Vérification des données restaurées incohérente.")
        return {"restored": True, "tables_verified": len(outcome["tables"]),
                "media_checked": outcome["verified_media"],
                "catalogues_checked": outcome["verified_catalogues"],
                "environment": "in-memory-sqlite-only"}


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Tester une restauration D1 hors ligne, sans toucher aux vrais comptes."
    )
    parser.add_argument("--artifact-dir", required=True, type=Path,
                        help="Dossier extrait de l'archive GitHub Actions chiffrée.")
    parser.add_argument("--private-key", required=True, type=Path,
                        help="Chemin local de la clé privée (ne jamais la publier).")
    args = parser.parse_args()
    try:
        result = verify_artifact(args.artifact_dir, args.private_key)
        print("PASS: restauration hors ligne : SQL, intégrité, profils, PIN, images et catalogues.")
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    except Exception as error:
        print("ÉCHEC : test de restauration hors ligne non validé (" +
              type(error).__name__ + ").", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
