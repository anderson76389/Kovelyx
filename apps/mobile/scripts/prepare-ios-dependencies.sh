#!/usr/bin/env bash
set -euo pipefail
repository_root="$(cd "$(dirname "$0")/.." && pwd -P)"
engine_root="${NUVIO_ENGINE_ROOT:-${repository_root}/../../vendor/engine}"
engine_framework="${engine_root}/platform/apple/NuvioEngine.xcframework"
if [[ ! -f "${engine_framework}/Info.plist" ]]; then
    echo "Apple hors distribution Kovelyx : fournir un moteur reconstruit localement via NUVIO_ENGINE_ROOT. Aucun téléchargement automatique." >&2
    exit 1
fi
if [[ ! -f "${repository_root}/MPVKit/Frameworks/Libmpv.xcframework/Info.plist" ]]; then
    echo "Fournir MPVKit/Frameworks/Libmpv.xcframework depuis une compilation locale." >&2
    exit 1
fi
