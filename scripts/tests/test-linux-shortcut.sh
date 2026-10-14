#!/usr/bin/env bash
# Tests sans installation pour le raccourci produit par nos scripts Linux.
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=../../apps/desktop/scripts/linux/linux-shortcut-definition.sh
source "$root/apps/desktop/scripts/linux/linux-shortcut-definition.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
nuvio_linux_write_desktop_entry "$tmp"
desktop="$tmp/usr/share/applications/kovelyx.desktop"

test -f "$desktop"
grep -Fxq 'Name=Kovelyx' "$desktop"
grep -Fxq 'Comment=Lecteur multimédia Kovelyx' "$desktop"
grep -Fxq 'Exec=/opt/kovelyx/bin/Kovelyx %u' "$desktop"
grep -Fxq 'Icon=/opt/kovelyx/lib/Kovelyx.png' "$desktop"
nuvio_linux_ensure_startup_wm_class "$tmp"
nuvio_linux_ensure_uri_handler "$tmp"
grep -q '^StartupWMClass=' "$desktop"
grep -q 'x-scheme-handler/kovelyx;' "$desktop"
nuvio_linux_desktop_entry_exists "$tmp"
if grep -Eq 'Name=Nuvio|Comment=Nuvio|Exec=/opt/nuvio|Icon=/opt/nuvio' "$desktop"; then
    echo 'Ancienne identité affichée dans le paquet Linux' >&2
    exit 1
fi
echo 'OK: raccourci Kovelyx créé sans référence de marque Nuvio affichée.'
