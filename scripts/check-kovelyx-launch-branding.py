#!/usr/bin/env python3
"""Garde-fou contre le retour des libellés Test/Nuvio Debug et le logo décentré.

Le test vérifie statiquement les ressources et les positions Compose.
Le rendu optique réel doit être confirmé sur un téléphone, un PC et une TV.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID_LABELS = (
    "apps/mobile/androidApp/src/debug/res/values/strings.xml",
    "apps/desktop/androidApp/src/debug/res/values/strings.xml",
    "apps/desktop/composeApp/src/androidMain/res/values/strings.xml",
    "apps/mobile/composeApp/src/androidMain/res/values/strings.xml",
    "apps/tv/app/src/debug/res/values/strings.xml",
    "apps/tv/app/src/main/res/values/strings.xml",
    "apps/tv/app/src/main/res/values-fr/strings.xml",
)
for filename in ANDROID_LABELS:
    document = ET.parse(ROOT / filename)
    labels = [node.text.strip() for node in document.getroot().findall("string")
              if node.get("name") == "app_name" and node.text]
    assert labels == ["Kovelyx"], f"{filename}: expected Kovelyx, got {labels}"

COMPOSE_STARTUP = (
    "apps/mobile/composeApp/src/commonMain/kotlin/com/nuvio/app/AppShellComponents.kt",
    "apps/desktop/composeApp/src/commonMain/kotlin/com/nuvio/app/AppShellComponents.kt",
)
for filename in COMPOSE_STARTUP:
    code = (ROOT / filename).read_text(encoding="utf-8")
    loading = code.split("internal fun AppLoadingContent(", 1)[1].split(
        "internal fun AppLaunchOverlay(", 1
    )[0]
    assert "AppBrandWordmark(" in loading, filename
    assert "NuvioLoadingIndicator(" in loading, filename
    assert loading.count(".align(Alignment.Center)") >= 2, filename
    assert ".offset(y = 76.dp)" in loading, filename

tv = (ROOT / "apps/tv/app/src/main/java/com/nuvio/tv/ui/components/StartupSplashScreen.kt").read_text(encoding="utf-8")
startup = tv.split("        // Positionne la marque", 1)[1]
assert startup.count(".align(Alignment.Center)") >= 2
assert ".offset(y = 88.dp)" in startup

desktop_gradle = (ROOT / "apps/desktop/composeApp/build.gradle.kts").read_text(encoding="utf-8")
assert 'packageName = "Kovelyx"' in desktop_gradle
print("OK: app names Kovelyx in all Android variants; logo centered independently of loader")
