import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("prune", Path(__file__).resolve().parents[1] / "prune-releases.py")
prune = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prune)


class RetentionTests(unittest.TestCase):
    def test_keeps_three_latest_publications_regardless_of_api_order(self):
        releases = [dict(id=i, tag_name=str(i), draft=False, published_at=f"2026-10-{i:02d}")
                    for i in (2, 5, 1, 4, 3)]
        releases.append(dict(id=6, tag_name="draft", draft=True, published_at=None))
        self.assertEqual([2, 1], [r["id"] for r in prune.obsolete_releases(releases)])

    def test_only_empty_drafts_older_than_latest_publication_are_removed(self):
        published = dict(id=1, draft=False, published_at="2026-10-29", created_at="2026-10-28")
        old_empty = dict(id=2, draft=True, created_at="2026-10-27", assets=[])
        new_empty = dict(id=3, draft=True, created_at="2026-10-30", assets=[])
        occupied = dict(id=4, draft=True, created_at="2026-10-27", assets=[{"name": "a.apk"}])
        self.assertEqual([old_empty], prune.obsolete_releases([published, old_empty, new_empty, occupied]))

    def test_does_not_delete_when_less_than_four_releases_exist(self):
        self.assertEqual([], prune.obsolete_releases([
            dict(id=1, draft=False, published_at="2026-10-28")]))


if __name__ == "__main__":
    unittest.main()
