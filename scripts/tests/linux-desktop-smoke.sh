#!/usr/bin/env bash
# Check the actual release DEB can launch, not merely that Gradle completed.
set -euo pipefail
[[ $# -eq 1 && -s "$1" ]] || { echo "Usage: $0 /path/package.deb" >&2; exit 2; }
temp="$(mktemp -d)"
pid=""
cleanup() {
  if [[ -n "$pid" ]]; then kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; fi
  rm -rf "$temp"
}
trap cleanup EXIT
dpkg-deb -x "$1" "$temp/root"
launcher="$temp/root/opt/kovelyx/bin/Kovelyx"
[[ -x "$launcher" ]] || { echo "Linux launcher missing" >&2; exit 1; }
test -s "$temp/root/opt/kovelyx/lib/Kovelyx.png"
file "$launcher" | grep -Eq 'ELF.*x86-64|shell script|POSIX shell'
xvfb-run -a -s '-screen 0 1280x720x24' "$launcher" > "$temp/stdout" 2> "$temp/stderr" &
pid=$!
sleep 15
if ! kill -0 "$pid" 2>/dev/null; then
  echo "::error::Kovelyx Linux stopped during real startup" >&2
  tail -n 35 "$temp/stderr" >&2
  exit 1
fi
if grep -Eqi 'UnsatisfiedLinkError|NoClassDefFoundError|FATAL EXCEPTION|libmpv.*not found' "$temp/stderr"; then
  echo "::error::Native/player startup error" >&2
  tail -n 35 "$temp/stderr" >&2
  exit 1
fi
echo "PASS: real Kovelyx Linux launcher remained active under Xvfb (15 s)"
