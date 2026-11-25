#!/usr/bin/env python3
"""Capture and verify a local, platform-specific Gradle/Robolectric dependency vault."""
import argparse
import hashlib
import io
import json
import os
import platform
import re
import shutil
import subprocess
import tarfile
from pathlib import Path, PurePosixPath

ARCHIVE = "dependencies.tar.gz"
CHECKSUM = "dependencies.sha256"
MANIFEST = "manifest.json"
VERSIONS = ("8.13", "9.4.1")


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def portable_platform():
    return {"system": platform.system(), "machine": platform.machine()}


def archive_filter(info):
    name = PurePosixPath(info.name).name
    if name.endswith((".lock", ".lck", ".part", ".lastUpdated", ".tmp")) or name == "gc.properties":
        return None
    if not (info.isfile() or info.isdir()):
        raise ValueError("Unsupported cache entry (link/device): " + info.name)
    info.uid = info.gid = 0
    info.uname = info.gname = ""
    return info


def capture(destination, gradle_home, maven_repo, commit, toolchain_files=None):
    # Explicit pre-downloaded installers/archives only, never the user's entire
    # SDK home, credential stores, or arbitrary directory trees.
    toolchains = {}
    for label, path in (toolchain_files or {}).items():
        path = Path(path)
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", label) or label in (".", ".."):
            raise ValueError("Invalid toolchain label: " + label)
        if path.is_symlink() or not path.is_file():
            raise ValueError("Toolchain must be a regular local archive/installer: " + str(path))
        if not path.name.lower().endswith((".zip", ".tar.gz", ".tgz", ".tar.xz", ".7z", ".msi", ".exe", ".deb", ".rpm", ".nupkg")):
            raise ValueError("Unsupported toolchain package: " + path.name)
        toolchains[label] = path
    if len({label.lower() for label in toolchains}) != len(toolchains):
        raise ValueError("Case-insensitive toolchain label collision.")
    modules = gradle_home / "caches" / "modules-2"
    dists = gradle_home / "wrapper" / "dists"
    robo = maven_repo / "org" / "robolectric"
    if not (modules / "files-2.1").is_dir() or not dists.is_dir():
        raise ValueError("Gradle cache incomplete. Build and test Kovelyx first.")
    versions = [v for v in VERSIONS if any(
        p.is_file() for p in dists.glob("gradle-" + v + "-*/**/gradle-" + v + "/bin/gradle")
    )]
    if not versions:
        raise ValueError("No supported extracted Gradle distribution found.")
    jars = sorted(robo.glob("**/android-all*.jar"))
    if not jars:
        print("WARNING: No Robolectric runtime jars. Run Android host tests before capturing.")
    manifest = {
        "format": 1, "platform": portable_platform(), "source_commit": commit,
        "gradle_versions_found": versions, "robolectric_jars": len(jars),
        "scope": "cached Gradle modules and distributions; org/robolectric Maven artifacts only",
        "offline_build_verified": False,
        "toolchain_files": {label: {"name": path.name, "sha256": digest(path)} for label, path in toolchains.items()},
    }
    destination.mkdir(parents=True, exist_ok=False)
    temporary = destination / (ARCHIVE + ".partial")
    try:
        with tarfile.open(temporary, "w:gz", compresslevel=1) as tar:
            tar.add(modules, arcname="gradle-home/caches/modules-2", filter=archive_filter)
            tar.add(dists, arcname="gradle-home/wrapper/dists", filter=archive_filter)
            if robo.is_dir():
                tar.add(robo, arcname="maven-repository/org/robolectric", filter=archive_filter)
            for label, path in toolchains.items():
                tar.add(path, arcname="toolchains/" + label + "/" + path.name, filter=archive_filter)
            payload = (json.dumps(manifest, indent=2) + "\n").encode()
            entry = tarfile.TarInfo(MANIFEST)
            entry.size = len(payload)
            entry.mode = 0o644
            tar.addfile(entry, io.BytesIO(payload))
        final = destination / ARCHIVE
        temporary.rename(final)
        (destination / CHECKSUM).write_text(digest(final) + "  " + ARCHIVE + "\n", encoding="ascii")
        print(json.dumps(verify(destination), indent=2))
    except BaseException:
        shutil.rmtree(destination)
        raise


def validate_member(member):
    path = PurePosixPath(member.name)
    if path.is_absolute() or ".." in path.parts or "\\" in member.name or ":" in member.name:
        raise ValueError("Unsafe archive path: " + member.name)
    allowed = (
        path == PurePosixPath(MANIFEST)
        or path.parts[:3] == ("gradle-home", "caches", "modules-2")
        or path.parts[:3] == ("gradle-home", "wrapper", "dists")
        or path.parts[:3] == ("maven-repository", "org", "robolectric")
        or (len(path.parts) == 3 and path.parts[0] == "toolchains"
            and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", path.parts[1]) and member.isfile())
    )
    if not allowed or not (member.isfile() or member.isdir()):
        raise ValueError("Unexpected archive entry: " + member.name)


def verify(bundle):
    archive = bundle / ARCHIVE
    expected = (bundle / CHECKSUM).read_text(encoding="ascii").split()
    if len(expected) != 2 or expected[1] != ARCHIVE or digest(archive) != expected[0]:
        raise ValueError("SHA-256 mismatch or invalid checksum file.")
    with tarfile.open(archive, "r:gz") as tar:
        seen = set()
        manifest = None
        for member in tar:
            validate_member(member)
            if member.name in seen:
                raise ValueError("Duplicate archive entry: " + member.name)
            seen.add(member.name)
            if member.name == MANIFEST:
                if not member.isfile() or member.size > 65536:
                    raise ValueError("Invalid manifest.")
                manifest = json.load(tar.extractfile(member))
        if not manifest or manifest.get("format") != 1:
            raise ValueError("Missing or unsupported manifest.")
    packages = manifest.get("toolchain_files", {})
    if not isinstance(packages, dict):
        raise ValueError("Invalid toolchain manifest.")
    for label, entry in packages.items():
        if not isinstance(label, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", label):
            raise ValueError("Invalid toolchain label in manifest.")
        if not isinstance(entry, dict):
            raise ValueError("Invalid toolchain entry.")
        name, checksum = entry.get("name"), entry.get("sha256")
        if (not isinstance(name, str) or not name or name in (".", "..")
                or "/" in name or "\\" in name or ":" in name
                or not isinstance(checksum, str) or not re.fullmatch(r"[a-f0-9]{64}", checksum)
                or ("toolchains/" + label + "/" + name) not in seen):
            raise ValueError("Invalid toolchain file in manifest.")
    return manifest


def restore(bundle, destination):
    manifest = verify(bundle)
    if manifest["platform"] != portable_platform():
        raise ValueError("Restore on the matching OS/architecture; capture a separate vault for each.")
    destination.mkdir(parents=True, exist_ok=False)
    try:
        with tarfile.open(bundle / ARCHIVE, "r:gz") as tar:
            for member in tar:
                validate_member(member)
                tar.extract(member, destination, filter="data")
        # Robolectric offline mode needs flat, Maven-named runtime JARs.
        robo = destination / "maven-repository" / "org" / "robolectric"
        flat = destination / "robolectric-jars"
        flat.mkdir()
        for jar in sorted(robo.glob("**/android-all*.jar")):
            target = flat / jar.name
            if target.exists():
                if digest(jar) != digest(target):
                    raise ValueError("Conflicting Robolectric JAR: " + jar.name)
                continue
            try:
                os.link(jar, target)
            except OSError:
                shutil.copy2(jar, target)
        for label, entry in manifest.get("toolchain_files", {}).items():
            package = destination / "toolchains" / label / entry["name"]
            if digest(package) != entry["sha256"]:
                raise ValueError("Toolchain package SHA-256 mismatch: " + label)
        print("Restored:", destination)
        print("Gradle home:", destination / "gradle-home")
        print("Robolectric offline JARs:", flat)
    except BaseException:
        shutil.rmtree(destination)
        raise


def source_commit():
    root = Path(__file__).resolve().parents[1]
    try:
        return subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    cap = sub.add_parser("capture")
    cap.add_argument("destination", type=Path)
    cap.add_argument("--gradle-home", type=Path, default=Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")))
    cap.add_argument("--maven-repo", type=Path, default=Path.home() / ".m2" / "repository")
    cap.add_argument("--toolchain-file", action="append", default=[], metavar="LABEL=PATH",
                     help="Optional offline toolchain installer/archive; repeat for each package.")
    check = sub.add_parser("verify")
    check.add_argument("bundle", type=Path)
    res = sub.add_parser("restore")
    res.add_argument("bundle", type=Path)
    res.add_argument("destination", type=Path)
    args = parser.parse_args()
    try:
        if args.action == "capture":
            toolchains = {}
            for item in args.toolchain_file:
                label, sep, path = item.partition("=")
                if not sep or not path or label in toolchains:
                    raise ValueError("Expected unique LABEL=PATH for each toolchain.")
                toolchains[label] = Path(path).expanduser()
            capture(args.destination.resolve(), args.gradle_home.resolve(), args.maven_repo.resolve(), source_commit(), toolchains)
        elif args.action == "verify":
            print(json.dumps(verify(args.bundle), indent=2))
        else:
            restore(args.bundle.resolve(), args.destination.resolve())
    except (OSError, ValueError, tarfile.TarError) as exc:
        parser.exit(1, "ERROR: " + str(exc) + "\n")


if __name__ == "__main__":
    main()
