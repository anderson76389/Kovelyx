#!/usr/bin/env python3
"""Google TV smoke: enter Kovelyx guest mode without credentials or writes to D1."""
from __future__ import annotations

import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def adb(*args: str, timeout: int = 60) -> str:
    result = subprocess.run(["adb", *args], text=True, capture_output=True,
                            check=True, timeout=timeout)
    return result.stdout.strip()


def ui_dump(label: str) -> ET.Element:
    remote = f"/sdcard/kovelyx-{label}.xml"
    local = Path("/tmp") / f"kovelyx-{label}.xml"
    adb("shell", "uiautomator", "dump", remote, timeout=75)
    adb("pull", remote, str(local))
    return ET.parse(local).getroot()


def guest_button(root: ET.Element):
    for node in root.iter("node"):
        text = node.get("text", "").strip().lower()
        if text in ("continue without account", "continuer sans compte",
                    "continuer en invité", "continuer comme invité"):
            return node
    return None


def alive():
    pid = adb("shell", "pidof", "com.kovelyx.tv.debug").strip()
    if not pid:
        raise AssertionError("Kovelyx TV fermé ou planté pendant le parcours invité")
    return pid


def main() -> None:
    alive()
    before = ui_dump("login")
    button = guest_button(before)
    if button is None:
        raise AssertionError("Bouton « Continuer sans compte » absent de la connexion TV")
    bounds = button.get("bounds", "")
    numbers = [int(n) for n in re.findall(r"\d+", bounds)]
    if len(numbers) != 4:
        raise AssertionError(f"Bornes bouton invité invalides : {bounds}")
    x = (numbers[0] + numbers[2]) // 2
    y = (numbers[1] + numbers[3]) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(12)
    alive()
    after = ui_dump("guest")
    if guest_button(after) is not None:
        # Guest navigation can be delayed on the first run (initial configuration).
        time.sleep(8)
        after = ui_dump("guest")
    if guest_button(after) is not None:
        raise AssertionError("La connexion TV reste affichée après « Continuer sans compte »")
    adb("shell", "screencap", "-p", "/sdcard/kovelyx-guest.png")
    adb("pull", "/sdcard/kovelyx-guest.png", "/tmp/kovelyx-guest.png")
    for key in ("KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_CENTER",
                "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_UP"):
        adb("shell", "input", "keyevent", key)
    time.sleep(3)
    alive()
    print("PASS : connexion invitée réelle sur Google TV ; écran quitté, télécommande active, sans crash.")


if __name__ == "__main__":
    main()
