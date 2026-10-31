#!/usr/bin/env python3
"""Verify the visible logo is centered inside every startup wordmark asset."""
from pathlib import Path
import unittest
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]


class CenteredBrandTests(unittest.TestCase):
    def test_all_platforms_and_theme_variants_center_the_visible_ink(self):
        folders = [
            ROOT / f"apps/{platform}/composeApp/src/commonMain/composeResources/drawable"
            for platform in ("mobile", "desktop")
        ] + [ROOT / "apps/tv/app/src/main/res/drawable"]
        for folder in folders:
            images = list(folder.glob("app_logo_wordmark*.png"))
            self.assertGreaterEqual(len(images), 6, str(folder))
            for path in images:
                with self.subTest(path=str(path.relative_to(ROOT))), Image.open(path) as source:
                    image = source.convert("RGBA")
                    bounds = image.getchannel("A").getbbox()
                    self.assertIsNotNone(bounds)
                    left, top, right, bottom = bounds
                    self.assertLessEqual(abs((left + right) - image.width), 1)
                    self.assertLessEqual(abs((top + bottom) - image.height), 1)

    def test_android_system_splash_icons_fit_the_centered_safe_circle(self):
        paths = list((ROOT / "apps/mobile/composeApp/src/androidMain/res/drawable-nodpi").glob("ic_splash_logo*.png"))
        paths += [ROOT / "apps/tv/app/src/main/res/drawable/app_logo_mark.png"]
        self.assertEqual(7, len(paths))
        for path in paths:
            with self.subTest(path=str(path.relative_to(ROOT))), Image.open(path) as source:
                image = source.convert("RGBA")
                left, top, right, bottom = image.getchannel("A").getbbox()
                self.assertLessEqual(abs(left + right - image.width), 1)
                self.assertLessEqual(abs(top + bottom - image.height), 1)
                radius = ((right - left) ** 2 + (bottom - top) ** 2) ** .5 / 2
                self.assertLessEqual(radius, image.width / 3 + 1)


if __name__ == "__main__":
    unittest.main()
