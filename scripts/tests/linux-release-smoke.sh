#!/usr/bin/env bash
# Fail closed: smoke-test REAL Linux packages, not an unpackaged Gradle :run.
# No user data/credentials. Requires dpkg-deb, xvfb-run, timeout and file.
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <full-release.deb> <full-release.AppImage>" >&2
  exit 2
fi
deb="$1"
appimage="$2"
[[ -s "$deb" && -s "$appimage" ]] || { echo "Missing Linux release package" >&2; exit 1; }

work="$(mktemp -d "${TMPDIR:-/tmp}/kovelyx-linux-smoke.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT

# Inspect the actual DEB and embedded jpackage Java runtime.
dpkg-deb -x "$deb" "$work/deb"
launcher="$work/deb/opt/kovelyx/bin/Kovelyx"
runtime="$work/deb/opt/kovelyx/lib/runtime"
[[ -x "$launcher" && -d "$runtime" ]] || {
  echo "DEB is missing its executable Kovelyx launcher or bundled Java runtime" >&2
  exit 1
}
file "$launcher" | grep -Eq 'ELF.*x86-64'
test -s "$work/deb/opt/kovelyx/lib/Kovelyx.png"

# Real type-2 AppImage extraction, avoiding a FUSE/kernel dependency in CI.
(
  cd "$work"
  "$appimage" --appimage-extract > /dev/null
)
app_run="$work/squashfs-root/AppRun"
[[ -x "$app_run" ]] || { echo "Extracted AppImage is missing executable AppRun" >&2; exit 1; }
[[ -x "$work/squashfs-root/bin/Kovelyx" ]] || {
  echo "Extracted AppImage is missing native Kovelyx launcher" >&2
  exit 1
}

# The GUI must survive a 22-second Xvfb startup window: exit 124 means timeout
# interrupted a still-running process, not an early crash. No real playback is claimed.
smoke() {
  local kind="$1" binary="$2" code=0
  local user_home="$work/home-$kind"
  mkdir -p "$user_home/.config" "$user_home/.local/share" "$user_home/.cache"
  set +e
  timeout --signal=TERM --kill-after=5s 22s \
    xvfb-run -a -s '-screen 0 1280x720x24' \
    env HOME="$user_home" XDG_CONFIG_HOME="$user_home/.config" \
      XDG_DATA_HOME="$user_home/.local/share" XDG_CACHE_HOME="$user_home/.cache" \
      NO_AT_BRIDGE=1 LIBGL_ALWAYS_SOFTWARE=1 "$binary" \
      > "$work/$kind.stdout" 2> "$work/$kind.stderr"
  code=$?
  set -e
  if [[ "$code" -ne 124 ]]; then
    echo "::error::Kovelyx $kind exited early (code $code); full distribution is not release-ready" >&2
    tail -n 60 "$work/$kind.stdout" >&2 || true
    tail -n 60 "$work/$kind.stderr" >&2 || true
    return 1
  fi
  if grep -Eiq 'UnsatisfiedLinkError|NoClassDefFoundError|FATAL EXCEPTION|cannot open shared object file|undefined symbol|SIGSEGV' "$work/$kind.stderr"; then
    echo "::error::Kovelyx $kind emitted a fatal native/JVM error" >&2
    tail -n 60 "$work/$kind.stderr" >&2
    return 1
  fi
  echo "PASS: real $kind GUI survived 22-second startup under Xvfb"
}
smoke "DEB" "$launcher"
smoke "AppImage" "$app_run"
echo "LIMITS: this is a startup smoke, not a full video/codec/extension/sync test."
