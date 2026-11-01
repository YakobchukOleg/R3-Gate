#!/usr/bin/env bash
set -uo pipefail
# Make sure the API 35 emulator is awake, unlocked and ready to focus app dialogs.
adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_HOME || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb shell settings put secure show_ime_with_hard_keyboard 0 || true
adb shell settings put secure autofill_service null || true
sleep 3

test_status=0
gradle --no-daemon connectedDebugAndroidTest > /tmp/workmeter-device-tests.log 2>&1 || test_status=$?
cat /tmp/workmeter-device-tests.log

# Retry once only for the Android emulator's intermittent dialog-focus failure.
# Keep actual assertion failures visible: never convert a failing suite into success.
if [ "$test_status" -ne 0 ] && grep -q 'RootViewWithoutFocusException' /tmp/workmeter-device-tests.log; then
  echo "Emulator dialog focus lost; retrying the complete connected test suite once."
  adb shell input keyevent KEYCODE_HOME || true
  adb shell am force-stop com.r3gate.app || true
  sleep 3
  test_status=0
  gradle --no-daemon connectedDebugAndroidTest || test_status=$?
fi
mkdir -p device-screenshots
adb pull /sdcard/Download/. device-screenshots/ || true
find app/build/outputs/androidTest-results/connected -name '*.xml' -exec cat {} \;
# Synthetic emulator UI only: keep two previews available for visual review.
for preview in r3-01-home r3-06-widget r3-10-wallet r3-11-widget-settings r3-08-filters-closed r3-12-object-search r3-13-timeline r3-14-history-days r3-15-expense-receipt r3-16-car-stock r3-17-charts r3-18-queue; do
  image="device-screenshots/$preview.png"
  if [ -f "$image" ]; then
    echo "R3_PREVIEW_BEGIN:$preview"
    base64 "$image"
    echo "R3_PREVIEW_END:$preview"
  fi
done
exit "$test_status"
