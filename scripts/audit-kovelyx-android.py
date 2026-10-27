#!/usr/bin/env python3
"""Android/Windows branding parity checks for the Kovelyx mono-repository.

These checks deliberately leave historical Kotlin package identifiers and licence
notices alone. They test shipped artwork and user-visible resources, not native
player correctness or external OAuth/API availability.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
DESKTOP = ROOT / "apps/desktop/composeApp/src/commonMain/composeResources/drawable"
MOBILE = ROOT / "apps/mobile/composeApp/src/commonMain/composeResources/drawable"
ANDROID = ROOT / "apps/mobile/composeApp/src/androidMain/res"
MANIFEST = ROOT / "apps/mobile/androidApp/src/main/AndroidManifest.xml"

ARTWORK = [
    "app_icon_original.png",
    "app_icon_arctic_blue.png",
    "app_icon_emerald.png",
    "app_icon_rose_gold.png",
    "app_icon_copper.png",
    "app_icon_graphite.png",
    "app_logo_wordmark.png",
    "app_logo_wordmark_original.png",
    "app_logo_wordmark_arctic_blue.png",
    "app_logo_wordmark_emerald.png",
    "app_logo_wordmark_rose_gold.png",
    "app_logo_wordmark_copper.png",
    "app_logo_wordmark_graphite.png",
    "app_logo_wordmark_gold.png",
]
SPLASHES = {
    "ic_splash_logo.png": "app_icon_original.png",
    "ic_splash_logo_arctic_blue.png": "app_icon_arctic_blue.png",
    "ic_splash_logo_emerald.png": "app_icon_emerald.png",
    "ic_splash_logo_rose_gold.png": "app_icon_rose_gold.png",
    "ic_splash_logo_copper.png": "app_icon_copper.png",
    "ic_splash_logo_graphite.png": "app_icon_graphite.png",
}

def verify() -> None:
    for name in ARTWORK:
        assert (MOBILE / name).read_bytes() == (DESKTOP / name).read_bytes(), f"Art différent : {name}"
    for output, source in SPLASHES.items():
        path = ANDROID / "drawable-nodpi" / output
        assert path.read_bytes() == (DESKTOP / source).read_bytes(), f"Splash différent : {output}"
        assert not path.with_suffix(".webp").exists(), f"Ancien splash présent : {output}"

    manifest = MANIFEST.read_text("utf-8")
    ET.fromstring(manifest)
    assert 'android:scheme="kovelyx"' in manifest
    assert 'android:name="com.nuvio.app.KovelyxApplication"' in manifest
    assert 'android:name="com.nuvio.app.NuvioApplication"' not in manifest
    assert 'android:scheme="nuvio"' not in manifest
    for variant in ("", "_arctic_blue", "_emerald", "_rose_gold", "_copper", "_graphite"):
        name = "ic_kovelyx" + variant
        assert f'android:icon="@drawable/{name}"' in manifest, f"Icône non utilisée : {name}"
        ET.parse(ANDROID / "drawable" / (name + ".xml"))

    app_src = (ROOT / "apps/mobile/composeApp/src/androidMain/kotlin/com/nuvio/app/KovelyxApplication.kt").read_text("utf-8")
    assert "class KovelyxApplication : Application()" in app_src
    assert not (ROOT / "apps/mobile/composeApp/src/androidMain/kotlin/com/nuvio/app/NuvioApplication.kt").exists()

    for locale in ("values", "values-fr"):
        resource = ROOT / f"apps/mobile/composeApp/src/commonMain/composeResources/{locale}/strings.xml"
        data = resource.read_text("utf-8")
        # This excludes historical resource identifiers, which are not visible on screen.
        visible = re.findall(r'<string\s+name="[^"]+"[^>]*>(.*?)</string>', data, re.S)
        assert all("Nuvio" not in value and "nuvio.tv" not in value for value in visible), (
            f"Ancienne marque visible dans {resource}: " +
            str([value[:70] for value in visible if "Nuvio" in value or "nuvio.tv" in value][:5])
        )

    gradle = (ROOT / "apps/mobile/composeApp/build.gradle.kts").read_text("utf-8")
    assert '"kovelyx://auth/trakt"' in gradle
    assert '"kovelyx://auth/simkl"' not in gradle, "Le retour OAuth SIMKL a été réintroduit"

    updater = (ROOT / "apps/mobile/composeApp/src/commonMain/kotlin/com/nuvio/app/features/updater/AppUpdaterRepository.kt").read_text("utf-8")
    assert "anderson76389/Kovelyx" in updater and "NuvioMedia" not in updater
    print(f"OK : {len(ARTWORK)} logos et icônes, {len(SPLASHES)} splash, 6 raccourcis, FR/EN et URLs Kovelyx.")

if __name__ == "__main__":
    try:
        verify()
    except (AssertionError, OSError, ET.ParseError) as exc:
        print(f"ERREUR de parité Android : {exc}", file=sys.stderr)
        sys.exit(1)
