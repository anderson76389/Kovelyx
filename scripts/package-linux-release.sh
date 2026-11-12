#!/usr/bin/env bash
# Deterministic, fail-closed Linux release collector. Never publish an
# arbitrary binary or one built at a different version.
set -Eeuo pipefail

if [[ $# != 4 ]]; then
  echo "Usage: $0 <version> <jpackage-release-root> <appimage-root> <destination>" >&2
  exit 2
fi
version="$1"
packages="$2"
appimages="$3"
output="$4"
[[ "$version" =~ ^[0-9]+[.][0-9]+[.][0-9]+(-(alpha|beta|rc))?$ ]] || {
  echo "Invalid version $version" >&2
  exit 2
}
[[ -d "$packages" && -d "$appimages" ]] || {
  echo "Linux build output missing" >&2
  exit 1
}
[[ ! -e "$output" ]] || {
  echo "Refusing to overwrite existing Linux release output" >&2
  exit 1
}
mkdir -p "$output"

find_unique() {
  local folder="$1" pattern="$2" label="$3"
  local -a matches
  mapfile -d '' matches < <(find "$folder" -type f -name "$pattern" -print0)
  if (( ${#matches[@]} != 1 )); then
    echo "Expected one $label; got ${#matches[@]} in $folder" >&2
    return 1
  fi
  [[ -s "${matches[0]}" ]] || {
    echo "Empty $label" >&2
    return 1
  }
  printf '%s\n' "${matches[0]}"
}

deb="$(find_unique "$packages/deb" '*.deb' 'DEB')"
rpm="$(find_unique "$packages/rpm" '*.rpm' 'RPM')"
image="$(find_unique "$appimages" '*.AppImage' 'AppImage')"

deb_version="$(dpkg-deb -f "$deb" Version)"
rpm_version="$(rpm -qp --qf '%{VERSION}' "$rpm")"
[[ "$deb_version" == "$version"-1 || "$deb_version" == "$version" ]] || {
  echo "DEB package version mismatch: $deb_version != $version" >&2
  exit 1
}
[[ "$rpm_version" == "$version" ]] || {
  echo "RPM package version mismatch: $rpm_version != $version" >&2
  exit 1
}
bash apps/desktop/scripts/linux/verify-linux-deb.sh "$deb"
bash apps/desktop/scripts/linux/verify-linux-rpm.sh "$rpm"

# Genuine jpackage payload: native launcher and embedded Java runtime.
dpkg-deb --contents "$deb" | grep -E '/opt/[^[:space:]]+/bin/Kovelyx$' >/dev/null || {
  echo "Missing Kovelyx native Linux launcher in DEB" >&2
  exit 1
}
dpkg-deb --contents "$deb" | grep -E '/opt/[^[:space:]]+/lib/runtime/' >/dev/null || {
  echo "Missing bundled Java runtime in DEB" >&2
  exit 1
}
rpm -qlp "$rpm" | grep -E '^/opt/[^[:space:]]+/bin/Kovelyx$' >/dev/null || {
  echo "Missing Kovelyx native Linux launcher in RPM" >&2
  exit 1
}
[[ "$(head -c 4 "$image" | od -An -tx1 | tr -d '[:space:]')" == 7f454c46 ]] || {
  echo "Invalid ELF AppImage" >&2
  exit 1
}
for f in "$deb" "$rpm" "$image"; do
  if (( $(stat -c '%s' "$f") < 1000000 )); then
    echo "Truncated Linux package: $f" >&2
    exit 1
  fi
done

prefix="Kovelyx-Linux"
cp -- "$deb" "$output/$prefix-amd64-v$version.deb"
cp -- "$rpm" "$output/$prefix-x86_64-v$version.rpm"
cp -- "$image" "$output/$prefix-x86_64-v$version.AppImage"
chmod +x "$output/$prefix-x86_64-v$version.AppImage"
(
  cd "$output"
  for f in *.deb *.rpm *.AppImage; do
    sha256sum -- "$f" > "$f.sha256"
  done
)
echo "PASS: Linux AppImage, DEB and RPM are full Kovelyx $version distributions."
