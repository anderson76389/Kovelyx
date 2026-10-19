#!/usr/bin/env python3
"""Crée le premier compte Kovelyx Cloud sans afficher ni sauvegarder son mot de passe.

Exécuter dans le Codespace après node scripts/setup.mjs. Aucun compte n'est
créé si l'utilisateur n'entre pas lui-même les identifiants. Les inscriptions
sont refermées même si un appel HTTP ou une vérification échoue.
"""
from __future__ import annotations

import getpass
import json
import re
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BASE = "https://kovelyx-cloud.mailpromahe.workers.dev"
TIMEOUT = 25


def api(method: str, path: str, payload=None, *, token=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urlopen(request, timeout=TIMEOUT) as response:
            body = response.read(180000)
            return json.loads(body) if body else None, response.headers
    except HTTPError as error:
        # Do not print request body, passwords, email or authentication headers.
        raise RuntimeError("Le serveur a répondu HTTP " + str(error.code) + " sur " + path) from error
    except URLError as error:
        raise RuntimeError("Le serveur Kovelyx n'est pas accessible par HTTPS") from error


def switch_registration(enabled: bool):
    action = "Ouverture temporaire" if enabled else "Fermeture"
    print(action + " des inscriptions (Cloudflare)...", flush=True)
    result = subprocess.run(
        ["npx", "wrangler", "secret", "put", "KOVELYX_REGISTRATION_OPEN"],
        input="true\n" if enabled else "false\n",
        text=True,
        capture_output=True,
        timeout=120,
        check=False,
    )
    if result.returncode:
        # Wrangler output is not relayed to avoid accidentally disclosing tokens.
        raise RuntimeError("Échec de configuration Cloudflare (" + action.lower() + ").")


def main():
    print("Kovelyx Cloud : création du premier compte utilisateur")
    health, _ = api("GET", "/health")
    if not isinstance(health, dict) or health.get("status") != "ok":
        raise RuntimeError("Le test /health ne confirme pas le serveur Kovelyx.")
    print("Serveur Kovelyx opérationnel. Rien n'est enregistré dans GitHub.")

    email = input("Adresse e-mail du compte Kovelyx : ").strip().lower()
    if len(email) > 254 or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        raise ValueError("Adresse e-mail invalide.")
    password = getpass.getpass("Mot de passe (au moins 12 caractères, masqué) : ")
    confirmation = getpass.getpass("Confirmer le mot de passe (masqué) : ")
    if len(password) < 12 or len(password) > 128 or password != confirmation:
        raise ValueError("Les mots de passe ne correspondent pas, ou longueur invalide.")
    print("Inscription sur votre serveur, sans envoyer de données à Nuvio/Supabase.")

    opened = False
    try:
        switch_registration(True)
        opened = True
        time.sleep(3)
        api("POST", "/api/auth/sign-up/email", {
            "name": email.split("@", 1)[0][:48] or "Kovelyx",
            "email": email,
            "password": password,
        })
        print("Compte créé dans votre base Cloudflare D1.")
    finally:
        if opened:
            try:
                switch_registration(False)
                print("Inscriptions publiques refermées.")
            except Exception:
                print("ATTENTION : inscriptions peut-être encore ouvertes !", file=sys.stderr)
                print("LANCEZ IMMÉDIATEMENT : printf 'false' | npx wrangler secret put KOVELYX_REGISTRATION_OPEN", file=sys.stderr)
                raise

    logged, headers = api("POST", "/api/auth/sign-in/email", {
        "email": email,
        "password": password
    })
    if not isinstance(logged, dict):
        raise RuntimeError("Connexion impossible : réponse inconnue.")
    token = headers.get("set-auth-token") or logged.get("token")
    if not isinstance(token, str) or len(token) < 16:
        raise RuntimeError("Connexion réussie mais aucun jeton Bearer valide n'est disponible.")
    me, _ = api("GET", "/v1/me", token=token)
    if not isinstance(me, dict) or me.get("email") != email:
        raise RuntimeError("L'API des comptes n'a pas confirmé le bon utilisateur.")
    print("Connexion e-mail/mot de passe vérifiée, identité confirmée.")

    profiles, _ = api("GET", "/v1/profiles", token=token)
    current = (profiles or {}).get("profiles", [])
    if not current:
        created, _ = api("POST", "/v1/profiles", {"displayName": "Principal"}, token=token)
        if not isinstance(created, dict) or not created.get("profile", {}).get("id"):
            raise RuntimeError("Création du premier profil non confirmée.")
        print("Profil principal créé sur la base D1.")
    else:
        print("Profils existants trouvés : aucune copie ajoutée.")

    print("\nSUCCÈS : compte Kovelyx créé et testé avec le vrai Worker.")
    print("Tu peux maintenant revenir dans ChatGPT pour intégrer la synchronisation des applications.")
    print("N'envoie pas ton mot de passe ni les jetons de session.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nOpération interrompue. Si besoin, vérifiez que les inscriptions sont fermées.")
        sys.exit(130)
    except Exception as error:
        print("Échec du test de compte : " + str(error), file=sys.stderr)
        sys.exit(1)
