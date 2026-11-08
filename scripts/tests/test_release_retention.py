import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("prune", Path(__file__).resolve().parents[1] / "prune-releases.py")
prune = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prune)


class RetentionTests(unittest.TestCase):
    @staticmethod
    def app_releases(count):
        return [
            dict(id=i, tag_name=f"v1.1.{i}", draft=False, published_at=f"2026-10-{i:02d}")
            for i in range(1, count + 1)
        ]

    def test_preserves_four_first_and_four_last_regardless_of_api_order(self):
        # GitHub may return Releases newest-first and across multiple pages.
        releases = self.app_releases(12)
        unordered = releases[::2] + releases[1::2]
        obsolete = prune.obsolete_releases(unordered)
        self.assertEqual({5, 6, 7, 8}, {r["id"] for r in obsolete})
        self.assertEqual({1, 2, 3, 4, 9, 10, 11, 12},
                         {r["id"] for r in releases} - {r["id"] for r in obsolete})

    def test_never_deletes_when_eight_or_fewer_published_app_releases_exist(self):
        for count in range(0, 9):
            self.assertEqual([], prune.obsolete_releases(self.app_releases(count)))

    def test_ninth_release_only_removes_the_middle_one(self):
        releases = self.app_releases(9)
        self.assertEqual([5], [r["id"] for r in prune.obsolete_releases(releases)])

    def test_only_empty_drafts_older_than_latest_publication_are_removed(self):
        published = dict(id=1, draft=False, published_at="2026-10-29", created_at="2026-10-28")
        old_empty = dict(id=2, draft=True, created_at="2026-10-27", assets=[])
        new_empty = dict(id=3, draft=True, created_at="2026-10-30", assets=[])
        occupied = dict(id=4, draft=True, created_at="2026-10-27", assets=[{"name": "a.apk"}])
        self.assertEqual([old_empty], prune.obsolete_releases([published, old_empty, new_empty, occupied]))

    def test_native_vault_is_protected_and_does_not_consume_app_quota(self):
        releases = self.app_releases(12)
        releases += [
            dict(id=98, tag_name="native-vault-v1", draft=False, published_at="2026-11-01"),
            dict(id=99, tag_name="native-vault-v2", draft=False, published_at="2026-11-02"),
        ]
        self.assertEqual({5, 6, 7, 8}, {r["id"] for r in prune.obsolete_releases(releases)})

    def test_current_three_public_app_releases_and_native_vault_stay_intact(self):
        releases = [
            dict(id=438054302, tag_name="v1.1.1072", draft=False, published_at="2026-11-08T05:32:55Z"),
            dict(id=438020160, tag_name="v1.1.1071", draft=False, published_at="2026-11-08T03:59:11Z"),
            dict(id=437988301, tag_name="v1.1.1068", draft=False, published_at="2026-11-08T02:36:21Z"),
            dict(id=432416339, tag_name="native-vault-v1", draft=False, published_at="2026-11-03T08:07:14Z"),
        ]
        self.assertEqual([], prune.obsolete_releases(releases))


if __name__ == "__main__":
    unittest.main()
