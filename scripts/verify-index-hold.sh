#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/reports/device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
FAILED=0
# Every class runs even if an earlier one fails, so one failure cannot hide the others; the script still exits non-zero.
run_test() {
  adb shell am instrument -w -r -e class "$1" com.stockfileexplorer.app.test/androidx.test.runner.AndroidJUnitRunner | tee app/build/reports/device/instrumentation-last.txt || true
  adb pull /sdcard/Download/ app/build/reports/device/ || true
  if grep -q 'OK (' app/build/reports/device/instrumentation-last.txt; then echo "TEST_RESULT PASS $1"; else echo "TEST_RESULT FAIL $1"; FAILED=1; fi
}
run_test com.stockfileexplorer.app.IndexAndHoldTest#a_largeIndexFirstBenchmarkAndPersistence,com.stockfileexplorer.app.IndexAndHoldTest#b_actualHoldAcrossAllViewsAndSelect
adb shell am force-stop com.stockfileexplorer.app
run_test com.stockfileexplorer.app.IndexAndHoldTest#c_productionColdRestartUsesPersistedRows
run_test com.stockfileexplorer.app.StorageAnalysisInstrumentedTest#storageAnalysisShowsRankedResults
run_test com.stockfileexplorer.app.SortAndSpeedTest
adb pull /sdcard/Download/ app/build/reports/device/
run_test com.stockfileexplorer.app.RestrictedOperationsTest
run_test com.stockfileexplorer.app.FileActionsInstrumentedTest
adb pull /sdcard/Download/ app/build/reports/device/
if [ "$SFE_TEST_API" = "35" ]; then
adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales de-AT
run_test com.stockfileexplorer.app.FileActionsInstrumentedTest#germanLibraryMenuUsesLocalizedActions
run_test com.stockfileexplorer.app.IndexAndHoldTest#b_actualHoldAcrossAllViewsAndSelect
adb pull /sdcard/Download/ app/build/reports/device/
run_test com.stockfileexplorer.app.StorageAnalysisInstrumentedTest#storageAnalysisShowsRankedResults
run_test com.stockfileexplorer.app.SortAndSpeedTest#b_librarySortControlAndVideoFrameThumbnail
run_test com.stockfileexplorer.app.SortAndSpeedTest#c_analysisRowsAreClickable
run_test com.stockfileexplorer.app.SortAndSpeedTest#d_dashboardAndPapierkorbMoveRestoreDelete
run_test com.stockfileexplorer.app.SortAndSpeedTest#e_analysisSpeedFirstResultsAndExactDuplicates
run_test com.stockfileexplorer.app.SortAndSpeedTest#f_trashSafetyRules
run_test com.stockfileexplorer.app.SortAndSpeedTest#g_viewerSwipeCountAndDeleteToBin
adb pull /sdcard/Download/ app/build/reports/device/
fi
exit $FAILED
