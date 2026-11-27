#!/usr/bin/env bash
set -euo pipefail

git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main
test "$(git rev-parse refs/remotes/origin/main)" = "$MAIN_SHA"
test "$(git rev-parse HEAD^)" = "$MAIN_SHA"
test "$(git rev-parse "$MAIN_SHA^")" = "$BASE_SHA"

baseline="$RUNNER_TEMP/dex04-base"
candidate="$RUNNER_TEMP/dex04-main"
output="$PWD/dex04-build"
mkdir -p "$output"
git worktree add --detach "$baseline" "$BASE_SHA"
git worktree add --detach "$candidate" "$MAIN_SHA"
test ! -e "$baseline/app/google-services.json"
test ! -e "$candidate/app/google-services.json"

export TALK_PLAY_KEYSTORE_FILE="$RUNNER_TEMP/dex04-offline.p12"
export TALK_PLAY_STORE_PASSWORD="$(openssl rand -hex 24)"
export TALK_PLAY_KEY_PASSWORD="$TALK_PLAY_STORE_PASSWORD"
export TALK_PLAY_KEY_ALIAS=talksullae-dex04
keytool -genkeypair -keystore "$TALK_PLAY_KEYSTORE_FILE" -storetype PKCS12   -alias "$TALK_PLAY_KEY_ALIAS" -storepass:env TALK_PLAY_STORE_PASSWORD   -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=TalkSullae disposable DEX-04 QA'

(cd "$baseline" && ./gradlew --no-daemon --max-workers=2 :app:assembleRelease)
(cd "$candidate" && ./gradlew --no-daemon --max-workers=2 :app:assembleRelease :app:bundleRelease :app:assembleDebug :app:assembleDebugAndroidTest)
cp "$baseline/app/build/outputs/apk/release/app-release.apk" "$output/before.apk"
cp "$candidate/app/build/outputs/apk/release/app-release.apk" "$output/after.apk"
cp "$candidate/app/build/outputs/apk/debug/app-debug.apk" "$output/debug.apk"
cp "$candidate/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "$output/debug-test-original.apk"
cp "$candidate/app/build/outputs/mapping/release/mapping.txt" "$output/mapping.txt"
printf 'Before: %s\nAfter: %s\n' "$BASE_SHA" "$MAIN_SHA" | tee "$output/source.txt"
sha256sum "$candidate/app/build/outputs/bundle/release/app-release.aab" | tee "$output/aab-sha256.txt"
python3 - "$candidate/app/build/outputs/bundle/release/app-release.aab" "$output" <<'PY'
import json
import re
import sys
from pathlib import Path
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as bundle:
    dex = [entry for entry in bundle.infolist() if re.fullmatch(r'[^/]+/dex/classes(\d+)?\.dex', entry.filename)]
    assert dex and any(b'~~R8{' in bundle.read(entry) for entry in dex)
    output = Path(sys.argv[2])
    mapping_bytes = (output / 'mapping.txt').stat().st_size
    assert mapping_bytes
    report = {'dex_entries': len(dex), 'dex_bytes': sum(entry.file_size for entry in dex),
              'mapping_bytes': mapping_bytes, 'firebase_config': False}
    (output / 'dex.json').write_text(json.dumps(report, indent=2) + '\n')
    print(report)
PY

for variant in before after; do
  "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$output/$variant.apk" |
    awk '/certificate SHA-256 digest/{print}' | tee "$output/$variant-cert.txt"
done
test -s "$output/after-cert.txt"
cmp "$output/before-cert.txt" "$output/after-cert.txt"

# Build a second test runner that scrolls to the real UI controls before injecting touch.
# The actual app APK is the unmodified exact-main debug build above.
python3 - "$candidate" <<'PY'
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
(cd "$candidate" && ./gradlew --no-daemon --max-workers=2 :app:assembleDebugAndroidTest)
cp "$candidate/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "$output/debug-test-scrolled.apk"
