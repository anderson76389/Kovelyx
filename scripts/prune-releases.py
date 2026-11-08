#!/usr/bin/env python3
"""Keep 4 oldest and 4 latest app Releases, preserving the native vault and tags."""
import json
import os
import re
import subprocess

REPOSITORY = "anderson76389/Kovelyx"


def is_native_archive(release):
    return bool(re.fullmatch(r"native-vault-v[0-9]+", release.get("tag_name", "")))


def obsolete_releases(releases):
    releases = [r for r in releases if not is_native_archive(r)]
    published = sorted((r for r in releases if not r.get("draft") and r.get("published_at")),
                       key=lambda r: (r["published_at"], r["id"]), reverse=True)
    # Retain the very first 4 and the most recent 4 releases. Their ranges
    # overlap naturally until at least 9 app releases have been published.
    # The native vault is excluded from this quota and is never deleted here.
    protected_ids = {r["id"] for r in published[:4] + published[-4:]}
    obsolete = [r for r in published if r["id"] not in protected_ids]
    # Empty abandoned drafts older than an already published release are obsolete too.
    abandoned = [r for r in releases if r.get("draft") and not r.get("assets")
                 and published and r.get("created_at") and r["created_at"] < published[0]["published_at"]]
    return obsolete + abandoned


def main():
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY:
        raise RuntimeError("Dépôt de nettoyage inattendu")
    pages = json.loads(subprocess.check_output(
        ["gh", "api", "--paginate", "--slurp", f"repos/{REPOSITORY}/releases?per_page=100"], text=True))
    releases = [r for page in pages for r in page]
    obsolete = obsolete_releases(releases)
    kept = [r["tag_name"] for r in releases if not r.get("draft") and r not in obsolete]
    print("Releases conservées : " + ", ".join(kept), flush=True)
    for release in obsolete:
        # Deleting the Release endpoint removes its assets, without deleting its Git tag.
        subprocess.run(["gh", "api", "--method", "DELETE",
                        f"repos/{REPOSITORY}/releases/{release['id']}"], check=True)
        print("Ancienne Release supprimée : " + release["tag_name"], flush=True)
    print(f"{len(obsolete)} anciennes Releases supprimées.", flush=True)


if __name__ == "__main__":
    main()
