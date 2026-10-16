#!/usr/bin/env python3
"""Preparer UNE valeur pour GitHub Actions Secret, sans appel API GitHub.

Execution depuis GitHub Codespaces : python3 scripts/prepare-android-signing.py
Le fichier temporaire contient des secrets : il ne doit jamais etre commite.
"""
import base64
import getpass
import json
import os
import subprocess
import tempfile
from pathlib import Path

keystore = Path.home() / "kovelyx-keys" / "kovelyx-release.jks"
if not keystore.is_file():
    raise SystemExit("Cle non trouvee : " + str(keystore))
print("Cle trouvee. Saisir les mots de passe sans les afficher.")
store_password = getpass.getpass("Mot de passe JKS : ")
key_password = getpass.getpass("Mot de passe de la cle (Entree si identique) : ") or store_password
if not store_password or not key_password or "\n" in store_password or "\n" in key_password:
    raise SystemExit("Mot de passe absent ou invalide.")
env = os.environ.copy()
env["KV_STORE_PASS"] = store_password
env["KV_KEY_PASS"] = key_password
verified = subprocess.run(
    [
        "keytool", "-certreq", "-alias", "kovelyx", "-keystore", str(keystore),
        "-storepass:env", "KV_STORE_PASS", "-keypass:env", "KV_KEY_PASS",
    ],
    env=env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True, timeout=40,
)
if verified.returncode != 0:
    raise SystemExit("Mot de passe incorrect. Aucun secret n'a ete cree. Reessaye.")
payload = {
    "keystore_base64": base64.b64encode(keystore.read_bytes()).decode("ascii"),
    "store_password": store_password,
    "key_password": key_password,
    "key_alias": "kovelyx",
}
value = base64.b64encode(json.dumps(payload, ensure_ascii=True).encode("utf-8")).decode("ascii")
if len(value) > 48 * 1024:
    raise SystemExit("Fichier trop grand pour un secret GitHub Actions.")
out = Path(tempfile.gettempdir()) / "kovelyx-signature-secret.txt"
fd = os.open(str(out), os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, "w", encoding="ascii") as file:
    file.write(value)
    file.write("\n")
print("Secret temporaire cree. Aucun mot de passe affiche.")
print("Ouvre-le dans l'editeur avec : code " + str(out))
print("Copie tout son contenu vers UN SEUL secret GitHub : KOVELYX_ANDROID_SIGNING_BUNDLE")
print("Apres enregistrement du secret, efface ce fichier : rm -f " + str(out))
