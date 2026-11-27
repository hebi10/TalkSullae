#!/usr/bin/env bash
set -euo pipefail

result="$PWD/dex-device-output"
mkdir -p "$result"
package=com.hebi.talksullae

adb shell svc wifi disable
adb shell svc data disable
adb shell wm size 1080x2400
adb shell wm density 420
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell settings put system font_scale 1.0

launch_release() {
  adb shell am force-stop "$package"
  adb shell am start -W -n "$package/.MainActivity" | tee "$result/$1-launch.txt"
  grep -Fq 'Status: ok' "$result/$1-launch.txt"
  python3 .github/scripts/dex03-talksullae-smoke.py
  mv "$result/01-optimized-release-startup.png" "$result/$1-startup.png"
  mv "$result/01-optimized-release-startup.xml" "$result/$1-startup.xml"
  mv "$result/02-optimized-release-offline-auth-guard.png" "$result/$1-auth-guard.png"
  mv "$result/02-optimized-release-offline-auth-guard.xml" "$result/$1-auth-guard.xml"
}

adb install dex04-build/before.apk | tee "$result/install-before.txt"
adb shell dumpsys package "$package" > "$result/package-before.txt"
if [[ "$QA_API" -ge 33 ]]; then
  adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
fi
launch_release before

adb install -r dex04-build/after.apk | tee "$result/replace-after.txt"
adb shell dumpsys package "$package" > "$result/package-after.txt"
before_install="$(sed -n 's/.*firstInstallTime=//p' "$result/package-before.txt")"
after_install="$(sed -n 's/.*firstInstallTime=//p' "$result/package-after.txt")"
test -n "$before_install"
test "$before_install" = "$after_install"
if [[ "$QA_API" -ge 33 ]]; then
  grep -Eq 'POST_NOTIFICATIONS: granted=true' "$result/package-after.txt"
fi
launch_release after
echo "PASS: API $QA_API exact-main release installed over same-version prior release, preserving first install time and supported notification grant" |
  tee "$result/replacement-result.txt"
