#!/usr/bin/env python3
"""Preserve tracked sources/binaries and verified MPV; never include local secrets."""
import hashlib
import json
import re
from pathlib import Path
import subprocess
import sys
import zipfile

MPV_PATH = "apps/desktop/composeApp/src/desktopMain/native/windows/runtime/libmpv-2.dll"
MPV_SHA = "07c68bb211f23a218ded0a36eb12207dc3aeb44e5318ffca6ce9dcc7c3173906"
MPV_SIZE = 115320296


def sha(data):
    return hashlib.sha256(data).hexdigest()


def create(output):
    root = Path(__file__).resolve().parents[1]
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    mpv = (root / MPV_PATH).read_bytes()
    if len(mpv) != MPV_SIZE or sha(mpv) != MPV_SHA:
        raise RuntimeError("MPV archive refused: unexpected size or SHA-256")
    sources = output / "Kovelyx-source-and-native-snapshot.zip"
    subprocess.run(["git", "-c", "core.autocrlf=false", "-c", "core.eol=lf", "archive", "--format=zip", "--output", str(sources), head], cwd=root, check=True)
    manifest = {"source_commit": head, "source_archive": sources.name, "files": [],
                "limitation": "Binary preservation is not proof of exact source reproducibility. No private cloud data or signing secrets included."}
    entries = subprocess.check_output(["git", "ls-tree", "-r", "-z", head], cwd=root).split(b"\0")
    tracked = {record.split(b"\t", 1)[1].decode(): record.split(b"\t", 1)[0].split()[2].decode() for record in entries if record}
    with zipfile.ZipFile(sources) as archive:
        for item in archive.infolist():
            if item.is_dir():
                continue
            data = archive.read(item)
            # Verify every archived tracked byte, not just native extensions.
            expected = tracked[item.filename]
            actual = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
            if expected != actual:
                # git archive smudges LFS objects; preserve their actual bytes,
                # verified against the pointer SHA-256 rather than the pointer blob.
                pointer = subprocess.check_output(["git", "cat-file", "blob", expected], cwd=root)
                match = re.fullmatch(rb"version https://git-lfs.github.com/spec/v1\noid sha256:([a-f0-9]{64})\nsize ([0-9]+)\n", pointer)
                if not match or sha(data) != match[1].decode() or len(data) != int(match[2]):
                    raise RuntimeError(f"Source archive differs from Git/LFS object: {item.filename}")
            manifest["files"].append({"path": item.filename, "size": len(data), "sha256": sha(data)})
    runtime = output / "Kovelyx-native-windows-runtime.zip"
    with zipfile.ZipFile(runtime, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("native/windows/libmpv-2.dll", mpv)
        archive.writestr("PROVENANCE.txt", f"Preserved from Kovelyx Releases. SHA256 {MPV_SHA}. Size {MPV_SIZE}. Not a source rebuild.\n")
    with zipfile.ZipFile(runtime) as archive:
        if sha(archive.read("native/windows/libmpv-2.dll")) != MPV_SHA:
            raise RuntimeError("Runtime archive failed read-back validation")
    manifest["mpv"] = {"size": len(mpv), "sha256": MPV_SHA}
    (output / "native-vault-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    for path in [sources, runtime, output / "native-vault-manifest.json"]:
        path.with_name(path.name + ".sha256").write_text(f"{sha(path.read_bytes())}  {path.name}\n", encoding="ascii")
    print(f"Verified {len(manifest['files'])} tracked files and the separate MPV runtime")


if __name__ == "__main__":
    create(sys.argv[1])
