#!/bin/bash
set -euo pipefail
mkdir -p update-notes-evidence
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
status=0
for lang in en de; do
  adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales "$lang"
  adb shell am force-stop com.stockfileexplorer.app
  adb shell am instrument -w -e class com.stockfileexplorer.app.UpdateNotesTest com.stockfileexplorer.app.test/androidx.test.runner.AndroidJUnitRunner | tee "update-notes-evidence/$lang-results.txt"
  if ! grep -q 'OK (' "update-notes-evidence/$lang-results.txt" || grep -q 'FAILURES\|INSTRUMENTATION_FAILED' "update-notes-evidence/$lang-results.txt"; then status=1; fi
  adb pull /sdcard/Android/data/com.stockfileexplorer.app/files/update-notes-evidence/. update-notes-evidence/
done
adb logcat -d > update-notes-evidence/logcat.txt
exit "$status"
