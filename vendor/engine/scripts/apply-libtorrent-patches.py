#!/usr/bin/env python3
"""Apply the complete pinned patch set transactionally; never trust a sentinel."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile


def canonical(data):
    return data.replace(b"\r\n", b"\n")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def apply(source):
    source = Path(source).resolve()
    cmake = Path(__file__).resolve().parents[1] / "cmake"
    manifest = json.loads((cmake / "libtorrent-patches.json").read_text())
    contents = {}
    states = set()
    for name, hashes in manifest["files"].items():
        content = canonical((source / name).read_bytes())
        actual = digest(content)
        state = next((key for key, value in hashes.items() if value == actual), None)
        if state is None:
            raise RuntimeError(f"Unrecognized pinned source: {name} ({actual})")
        states.add(state)
        contents[name] = content
    if len(states) != 1:
        raise RuntimeError("Partially patched libtorrent; refusing to alter the source")
    if states == {"patched"}:
        print("Pinned libtorrent: all 11 patched file hashes verified")
        return
    # A failed patch must not leave a partly patched FetchContent cache.
    with tempfile.TemporaryDirectory(prefix="kovelyx-libtorrent-") as directory:
        temporary = Path(directory)
        for name, content in contents.items():
            target = temporary / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
        env = dict(os.environ, GIT_CEILING_DIRECTORIES=str(temporary.parent))
        for name in manifest["patches"]:
            result = subprocess.run(
                ["git", "-c", "core.autocrlf=false", "apply", "--unidiff-zero",
                 str(cmake / "patches" / name)],
                cwd=temporary, env=env, capture_output=True, text=True,
            )
            if result.returncode:
                raise RuntimeError(f"Patch {name}: {result.stderr.strip()}")
        patched = {}
        for name, hashes in manifest["files"].items():
            content = (temporary / name).read_bytes()
            if digest(content) != hashes["patched"]:
                raise RuntimeError(f"Unexpected patched output: {name}")
            patched[name] = content
        for name, content in patched.items():
            (source / name).write_bytes(content)
    print("Pinned libtorrent: 8 patches applied; all 11 output hashes verified")


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise RuntimeError("usage: apply-libtorrent-patches.py SOURCE_DIRECTORY")
        apply(sys.argv[1])
    except (OSError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
