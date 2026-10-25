#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
# Set LEO_KEYSTORE for a custom signing key. Signing material stays outside Git.
export LEO_KEYSTORE="${LEO_KEYSTORE:-$HOME/.android/debug.keystore}"
[[ -f "$LEO_KEYSTORE" ]] || { echo 'Set LEO_KEYSTORE to your signing key. See docs/leo-other-devices.md.'; exit 1; }
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
