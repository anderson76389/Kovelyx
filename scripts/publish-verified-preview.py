#!/usr/bin/env python3
"""Publish one complete preview from tested artifacts of one exact commit."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

REPOSITORY = "anderson76389/Kovelyx"
WINDOWS = "Kovelyx Windows - MSI et tests de packaging"
FUNCTIONAL = "Tester les fonctions Kotlin Android TV et Android mobile"
FRENCH = "Vérifier le français et les logos Windows Android et TV"


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True)


def api(path):
    return json.loads(gh("api", f"repos/{REPOSITORY}/{path}"))


def matching_run(runs, name, sha):
    matches = [r for r in runs if r.get("name") == name and r.get("head_sha") == sha
               and r.get("event") == "push"
               and r.get("head_repository", {}).get("full_name") == REPOSITORY]
    return max(matches, key=lambda r: r["run_number"], default=None)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def unique_file(root, pattern):
    files = [p for p in root.rglob(pattern) if p.is_file()]
    if len(files) != 1 or files[0].stat().st_size < 1_000_000:
        raise RuntimeError(f"Un paquet complet unique est requis : {pattern}")
    return files[0]


def windows_package_names(sha, tag):
    """CI artifacts use the source SHA; public updater assets use the version tag."""
    for stem, extension in (("Kovelyx-Windows", "msi"),
                            ("Kovelyx-Windows-Setup", "exe"),
                            ("Kovelyx-Windows-Portable", "zip")):
        yield f"{stem}-{sha}.{extension}", f"{stem}-{tag}.{extension}"


def ensure_tag(tag, sha):
    # A draft release does not guarantee that the corresponding Git ref exists.
    existing = subprocess.run(["gh", "api", f"repos/{REPOSITORY}/git/ref/tags/{tag}"],
                              text=True, capture_output=True)
    if existing.returncode != 0:
        gh("api", "--method", "POST", f"repos/{REPOSITORY}/git/refs",
           "-f", f"ref=refs/tags/{tag}", "-f", f"sha={sha}")
    ref = api(f"git/ref/tags/{tag}")["object"]
    if ref["type"] == "tag":
        ref = api(f"git/tags/{ref['sha']}")["object"]
    if ref["sha"] != sha:
        raise RuntimeError("Le tag existant désigne un autre commit")


def find_release(tag):
    # The tag endpoint returns 404 for drafts; the authenticated collection includes them.
    return next((r for r in api("releases?per_page=100") if r["tag_name"] == tag), None)


def recoverable_source(current_sha):
    allowed = {"scripts/publish-verified-preview.py", "scripts/tests/test_verified_preview.py",
               ".github/workflows/recover-verified-preview.yml", "scripts/prune-releases.py",
               "scripts/tests/test_release_retention.py"}
    for run in api("actions/runs?per_page=100")["workflow_runs"]:
        if (run.get("name") != "Compiler Kovelyx (APK et diagnostic Windows)"
                or run.get("event") != "push"
                or run.get("head_branch") != "fix/cloudflare-network-status-and-settings"
                or run.get("head_repository", {}).get("full_name") != REPOSITORY):
            continue
        source_sha = run["head_sha"]
        ancestor = subprocess.run(["git", "merge-base", "--is-ancestor", source_sha, current_sha])
        if ancestor.returncode != 0:
            continue
        changed = set(subprocess.check_output(
            ["git", "diff", "--name-only", source_sha, current_sha], text=True).splitlines())
        if not changed or not changed.issubset(allowed):
            continue
        jobs = api(f"actions/runs/{run["id"]}/jobs?per_page=100")["jobs"]
        if all(any(j["name"] == name and j["conclusion"] == "success" for j in jobs)
               for name in ("Android TV - APK Full Debug", "Android mobile - APK Full Debug")):
            return run
    return None


def main():
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY:
        raise RuntimeError("Dépôt de publication inattendu")
    sha = os.environ["GITHUB_SHA"]
    if os.environ.get("KOVELYX_RECOVER_PREVIEW") == "1":
        source = recoverable_source(sha)
        if source is None:
            print("Aucun ancien build identique à reprendre ; publication normale conservée.")
            return
        sha = source["head_sha"]
        build_id = source["id"]
    else:
        build_id = int(os.environ["GITHUB_RUN_ID"])
        source = api(f"actions/runs/{build_id}")
    if source["head_sha"] != sha or source["event"] != "push":
        raise RuntimeError("La provenance du build Android est invalide")
    jobs = api(f"actions/runs/{build_id}/jobs?per_page=100")["jobs"]
    for name in ("Android TV - APK Full Debug", "Android mobile - APK Full Debug"):
        if not any(j["name"] == name and j["conclusion"] == "success" for j in jobs):
            raise RuntimeError(f"Build Android non validé : {name}")
    deadline = time.monotonic() + 1800
    while True:
        runs = api("actions/runs?per_page=100")["workflow_runs"]
        proofs = [matching_run(runs, name, sha) for name in (WINDOWS, FUNCTIONAL, FRENCH)]
        failed = [r for r in proofs if r and r["status"] == "completed" and r["conclusion"] != "success"]
        if failed:
            raise RuntimeError("Publication bloquée : un contrôle du même commit a échoué")
        if all(r and r["status"] == "completed" and r["conclusion"] == "success" for r in proofs):
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("Publication bloquée : contrôles Windows et Android non terminés")
        print("Attente des tests et du packaging du même commit…", flush=True)
        time.sleep(30)
    windows_id = proofs[0]["id"]
    version = subprocess.check_output(
        ["python3", "scripts/desktop-release-version.py", "--commit", sha], text=True
    ).strip()
    tag = "v" + version
    if not version.endswith(("-alpha", "-beta", "-rc")):
        raise RuntimeError("Ce workflow publie uniquement des préversions")
    with tempfile.TemporaryDirectory(prefix="kovelyx-release-") as directory:
        root = Path(directory)
        for run, artifact, destination in (
            (build_id, "kovelyx-mobile-debug", "mobile"),
            (build_id, "kovelyx-tv-debug", "tv"),
            (windows_id, "Kovelyx-Windows-installers-experimental", "windows"),
        ):
            gh("run", "download", str(run), "--repo", REPOSITORY, "--name", artifact,
               "--dir", str(root / destination))
        output = root / "publication"
        output.mkdir()
        tools = sorted((Path(os.environ["ANDROID_HOME"]) / "build-tools").iterdir(),
                       key=lambda p: tuple(int(x) if x.isdigit() else 0 for x in p.name.split(".")))[-1]
        certificates = []
        for platform, package, pattern, name in (
            ("mobile", "com.kovelyx.mobile.debug", "*.apk", f"Kovelyx-Android-{tag}.apk"),
            ("tv", "com.kovelyx.tv.debug", "*universal*.apk", f"Kovelyx-TV-{tag}-universal.apk"),
        ):
            apk = unique_file(root / platform, pattern)
            certificate = subprocess.check_output(
                [str(tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
            certificates.append([line for line in certificate.splitlines() if "certificate SHA-256 digest:" in line])
            badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(apk)], text=True)
            if f"package: name='{package}'" not in badging or f"versionName='{version}'" not in badging:
                raise RuntimeError("Identité ou version de l’APK incorrecte")
            shutil.copyfile(apk, output / name)
        if not certificates[0] or certificates[0] != certificates[1]:
            raise RuntimeError("Les deux APK ne portent pas le même certificat Kovelyx")
        for source_name, public_name in windows_package_names(sha, tag):
            shutil.copyfile(unique_file(root / "windows", source_name), output / public_name)
        for path in list(output.iterdir()):
            path.with_name(path.name + ".sha256").write_text(f"{digest(path)}  {path.name}\n", encoding="utf-8")
        notes = root / "notes.md"
        notes.write_text(
            "Préversion de Kovelyx pour Windows, Android et Android TV.\n\n"
            "- Mises à jour depuis la bannière en haut de l’application.\n"
            "- Logo Kovelyx centré au démarrage.\n"
            "- Corrections des textes français.\n\n"
            f"Sources : `{sha}`. Paquets et tests automatiques vérifiés sur le même commit. "
            "Les usages sur appareils physiques restent à valider.\n", encoding="utf-8")
        ensure_tag(tag, sha)
        if find_release(tag) is None:
            gh("release", "create", tag, "--repo", REPOSITORY, "--target", sha, "--draft",
               "--prerelease", "--title", f"Kovelyx {version} — Windows, Android et TV",
               "--notes-file", str(notes))
        ref = api(f"git/ref/tags/{tag}")["object"]
        if ref["type"] == "tag":
            ref = api(f"git/tags/{ref['sha']}")["object"]
        if ref["sha"] != sha:
            raise RuntimeError("Le tag existant désigne un autre commit")
        release = find_release(tag)
        if release is None:
            raise RuntimeError("Brouillon de publication introuvable")
        if not release["prerelease"]:
            raise RuntimeError("Refus de modifier une version stable")
        assets = {a["name"]: a for a in release["assets"]}
        for path in sorted(output.iterdir()):
            if path.name in assets:
                if assets[path.name].get("digest") != "sha256:" + digest(path):
                    raise RuntimeError("Un fichier publié différent existe déjà ; aucun écrasement")
            else:
                gh("release", "upload", tag, str(path), "--repo", REPOSITORY)
        gh("release", "edit", tag, "--repo", REPOSITORY, "--draft=false", "--prerelease", "--latest=false")
        print(f"Préversion complète publiée : {tag}", flush=True)
        subprocess.run(["python3", "scripts/prune-releases.py"], check=True)


if __name__ == "__main__":
    main()
