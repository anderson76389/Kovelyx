#!/usr/bin/env python3
"""Deterministic SemVer for Desktop builds and GitHub releases.

Both Windows and Linux builds on the same Git commit receive the same version.
No edit of DesktopVersion.properties or upstream code is needed for each build.
"""
import argparse
import pathlib
import re
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--commit", default="HEAD")
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[1]
    raw = subprocess.check_output(
        ["git", "show", f"{args.commit}:apps/desktop/composeApp/Configuration/DesktopVersion.properties"],
        cwd=root, text=True
    )
    match = re.search(r"(?m)^VERSION_NAME=([0-9]+)\.([0-9]+)\.([0-9]+)(?:-(alpha|beta|rc))?\s*$", raw)
    if not match:
        raise SystemExit("Unsupported desktop VERSION_NAME in source commit")
    count = int(subprocess.check_output(
        ["git", "rev-list", "--count", args.commit], cwd=root, text=True
    ).strip())
    if count <= 0:
        raise SystemExit("Git history is incomplete; use checkout fetch-depth: 0")
    major, minor, _, stage = match.groups()
    version = f"{major}.{minor}.{count}"
    if stage:
        version += f"-{stage}"
    print(version)


if __name__ == "__main__":
    main()
