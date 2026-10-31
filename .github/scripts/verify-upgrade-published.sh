#!/bin/bash
# Upgrade proof against the actually published release, run on the emulator after the functional suites.
# Test build only: nothing is published or uploaded anywhere.
set -e
PKG=com.stockfileexplorer.app
NEW=app/build/outputs/apk/debug/app-debug.apk
TESTAPK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
OLD="$RUNNER_TEMP/sfe-published-0.2.12.apk"
[ -s "$TESTAPK" ] || ./gradlew assembleDebugAndroidTest
APKSIGNER=$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)
AAPT=$(ls "$ANDROID_HOME"/build-tools/*/aapt | sort -V | tail -1)
curl -fL --retry 3 -o "$OLD" https://github.com/manuelmania93-svg/FileExplorerCL/releases/download/v0.2.12/File-Explorer-Control-0.2.12.apk
echo "UPGRADE built APK sha256: $(sha256sum $NEW | cut -d' ' -f1)  source commit: ${GITHUB_SHA:-unknown}"
echo "UPGRADE published 0.2.12 sha256: $(sha256sum $OLD | cut -d' ' -f1)"
old_cert=$("$APKSIGNER" verify --print-certs "$OLD" | grep -m1 'certificate SHA-256 digest' | awk '{print $NF}')
new_cert=$("$APKSIGNER" verify --print-certs "$NEW" | grep -m1 'certificate SHA-256 digest' | awk '{print $NF}')
echo "UPGRADE signer published: $old_cert"; echo "UPGRADE signer built:     $new_cert"
[ -n "$old_cert" ] && [ "$old_cert" = "$new_cert" ] || { echo "UPGRADE FAIL: signer differs"; exit 1; }
"$AAPT" dump badging "$NEW" | grep -E "^package:|application-label:'" | head -3
adb uninstall $PKG || true
out=$(adb install "$OLD"); echo "$out"; echo "$out" | grep -q Success
echo "UPGRADE before: $(adb shell dumpsys package $PKG | grep -E 'versionName' | head -1 | tr -d '\r')"
out=$(adb install -r "$NEW"); echo "$out"; echo "$out" | grep -q Success
out=$(adb install -r -t "$TESTAPK"); echo "$out"; echo "$out" | grep -q Success
echo "UPGRADE after:  $(adb shell dumpsys package $PKG | grep -E 'versionName|versionCode' | head -2 | tr -d '\r' | tr '\n' ' ')"
run() { out=$(adb shell am instrument -w -e class "com.stockfileexplorer.app.UpgradeStateTest#$1" $PKG.test/androidx.test.runner.AndroidJUnitRunner); echo "$out"; echo "$out" | grep -q "^OK ("; }
run upgradedAppHasNewVersionLabelAndIncomingHandlers
run seedSort
echo "UPGRADE DIAG prefs right after seedSort (before any explicit flush):"; adb shell run-as $PKG cat shared_prefs/file-action-preferences.xml || echo "(run-as read failed)"
run flushPrefs
echo "UPGRADE DIAG prefs after explicit flush:"; adb shell run-as $PKG cat shared_prefs/file-action-preferences.xml || echo "(run-as read failed)"
out=$(adb install -r "$NEW"); echo "$out"; echo "$out" | grep -q Success
echo "UPGRADE DIAG prefs after reinstall:"; adb shell run-as $PKG cat shared_prefs/file-action-preferences.xml || echo "(run-as read failed)"
run sortSurvivedReinstall
echo "UPGRADE OK: published 0.2.12 -> built test build, same signer, label/handlers/version checked, saved sort survives reinstall"
