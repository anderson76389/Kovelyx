#!/usr/bin/env bash
# Assemble ONE GitHub prerelease from Windows, Android and Android TV built at
# exactly the same main-branch commit. Never replace an existing release asset.
set -euo pipefail
: "${GH_TOKEN:?GitHub token required}"
: "${GITHUB_REPOSITORY:?GitHub repository required}"
: "${TRIGGER_NAME:?Trigger required}"
: "${TRIGGER_RUN_ID:?Trigger run ID required}"

proof_dir="$(mktemp -d)"
trap 'rm -rf "$proof_dir"' EXIT
mkdir -p dist
[[ "$TRIGGER_RUN_ID" =~ ^[0-9]+$ ]] || exit 1

run_json="$proof_dir/trigger.json"
gh api "repos/$GITHUB_REPOSITORY/actions/runs/$TRIGGER_RUN_ID" > "$run_json"
jq -e --arg repo "$GITHUB_REPOSITORY" --arg name "$TRIGGER_NAME" '
  .head_repository.full_name == $repo and .name == $name and
  .status == "completed" and .conclusion == "success"
' "$run_json" >/dev/null

source_sha=""
build_id=""
smoke_id=""
tag=""

# A workflow_run can be reported as successful when its publish/smoke job
# was skipped (e.g. an APK build was cancelled). Never attempt to download a
# non-existent proof or turn this normal skip into a red publishing workflow.
proof_name=""
case "$TRIGGER_NAME" in
  "Publier automatiquement les EXE de test Windows")
    proof_name="kovelyx-windows-release-proof" ;;
  "Tester Kovelyx TV sur émulateur Android TV")
    proof_name="kovelyx-tv-build-proof" ;;
  *)
    echo "::error::Unexpected workflow '$TRIGGER_NAME'"; exit 1 ;;
esac
gh api "repos/$GITHUB_REPOSITORY/actions/runs/$TRIGGER_RUN_ID/artifacts?per_page=100" \
  > "$proof_dir/artifacts.json"
if ! jq -e --arg proof "$proof_name" '
    [.artifacts[]? | select(.name == $proof and .expired == false)] | length == 1
' "$proof_dir/artifacts.json" >/dev/null; then
  echo "::notice::Le workflow source ne contient aucune preuve $proof_name (job ignoré ou annulé). Aucune Release modifiée."
  exit 0
fi

if [[ "$TRIGGER_NAME" == "Publier automatiquement les EXE de test Windows" ]]; then
  gh run download "$TRIGGER_RUN_ID" --repo "$GITHUB_REPOSITORY" \
    --name kovelyx-windows-release-proof --dir "$proof_dir/windows"
  proof="$proof_dir/windows/kovelyx-windows-release-proof.json"
  source_sha="$(jq -r '.source_sha // empty' "$proof")"
  tag="$(jq -r '.tag // empty' "$proof")"
elif [[ "$TRIGGER_NAME" == "Tester Kovelyx TV sur émulateur Android TV" ]]; then
  gh run download "$TRIGGER_RUN_ID" --repo "$GITHUB_REPOSITORY" \
    --name kovelyx-tv-build-proof --dir "$proof_dir/smoke"
  proof="$proof_dir/smoke/kovelyx-tv-build-proof.json"
  source_sha="$(jq -r '.source_sha // empty' "$proof")"
  build_id="$(jq -r '.build_run_id // empty' "$proof")"
  smoke_id="$TRIGGER_RUN_ID"
else
  echo "::error::Unexpected workflow '$TRIGGER_NAME'"
  exit 1
fi
[[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || { echo "::error::Untrusted source SHA"; exit 1; }
git cat-file -e "$source_sha^{commit}"
git merge-base --is-ancestor "$source_sha" HEAD || {
  echo "::error::The source commit is not in the current main history"
  exit 1
}
expected_tag="v$(python3 scripts/desktop-release-version.py --commit "$source_sha")"
[[ -z "$tag" || "$tag" == "$expected_tag" ]] || { echo "::error::Windows release tag mismatch"; exit 1; }
tag="$expected_tag"

if [[ -z "$build_id" ]]; then
  # An original push build of the EXACT same commit is required.
  gh api "repos/$GITHUB_REPOSITORY/actions/workflows/build.yml/runs?branch=main&event=push&per_page=100" \
    > "$proof_dir/android-runs.json"
  build_id="$(jq -r --arg sha "$source_sha" '
     [.workflow_runs[] | select(.head_sha == $sha and .conclusion == "success" and .status == "completed")] |
     sort_by(.run_number) | last | .id // empty
  ' "$proof_dir/android-runs.json")"
fi
if [[ ! "$build_id" =~ ^[0-9]+$ ]]; then
  echo "No successful Android build at $source_sha yet; waiting for the TV smoke event."
  exit 0
fi

gh api "repos/$GITHUB_REPOSITORY/actions/runs/$build_id" > "$proof_dir/build.json"
if ! jq -e --arg sha "$source_sha" --arg repo "$GITHUB_REPOSITORY" '
 .head_sha == $sha and .head_branch == "main" and
 .head_repository.full_name == $repo and .event == "push" and
 .name == "Compiler Kovelyx (APK et diagnostic Windows)" and
 .status == "completed" and .conclusion == "success"
' "$proof_dir/build.json" >/dev/null; then
  echo "::error::Android build does not match the main-branch source"
  exit 1
fi

gh api "repos/$GITHUB_REPOSITORY/actions/runs/$build_id/jobs?per_page=100" > "$proof_dir/jobs.json"
for job in "Android mobile - APK Full Debug" "Android TV - APK Full Debug"; do
  jq -e --arg job "$job" '[.jobs[] | select(.name == $job and .conclusion == "success")] | length == 1' \
    "$proof_dir/jobs.json" >/dev/null || { echo "::error::Unsuccessful build: $job"; exit 1; }
done

# The emulator proof links one tested TV APK to precisely this Android build.
if [[ -z "$smoke_id" ]]; then
  gh api "repos/$GITHUB_REPOSITORY/actions/workflows/android-tv-smoke.yml/runs?per_page=100" \
    > "$proof_dir/smoke-runs.json"
  while IFS= read -r candidate; do
    [[ "$candidate" =~ ^[0-9]+$ ]] || continue
    candidate_dir="$proof_dir/smoke-$candidate"
    mkdir -p "$candidate_dir"
    if gh run download "$candidate" --repo "$GITHUB_REPOSITORY" \
      --name kovelyx-tv-build-proof --dir "$candidate_dir" >/dev/null 2>&1; then
      candidate_file="$candidate_dir/kovelyx-tv-build-proof.json"
      if [[ -s "$candidate_file" ]] && jq -e --arg sha "$source_sha" --argjson build "$build_id" '
        .source_sha == $sha and .build_run_id == $build
      ' "$candidate_file" >/dev/null; then
        smoke_id="$candidate"
        break
      fi
    fi
  done < <(jq -r '[.workflow_runs[] | select(.status == "completed" and .conclusion == "success" and .event == "workflow_run")] | .[].id' "$proof_dir/smoke-runs.json")
fi
if [[ -z "$smoke_id" ]]; then
  echo "The TV emulator has not validated this exact build yet; no release changed."
  exit 0
fi

gh api "repos/$GITHUB_REPOSITORY/actions/runs/$smoke_id" > "$proof_dir/smoke-run.json"
jq -e --arg repo "$GITHUB_REPOSITORY" '
 .head_repository.full_name == $repo and .name == "Tester Kovelyx TV sur émulateur Android TV" and
 .event == "workflow_run" and .status == "completed" and .conclusion == "success"
' "$proof_dir/smoke-run.json" >/dev/null
# Verify proof even when the smoke event is the trigger.
if [[ "$TRIGGER_NAME" == "Tester Kovelyx TV sur émulateur Android TV" ]]; then
  jq -e --arg sha "$source_sha" --argjson build "$build_id" '
    .source_sha == $sha and .build_run_id == $build
  ' "$proof_dir/smoke/kovelyx-tv-build-proof.json" >/dev/null
fi

if ! gh release view "$tag" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  echo "Windows Release $tag not yet published; no partial combined release."
  exit 0
fi
gh release view "$tag" --repo "$GITHUB_REPOSITORY" --json isPrerelease,assets,body \
  > "$proof_dir/release.json"
git fetch --quiet origin "refs/tags/$tag:refs/tags/$tag"
actual_sha="$(git rev-parse "refs/tags/$tag^{commit}")"
[[ "$actual_sha" == "$source_sha" ]] || { echo "::error::Release is tagged at another commit"; exit 1; }

# Do not touch legacy Windows assets, titles, or updater semantics.
for name in "Kovelyx-Windows-Setup-$tag.exe" "Kovelyx-Windows-$tag.msi" "Kovelyx-Windows-Portable-$tag.zip"; do
  jq -e --arg name "$name" '[.assets[] | select(.name == $name and .size > 1000000)] | length == 1' \
    "$proof_dir/release.json" >/dev/null || { echo "::error::Windows package missing from $tag: $name"; exit 1; }
done

mobile_name="Kovelyx-Android-$tag.apk"
tv_name="Kovelyx-TV-$tag-universal.apk"
existing="$(jq -r --arg m "$mobile_name" --arg t "$tv_name" '
  [.assets[] | select(.name == $m or .name == ($m + ".sha256") or
      .name == $t or .name == ($t + ".sha256"))] | length
' "$proof_dir/release.json")"
if [[ "$existing" == 4 ]]; then
  echo "Already contains both Android APKs: $tag"
  exit 0
fi
# A complete showcase Release can be marked Latest (non-prerelease).
# Do not attempt a second upload, but keep partial stable releases read-only.
jq -e '.isPrerelease == true' "$proof_dir/release.json" >/dev/null || {
  echo "::error::Refusing to modify an incomplete non-prerelease release"
  exit 1
}
[[ "$existing" == 0 ]] || { echo "::error::Partial Android assets detected; refusing overwrite"; exit 1; }

mkdir -p "$proof_dir/mobile" "$proof_dir/tv"
gh run download "$build_id" --repo "$GITHUB_REPOSITORY" \
  --name kovelyx-mobile-debug --dir "$proof_dir/mobile"
gh run download "$build_id" --repo "$GITHUB_REPOSITORY" \
  --name kovelyx-tv-debug --dir "$proof_dir/tv"
mapfile -d '' mobile_apks < <(find "$proof_dir/mobile" -type f -name '*.apk' -print0)
mapfile -d '' tv_apks < <(find "$proof_dir/tv" -type f -iname '*universal*.apk' -print0)
[[ "${#mobile_apks[@]}" == 1 && "${#tv_apks[@]}" == 1 ]] || {
  echo "::error::Expected exactly one mobile APK and one TV universal APK"
  exit 1
}
tools_dir="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
for spec in "mobile|${mobile_apks[0]}|com.kovelyx.mobile.debug" "tv|${tv_apks[0]}|com.kovelyx.tv.debug"; do
  IFS='|' read -r platform apk application_id <<< "$spec"
  [[ "$(stat -c '%s' "$apk")" -gt 1000000 ]] || exit 1
  unzip -tq "$apk" >/dev/null
  "$tools_dir/apksigner" verify --verbose "$apk" >/dev/null
  "$tools_dir/aapt" dump badging "$apk" > "$proof_dir/$platform-badging"
  grep -F "package: name='$application_id'" "$proof_dir/$platform-badging" >/dev/null
  if [[ "$platform" == tv ]]; then
    grep -E 'leanback|android.software.leanback' "$proof_dir/$platform-badging" >/dev/null
  fi
done
cp -- "${mobile_apks[0]}" "dist/$mobile_name"
cp -- "${tv_apks[0]}" "dist/$tv_name"
(cd dist && sha256sum "$mobile_name" > "$mobile_name.sha256" && sha256sum "$tv_name" > "$tv_name.sha256")

# Idempotent, additive upload: never use --clobber on published assets.
gh release upload "$tag" \
  "dist/$mobile_name" "dist/$mobile_name.sha256" \
  "dist/$tv_name" "dist/$tv_name.sha256" --repo "$GITHUB_REPOSITORY"

notes_file="$proof_dir/notes.md"
jq -r '.body // ""' "$proof_dir/release.json" > "$notes_file"
cat >> "$notes_file" <<EOF

## Android et Android TV — préversions de test

- **Android** : \`$mobile_name\` (APK Debug).
- **Android TV / TV Box** : \`$tv_name\` (APK Debug universelle).
- Les deux APK proviennent du même commit que les paquets Windows : \`$source_sha\`.
- Compilation : https://github.com/$GITHUB_REPOSITORY/actions/runs/$build_id
- Test de démarrage sur émulateur TV : https://github.com/$GITHUB_REPOSITORY/actions/runs/$smoke_id
- SHA-256 fourni pour les deux APK.
- **Attention :** signatures Debug non pérennes ; mise à jour par-dessus une APK déjà installée NON garantie. Préserver/sauvegarder les données avant changement de clé. Un test sur appareil réel reste nécessaire.
- La Release demeure expérimentale ; cette publication ne certifie ni streaming, ni synchro multi-appareils.
EOF
gh release edit "$tag" --repo "$GITHUB_REPOSITORY" \
  --title "Kovelyx $tag — PRÉVERSION EXPÉRIMENTALE · Windows · Android · Android TV" \
  --notes-file "$notes_file"
echo "✅ Release commune complète : https://github.com/$GITHUB_REPOSITORY/releases/tag/$tag" >> "$GITHUB_STEP_SUMMARY"
