#!/usr/bin/env python3
"""Prevent reintroducing retired archive names while keeping native contents intact."""
from __future__ import annotations

import hashlib
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
APPLICATIONS = (
    ROOT / "apps/mobile/composeApp",
    ROOT / "apps/tv/app",
    ROOT / "apps/desktop/composeApp",
)
REBRANDED_ANDROID_AARS = (
    ("lib-nuvio-engine-android-0.1.4.aar", "lib-kovelyx-engine-android-0.1.4.aar"),
    ("quickjs-kt-android-1.0.5-nuvio.aar", "quickjs-kt-android-1.0.5-kovelyx.aar"),
)


def assert_native_assets() -> None:
    for old, new in REBRANDED_ANDROID_AARS:
        fingerprints = set()
        for application in APPLICATIONS:
            old_path = application / "libs" / old
            new_path = application / "libs" / new
            assert not old_path.exists(), f"Ancienne archive toujours présente : {old_path}"
            assert new_path.is_file(), f"Archive locale manquante : {new_path}"
            with new_path.open("rb") as archive:
                fingerprints.add(hashlib.file_digest(archive, "sha256").hexdigest())
        assert len(fingerprints) == 1, f"Contenu binaire divergent entre plateformes : {new}"

    desktop = APPLICATIONS[2]
    for old, new, directory in (
        ("nuvio-engine-jvm-0.1.4.jar", "kovelyx-engine-jvm-0.1.4.jar", desktop / "libs"),
        ("nuvio-app-icon-transparent.icns", "kovelyx-app-icon-transparent.icns",
         desktop / "src/desktopMain/resources/icons"),
    ):
        assert not (directory / old).exists(), f"Ancien nom toujours présent : {old}"
        assert (directory / new).is_file(), f"Nouveau fichier absent : {new}"

    expected_build_references = {
        "apps/mobile/composeApp/build.gradle.kts": (
            "quickjs-kt-android-1.0.5-kovelyx.aar",
        ),
        "apps/tv/app/build.gradle.kts": (
            "lib-kovelyx-engine-android-0.1.4.aar",
            "quickjs-kt-android-1.0.5-kovelyx.aar",
        ),
        "apps/desktop/composeApp/build.gradle.kts": (
            "quickjs-kt-android-1.0.5-kovelyx.aar",
            "kovelyx-engine-jvm-0.1.4.jar",
            "kovelyx-app-icon-transparent.icns",
        ),
    }
    for path, needles in expected_build_references.items():
        content = (ROOT / path).read_text(encoding="utf-8")
        for needle in needles:
            assert needle in content, f"Référence Gradle absente : {path}: {needle}"
        for old, _ in REBRANDED_ANDROID_AARS:
            assert old not in content, f"Ancien nom réintroduit dans {path}"


def assert_no_retired_sources() -> None:
    # Le préparateur Linux ne doit pas réclamer de compte retiré par le backend Cloudflare.
    linux_setup = (ROOT / "apps/desktop/scripts/linux/configure-desktop-runtime.sh").read_text(encoding="utf-8")
    assert "supabase" not in linux_setup.lower(), "Script Linux lie encore une ancienne base de comptes"
    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    paths = [Path(p.decode("utf-8")) for p in tracked.split(b"\0") if p]
    # The old account-provider name may remain in history, but not in current file paths.
    assert not [str(p) for p in paths if "supabase" in str(p).lower()], (
        "Une ancienne archive de comptes est revenue dans l'arborescence actuelle"
    )
    for path in paths:
        if path == Path("README.md") or (
            path.parts and path.parts[0] == "docs" and path.suffix == ".md"
        ):
            content = (ROOT / path).read_text(encoding="utf-8", errors="replace")
            assert "supabase" not in content.lower(), (
                f"Référence à l'ancien service de comptes dans le document courant : {path}"
            )


if __name__ == "__main__":
    assert_native_assets()
    assert_no_retired_sources()
    print("OK : archives locales renommées, cohérentes et aucun ancien chemin de fournisseur")
