#!/usr/bin/env python3
"""Test Kovelyx Android TV with a real D-pad, no account or production writes."""
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


def bounds(node: ET.Element) -> tuple[int, int, int, int]:
    numbers = [int(n) for n in re.findall(r"\d+", node.get("bounds", ""))]
    if len(numbers) != 4:
        raise AssertionError("Bornes d'élément de télécommande absentes")
    return tuple(numbers)


def guest_card(root: ET.Element) -> ET.Element | None:
    parents = {child: parent for parent in root.iter() for child in parent}
    for item in root.iter("node"):
        text = item.get("text", "").strip().lower()
        if text in ("continue without account", "continuer sans compte",
                    "continuer en invité", "continuer comme invité"):
            node = item
            while node in parents:
                node = parents[node]
                if node.get("clickable") == "true":
                    return node
    return None


def focused_on(root: ET.Element, target: ET.Element) -> bool:
    tx1, ty1, tx2, ty2 = bounds(target)
    for node in root.iter("node"):
        if node.get("focused") == "true":
            x1, y1, x2, y2 = bounds(node)
            cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
            if tx1 <= cx <= tx2 and ty1 <= cy <= ty2:
                return True
    return False


def alive() -> None:
    if not adb("shell", "pidof", "com.kovelyx.tv.debug"):
        raise AssertionError("Kovelyx TV arrêté ou planté sur Google TV")


def main() -> None:
    alive()
    root = ui_dump("login")
    if guest_card(root) is None:
        raise AssertionError("Bouton « Continuer sans compte » absent de l'écran TV")
    # TV Material cards react to DPAD CENTER, not necessarily to injected screen taps.
    # Locate and focus the actual clickable ancestor, never guess pixel coordinates.
    for step in range(12):
        target = guest_card(root)
        if target is not None and focused_on(root, target):
            print(f"Focus télécommande sur le bouton invité après {step} déplacements")
            break
        for node in root.iter("node"):
            if node.get("focused") == "true":
                print("Focus courant:", node.get("bounds"), node.get("text"))
        adb("shell", "input", "keyevent", "KEYCODE_DPAD_DOWN")
        time.sleep(0.6)
        root = ui_dump("focus")
    else:
        raise AssertionError("Bouton invité inatteignable avec la télécommande DPAD")

    adb("shell", "input", "keyevent", "KEYCODE_DPAD_CENTER")
    time.sleep(12)
    alive()
    after = ui_dump("guest")
    if guest_card(after) is not None:
        time.sleep(8)
        after = ui_dump("guest")
    if guest_card(after) is not None:
        raise AssertionError("Le bouton invité a été activé mais la connexion reste affichée")
    adb("shell", "screencap", "-p", "/sdcard/kovelyx-guest.png")
    adb("pull", "/sdcard/kovelyx-guest.png", "/tmp/kovelyx-guest.png")
    for key in ("KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_CENTER",
                "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_UP"):
        adb("shell", "input", "keyevent", key)
    time.sleep(3)
    alive()
    print("PASS: mode invité atteint avec DPAD+OK, application active et contrôlable.")


if __name__ == "__main__":
    main()
