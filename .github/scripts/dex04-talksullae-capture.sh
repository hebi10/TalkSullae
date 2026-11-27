#!/usr/bin/env bash
set -euo pipefail

package=com.hebi.talksullae
capture="$PWD/dex04-capture"
mkdir -p "$capture"
adb shell svc wifi disable
adb shell svc data disable
adb install dex04-build/debug.apk
adb install dex04-build/debug-test-original.apk
adb shell wm size 1080x2400
adb shell wm density 420
adb shell settings put system font_scale 2.0
adb shell pm clear "$package"
python3 scripts/dismiss_launcher_anr.py
adb shell am instrument -w -r -e captureDesign true -e designTheme light   -e class "$package.DesignNavigationCaptureTest" "$package.test/androidx.test.runner.AndroidJUnitRunner" |
  tee "$capture/original-navigation-200.txt"
adb uninstall "$package.test"
adb install dex04-build/debug-test-scrolled.apk

cd "$RUNNER_TEMP/talksullae-main"
python3 scripts/qa/export_design_captures.py --expected-sha "$(git rev-parse HEAD)"   --profile phone --theme light --font-scale 2.0 --output "$capture/scrolled"
