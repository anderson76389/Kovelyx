#!/usr/bin/env python3
"""Fail-closed Linux release artifact verification and deterministic naming."""
import argparse
import hashlib
import pathlib
import shutil
import subprocess

def locate(root, extension, arch):
    candidates = [
        p for p in root.rglob("*" + extension)
        if p.is_file() and p.stat().st_size > 1_000_000 and
        (extension != ".AppImage" or "x86_64" in p.name)
    ]
    if len(candidates) != 1:
        raise ValueError(f"Exactly one {extension} package required, found {len(candidates)}")
    source = candidates[0]
    if extension == ".deb":
        output = subprocess.check_output(["dpkg-deb", "-f", str(source), "Architecture"], text=True).strip()
        if output != "amd64":
            raise ValueError(f"Unexpected DEB architecture: {output}")
    if extension == ".rpm":
        output = subprocess.check_output(["rpm", "-qp", "--qf", "%{ARCH}", str(source)], text=True).strip()
        if output != "x86_64":
            raise ValueError(f"Unexpected RPM architecture: {output}")
    if extension == ".AppImage":
        with source.open("rb") as stream:
            if stream.read(4) != bytes([0x7f,69,76,70]):
                raise ValueError("AppImage is not an ELF binary")
    return source

def collect(root, dest, tag):
    if not tag.startswith("v") or any(c not in "0123456789.v-abcdefghijklmnopqrstuvwxyz" for c in tag):
        raise ValueError("Invalid release tag")
    files = {
        ".deb": f"Kovelyx-Linux-amd64-{tag}.deb",
        ".rpm": f"Kovelyx-Linux-x86_64-{tag}.rpm",
        ".AppImage": f"Kovelyx-Linux-x86_64-{tag}.AppImage",
    }
    dest.mkdir(parents=True, exist_ok=True)
    for suffix, public_name in files.items():
        source = locate(root, suffix, tag)
        result = dest / public_name
        shutil.copyfile(source, result)
        with result.open("rb") as f:
            digest = hashlib.file_digest(f, "sha256").hexdigest()
        result.with_name(public_name + ".sha256").write_text(
            f"{digest}  {public_name}\n", encoding="utf-8")
        print(f"PASS: {source} -> {public_name}, sha256={digest}")
    assert len(list(dest.iterdir())) == 6

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path, required=True)
    parser.add_argument("--dest", type=pathlib.Path, required=True)
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    collect(args.source, args.dest, args.tag)
