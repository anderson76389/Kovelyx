#!/usr/bin/env bash
# Publish a verified Linux AppImage even when the experimental DEB/RPM job fails.
set -euo pipefail
: "${GH_TOKEN:?GH_TOKEN required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY required}"
: "${LINUX_RUN_ID:?LINUX_RUN_ID required}"
[[ "$LINUX_RUN_ID" =~ ^[0-9]+$ ]] || exit 1

gh api "repos/$GITHUB_REPOSITORY/actions/runs/$LINUX_RUN_ID" > linux-run.json
jq -e --arg repo "$GITHUB_REPOSITORY" '
  .head_repository.full_name == $repo and .head_branch == "main" and
  .name == "Kovelyx Linux - AppImage PC x86-64" and .status == "completed"
' linux-run.json >/dev/null || { echo "::error::Not a completed trusted main Linux run"; exit 1; }
source_sha="$(jq -r '.head_sha' linux-run.json)"
git merge-base --is-ancestor "$source_sha" HEAD || { echo "::error::Source SHA not in main"; exit 1; }
gh api "repos/$GITHUB_REPOSITORY/actions/runs/$LINUX_RUN_ID/jobs?per_page=100" > linux-jobs.json
jq -e '[.jobs[] | select(.name=="Compiler Kovelyx Linux AppImage" and .conclusion=="success")] | length == 1' linux-jobs.json >/dev/null || {
  echo "Linux AppImage compilation is not successful; release unchanged."
  exit 0
}
gh api "repos/$GITHUB_REPOSITORY/actions/runs/$LINUX_RUN_ID/artifacts?per_page=100" > linux-artifacts.json
jq -e '[.artifacts[] | select(.name=="Kovelyx-Linux-AppImage-experimental" and .expired==false)] | length == 1' linux-artifacts.json >/dev/null || {
  echo "::error::Missing AppImage artifact"; exit 1;
}
version="$(python3 scripts/desktop-release-version.py --commit "$source_sha")"
tag="v$version"
mkdir -p linux-assets dist
gh run download "$LINUX_RUN_ID" --repo "$GITHUB_REPOSITORY" \
  --name Kovelyx-Linux-AppImage-experimental --dir linux-assets
mapfile -d '' -t images < <(find linux-assets -type f -name 'Kovelyx-Linux-x86_64-*.AppImage' -print0)
(( ${#images[@]} == 1 )) || { echo "::error::Expected one AppImage"; exit 1; }
image_path="${images[0]}"
test "$(stat -c %s "$image_path")" -gt 1000000
test -s "$image_path.sha256"
(cd "$(dirname "$image_path")" && sha256sum --check -- "$(basename "$image_path").sha256")
cp -- "$image_path" "dist/Kovelyx-Linux-x86_64-$tag.AppImage"
(cd dist && sha256sum "Kovelyx-Linux-x86_64-$tag.AppImage" > "Kovelyx-Linux-x86_64-$tag.AppImage.sha256")

# Native packages are optional; NEVER claim they work when the job failed.
if jq -e '[.jobs[] | select(.name=="Compiler Kovelyx Linux DEB et RPM" and .conclusion=="success")] | length == 1' linux-jobs.json >/dev/null &&
  jq -e '[.artifacts[] | select(.name=="Kovelyx-Linux-Packages-experimental" and .expired==false)] | length == 1' linux-artifacts.json >/dev/null; then
  mkdir -p native-packages
  gh run download "$LINUX_RUN_ID" --repo "$GITHUB_REPOSITORY" \
    --name Kovelyx-Linux-Packages-experimental --dir native-packages
  mapfile -d '' -t debs < <(find native-packages -type f -name '*.deb' -print0)
  mapfile -d '' -t rpms < <(find native-packages -type f -name '*.rpm' -print0)
  if (( ${#debs[@]} == 1 && ${#rpms[@]} == 1 )); then
    cp -- "${debs[0]}" "dist/Kovelyx-Linux-amd64-$tag.deb"
    cp -- "${rpms[0]}" "dist/Kovelyx-Linux-x86_64-$tag.rpm"
    (cd dist && sha256sum ./*.deb > "Kovelyx-Linux-amd64-$tag.deb.sha256" &&
      sha256sum ./*.rpm > "Kovelyx-Linux-x86_64-$tag.rpm.sha256")
  fi
fi

cat > linux-notes.md <<EOF
## Kovelyx Linux $tag (test)

Linux x86-64 AppImage; optional DEB/RPM packages only when their build succeeds.
- Source run: https://github.com/$GITHUB_REPOSITORY/actions/runs/$LINUX_RUN_ID
- Source commit: $source_sha
- SHA-256 files included for each artifact.
- Experimental, unsigned. Launch and playback need testing on real Linux machines.
- Use the application update banner for a verified download when supported.
- See docs/PROVENANCE.md for third-party license information.
EOF

if ! gh release view "$tag" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  gh release create "$tag" --repo "$GITHUB_REPOSITORY" \
    --target "$source_sha" --title "Kovelyx $tag — préversion PC" \
    --prerelease --notes-file linux-notes.md dist/* || {
    # Windows may create the same tag concurrently.
    gh release view "$tag" --repo "$GITHUB_REPOSITORY" >/dev/null
    gh release upload "$tag" --repo "$GITHUB_REPOSITORY" --clobber dist/*
  }
else
  gh release view "$tag" --repo "$GITHUB_REPOSITORY" --json isPrerelease --jq '.isPrerelease' | grep -qx true || {
    echo "::error::Refusing to modify a stable release"; exit 1;
  }
  gh release upload "$tag" --repo "$GITHUB_REPOSITORY" --clobber dist/*
fi
echo "Linux AppImage released: $tag"
