#!/usr/bin/env bash
# Import statique de sources publiques épinglées. Ne modifie jamais les dossiers déjà importés.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for required in git tar awk find; do
  command -v "$required" >/dev/null || { echo "Commande absente : $required" >&2; exit 1; }
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Ne jamais suivre silencieusement une branche qui a changé : SHA = source de vérité.
fetch_commit() {
  local repository="$1" sha="$2" workspace="$3"
  mkdir -p "$workspace"
  git -C "$workspace" init -q
  git -C "$workspace" remote add origin "https://github.com/${repository}.git"
  GIT_LFS_SKIP_SMUDGE=1 git -C "$workspace" fetch --quiet --no-tags --depth=1 origin "$sha"
  local actual
  actual="$(git -C "$workspace" rev-parse FETCH_HEAD)"
  [[ "$actual" == "$sha" ]] || { echo "SHA inattendu pour $repository : $actual" >&2; exit 1; }
  git -C "$workspace" -c advice.detachedHead=false checkout -q --detach FETCH_HEAD
}

copy_snapshot() {
  local project="$1" sha="$2" destination="$3"
  local scratch="$WORK/$(basename "$destination")"
  echo "==> Import de $project ($sha) dans $destination"
  if [[ -e "$destination" ]]; then
    echo "ERREUR : le dossier $destination existe déjà, protection contre l'écrasement." >&2
    exit 1
  fi

  fetch_commit "$project" "$sha" "$scratch"
  mkdir -p "$destination"
  git -C "$scratch" archive --format=tar HEAD | tar -xf - -C "$destination"
  printf '%s\n' "$project @ $sha" > "$destination/.kovelyx-source.txt"
  # Les gitlinks de sous-modules ne sont pas inclus dans git archive.
  git -C "$scratch" ls-tree HEAD | awk '$2=="commit" { print $4 " " $3 }' > "$destination/.kovelyx-gitlinks.txt"
  [[ -s "$destination/LICENSE" ]] || { echo "Licence absente pour $project" >&2; exit 1; }
}

vendor_mpvkit() {
  local destination="$1" sha="$2"
  local scratch="$WORK/mpv-$(basename "$(dirname "$destination")")"
  echo "==> MPVKit $sha dans $destination"
  fetch_commit "NuvioMedia/MPVKit" "$sha" "$scratch"
  mkdir -p "$destination"
  git -C "$scratch" archive --format=tar HEAD | tar -xf - -C "$destination"
  printf '%s\n' "NuvioMedia/MPVKit @ $sha" > "$destination/.kovelyx-source.txt"
  [[ -s "$destination/LICENSE" ]] || { echo "Licence MPVKit manquante" >&2; exit 1; }
}

commit_step() {
  local path="$1" commit_message="$2"
  # La copie de travail contient uniquement les archives publiques, jamais leurs .git ni un fichier .env privé.
  local large
  large="$(find "$path" -type f -size +95M -print -quit)"
  if [[ -n "$large" ]]; then
    echo "Blocage de sécurité : fichier > 95 Mio : $large (prévoir Git LFS/stockage adapté)." >&2
    exit 1
  fi
  git add -f -A -- "$path"
  if ! git diff --cached --quiet; then
    git -c user.name="Kovelyx Source Import" -c user.email="github-actions[bot]@users.noreply.github.com" \
      commit -m "$commit_message"
    if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
      git push origin "HEAD:${GITHUB_REF_NAME:-main}"
    fi
  fi
}

# Les quatre snapshots forment un monorepo. Un succès est commité séparément
# pour éviter de perdre les premiers imports si l'un des suivants échoue.
if [[ ! -e apps/tv ]]; then
  copy_snapshot "NuvioMedia/NuvioTV" "fe3142bf5c8357e59e0ecb274c54eb5ccb284b00" "apps/tv"
  commit_step "apps/tv" "import(tv): capture GPL-3.0 de NuvioTV"
else
  echo "apps/tv existe : aucun écrasement"
fi

if [[ ! -e apps/mobile ]]; then
  copy_snapshot "NuvioMedia/NuvioMobile" "6d435785c0e082ae3d4fbb8152e1ff5e45c2e575" "apps/mobile"
  vendor_mpvkit "apps/mobile/MPVKit" "bfeb6fcfa2bd5081a9666ae5bb42b50e6c1537e8"
  # MPVKit est maintenant un dossier standard contenant les sources, non un gitlink externe.
  rm -f apps/mobile/.gitmodules
  commit_step "apps/mobile" "import(mobile): capture GPL-3.0 et MPVKit local"
else
  echo "apps/mobile existe : aucun écrasement"
fi

if [[ ! -e apps/desktop ]]; then
  copy_snapshot "NuvioMedia/NuvioDesktop" "51963ee4d83057ef08836f4ed9ab68037e081dd3" "apps/desktop"
  vendor_mpvkit "apps/desktop/MPVKit" "bb1d0250ddcfa9d220761fcdad6011ac9fecf15e"
  rm -f apps/desktop/.gitmodules
  commit_step "apps/desktop" "import(desktop): capture GPL-3.0 et MPVKit local"
else
  echo "apps/desktop existe : aucun écrasement"
fi

if [[ ! -e server ]]; then
  copy_snapshot "NuvioMedia/self-host" "39ea2bd1bc71636127f9d797599c23b4236960c4" "server"
  commit_step "server" "import(server): capture Apache-2.0 de self-host"
else
  echo "server existe : aucun écrasement"
fi

echo "Sources importées ou déjà présentes. Voir docs/INDEPENDANCE.md pour les dépendances restantes."
