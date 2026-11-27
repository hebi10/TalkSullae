#!/usr/bin/env bash
set -euo pipefail

main=219c8063b458aa5168f2850749593927caace4f7
test "$(git rev-parse HEAD^)" = "$main"
exact_main="$RUNNER_TEMP/talksullae-main"
git worktree add --detach "$exact_main" "$main"
test ! -e "$exact_main/app/google-services.json"
mkdir -p dex04-build

(cd "$exact_main" && ./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace)
cp "$exact_main/app/build/outputs/apk/debug/app-debug.apk" dex04-build/debug.apk
cp "$exact_main/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" dex04-build/debug-test-original.apk

python3 - "$exact_main" <<'PY'
import sys
from pathlib import Path

directory = Path(sys.argv[1]) / 'app/src/androidTest/java/com/hebi/talksullae'
for filename in ('MainNavigationTest.kt', 'DesignNavigationCaptureTest.kt'):
    file = directory / filename
    source = file.read_text()
    target = 'onNodeWithTag("onboarding-continue").performClick()'
    assert source.count(target) == 1
    file.write_text(source.replace(target, 'onNodeWithTag("onboarding-continue").performScrollTo().performClick()'))
PY
(cd "$exact_main" && ./gradlew --no-daemon --max-workers=2 :app:assembleDebugAndroidTest --stacktrace)
cp "$exact_main/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" dex04-build/debug-test-scrolled.apk
