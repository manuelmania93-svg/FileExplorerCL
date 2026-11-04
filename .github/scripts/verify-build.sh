#!/bin/bash
set -e
# Functional suites do not abort the script: every later suite, the upgrade checks and the screenshot gate still run.
# Any failed suite makes the final status red (see the end of this file).
FAILED_SUITES=""
capture() {
  mkdir -p app/build/reports/device/Download
  # Copy evidence only; recursive Download pulls include fixture/provider paths
  # whose transient permissions can make adb pull fail after tests passed.
  adb shell 'find /sdcard/Download -maxdepth 1 -type f -name "sfe-*.png"' | tr -d '\r' > /tmp/sfe-capture-list
  while IFS= read -r path; do
    [ -n "$path" ] && { adb pull "$path" app/build/reports/device/Download/ || echo "capture: skipped $path"; }
  done < /tmp/sfe-capture-list
}
# The package intermittently disappears from PackageManager between test classes
# (cause undetermined); a following reinstall wipes the all-files grant and the
# per-app locale. Re-establish both idempotently before tests that need them.
# Run a cleanup command only when the package is verifiably present. When the
# package intermittently disappeared, a later reinstall already reset these
# settings, so skipping is correct; any other failure must fail the run.
if_package_present() {
  local label="$1"; shift
  if adb shell pm list packages | tr -d '\r' | grep -qx 'package:com.stockfileexplorer.app'; then
    "$@"
  else
    echo "package absent at $label; skipping: $*"
  fi
}
ensure_installed_de_at() {
  echo "packages before $1: $(adb shell pm list packages | grep -i stockfileexplorer | tr -d '\r')"
  ./gradlew installDebug
  adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
  adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales de-AT
  adb shell am force-stop com.stockfileexplorer.app
}
run_pyramid_tests() {
  ensure_installed_de_at "StoragePyramidColorTest"
  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.StoragePyramidColorTest -Pandroid.testInstrumentationRunnerArguments.capture=pyramid || (capture; exit 1)
  capture
  ensure_installed_de_at "StoragePyramidUsageAccessTest"
  # Emulator test staging only (never shipped, never user-facing): create a 24 MB
  # fixture in a HIDDEN subdir of the app's own Android/media dir on the lower
  # filesystem (hidden so the app's own FileIndex excludes it and the pyramid UI
  # proof is not polluted), and stamp it with the app's EXT_DATA quota project id
  # via chattr -p as root - the same FS_IOC_FSSETXATTR operation vold's
  # SetQuotaProjectId performs (system/vold Utils.cpp), applied directly because
  # the mount-service shell route was refused on this image. Requires adb root,
  # which the google_apis userdebug emulator image supports. The lsattr output is
  # the on-device proof of the stamp; failures here make
  # frameworkFixupAttributesOwnMediaDir fail with a precise assertion.
  stage_attributed_media_fixture() {
    adb root >/dev/null 2>&1 || echo "staging: adb root failed (image not rootable?)"
    adb wait-for-device
    adb shell '
      PKG=com.stockfileexplorer.app
      DIR=/data/media/0/Android/media/$PKG
      PROOF=/data/media/0/Download/sfe-staging-proof.txt
      mkdir -p "$DIR/.staging-sfe" &&
        dd if=/dev/zero of="$DIR/.staging-sfe/sfe-media-fixture.bin" bs=1048576 count=24
      UID=$(stat -c %u /data/data/$PKG)
      PROJID=$((UID - 10000 + 20000))
      OBSERVED=0
      if chattr -Rp "$PROJID" "$DIR" && chattr -R +P "$DIR" && chown -R "$UID:1023" "$DIR"; then
        OBSERVED=$(lsattr -pd "$DIR/.staging-sfe/sfe-media-fixture.bin" | awk "{print \$1}")
        echo "staging: stamped projid $PROJID for uid $UID, observed $OBSERVED"
      else
        echo "staging: direct project-id stamp failed (chattr -p unsupported or refused)"
      fi
      { echo "uid=$UID projid=$PROJID stamped=$OBSERVED"; lsattr -pdR "$DIR"; } > "$PROOF"
      chmod 0644 "$PROOF"
    ' || echo "staging: fixture creation failed"
    adb shell "lsattr -pdR /data/media/0/Android/media/com.stockfileexplorer.app" || echo "staging: lsattr failed"
    adb unroot >/dev/null 2>&1 || true
    adb wait-for-device
  }
  stage_attributed_media_fixture
  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.StoragePyramidUsageAccessTest -Pandroid.testInstrumentationRunnerArguments.capture=pyramid-granted || (capture; exit 1)
  capture
  if_package_present "usage-stats reset" adb shell appops set com.stockfileexplorer.app android:get_usage_stats default
}
mkdir -p app/build/reports/device
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew installDebug
# Warm the cold emulator: the first real launch can take >20s (dex2oat/JIT on a
# fresh boot) and outlast the tests' launch wait. Force-stop so tests see a clean start.
adb shell monkey -p com.stockfileexplorer.app -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 25
adb shell am force-stop com.stockfileexplorer.app
if [ "${2:-false}" = "true" ]; then
  # Targeted validation: only the storage pyramid tests (minutes instead of the full suite).
  run_pyramid_tests
  if_package_present "locale reset (pyramid only)" adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales ""
  exit 0
fi
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.SavedLocationsTest || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.RemoteAndSearchTest -Pandroid.testInstrumentationRunnerArguments.capture=remote-search || { capture; adb logcat -d > app/build/reports/device/logcat-remote-search.txt || true; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
# Verify the exact immediately previous public APK upgrades without uninstalling.
curl -fL --retry 3 -o "$RUNNER_TEMP/sfe-0.2.11.apk" https://github.com/manuelmania93-svg/FileExplorerControl/releases/download/v0.2.11/File-Explorer-Control-0.2.11.apk
out=$(adb install -r -d "$RUNNER_TEMP/sfe-0.2.11.apk"); echo "$out"; echo "$out" | grep -q Success
out=$(adb install -r app/build/outputs/apk/debug/app-debug.apk); echo "$out"; echo "$out" | grep -q Success
adb shell dumpsys package com.stockfileexplorer.app | grep -E 'versionCode|versionName'
if [ "${1:-false}" = "true" ]; then exit 0; fi
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.GateFlowTest -Pandroid.testInstrumentationRunnerArguments.capture=gate || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
adb shell cmd uimode night yes
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.GateFlowTest -Pandroid.testInstrumentationRunnerArguments.capture=gate-night || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
adb shell cmd uimode night no
adb shell am force-stop com.stockfileexplorer.app || true
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
adb push app/src/androidTest/assets/fixtures/sfe-fixture-report.pdf /sdcard/Download/sfe-fixture-report.pdf
adb push app/src/androidTest/assets/fixtures/sfe-fixture-notes.docx /sdcard/Download/sfe-fixture-notes.docx
adb push app/src/androidTest/assets/fixtures/sfe-fixture-todo.txt /sdcard/Download/sfe-fixture-todo.txt
adb push app/src/androidTest/assets/viewers/sample-picture.jpg /sdcard/Download/sfe-sample-picture.jpg
adb shell ls -la /sdcard/Download
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.DocumentVisibilitySmokeTest -Pandroid.testInstrumentationRunnerArguments.capture=portrait || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
adb shell cmd uimode night yes
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.DocumentVisibilitySmokeTest -Pandroid.testInstrumentationRunnerArguments.capture=night || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
adb shell cmd uimode night no
capture
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.SelectionBarTest -Pandroid.testInstrumentationRunnerArguments.capture=select || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.LauncherIconTest -Pandroid.testInstrumentationRunnerArguments.capture=icon || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.BackNavigationTest || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.TrashAndAppsTest -Pandroid.testInstrumentationRunnerArguments.capture=trash || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.OpenSpeedBaselineTest || echo "DIAGNOSTIC: speed probe failed (timing only, not a functional gate)"
adb pull /sdcard/Download/sfe-open-speed.txt app/build/reports/device/ || true
capture
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.SortPersistenceTest || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.DiskSpaceRefreshTest || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
adb pull /sdcard/Download/sfe-disk-refresh.txt app/build/reports/device/ || true
capture
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.UserFlowsTest || { capture; adb logcat -d > app/build/reports/device/logcat-flows.txt || true; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.IncomingFileTest -Pandroid.testInstrumentationRunnerArguments.capture=incoming || { capture; adb logcat -d > app/build/reports/device/logcat-incoming.txt || true; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.BuiltInViewersTest -Pandroid.testInstrumentationRunnerArguments.capture=viewers || { capture; adb logcat -d > app/build/reports/device/logcat-viewers.txt || true; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
adb shell cmd uimode night yes
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.BuiltInViewersTest -Pandroid.testInstrumentationRunnerArguments.capture=viewers-night || { capture; adb logcat -d > app/build/reports/device/logcat-viewers-night.txt || true; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
adb shell cmd uimode night no
# Evidence screenshots from the earlier suites are already pulled by capture above. Delete them from the device (they are already in the report) so the
# German layout suite (which scrolls the Download list for its fixtures) sees the folder it was written against.
adb shell 'rm -f /sdcard/Download/sfe-*.png /sdcard/sfe-evidence-moved/* 2>/dev/null || true'
echo "DIAG Download before German suite:"; adb shell ls -la /sdcard/Download | head -60
echo "DIAG sample jpg present: $(adb shell ls /sdcard/Download/sfe-sample-picture.jpg 2>&1 | tr -d '\r')"
# German-locale layout run via per-app language: no reboot and no
# root, so the installed package and the all-files grant stay in
# place (the previous reboot-based approach lost the package).
# Diagnose package visibility, then guarantee it: the package
# intermittently disappears from PackageManager after the test
# classes finish (cause undetermined), so reinstall idempotently.
echo "packages before locale step: $(adb shell pm list packages | grep -i stockfileexplorer | tr -d '\r')"
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales de-AT
adb shell am force-stop com.stockfileexplorer.app
echo "app locales: $(adb shell cmd locale get-app-locales com.stockfileexplorer.app | tr -d '\r')"
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.GermanLayoutTest -Pandroid.testInstrumentationRunnerArguments.capture=german || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
# Pyramid colour + installed-apps proofs (each test re-verifies install, grant, locale).
run_pyramid_tests
if_package_present "locale reset" adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales ""
# Upgrade from the immediately previous published version too.
curl -fL --retry 3 -o "$RUNNER_TEMP/sfe-0.2.9.apk" https://github.com/manuelmania93-svg/FileExplorerControl/releases/download/v0.2.9/File-Explorer-Control-0.2.9.apk
adb uninstall com.stockfileexplorer.app || true
out=$(adb install "$RUNNER_TEMP/sfe-0.2.9.apk"); echo "$out"; echo "$out" | grep -q Success
out=$(adb install -r app/build/outputs/apk/debug/app-debug.apk); echo "$out"; echo "$out" | grep -q Success
# Upgrade path proof: install the actually released 0.2.0 APK, then
# upgrade-install this build over it (fails if the signer differs).
curl -fL --retry 3 -o "$RUNNER_TEMP/sfe-0.2.0.apk" https://github.com/manuelmania93-svg/StockFileExplorer/releases/download/v0.2.0/StockFileExplorer-0.2.0.apk
out=$(adb install -r -d "$RUNNER_TEMP/sfe-0.2.0.apk"); echo "$out"; echo "$out" | grep -q Success
out=$(adb install -r app/build/outputs/apk/debug/app-debug.apk); echo "$out"; echo "$out" | grep -q Success
adb shell dumpsys package com.stockfileexplorer.app | grep -E 'versionCode|versionName' 
capture
bash .github/scripts/verify-upgrade-published.sh || FAILED_SUITES="$FAILED_SUITES upgrade-from-published-0.2.12"
capture
# The screenshots that prove the incoming-file, sort, extract and disk flows must exist: a skipped pull must not hide them.
missing=""
for f in sfe-flows-sort-4-after-rebuild.png sfe-flows-extract-5-done.png sfe-flows-chooser-2-text-viewer.png sfe-disk-1-before.png sfe-disk-3-after.png; do
  [ -s "app/build/reports/device/Download/$f" ] || { echo "MISSING required screenshot: $f"; missing="$missing $f"; }
done
[ -z "$missing" ] || { echo "Required screenshots missing:$missing"; FAILED_SUITES="$FAILED_SUITES missing-screenshots"; }
[ -z "$FAILED_SUITES" ] || { echo "FAILED functional suites (script lines):$FAILED_SUITES"; exit 1; }
