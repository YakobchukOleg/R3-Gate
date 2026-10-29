#!/usr/bin/env bash
set -uo pipefail
test_status=0
gradle --no-daemon connectedDebugAndroidTest || test_status=$?
mkdir -p device-screenshots
adb pull /sdcard/Download/. device-screenshots/ || true
find app/build/outputs/androidTest-results/connected -name '*.xml' -exec cat {} \;
# Synthetic emulator UI only: keep two previews available for visual review.
for preview in r3-01-home r3-06-widget r3-10-wallet r3-11-widget-settings r3-08-filters-closed r3-12-object-search r3-13-timeline r3-14-history-days r3-15-expense-receipt; do
  image="device-screenshots/$preview.png"
  if [ -f "$image" ]; then
    echo "R3_PREVIEW_BEGIN:$preview"
    base64 "$image"
    echo "R3_PREVIEW_END:$preview"
  fi
done
exit "$test_status"
