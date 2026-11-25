#!/usr/bin/env python3
"""Prefetch the exact Robolectric 4.16 Android runtimes; fail closed on corruption."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

BASE = "https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented"
# DefaultSdkProvider from tag robolectric-4.16, PREINSTRUMENTED_VERSION=7.
VERSIONS = ("14-robolectric-10818077-i7", "15-robolectric-13954326-i7", "16-robolectric-13921718-i7")


def download(url, dest):
    subprocess.run([
        "curl", "--fail", "--silent", "--show-error", "--location",
        "--retry", "5", "--retry-all-errors", "--retry-delay", "3",
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
    url = BASE + "/" + version + "/" + name
    with tempfile.TemporaryDirectory(dir=directory) as tmp:
        work = Path(tmp)
        checksum = work / "checksum"
        download(url + ".sha512", checksum)
        expected = checksum.read_text(encoding="ascii").strip()
        target = directory / name
        if target.is_file():
            try:
                validate(target, expected)
                print("Verified cached:", name, flush=True)
                return
            except ValueError:
                pass
        candidate = work / name
        download(url, candidate)
        validate(candidate, expected)
        os.replace(candidate, target)
        print("Verified downloaded:", name, flush=True)


if __name__ == "__main__":
    for version in VERSIONS:
        prefetch(Path.home() / ".m2" / "repository", version)
