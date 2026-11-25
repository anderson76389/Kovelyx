#!/usr/bin/env python3
"""Prefetch the exact Robolectric 4.16 Android runtimes; fail closed on corruption."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

BASES = (
    "https://maven-central.storage-download.googleapis.com/maven2",
    "https://repo.maven.apache.org/maven2",
)
ARTIFACT = "org/robolectric/android-all-instrumented"
# DefaultSdkProvider from tag robolectric-4.16, PREINSTRUMENTED_VERSION=7.
VERSIONS = ("14-robolectric-10818077-i7", "15-robolectric-13954326-i7", "16-robolectric-13921718-i7")


def download(url, dest):
    subprocess.run([
        "curl", "--fail", "--silent", "--show-error", "--location",
        "--retry", "2", "--retry-delay", "10", "--retry-max-time", "60",
        "--connect-timeout", "20", "--max-time", "240",
        "--proto", "=https", "--proto-redir", "=https",
        "--output", str(dest), url
    ], check=True)


def validate(jar, expected):
    if not re.fullmatch(r"[0-9a-fA-F]{128}", expected):
        raise ValueError("Malformed SHA-512")
    with jar.open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha512").hexdigest()
    if actual != expected.lower():
        raise ValueError("SHA-512 mismatch: " + jar.name)
    if not zipfile.is_zipfile(jar):
        raise ValueError("Invalid JAR: " + jar.name)


def prefetch(repo, version):
    directory = repo / "org" / "robolectric" / "android-all-instrumented" / version
    directory.mkdir(parents=True, exist_ok=True)
    name = "android-all-instrumented-" + version + ".jar"
    target = directory / name
    saved_checksum = directory / (name + ".sha512")
    # A checksum is only persisted after a verified download. GitHub cache is
    # optional and scoped to these public, versioned artifacts.
    if target.is_file() and saved_checksum.is_file():
        try:
            validate(target, saved_checksum.read_text(encoding="ascii").strip())
            print("Verified cached:", name, flush=True)
            return
        except (OSError, ValueError):
            pass
    failures = []
    for base in BASES:
        url = base + "/" + ARTIFACT + "/" + version + "/" + name
        try:
            with tempfile.TemporaryDirectory(dir=directory) as tmp:
                work = Path(tmp)
                checksum = work / "checksum"
                download(url + ".sha512", checksum)
                expected = checksum.read_text(encoding="ascii").strip()
                if target.is_file():
                    try:
                        validate(target, expected)
                        os.replace(checksum, saved_checksum)
                        print("Verified cached:", name, flush=True)
                        return
                    except ValueError:
                        pass
                candidate = work / name
                download(url, candidate)
                validate(candidate, expected)
                os.replace(candidate, target)
                os.replace(checksum, saved_checksum)
                print("Verified downloaded:", name, flush=True)
                return
        except (OSError, ValueError, subprocess.CalledProcessError) as exc:
            failures.append(f"{base}: {exc}")
    raise ValueError("No verified source for " + name + ": " + "; ".join(failures))


if __name__ == "__main__":
    for version in VERSIONS:
        prefetch(Path.home() / ".m2" / "repository", version)
