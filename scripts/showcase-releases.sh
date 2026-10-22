#!/usr/bin/env bash
# Keep a fully packaged Kovelyx preview visible in the GitHub repository sidebar.
# Release metadata only: never touch tags, binaries or users' data.
set -euo pipefail

: "${GH_TOKEN:?The workflow needs its GitHub token}"
: "${GITHUB_REPOSITORY:?Repository is required}"

releases_file="$(mktemp)"
trap 'rm -f "$releases_file"' EXIT

gh api "repos/$GITHUB_REPOSITORY/releases?per_page=100" > "$releases_file"

# Select only published, complete, three-platform, versioned previews.
# Separate TV-only previews and half-finished Windows uploads are never promoted.
selected_tag="$(jq -r '
  def complete($r):
    ["Kovelyx-Windows-Setup-" + $r.tag_name + ".exe",
     "Kovelyx-Windows-" + $r.tag_name + ".msi",
     "Kovelyx-Windows-Portable-" + $r.tag_name + ".zip",
     "Kovelyx-Android-" + $r.tag_name + ".apk",
     "Kovelyx-TV-" + $r.tag_name + "-universal.apk"] as $files
    | all($files[]; . as $f | any($r.assets[]?; .name == $f and .size > 1000000))
      and all($files[]; . as $f | any($r.assets[]?; .name == ($f + ".sha256") and .size > 30));
  [ .[]
    | select(.draft == false and .published_at != null)
    | select(.tag_name | test("^v[0-9]+[.][0-9]+[.][0-9]+-(alpha|beta|rc)$"))
    | select(complete(.))
  ] | sort_by(.published_at) | last | .tag_name // empty
' "$releases_file")"

if [[ -z "$selected_tag" ]]; then
  echo "Aucune Release Windows/Android/TV complète ; pas de changement."
  exit 0
fi

previous_latest="$(gh api "repos/$GITHUB_REPOSITORY/releases/latest" --jq '.tag_name' 2>/dev/null || true)"
if [[ "$previous_latest" == "$selected_tag" ]]; then
  echo "La Release $selected_tag est déjà Latest."
  exit 0
fi

# The sidebar only presents a Latest release when it is non-prerelease.
# Make the experimental status explicit even though GitHub labels it Latest.
gh release edit "$selected_tag" --repo "$GITHUB_REPOSITORY" \
  --prerelease=false --latest \
  --title "Kovelyx $selected_tag — ALPHA / EXPÉRIMENTAL · Windows · Android · Android TV"

confirmed=false
for attempt in 1 2 3 4 5; do
  if gh api "repos/$GITHUB_REPOSITORY/releases/latest" \
    --jq '.tag_name' 2>/dev/null | grep -Fxq -- "$selected_tag"; then
    confirmed=true
    break
  fi
  sleep 3
done
if [[ "$confirmed" != true ]]; then
  echo "::error::La Release a été éditée mais /releases/latest ne la confirme pas."
  exit 1
fi

link="https://github.com/$GITHUB_REPOSITORY/releases/tag/$selected_tag"
echo "Dernière Release complète et expérimentale affichée dans la colonne GitHub : $link"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  printf '### Release Kovelyx visible dans la colonne GitHub\n\n' >> "$GITHUB_STEP_SUMMARY"
  printf -- '- [Kovelyx %s — ALPHA / EXPÉRIMENTAL](%s)\n' "$selected_tag" "$link" >> "$GITHUB_STEP_SUMMARY"
  printf -- '- Windows EXE/MSI/ZIP et APK Android/TV présents. **Latest ne certifie pas la stabilité.**\n' >> "$GITHUB_STEP_SUMMARY"
fi
