#!/usr/bin/env python3
"""Prevent false-green/never-run TV emulator smoke and Android preview checks."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
workflows = root / ".github/workflows"
build = (workflows / "build.yml").read_text(encoding="utf-8")
expected = {
    "tv": "Android TV - APK optimisé (identité existante)",
    "mobile": "Android mobile - APK optimisé (identité existante)",
}
references = {
    "tv": ["android-tv-smoke.yml", "publish-tv-preview.yml", "android-preview.yml"],
    "mobile": ["android-emulator-smoke.yml", "publish-android-debug.yml", "android-preview.yml"],
}
for platform, job_name in expected.items():
    assert f"name: {job_name}" in build, f"Job {platform} renommé sans synchronisation"
    for workflow in references[platform]:
        text = (workflows / workflow).read_text(encoding="utf-8")
        assert job_name in text, f"{workflow} : la vérification {platform} ignore le vrai job"
        assert f"Android {'TV' if platform == 'tv' else 'mobile'} - APK Full Debug" not in text, (
            f"{workflow} : ancien nom empêchant le test réel"
        )
for workflow, artifact in [
    ("android-tv-smoke.yml", "kovelyx-tv-debug"),
    ("android-emulator-smoke.yml", "kovelyx-mobile-debug"),
]:
    assert artifact in (workflows / workflow).read_text(encoding="utf-8"), (
        f"{workflow} : mauvais artefact APK"
    )
print("PASS : vrais jobs APK signés reconnus par les tests émulateur et préversions.")
