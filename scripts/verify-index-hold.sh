#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/reports/device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
run_test() {
  adb shell am instrument -w -r -e class "$1" com.stockfileexplorer.app.test/androidx.test.runner.AndroidJUnitRunner | tee app/build/reports/device/instrumentation-last.txt
  adb pull /sdcard/Download/ app/build/reports/device/ || true
  grep -q 'OK (' app/build/reports/device/instrumentation-last.txt
}
run_test com.stockfileexplorer.app.IndexAndHoldTest#a_largeIndexFirstBenchmarkAndPersistence,com.stockfileexplorer.app.IndexAndHoldTest#b_actualHoldAcrossAllViewsAndSelect
run_test com.stockfileexplorer.app.FileActionsInstrumentedTest#customizableMenuAddsRequestedActions
adb pull /sdcard/Download/ app/build/reports/device/
adb shell am force-stop com.stockfileexplorer.app
run_test com.stockfileexplorer.app.IndexAndHoldTest#c_productionColdRestartUsesPersistedRows
adb pull /sdcard/Download/ app/build/reports/device/
if [ "$SFE_TEST_API" = "35" ]; then
adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales de-AT
run_test com.stockfileexplorer.app.IndexAndHoldTest#b_actualHoldAcrossAllViewsAndSelect
adb pull /sdcard/Download/ app/build/reports/device/
fi
