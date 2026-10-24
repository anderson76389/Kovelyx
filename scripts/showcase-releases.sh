#!/usr/bin/env bash
# Keep alpha/beta/rc releases correctly classified; expose the latest complete preview in the job summary.
# Metadata only: never touch tags, binaries or users' data.
set -euo pipefail

: "${GH_TOKEN:?The workflow needs its GitHub token}"
: "${GITHUB_REPOSITORY:?Repository is required}"

releases_file="$(mktemp)"
trap 'rm -f "$releases_file"' EXIT

gh api "repos/$GITHUB_REPOSITORY/releases?per_page=100" > "$releases_file"

# Franciser les seules descriptions Windows historiques en anglais.
# Aucun changement de tag, binaire, empreinte ou texte Android/TV déjà français.
mapfile -t anciennes_notes_anglaises < <(jq -r '
  .[]
  | select(.draft == false and .published_at != null)
  | select(
      (.tag_name | test("^v[0-9]+[.][0-9]+[.][0-9]+-(alpha|beta|rc)$"))
      or (.tag_name | test("^(android|tv)-preview-[0-9a-f]{12}$"))
    )
  | select(
      ((.body // "") | startswith("## Kovelyx Windows — preview "))
      or ((.name // "") | endswith(" — test"))
      or ((.name // "") | startswith("Kovelyx Android experimental - "))
      or ((.body // "") | contains("\nBuild : "))
      or ((.body // "") | contains("\nBuild TV : "))
    )
  | .tag_name
' "$releases_file")

for ancienne_version in "${anciennes_notes_anglaises[@]}"; do
  meta="$(mktemp)"
  notes="$(mktemp)"
  titre="$(mktemp)"
  jq -c --arg tag "$ancienne_version" '.[] | select(.tag_name == $tag) | {tag_name,name,body}' "$releases_file" > "$meta"
  python3 scripts/localize_release_metadata.py "$meta" "$notes" "$titre"
  [[ -s "$notes" && -s "$titre" ]] || {
    echo "::error::La francisation de $ancienne_version a produit un fichier vide."
    rm -f "$meta" "$notes" "$titre"
    exit 1
  }
  gh release edit "$ancienne_version" --repo "$GITHUB_REPOSITORY" --title "$(cat "$titre")" --notes-file "$notes"
  echo "Notes GitHub francisées : $ancienne_version"
  rm -f "$meta" "$notes" "$titre"
done

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

# Experimental releases must remain GitHub PRE-RELEASES. Never promote an
# alpha to stable solely to obtain GitHub's "Latest" sidebar badge.
# Repair older misclassified alpha/beta/rc releases without touching binaries,
# tags, descriptions or publication dates.
mapfile -t misclassified_tags < <(jq -r '
  .[] | select(.draft == false and .published_at != null and .prerelease == false)
  | select(.tag_name | test("^v[0-9]+[.][0-9]+[.][0-9]+-(alpha|beta|rc)([.]|$)"))
  | .tag_name
' "$releases_file")
for preview_tag in "${misclassified_tags[@]}"; do
  echo "Correction du statut de préversion : $preview_tag"
  gh release edit "$preview_tag" --repo "$GITHUB_REPOSITORY" --prerelease
  gh release view "$preview_tag" --repo "$GITHUB_REPOSITORY" \
    --json isPrerelease --jq '.isPrerelease' | grep -qx true || {
      echo "::error::Préversion GitHub non confirmée pour $preview_tag"
      exit 1
    }
done

link="https://github.com/$GITHUB_REPOSITORY/releases/tag/$selected_tag"
echo "Dernière préversion complète (Windows, Android, TV) : $link"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  printf '### Préversion Kovelyx complète\n\n' >> "$GITHUB_STEP_SUMMARY"
  printf -- '- [Kovelyx %s — ALPHA / EXPÉRIMENTAL](%s)\n' "$selected_tag" "$link" >> "$GITHUB_STEP_SUMMARY"
  printf -- '- Chaque version alpha/beta/rc conserve le statut **Pre-release**. Utiliser le lien Releases du README au lieu du badge Latest réservé aux versions stables.\n' >> "$GITHUB_STEP_SUMMARY"
fi
