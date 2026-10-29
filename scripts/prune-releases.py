#!/usr/bin/env python3
"""Keep the three newest published Releases; Git tags and drafts are preserved."""
import json
import os
import subprocess

REPOSITORY = "anderson76389/Kovelyx"


def obsolete_releases(releases):
    published = sorted((r for r in releases if not r.get("draft") and r.get("published_at")),
                       key=lambda r: (r["published_at"], r["id"]), reverse=True)
    # Empty abandoned drafts older than an already published release are obsolete too.
    abandoned = [r for r in releases if r.get("draft") and not r.get("assets")
                 and published and r.get("created_at") and r["created_at"] < published[0]["published_at"]]
    return published[3:] + abandoned


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
