#!/usr/bin/env bash
set -uo pipefail
test_status=0
gradle --no-daemon connectedDebugAndroidTest || test_status=$?
mkdir -p device-screenshots
adb pull /sdcard/Download/. device-screenshots/ || true
find app/build/outputs/androidTest-results/connected -name '*.xml' -exec cat {} \;
exit "$test_status"
