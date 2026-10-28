#!/usr/bin/env python3
"""Validate 100% French localization of Kovelyx Desktop and Android resources.

Run directly from the repository root:
    python scripts/tests/check-french-resources.py
    python scripts/tests/check-french-resources.py --mobile

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
    / ("apps/mobile" if "--mobile" in sys.argv else "apps/desktop")
    / "composeApp/src/commonMain/composeResources"
)
SOURCE_MODULES = (
    "strings.xml",
    "autosync_strings.xml",
    "autosync_toast_strings.xml",
    "strings_mdblist.xml",
    "strings_servers.xml",
)
FORMAT_ARGUMENT = re.compile(r"%(?:[1-9]\d*\$)?[-+#0,(]*\d*(?:\.\d+)?[sdfoxXeEgGaAcCbBhHn]")


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



def read_plurals(path: Path) -> dict[str, dict[str, str]]:
    root = ET.parse(path).getroot()
    plurals = {}
    for element in root.findall("plurals"):
        name = element.attrib.get("name")
        if not name or name in plurals:
            raise ValueError(f"{path}: invalid or duplicate plural key {name}")
        items = {}
        for item in element.findall("item"):
            quantity = item.attrib.get("quantity")
            if not quantity or quantity in items:
                raise ValueError(f"{path}: duplicated plural quantity in {name}")
            items[quantity] = "".join(item.itertext())
        plurals[name] = items
    return plurals


def validate_plurals() -> int:
    original = read_plurals(RESOURCE_ROOT / "values" / "strings.xml")
    french = read_plurals(RESOURCE_ROOT / "values-fr" / "strings.xml")
    missing = original.keys() - french.keys()
    extra = french.keys() - original.keys()
    if missing or extra:
        raise ValueError(f"Plural keys: missing={sorted(missing)}; unexpected={sorted(extra)}")
    for key, quantities in original.items():
        localized = french[key]
        for quantity, english in quantities.items():
            if quantity not in localized:
                raise ValueError(f"Plural '{key}' missing '{quantity}' in French")
            source_args = sorted(FORMAT_ARGUMENT.findall(english))
            translated_args = sorted(FORMAT_ARGUMENT.findall(localized[quantity]))
            if source_args != translated_args:
                raise ValueError(f"Plural '{key}' quantity={quantity}: incompatible format arguments")
    return len(french)

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

    plural_count = validate_plurals()
    print(f"OK: {len(french)} / {len(english)} French strings covered (100%).")
    print(f"OK: {plural_count} / {plural_count} French plural groups covered (100%).")
    print("OK: XML is valid, no duplicate keys, and formatting arguments match.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
