#!/usr/bin/env python3
"""Validate 100% French localization of every Kovelyx Desktop string module.

Run directly from the repository root:
    python scripts/tests/check-french-resources.py

Preserves Compose/Android formatting markers, exact key names and XML validity.
Do not replace missing translations with raw English: translate them deliberately.
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

RESOURCE_ROOT = (
    Path(__file__).resolve().parents[2]
    / "apps/desktop/composeApp/src/commonMain/composeResources"
)
SOURCE_MODULES = (
    "strings.xml",
    "autosync_strings.xml",
    "autosync_toast_strings.xml",
    "strings_mdblist.xml",
    "strings_servers.xml",
)
FORMAT_ARGUMENT = re.compile(r"%(?:[1-9]\d*\$)?[sd]")


def read_strings(path: Path) -> dict[str, str]:
    # Parse XML properly, not with a fragile multiline regular expression.
    document = ET.parse(path)
    root = document.getroot()
    if root.tag != "resources":
        raise ValueError(f"{path}: expected a resources root element")
    names: list[str] = []
    values: dict[str, str] = {}
    for element in root.findall("string"):
        name = element.attrib.get("name")
        if not name:
            raise ValueError(f"{path}: string without a name")
        names.append(name)
        values[name] = "".join(element.itertext())
    duplicates = [n for n, c in Counter(names).items() if c > 1]
    if duplicates:
        raise ValueError(f"{path}: duplicate string keys: {', '.join(duplicates)}")
    return values


def main() -> int:
    english: dict[str, str] = {}
    for filename in SOURCE_MODULES:
        path = RESOURCE_ROOT / "values" / filename
        values = read_strings(path)
        overlap = english.keys() & values.keys()
        if overlap:
            raise ValueError(f"{filename} duplicates source keys: {sorted(overlap)}")
        english.update(values)

    french = read_strings(RESOURCE_ROOT / "values-fr" / "strings.xml")
    missing = sorted(english.keys() - french.keys())
    extra = sorted(french.keys() - english.keys())
    mismatches = []
    for key in english.keys() & french.keys():
        original = sorted(FORMAT_ARGUMENT.findall(english[key]))
        localized = sorted(FORMAT_ARGUMENT.findall(french[key]))
        if original != localized:
            mismatches.append((key, original, localized))

    if missing:
        print(f"FAIL: {len(missing)} French translations are missing:", file=sys.stderr)
        print("\n".join(missing), file=sys.stderr)
    if extra:
        print(f"FAIL: {len(extra)} French keys have no source:", file=sys.stderr)
        print("\n".join(extra), file=sys.stderr)
    if mismatches:
        print("FAIL: incompatible Kotlin/Android format markers:", file=sys.stderr)
        for key, original, localized in sorted(mismatches):
            print(f"  {key}: source={original}, french={localized}", file=sys.stderr)

    if missing or extra or mismatches:
        return 1

    print(f"OK: {len(french)} / {len(english)} French strings covered (100%).")
    print("OK: XML is valid, no duplicate keys, and formatting arguments match.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
