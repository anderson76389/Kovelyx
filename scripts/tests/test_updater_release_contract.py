#!/usr/bin/env python3
"""Contrôle réel des Releases utilisées par le bouton Mettre à jour de Kovelyx.

Ce contrôle télécharge UNIQUEMENT les petits fichiers .sha256 depuis GitHub :
la compilation et l'installation de l'APK/EXE restent testées séparément.
"""
from __future__ import annotations

import json
import os
import re
import urllib.request

REPO = "anderson76389/Kovelyx"
API = f"https://api.github.com/repos/{REPO}/releases?per_page=100"
SEMVER = re.compile(r"^v(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc))?$")
SHA_LINE = re.compile(r"^([a-fA-F0-9]{64})\s+\*?(.+)$")
COMPACT_START = (1, 1, 1168)
DIRECT_START = (1, 1, 1169)
LEGACY_BRIDGE = "v1.1.1167"


def check_four_platform_bundles(release: dict, versions: dict) -> None:
    tag = release["tag_name"]
    names = {
        f"Kovelyx-Android-{tag}.zip",
        f"Kovelyx-TV-{tag}.zip",
        f"Kovelyx-Linux-{tag}.zip",
        f"Kovelyx-Windows-{tag}.zip",
    }
    assets = release.get("assets", [])
    assert len(assets) == 4 and {x["name"] for x in assets} == names, (
        tag, "une version doit contenir exactement quatre packs, aucun autre fichier"
    )
    for asset in assets:
        name = asset["name"]
        assert asset.get("size", 0) > 2_000_000, (tag, name, "pack vide")
        assert asset.get("state") == "uploaded", (tag, name, "upload incomplet")
        assert re.fullmatch("sha256:[a-fA-F0-9]{64}", asset.get("digest", "")), (
            tag, name, "digest GitHub manquant"
        )
        assert asset.get("browser_download_url") == (
            f"https://github.com/{REPO}/releases/download/{tag}/{name}"
        ), (tag, name, "URL de pack incorrecte")
    assert LEGACY_BRIDGE in versions, "Bridge v1.1.1167 supprimé : anciens clients bloqués"
    legacy_assets = {a["name"] for a in versions[LEGACY_BRIDGE].get("assets", [])}
    assert all(file_for_tag(LEGACY_BRIDGE) in legacy_assets
               for file_for_tag in TRACKS.values()), "Bridge incomplet"
    print(f"OK : {tag}, exactement quatre packs validés par SHA-256 GitHub ; bridge préservé")

def check_direct_installers(release: dict, versions: dict) -> None:
    tag = release["tag_name"]
    binaries = {
        f"Kovelyx-Android-{tag}.apk",
        f"Kovelyx-TV-{tag}-universal.apk",
        f"Kovelyx-Windows-Setup-{tag}.exe",
        f"Kovelyx-Linux-x86_64-{tag}.AppImage",
    }
    expected = binaries | {name + ".sha256" for name in binaries}
    assets = release.get("assets", [])
    assert len(assets) == len(expected) and {a["name"] for a in assets} == expected, (
        tag, "quatre installateurs directs et leurs 4 fichiers SHA-256 requis"
    )
    for asset in assets:
        name = asset["name"]
        assert asset.get("state") == "uploaded", (tag, name, "upload incomplet")
        assert asset.get("size", 0) > (50 if name.endswith(".sha256") else 1_000_000), (
            tag, name, "fichier absent ou tronqué"
        )
        assert re.fullmatch(r"sha256:[a-fA-F0-9]{64}", asset.get("digest", "")), (
            tag, name, "empreinte officielle absente"
        )
        assert asset.get("browser_download_url") == (
            f"https://github.com/{REPO}/releases/download/{tag}/{name}"
        ), (tag, name, "URL incorrecte")
    assert LEGACY_BRIDGE in versions, "Bridge v1.1.1167 supprimé"
    bridge = {a["name"] for a in versions[LEGACY_BRIDGE].get("assets", [])}
    assert all(file_for_tag(LEGACY_BRIDGE) in bridge
               for file_for_tag in TRACKS.values()), "Bridge rétrocompatible incomplet"
    print(f"OK : {tag}, quatre installateurs directs vérifiés (plus 4 SHA-256 rétrocompatibles)")


TRACKS = {
    "Windows installé (MSI)": lambda tag: f"Kovelyx-Windows-{tag}.msi",
    "Windows installé (EXE)": lambda tag: f"Kovelyx-Windows-Setup-{tag}.exe",
    "Windows portable (ZIP)": lambda tag: f"Kovelyx-Windows-Portable-{tag}.zip",
    "Android mobile Debug": lambda tag: f"Kovelyx-Android-{tag}.apk",
    "Android TV Debug": lambda tag: f"Kovelyx-TV-{tag}-universal.apk",
}


def read_bytes(url: str) -> bytes:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "Kovelyx-Updater-Release-Contract/1.0",
    }
    token = os.environ.get("GH_TOKEN", "").strip()
    if token and url.startswith("https://api.github.com/"):
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=25) as result:
        data = result.read(1024 * 1024)
    return data


def check_checksum(asset: dict, sha_asset: dict, tag: str) -> None:
    name = asset["name"]
    assert asset.get("size", 0) > 1024 * 1024, (tag, name, "fichier trop petit")
    url = asset.get("browser_download_url", "")
    checksum_url = sha_asset.get("browser_download_url", "")
    prefix = f"https://github.com/{REPO}/releases/download/{tag}/"
    assert url.startswith(prefix) and url.endswith("/" + name), (tag, name, url)
    assert checksum_url == url + ".sha256", (tag, name, "URL de contrôle incorrecte")
    content = read_bytes(checksum_url).decode("utf-8").strip().splitlines()
    assert len(content) == 1, (tag, name, "empreinte vide ou ambiguë")
    found = SHA_LINE.fullmatch(content[0])
    assert found and found.group(2).removeprefix("./") == name, (
        tag, name, "empreinte non associée au bon fichier"
    )


def run() -> None:
    releases = json.loads(read_bytes(API))
    candidates = [
        (tuple(map(int, match.groups()[:3])), release)
        for release in releases
        if not release.get("draft") and (match := SEMVER.fullmatch(release.get("tag_name", "")))
    ]
    assert candidates, "Aucune Release numérotée publiée"
    # Un build Windows peut être prêt avant Android/TV, et inversement.
    # L'updater choisit la Release compatible la plus récente par appareil ;
    # le contrôle CI doit reproduire cette règle, sans exiger une Release
    # multiplateforme simultanée ni accepter un checksum manquant.
    ordered = [release for _, release in sorted(candidates, reverse=True, key=lambda row: row[0])]
    latest_tag = ordered[0]["tag_name"]
    print(f"Dernière Release numérotée : {latest_tag}")
    latest_version = tuple(map(int, SEMVER.fullmatch(latest_tag).groups()[:3]))
    if latest_version >= DIRECT_START:
        check_direct_installers(
            ordered[0], {r.get("tag_name"): r for r in releases}
        )
    elif latest_version >= COMPACT_START:
        check_four_platform_bundles(
            ordered[0], {r.get("tag_name"): r for r in releases}
        )
    for label, file_for_tag in TRACKS.items():
        compatible = next(
            (
                (release, {asset["name"]: asset for asset in release.get("assets", [])})
                for release in ordered
                if file_for_tag(release["tag_name"]) in {
                    asset["name"] for asset in release.get("assets", [])
                }
            ),
            None,
        )
        assert compatible is not None, (label, "Aucune Release compatible publiée")
        release, assets = compatible
        tag = release["tag_name"]
        name = file_for_tag(tag)
        assert name + ".sha256" in assets, (tag, label, name, "SHA256 absent")
        check_checksum(assets[name], assets[name + ".sha256"], tag)
        print(f"OK : {label} ({tag}), URL GitHub et fichier SHA-256")

    # Les APK signés utilisent des tags séparés et la même clé permanente.
    # Ils sont facultatifs tant que le pipeline de signature n'a pas passé la CI.
    for prefix, filename, label in (
        ("android-v", lambda tag: f"Kovelyx-Android-{tag}-universal.apk", "Android signé"),
        ("tv-v", lambda tag: f"Kovelyx-TV-{tag}-universal.apk", "Android TV signé"),
    ):
        releases_for_track = [
            item for item in releases if item.get("tag_name", "").startswith(prefix)
            and not item.get("draft")
        ]
        if not releases_for_track:
            print(f"EN ATTENTE : {label}, aucune Release signée publiée")
            continue
        signed = max(releases_for_track, key=lambda x: x.get("created_at", ""))
        signed_tag = signed["tag_name"]
        signed_assets = {a["name"]: a for a in signed.get("assets", [])}
        name = filename(signed_tag)
        assert name in signed_assets and name + ".sha256" in signed_assets, (signed_tag, name)
        check_checksum(signed_assets[name], signed_assets[name + ".sha256"], signed_tag)
        print(f"OK : {label}, URL officielle et fichier SHA-256 ({signed_tag})")

    print("Contrat Releases en ligne valide. Installation sur appareil NON prouvée.")


if __name__ == "__main__":
    run()
