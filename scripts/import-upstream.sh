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
  # Les commits MPVKit épinglés n'ont pas de LICENSE dans leur propre arbre.
  # On copie le texte LGPL-3.0 du dépôt public actuel, en indiquant clairement
  # que son applicabilité au vieux commit doit être confirmée avant redistribution.
  if [[ ! -s "$destination/LICENSE" ]]; then
    local license_checkout="$WORK/mpv-license-$(basename "$(dirname "$destination")")"
    fetch_commit "NuvioMedia/MPVKit" "f5d1f0e3e9d7341d922162f8a590b16ac7eefc36" "$license_checkout"
    git -C "$license_checkout" show HEAD:LICENSE > "$destination/LICENSE"
    cat > "$destination/LICENCE_ORIGINE.txt" <<'NOTICE'
Le commit MPVKit épinglé ne contenait pas de fichier LICENSE.
Le texte de LGPL-3.0 présent ici a été copié depuis le commit public
f5d1f0e3e9d7341d922162f8a590b16ac7eefc36 (branche principale MPVKit).
Cette copie documentaire ne suffit PAS, à elle seule, à prouver que le même
contrat de licence couvrait le commit historique. Vérification requise
avant distribution de binaires.
NOTICE
  fi
  [[ -s "$destination/LICENSE" ]] || { echo "Texte de licence MPVKit introuvable" >&2; exit 1; }
}

commit_step() {
  local path="$1" commit_message="$2"
  # La copie de travail contient uniquement les archives publiques, jamais leurs .git ni un fichier .env privé.
  # GitHub refuse les fichiers Git ordinaires dépassant 100 Mo.
  # Ne surtout pas pousser les gros binaires sans validation de leur licence.
  # Inventorier précisément chaque fichier exclu pour le restaurer plus tard
  # via un stockage Kovelyx vérifié (sha256 + instructions de récupération).
  local manifest="$path/.kovelyx-binaries-a-sauvegarder.txt"
  local oversized=0
  while IFS= read -r -d '' large; do
    if (( oversized == 0 )); then
      {
        echo "# Fichiers hors Git (> 95 Mio), non sauvegardés ici"
        echo "# Ces fichiers DOIVENT être restaurés pour une compilation/lecture complète."
        echo "# SHA256 | OCTETS | CHEMIN ORIGINAL"
      } > "$manifest"
    fi
    oversized=$((oversized + 1))
    printf '%s | %s | %s\n' "$(sha256sum "$large" | awk '{print $1}')" "$(stat -c '%s' "$large")" "$large" >> "$manifest"
    echo "ATTENTION: binaire volumineux hors dépôt Git : $large" >&2
    rm -f -- "$large"
  done < <(find "$path" -type f -size +95M -print0)
  git add -f -A -- "$path"
  if ! git diff --cached --quiet; then
    git -c user.name="Kovelyx Source Import" -c user.email="github-actions[bot]@users.noreply.github.com" \
      commit -m "$commit_message"
    if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
      git push origin "HEAD:${GITHUB_REF_NAME:-main}"
    fi
  fi
}

# Les trois snapshots forment un monorepo. Un succès est commité séparément
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


echo "Sources importées ou déjà présentes. Voir docs/INDEPENDANCE.md pour les dépendances restantes."
