#!/usr/bin/env python3
"""Read-only RAM/PSS and UI-jank samples on a disposable Google TV emulator.

This is a trend diagnostic, not a proof of absence of all native memory leaks
or an estimate of RAM on a physical TV. No account or user data is sent.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import time

PACKAGE = "com.kovelyx.tv.debug"
OUTPUT = Path("/tmp/kovelyx-tv-memory")
OUTPUT.mkdir(parents=True, exist_ok=True)


def adb(*args: str, timeout: int = 45) -> str:
    return subprocess.run(
        ["adb", *args], capture_output=True, text=True,
        check=True, timeout=timeout
    ).stdout


def sample(label: str) -> dict:
    pid = adb("shell", "pidof", PACKAGE).strip()
    if not pid:
        raise RuntimeError(f"Kovelyx TV is no longer alive at {label}")
    mem = adb("shell", "dumpsys", "meminfo", PACKAGE)
    if "TOTAL" not in mem or PACKAGE not in mem:
        raise RuntimeError(f"Unable to measure PSS at {label}")
    (OUTPUT / f"{label}-meminfo.txt").write_text(mem, encoding="utf-8")
    gfx = adb("shell", "dumpsys", "gfxinfo", PACKAGE, "framestats")
    (OUTPUT / f"{label}-gfxinfo.txt").write_text(gfx, encoding="utf-8")
    match = re.search(r"^\s*TOTAL PSS:\s*([\d,]+)", mem, re.MULTILINE)
    return {"label": label, "pid": pid, "total_pss_kib": int(match.group(1).replace(",", "")) if match else None}


def main() -> None:
    metrics = [sample("startup")]
    # D-pad navigation only: no account changes, downloads, or player playback.
    # Every repeat exercises Compose focus and UI recomposition on the emulator.
    keys = ("KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_UP")
    for cycle in range(12):
        for key in keys:
            adb("shell", "input", "keyevent", key)
        if cycle in (3, 7):
            metrics.append(sample(f"cycle-{cycle + 1}"))
    time.sleep(3)
    metrics.append(sample("after-idle"))
    report = {
        "package": PACKAGE,
        "environment": "Google TV emulator; not physical TV",
        "cycle_count": 12,
        "samples": metrics,
        "warning": "Short-run PSS changes are not sufficient to prove or disprove memory leaks.",
    }
    (OUTPUT / "report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
