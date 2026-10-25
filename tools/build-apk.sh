#!/usr/bin/env bash
# Build and export a signed, checked APK; does not connect to or modify any TV.
set -euo pipefail
cd "$(dirname "$0")/.."
./tools/build-leo.sh
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/LeoTV.apk
if command -v shasum >/dev/null 2>&1; then
    (cd dist && shasum -a 256 LeoTV.apk > LeoTV.apk.sha256)
else
    (cd dist && sha256sum LeoTV.apk > LeoTV.apk.sha256)
fi
printf 'APK: %s/dist/LeoTV.apk\n' "$PWD"
