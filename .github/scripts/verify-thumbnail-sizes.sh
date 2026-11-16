#!/bin/bash
set -euo pipefail
mkdir -p thumbnail-evidence
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
status=0
for lang in en de; do
  adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales "$lang"
  adb shell am force-stop com.stockfileexplorer.app
  adb shell am instrument -w -e class com.stockfileexplorer.app.ThumbnailSizeTest#sizesPersistAndGrowRecentRows com.stockfileexplorer.app.test/androidx.test.runner.AndroidJUnitRunner | tee "thumbnail-evidence/$lang-results.txt"
  adb shell am force-stop com.stockfileexplorer.app
  adb shell am instrument -w -e class com.stockfileexplorer.app.ThumbnailSizeTest#savedLargeSurvivesNewProcess com.stockfileexplorer.app.test/androidx.test.runner.AndroidJUnitRunner | tee "thumbnail-evidence/$lang-persistence-results.txt"
  if ! grep -q 'OK (' "thumbnail-evidence/$lang-persistence-results.txt" || grep -q 'FAILURES\|INSTRUMENTATION_FAILED' "thumbnail-evidence/$lang-persistence-results.txt"; then status=1; fi
  adb pull /sdcard/Android/data/com.stockfileexplorer.app/files/thumbnail-evidence/. thumbnail-evidence/ || true
  if ! grep -q 'OK (' "thumbnail-evidence/$lang-results.txt" || grep -q 'FAILURES\|INSTRUMENTATION_FAILED' "thumbnail-evidence/$lang-results.txt"; then status=1; fi
done
adb logcat -d > thumbnail-evidence/logcat.txt
exit "$status"
