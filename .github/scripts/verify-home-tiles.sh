#!/bin/bash
set -euo pipefail
mkdir -p home-evidence
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
status=0
for lang in en de; do
  adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales "$lang"
  adb shell am force-stop com.stockfileexplorer.app
  adb shell am instrument -w -e class com.stockfileexplorer.app.HomeTilesTest com.stockfileexplorer.app.test/androidx.test.runner.AndroidJUnitRunner | tee "home-evidence/$lang-results.txt"
  adb pull /sdcard/Android/data/com.stockfileexplorer.app/files/home-evidence/. home-evidence/ || true
  if ! grep -q 'OK (' "home-evidence/$lang-results.txt" || grep -q 'FAILURES\|INSTRUMENTATION_FAILED' "home-evidence/$lang-results.txt"; then status=1; fi
done
adb logcat -d > home-evidence/logcat.txt
exit "$status"
