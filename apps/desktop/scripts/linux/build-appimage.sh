#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "Usage: $0 <app-root> <output-appimage> <appimagetool-path> [--update-information <value>] [--website-url <value>]" >&2
}

if [[ $# -lt 3 ]]; then
    usage
    exit 2
fi

app_root="$1"
output_appimage="$2"
appimagetool_path="$3"
shift 3

update_information="${APPIMAGE_UPDATE_INFORMATION:-${UPDATE_INFORMATION:-}}"
website_url="${APPIMAGE_WEBSITE_URL:-}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --update-information)
            [[ $# -ge 2 ]] || {
                echo "Missing value for --update-information" >&2
                exit 2
            }
            update_information="$2"
            shift 2
            ;;
        --website-url)
            [[ $# -ge 2 ]] || {
                echo "Missing value for --website-url" >&2
                exit 2
            }
            website_url="$2"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            exit 2
            ;;
    esac
done

if [[ ! -d "$app_root" ]]; then
    echo "App root not found: $app_root" >&2
    exit 1
fi

if [[ ! -x "$appimagetool_path" ]]; then
    echo "appimagetool is not executable: $appimagetool_path" >&2
    exit 1
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/kovelyx-appimage.XXXXXX")"
cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT

app_dir="$work_dir/Kovelyx.AppDir"
mkdir -p "$app_dir"
cp -a "$app_root"/. "$app_dir/"

# AppDir must use the brand actually built by jpackage. Never silently
# package a Nuvio-branded launcher or point to an upstream application.
launcher="$app_dir/bin/Kovelyx"
[[ -x "$launcher" ]] || {
    echo "Kovelyx Linux launcher missing from $launcher" >&2
    exit 1
}

icon_source="$app_dir/lib/Kovelyx.png"
[[ -s "$icon_source" ]] || {
    echo "Kovelyx Linux icon missing at $icon_source" >&2
    exit 1
}
cp "$icon_source" "$app_dir/Kovelyx.png"
ln -s Kovelyx.png "$app_dir/.DirIcon"

cat > "$app_dir/Kovelyx.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=Kovelyx
Comment=Lecteur multimédia pour Linux
Exec=AppRun %u
Icon=Kovelyx
Terminal=false
Categories=AudioVideo;Video;Player;
StartupNotify=true
DESKTOP

cat > "$app_dir/AppRun" <<'LAUNCHER'
#!/usr/bin/env bash
set -euo pipefail
here="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "$here/bin/Kovelyx" "$@"
LAUNCHER
chmod +x "$app_dir/AppRun"

mkdir -p "$(dirname "$output_appimage")"
appimagetool_args=("$app_dir" "$output_appimage")
if [[ -n "$update_information" ]]; then
    appimagetool_args=("-u" "$update_information" "${appimagetool_args[@]}")
fi

ARCH=x86_64 "$appimagetool_path" --appimage-extract-and-run "${appimagetool_args[@]}"

if [[ ! -f "$output_appimage" ]]; then
    echo "Expected AppImage output was not produced: $output_appimage" >&2
    exit 1
fi

chmod +x "$output_appimage"
printf 'Built AppImage artifact: %s\n' "$output_appimage"
