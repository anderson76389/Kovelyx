#!/usr/bin/env python3
"""Retired Nuvio URL text must not override current Kovelyx account messages.

Historical hostnames in rejection tests, source provenance and binary ABI names
are intentionally outside this translation-only check.
"""
from __future__ import annotations

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOTS = (
    ROOT / "apps/tv/app/src/main/res",
    ROOT / "apps/mobile/composeApp/src/commonMain/composeResources",
    ROOT / "apps/desktop/composeApp/src/commonMain/composeResources",
)
KEYS = (
    "custom_server_error_official_active",
    "custom_server_error_official_available",
    "server_error_official_active",
    "server_error_official_available",
    "compose_auth_link_open",
)
OLD_DOMAIN = re.compile(r"(?:api[.])?nuvio[.]tv|nuvioapp[.]space", re.I)
RESOURCE = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.DOTALL)


class RetiredAccountTranslationsTest(unittest.TestCase):
    def test_account_labels_have_no_retired_server_links(self) -> None:
        inspected = 0
        for directory in SOURCE_ROOTS:
            for path in directory.glob("values*/*.xml"):
                inspected += 1
                xml = path.read_text(encoding="utf-8")
                for match in RESOURCE.finditer(xml):
                    if match.group(1) in KEYS:
                        self.assertNotRegex(
                            match.group(2), OLD_DOMAIN,
                            f"Ancien serveur de comptes dans {path.relative_to(ROOT)}:"
                            f"{match.group(1)}",
                        )
        self.assertGreater(inspected, 65, "Les ressources des trois clients doivent être inspectées")

    def test_default_fallback_has_independent_account_messages(self) -> None:
        for directory, keys in (
            (SOURCE_ROOTS[0], KEYS[:2]),
            (SOURCE_ROOTS[1], KEYS[2:]),
            (SOURCE_ROOTS[2], KEYS[2:]),
        ):
            text = (directory / "values/strings.xml").read_text(encoding="utf-8")
            resource_values = dict(RESOURCE.findall(text))
            for key in keys:
                self.assertIn(key, resource_values)
                self.assertNotRegex(resource_values[key], OLD_DOMAIN)


if __name__ == "__main__":
    unittest.main()
