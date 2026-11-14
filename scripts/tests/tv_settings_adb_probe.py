#!/usr/bin/env python3
"""Non-destructive Android TV / Freebox Pop settings UI smoke test via ADB.

Runs against the installed app and a real remote-control focus tree.  Navigates
all accessible settings categories without changing account, network, or player
preferences.  The report is LOCAL: no telemetry or data upload is performed.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

CATEGORIES = (
    ("account", ("Compte", "Account"), False),
    ("profiles", ("Profils", "Profiles"), False),
    ("appearance", ("Apparence", "Appearance"), True),
    ("layout", ("Disposition", "Layout"), True),
    ("content", ("Sources de contenu", "Content sources"), True),
    ("playback", ("Lecture", "Playback"), True),
    ("integrations", ("Services et métadonnées", "Services and metadata"), True),
    ("tracking", ("Suivi", "Tracking"), True),
    ("advanced", ("Avancé", "Advanced"), True),
    ("about", ("À propos", "About"), True),
    ("debug", ("Débogage", "Debug"), False),
)
NAV_SETTINGS = ("Paramètres", "Settings")
ANDROID_KEYS = ("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_DOWN")


class Probe:
    def __init__(self, device: str | None, package: str, out: Path):
        self.device, self.package, self.out = device, package, out
        self.out.mkdir(parents=True, exist_ok=True)
        self.report = {"device": device or "adb-default", "package": package,
                       "platform": "Android TV / Google TV; physical Freebox Pop not inferred",
                       "categories": [], "memory_pss_kib": [], "errors": []}
        self.screen_width, self.screen_height = 1920, 1080

    def adb(self, *args: str, timeout: int = 80) -> str:
        cmd = ["adb"] + (["-s", self.device] if self.device else []) + list(args)
        cp = subprocess.run(cmd, check=True, capture_output=True,
                            text=True, timeout=timeout)
        return cp.stdout.strip()

    def dump(self) -> ET.Element:
        self.adb("shell", "uiautomator", "dump", "/sdcard/kv-settings-audit.xml")
        raw = self.adb("exec-out", "cat", "/sdcard/kv-settings-audit.xml")
        root = ET.fromstring(raw[raw.index("<?xml"):])
        return root

    def is_alive(self) -> bool:
        try:
            return bool(self.adb("shell", "pidof", self.package))
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            return False

    def verify_alive(self) -> None:
        if not self.is_alive():
            raise AssertionError("Kovelyx TV s'est arrêté ou a planté pendant la navigation")

    def key(self, name: str) -> None:
        self.adb("shell", "input", "keyevent", name)

    def swipe(self, forward: bool, horizontal: bool) -> None:
        w, h = self.screen_width, self.screen_height
        if horizontal:
            x1, x2 = (int(w * .82), int(w * .25)) if forward else (int(w * .25), int(w * .82))
            self.adb("shell", "input", "swipe", str(x1), str(int(h * .16)),
                     str(x2), str(int(h * .16)), "330")
        else:
            y1, y2 = (int(h * .81), int(h * .25)) if forward else (int(h * .25), int(h * .81))
            self.adb("shell", "input", "swipe", str(int(w * .095)), str(y1),
                     str(int(w * .095)), str(y2), "330")
        time.sleep(.3)

    def nodes(self, root: ET.Element):
        return list(root.iter("node"))

    def label(self, node: ET.Element) -> str:
        return " ".join((node.get("text") or "", node.get("content-desc") or "")).strip()

    def bounds(self, node: ET.Element):
        values = [int(n) for n in re.findall(r"\d+", node.get("bounds", ""))]
        return tuple(values) if len(values) == 4 else None

    def target(self, root: ET.Element, names: tuple[str, ...], rail: bool = False):
        parent = {child: ancestor for ancestor in root.iter() for child in ancestor}
        choices = []
        for node in self.nodes(root):
            if not any(self.label(node).casefold() == name.casefold() for name in names):
                continue
            anchor = node
            while anchor in parent and anchor.get("clickable") != "true":
                anchor = parent[anchor]
            if anchor.get("clickable") != "true":
                continue
            rect = self.bounds(anchor)
            if not rect:
                continue
            x1, y1, x2, y2 = rect
            if x2 <= x1 or y2 <= y1:
                continue
            score = 0
            if rail:
                # Exclude option controls in the content pane. Categories only
                # occupy the left rail or the Horizon top rail.
                if x1 > self.screen_width * .36 and y1 > self.screen_height * .28:
                    continue
                score = min(x1 / self.screen_width, y1 / self.screen_height)
            choices.append((score, anchor))
        if not choices:
            return None
        choices.sort(key=lambda item: item[0])
        return choices[0][1]

    def tap(self, node: ET.Element):
        x1, y1, x2, y2 = self.bounds(node)
        self.adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(1)
        self.verify_alive()

    def settings_page(self, root: ET.Element) -> bool:
        found = 0
        for _, names, _ in CATEGORIES:
            if any(self.label(node).casefold() in {x.casefold() for x in names}
                   for node in self.nodes(root)):
                found += 1
        return found >= 3

    def enter_settings(self):
        for attempt in range(28):
            self.verify_alive()
            page = self.dump()
            if self.settings_page(page):
                return page
            entry = self.target(page, NAV_SETTINGS)
            if entry is not None:
                self.tap(entry)
                page = self.dump()
                if self.settings_page(page):
                    return page
            # Expand the app sidebar with DPAD_LEFT, then browse it with DPAD_DOWN.
            self.key(ANDROID_KEYS[0] if attempt < 3 else ANDROID_KEYS[1])
            time.sleep(.3)
        raise AssertionError("Impossible de trouver et ouvrir Paramètres via l'interface TV")

    def memory(self, name: str):
        try:
            output = self.adb("shell", "dumpsys", "meminfo", self.package)
            m = re.search(r"TOTAL PSS:\s*(\d+)", output)
            if not m:
                m = re.search(r"^\s*TOTAL\s+(\d+)\s+", output, re.MULTILINE)
            if m:
                self.report["memory_pss_kib"].append({"section": name, "pss_kib": int(m.group(1))})
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            pass

    def audit(self, already_open: bool):
        raw_size = self.adb("shell", "wm", "size")
        m = re.search(r"(\d+)x(\d+)", raw_size)
        if m:
            self.screen_width, self.screen_height = int(m.group(1)), int(m.group(2))
        self.verify_alive()
        self.adb("logcat", "-c")
        root = self.dump() if already_open else self.enter_settings()
        if not self.settings_page(root):
            raise AssertionError("L'écran Paramètres n'est pas ouvert")
        first = next((self.target(root, names, rail=True) for _, names, _ in CATEGORIES
                      if self.target(root, names, rail=True) is not None), None)
        if first is None:
            raise AssertionError("Aucune rubrique de paramètres accessible")
        rail_centers = []
        for _, names, _ in CATEGORIES:
            anchor = self.target(root, names, rail=True)
            if anchor is not None:
                x1, y1, x2, y2 = self.bounds(anchor)
                rail_centers.append(((x1 + x2) / 2, (y1 + y2) / 2))
        if len(rail_centers) >= 2:
            xs, ys = zip(*rail_centers)
            horizontal = max(xs) - min(xs) > max(ys) - min(ys)
        else:
            rect = self.bounds(first)
            horizontal = rect[1] < self.screen_height * .22
        self.report["layout"] = "horizontal / Horizon" if horizontal else "vertical"
        # Start at the beginning of the rail; only scroll the rail, not the options.
        for _ in range(5):
            self.swipe(forward=False, horizontal=horizontal)
        self.memory("startup")
        for category, names, required in CATEGORIES:
            found = None
            for _ in range(9):
                root = self.dump()
                found = self.target(root, names, rail=True)
                if found is not None:
                    break
                self.swipe(forward=True, horizontal=horizontal)
            if found is None:
                status = "missing" if required else "not-visible / optional"
                self.report["categories"].append({"category": category, "status": status})
                if required:
                    self.report["errors"].append(f"Rubrique obligatoire inaccessible : {category}")
                continue
            self.tap(found)
            page = self.dump()
            if not self.settings_page(page):
                self.report["errors"].append(f"Sortie inattendue des paramètres : {category}")
                break
            self.report["categories"].append({"category": category, "status": "opened"})
            self.memory(category)
            # Exercise the real DPAD without triggering destructive settings actions.
            self.key("KEYCODE_DPAD_RIGHT" if not horizontal else "KEYCODE_DPAD_DOWN")
            self.key("KEYCODE_DPAD_LEFT" if not horizontal else "KEYCODE_DPAD_UP")
            self.verify_alive()
        self.adb("shell", "screencap", "-p", "/sdcard/kv-settings-audit.png")
        try:
            self.adb("pull", "/sdcard/kv-settings-audit.png", str(self.out / "settings-final.png"))
        except subprocess.CalledProcessError:
            pass
        logs = self.adb("logcat", "-d", "-v", "brief")
        issues = [line for line in logs.splitlines() if
                  ("ANR in " + self.package) in line or
                  ("Process: " + self.package) in line and "FATAL" in line]
        if issues:
            self.report["errors"].append("Crash/ANR détecté sur Kovelyx TV")
        if self.report["errors"]:
            raise AssertionError("; ".join(self.report["errors"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", help="Numéro/serial ADB, par ex. IP:5555 de la Freebox Pop")
    parser.add_argument("--package", default="com.kovelyx.tv.debug")
    parser.add_argument("--settings-open", action="store_true",
                        help="Le panneau Paramètres est déjà ouvert sur la télévision")
    parser.add_argument("--output", default="/tmp/kovelyx-tv-settings-audit")
    args = parser.parse_args()
    probe = Probe(args.device, args.package, Path(args.output))
    try:
        probe.audit(args.settings_open)
    except Exception as exc:
        probe.report["errors"].append(str(exc))
    finally:
        (probe.out / "report.json").write_text(
            json.dumps(probe.report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if probe.report["errors"]:
        print("FAIL: " + "; ".join(probe.report["errors"]), file=sys.stderr)
        return 1
    print("PASS: rubriques TV accessibles, navigation DPAD sans crash; paramètres non modifiés")
    print("Rapport local : " + str(probe.out / "report.json"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
