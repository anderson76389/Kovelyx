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
LINUX = "Kovelyx Linux - packages et tests"
LINUX_JOB = "Compiler Kovelyx Linux x86_64 - AppImage DEB RPM"
FUNCTIONAL = "Tester les fonctions Kotlin Android TV et Android mobile"
FRENCH = "Vérifier le français et les logos Windows Android et TV"
ANDROID_JOB_NAMES = (
    {"Android TV - APK Full Debug", "Android TV - APK optimisé (identité existante)"},
    {"Android mobile - APK Full Debug", "Android mobile - APK optimisé (identité existante)"},
)


def android_jobs_succeeded(jobs):
    return all(any(job.get("name") in names and job.get("conclusion") == "success"
                   for job in jobs) for names in ANDROID_JOB_NAMES)


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



def linux_package_names(tag):
    return (
        f"Kovelyx-Linux-x86_64-{tag}.AppImage",
        f"Kovelyx-Linux-amd64-{tag}.deb",
        f"Kovelyx-Linux-x86_64-{tag}.rpm",
    )


def verify_linux_artifacts(directory, tag):
    """Fail closed on missing, unexpected, corrupt or mismatched Linux packages."""
    names = set(linux_package_names(tag))
    expected = names | {name + ".sha256" for name in names}
    files = {path.name: path for path in directory.rglob("*") if path.is_file()}
    if set(files) != expected:
        raise RuntimeError(f"Artefacts Linux incomplets ou inattendus: {set(files) ^ expected}")
    for name in names:
        path = files[name]
        if path.stat().st_size < 1_000_000:
            raise RuntimeError("Paquet Linux tronqué : " + name)
        checksum = files[name + ".sha256"].read_text(encoding="utf-8").strip()
        if checksum != f"{digest(path)}  {name}":
            raise RuntimeError("Empreinte Linux invalide : " + name)
        if name.endswith(".AppImage") and path.open("rb").read(4) != b"\x7fELF":
            raise RuntimeError("En-tête AppImage incorrect")
        if name.endswith(".deb") and path.open("rb").read(8) != b"!<arch>\n":
            raise RuntimeError("En-tête Debian incorrect")
        if name.endswith(".rpm") and path.open("rb").read(4) != b"\xed\xab\xee\xdb":
            raise RuntimeError("En-tête RPM incorrect")
    return [files[name] for name in sorted(names)]

def windows_package_names(sha, tag):
    """CI artifacts use the source SHA; public updater assets use the version tag."""
    for stem, extension in (("Kovelyx-Windows", "msi"),
                            ("Kovelyx-Windows-Setup", "exe"),
                            ("Kovelyx-Windows-Portable", "zip")):
        yield f"{stem}-{sha}.{extension}", f"{stem}-{tag}.{extension}"


def previous_signed_public_release(releases, new_tag):
    """Newest stable unified Release older than the candidate, with both official APKs."""
    import re
    version = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)(?:-(?:alpha|beta|rc))?", new_tag)
    if not version:
        raise RuntimeError("Version de publication non reconnue")
    target = tuple(int(n) for n in version.groups())
    eligible = []
    for release in releases:
        tag = release.get("tag_name", "")
        match = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)", tag)
        if release.get("draft") or release.get("prerelease") or not match:
            continue
        revision = tuple(int(n) for n in match.groups())
        if revision >= target:
            continue
        names = {asset.get("name") for asset in release.get("assets", [])}
        expected = {f"Kovelyx-Android-{tag}.apk", f"Kovelyx-TV-{tag}-universal.apk"}
        if expected.issubset(names):
            eligible.append((revision, release))
    if not eligible:
        raise RuntimeError("Aucune Release signée précédente avec mobile et TV : contrôle d'upgrade impossible")
    return max(eligible, key=lambda pair: pair[0])[1]


def verify_previous_public_signatures(root, tools, new_tag, current_signatures):
    """Never publish APKs that would force users to uninstall their installed copy.

    PR APKs intentionally have a different, untrusted build key. The trusted
    main publication runner MUST compare the APKs freshly signed with the
    permanent secret against the previous already-published official APKs.
    """
    previous = previous_signed_public_release(api("releases?per_page=100"), new_tag)
    previous_tag = previous["tag_name"]
    folder = root / "previous-release-signatures"
    folder.mkdir()
    for index, name in enumerate((
        f"Kovelyx-Android-{previous_tag}.apk",
        f"Kovelyx-TV-{previous_tag}-universal.apk",
    )):
        gh("release", "download", previous_tag, "--repo", REPOSITORY,
           "--pattern", name, "--pattern", name + ".sha256", "--dir", str(folder))
        apk = folder / name
        checksum_file = folder / (name + ".sha256")
        if not apk.is_file() or not checksum_file.is_file():
            raise RuntimeError("Ancien APK ou empreinte officielle manquante")
        lines = checksum_file.read_text(encoding="utf-8").strip().splitlines()
        if len(lines) != 1:
            raise RuntimeError("Empreinte SHA-256 historique invalide")
        import re
        match = re.fullmatch(r"([0-9a-fA-F]{64})\s+\*?(.+)", lines[0])
        if not match or match.group(2).removeprefix("./") != name:
            raise RuntimeError("Empreinte de l'ancienne APK non associée au bon fichier")
        if digest(apk).lower() != match.group(1).lower():
            raise RuntimeError("Ancienne APK endommagée : refus de publication")
        output = subprocess.check_output(
            [str(tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
        historical = [line for line in output.splitlines()
                      if "certificate SHA-256 digest:" in line]
        if not historical or historical != current_signatures[index]:
            raise RuntimeError(
                "Signature APK modifiée par rapport à la version installée : "
                "publication bloquée pour protéger les données de l'utilisateur"
            )
    print(f"PASS : certificats mobile et TV identiques à {previous_tag}. "
          "Mises à jour sans désinstallation autorisées.", flush=True)


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


def wait_for_release(tag):
    # A new draft may not appear immediately in the collection read just before creation.
    for attempt in range(13):
        release = find_release(tag)
        if release is not None:
            return release
        if attempt < 12:
            time.sleep(5)
    raise RuntimeError("Brouillon de publication introuvable après plusieurs essais")


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
        if android_jobs_succeeded(jobs):
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
    if not android_jobs_succeeded(jobs):
        raise RuntimeError("Les deux builds Android doivent être validés sur le même commit")
    deadline = time.monotonic() + 6000
    while True:
        runs = api(f"actions/runs?head_sha={sha}&per_page=100")["workflow_runs"]
        proofs = [matching_run(runs, name, sha) for name in (WINDOWS, LINUX, FUNCTIONAL, FRENCH)]
        failed = [r for r in proofs if r and r["status"] == "completed" and r["conclusion"] != "success"]
        if failed:
            raise RuntimeError("Publication bloquée : un contrôle du même commit a échoué")
        if all(r and r["status"] == "completed" and r["conclusion"] == "success" for r in proofs):
            linux_jobs = api(f"actions/runs/{proofs[1]['id']}/jobs?per_page=100")["jobs"]
            if not any(job.get("name") == LINUX_JOB and job.get("conclusion") == "success" for job in linux_jobs):
                raise RuntimeError("La construction Linux complète n'a pas réussi")
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("Publication bloquée : contrôles Windows, Linux et Android non terminés")
        print("Attente des tests et du packaging du même commit…", flush=True)
        time.sleep(30)
    windows_id = proofs[0]["id"]
    linux_id = proofs[1]["id"]
    version = subprocess.check_output(
        ["python3", "scripts/desktop-release-version.py", "--commit", sha], text=True
    ).strip()
    tag = "v" + version
    preview = version.endswith(("-alpha", "-beta", "-rc"))
    with tempfile.TemporaryDirectory(prefix="kovelyx-release-") as directory:
        root = Path(directory)
        for run, artifact, destination in (
            (build_id, "kovelyx-mobile-debug", "mobile"),
            (build_id, "kovelyx-tv-debug", "tv"),
            (windows_id, "Kovelyx-Windows-installers-experimental", "windows"),
            (linux_id, "kovelyx-linux-packages", "linux"),
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
        # GitHub PR builds are signed with an ephemeral key. The actual
        # production archive must preserve the historical permanent signer.
        verify_previous_public_signatures(root, tools, tag, certificates)
        for source_name, public_name in windows_package_names(sha, tag):
            shutil.copyfile(unique_file(root / "windows", source_name), output / public_name)
        for linux_file in verify_linux_artifacts(root / "linux", tag):
            shutil.copyfile(linux_file, output / linux_file.name)
        for path in list(output.iterdir()):
            path.with_name(path.name + ".sha256").write_text(f"{digest(path)}  {path.name}\n", encoding="utf-8")
        notes = root / "notes.md"
        notes.write_text(
            "Kovelyx pour Windows, Linux x86_64, Android et Android TV.\n\n"
            "- Linux : AppImage (distributions compatibles), DEB (Debian/Ubuntu), RPM (Fedora et dérivées).\n"
            "- Le runtime Java est inclus ; libmpv, WebKitGTK et les codecs Linux sont des dépendances système.\n"
            "- Les paquets Linux proviennent des mêmes sources Compose Desktop que Windows, avec un build natif et des tests propres.\n"
            "- Profils TV en consultation : noms, photos et fonds reçus du cloud. Modification sur Android mobile ou PC.\n"
            "- Politique de confidentialité, En savoir plus et Discord retirés de l’interface.\n"
            "- Connexions Trakt et MDBList : accès direct à la configuration si les clés manquent.\n"
            "- Mises à jour depuis la bannière en haut de l’application.\n"
            "- Logo Kovelyx centré au démarrage.\n"
            "- Corrections des textes français.\n"
            "- Synchronisation privée des photos et fonds de profil, avec cache local.\n"
            "  Cette fonction nécessite le Worker Cloudflare et la migration D1 0004_profile_media_gifs ; "
            "installer l’application seule ne met pas le serveur à jour.\n"
            "- Synchronisation des addons : URL configurée, ordre, nom et activation ; suppressions et reprise hors ligne.\n"
            "- Bibliothèque, œuvres vues et progression : scénarios de synchronisation validés entre trois sessions de test indépendantes (Windows, Android et TV) sur Worker + D1 locaux ; essais sur appareils physiques toujours nécessaires.\n"
            "- Partage manuel entre profils du même compte : choisir destinataires, extensions et catalogues, fusion sans effacer les dossiers existants ; aucun historique/PIN partagé.\n"
            "- Android TV : test réel du mode invité et de la télécommande Google TV ; correction des tests précédemment ignorés.\n"
            "- Profils protégés par PIN : nécessitent la migration D1 0005 sur le Worker Cloudflare ; un APK seul ne modifie pas la base.\n"
            "- Regroupement des envois TV, quatre requêtes de manifeste au maximum et reprise des suppressions.\n\n"
            f"Sources : `{sha}`. Paquets Windows, Linux, Android et TV testés et vérifiés sur le même commit. "
            "Les usages sur appareils physiques restent à valider.\n", encoding="utf-8")
        ensure_tag(tag, sha)
        if find_release(tag) is None:
            gh("release", "create", tag, "--repo", REPOSITORY, "--target", sha, "--draft",
               *( ["--prerelease"] if preview else [] ), "--title", f"Kovelyx {version} — Windows, Linux, Android et TV",
               "--notes-file", str(notes))
        ref = api(f"git/ref/tags/{tag}")["object"]
        if ref["type"] == "tag":
            ref = api(f"git/tags/{ref['sha']}")["object"]
        if ref["sha"] != sha:
            raise RuntimeError("Le tag existant désigne un autre commit")
        release = wait_for_release(tag)
        if not release["draft"] and not release["prerelease"]:
            raise RuntimeError("Refus de modifier une version stable")
        assets = {a["name"]: a for a in release["assets"]}
        for path in sorted(output.iterdir()):
            if path.name in assets:
                if assets[path.name].get("digest") != "sha256:" + digest(path):
                    raise RuntimeError("Un fichier publié différent existe déjà ; aucun écrasement")
            else:
                gh("release", "upload", tag, str(path), "--repo", REPOSITORY)
        gh("release", "edit", tag, "--repo", REPOSITORY, "--draft=false", f"--prerelease={str(preview).lower()}", f"--latest={str(not preview).lower()}")
        print(f"Release complète publiée : {tag}", flush=True)
        subprocess.run(["python3", "scripts/prune-releases.py"], check=True)


if __name__ == "__main__":
    main()
