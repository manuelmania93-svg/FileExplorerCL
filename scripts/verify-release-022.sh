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
# Byte-equivalent production code was benchmarked onAPI30/35 in PR1; verify
# actions and actual restart again on the permanent-signed release artifact.
run_test com.stockfileexplorer.app.IndexAndHoldTest#b_actualHoldAcrossAllViewsAndSelect
adb shell am force-stop com.stockfileexplorer.app
run_test com.stockfileexplorer.app.IndexAndHoldTest#c_productionColdRestartUsesPersistedRows
adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales de-AT
run_test com.stockfileexplorer.app.IndexAndHoldTest#b_actualHoldAcrossAllViewsAndSelect
adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales ''
# Install the actual published predecessor, preserve an app-private sentinel,
# then upgrade without uninstalling or clearing data. Matching signer required.
curl -fL --retry 3 -o "$RUNNER_TEMP/sfe-021.apk" https://github.com/manuelmania93-svg/StockFileExplorer/releases/download/v0.2.1/File-Explorer-Control-0.2.1.apk
adb shell am force-stop com.stockfileexplorer.app
adb install -r -d "$RUNNER_TEMP/sfe-021.apk" | tee app/build/reports/device/upgrade-021.txt
adb shell am start -n com.stockfileexplorer.app/.MainActivity
adb shell appops set --uid com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
adb shell run-as com.stockfileexplorer.app mkdir -p files
adb shell run-as com.stockfileexplorer.app touch files/upgrade-proof.txt
adb install -r app/build/outputs/apk/debug/app-debug.apk | tee -a app/build/reports/device/upgrade-021.txt
adb shell run-as com.stockfileexplorer.app ls files/upgrade-proof.txt | tee -a app/build/reports/device/upgrade-021.txt
adb shell dumpsys package com.stockfileexplorer.app | grep -E 'versionCode|versionName' | tee -a app/build/reports/device/upgrade-021.txt
run_test com.stockfileexplorer.app.IndexAndHoldTest#c_productionColdRestartUsesPersistedRows
