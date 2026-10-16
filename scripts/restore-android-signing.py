#!/usr/bin/env python3
"""Restore GitHub Actions Android signing environment from one encrypted Actions Secret."""
import base64
import json
import os
from pathlib import Path

raw = os.environ.get("KOVELYX_ANDROID_SIGNING_BUNDLE", "")
if not raw:
    raise SystemExit("Missing KOVELYX_ANDROID_SIGNING_BUNDLE; add it in repository Actions secrets.")
try:
    blob = json.loads(base64.b64decode(raw, validate=True))
    key_bytes = base64.b64decode(blob["keystore_base64"], validate=True)
    fields = {
        "KOVELYX_RELEASE_STORE_PASSWORD": blob["store_password"],
        "KOVELYX_RELEASE_KEY_PASSWORD": blob["key_password"],
        "KOVELYX_RELEASE_KEY_ALIAS": blob["key_alias"],
    }
except (KeyError, ValueError, TypeError) as exc:
    raise SystemExit("Invalid signing bundle format.") from exc
if not key_bytes or len(key_bytes) > 1024 * 1024:
    raise SystemExit("Invalid JKS file.")
if any(not isinstance(v, str) or not v or "\n" in v or "\r" in v for v in fields.values()):
    raise SystemExit("Invalid signing credentials.")
path = Path(os.environ["RUNNER_TEMP"]) / "kovelyx-release.jks"
fd = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "wb") as out:
    out.write(key_bytes)
with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as env_file:
    for name, value in fields.items():
        env_file.write(f"{name}={value}\n")
    env_file.write(f"KOVELYX_RELEASE_STORE_FILE={path}\n")
for key in ("KOVELYX_RELEASE_STORE_PASSWORD", "KOVELYX_RELEASE_KEY_PASSWORD"):
    print("::add-mask::" + fields[key])
print("Kovelyx signing bundle loaded.")
