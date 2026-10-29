#!/usr/bin/env bash
set -euo pipefail
P=com.stockfileexplorer.app
L=out/upgrade.txt
adb install out/v0.2.4.apk | tee $L
adb shell appops set --uid $P MANAGE_EXTERNAL_STORAGE allow
adb shell am start -W -n $P/.MainActivity | tee -a $L
sleep 8
adb shell run-as $P mkdir -p files
adb shell "run-as $P sh -c 'echo sentinel-024 > files/upgrade-proof.txt'"
echo "== before" | tee -a $L
adb shell dumpsys package $P | grep -E 'versionCode|versionName' | tee -a $L
adb shell am force-stop $P
adb logcat -c
adb install -r out/v0.2.5.apk | tee -a $L
echo "== after" | tee -a $L
adb shell dumpsys package $P | grep -E 'versionCode|versionName' | tee -a $L
adb shell run-as $P cat files/upgrade-proof.txt | tee -a $L
adb shell am start -W -n $P/.MainActivity | tee -a $L
sleep 10
adb shell pidof $P | tee -a $L
adb shell dumpsys activity activities | grep -m3 -E 'topResumedActivity|mResumedActivity' | tee -a $L
adb exec-out screencap -p > out/after-upgrade.png
adb logcat -d -b crash > out/crash.txt || true
adb logcat -d | grep -E 'FATAL EXCEPTION|ANR in' > out/fatal.txt || true
grep -q 'versionName=0.2.5' $L
grep -q sentinel-024 $L
test -n "$(adb shell pidof $P)"
test ! -s out/fatal.txt
