#!/usr/bin/env bash
set -euo pipefail

mkdir -p dex-device-output
adb shell svc wifi disable
adb shell svc data disable
adb install app/build/outputs/apk/release/app-release.apk
adb shell am start -W -n com.hebi.talksullae/.MainActivity
python3 .github/scripts/dex03-talksullae-smoke.py

# Release deliberately has no local demo. Check those flows independently on Debug.
adb uninstall com.hebi.talksullae
adb install app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class 'com.hebi.talksullae.MainNavigationTest#roomCreationRequiresValidNameAndShowsLocalRoom,com.hebi.talksullae.RoomChatComposeTest' \
  com.hebi.talksullae.test/androidx.test.runner.AndroidJUnitRunner | tee dex-device-output/debug-room-and-chat.txt
grep -Eq '^OK \([1-9][0-9]* tests?\)' dex-device-output/debug-room-and-chat.txt
