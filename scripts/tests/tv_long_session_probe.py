#!/usr/bin/env python3
"""Non-destructive Android TV 2-GB emulator endurance test.

Exercises repeated DPAD navigation, HOME -> relaunch, and screen sleep/wake.
No production login, account mutation, torrent request or downloads.
PSS trend and UI frame stats are diagnostic, NOT proof of leak-free operation.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import time

PACKAGE = "com.kovelyx.tv.debug"
OUTPUT = Path("/tmp/kovelyx-tv-endurance")
ERROR_EVENT = re.compile(r"(?:am_crash|am_anr).*com\.kovelyx\.tv\.debug", re.IGNORECASE)


def adb(*args: str, timeout: int = 40) -> str:
    command = subprocess.run(
        ["adb", *args], text=True, capture_output=True, timeout=timeout
    )
    if command.returncode:
        raise RuntimeError(
            f"adb failed at {' '.join(args)}: "
            + (command.stderr or command.stdout)[-900:]
        )
    return command.stdout


def alive(label: str) -> str:
    pid = adb("shell", "pidof", PACKAGE).strip()
    if not pid:
        raise AssertionError(f"TV process absent at {label}")
    return pid


def sample(label: str) -> dict:
    pid = alive(label)
    mem = adb("shell", "dumpsys", "meminfo", PACKAGE)
    gfx = adb("shell", "dumpsys", "gfxinfo", PACKAGE, "framestats")
    match = re.search(r"^\s*TOTAL PSS:\s*([\d,]+)", mem, re.MULTILINE)
    if not match:
        raise AssertionError("Android did not report TOTAL PSS at " + label)
    (OUTPUT / f"{label}-meminfo.txt").write_text(mem, encoding="utf-8")
    (OUTPUT / f"{label}-gfxinfo.txt").write_text(gfx, encoding="utf-8")
    return {
        "label": label,
        "pid": pid,
        "pss_kib": int(match.group(1).replace(",", "")),
        "uptime_s": round(time.monotonic() - START, 1),
    }


def restore_from_home() -> None:
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.0)
    adb("shell", "monkey", "-p", PACKAGE, "-c",
        "android.intent.category.LEANBACK_LAUNCHER", "1", timeout=40)
    time.sleep(3.0)
    alive("relaunch-after-home")


def sleep_wake() -> None:
    adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
    time.sleep(2.0)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(1.0)
    restore_from_home()


def check_events() -> None:
    events = adb("logcat", "-d", "-b", "events", "-v", "brief", timeout=50)
    (OUTPUT / "events.txt").write_text(events, encoding="utf-8")
    if ERROR_EVENT.search(events):
        raise AssertionError("Android TV ANR/crash event found; see events.txt")


def run(cycles: int, pause: float) -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    adb("logcat", "-c", "-b", "events")
    report = {
        "environment": "Google TV API 34 emulator, simulated 2 GiB RAM",
        "package": PACKAGE,
        "cycles": cycles,
        "samples": [],
        "home_resumes": 0,
        "sleep_wake_resumes": 0,
        "limitations": [
            "Emulator is not a physical Android TV device.",
            "DPAD focus endurance does not test 4K/Dolby playback or external addons.",
            "PSS trend alone cannot prove or exclude a native memory leak.",
        ],
    }
    keys = ("KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_DOWN",
            "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_UP")
    try:
        alive("initial")
        report["samples"].append(sample("start"))
        for i in range(cycles):
            adb("shell", "input", "keyevent", keys[i % len(keys)])
            if i % 4 == 3:
                adb("shell", "input", "keyevent", "KEYCODE_DPAD_CENTER")
            time.sleep(pause)
            if (i + 1) % 24 == 0:
                restore_from_home()
                report["home_resumes"] += 1
            if (i + 1) % 48 == 0:
                sleep_wake()
                report["sleep_wake_resumes"] += 1
            if (i + 1) % 16 == 0:
                report["samples"].append(sample(f"cycle-{i + 1}"))
                check_events()
        report["samples"].append(sample("finished"))
        check_events()
        base = report["samples"][1]["pss_kib"] if len(report["samples"]) > 2 else report["samples"][0]["pss_kib"]
        growth = report["samples"][-1]["pss_kib"] - base
        report["pss_growth_after_warmup_kib"] = growth
        report["pss_growth_warning"] = growth > 128 * 1024
        report["passed"] = True
        print(f"PASS: {cycles} navigation cycles, {report['home_resumes']} home resumes, "
              f"{report['sleep_wake_resumes']} sleep/wake resumes; no detected app ANR/crash")
        if report["pss_growth_warning"]:
            print("WARNING: PSS grew >128 MiB after warm-up; inspect saved meminfo snapshots.")
    finally:
        report["elapsed_s"] = round(time.monotonic() - START, 1)
        (OUTPUT / "report.json").write_text(
            json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8"
        )
        try:
            (OUTPUT / "logcat.txt").write_text(
                adb("logcat", "-d", "-v", "brief", timeout=60), encoding="utf-8"
            )
        except Exception as error:
            print("Could not collect final logcat:", type(error).__name__)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--cycles", type=int, default=96)
    parser.add_argument("--pause", type=float, default=1.4)
    args = parser.parse_args()
    if not (24 <= args.cycles <= 240 and 0.2 <= args.pause <= 5):
        parser.error("cycles must be 24..240 and pause must be 0.2..5 seconds")
    START = time.monotonic()
    run(args.cycles, args.pause)
