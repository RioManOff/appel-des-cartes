#!/bin/bash
# Lancé dans l'émulateur Android du test automatique (workflow widget-test.yml)
mkdir -p out
A=android/app/build/outputs/apk
adb install -r $A/debug/app-debug.apk
adb install -r $A/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.appeldescartes.app.AdcWidgetTest com.appeldescartes.app.test/androidx.test.runner.AndroidJUnitRunner > out/instrument.txt 2>&1
cat out/instrument.txt | tail -60
for f in $(adb shell run-as com.appeldescartes.app ls files | tr -d '\r'); do
  case "$f" in *.png|*.txt) adb exec-out run-as com.appeldescartes.app cat "files/$f" > "out/$f";; esac
done
adb exec-out screencap -p > out/ecran_fin.png
adb logcat -d > out/logcat_complet.txt
grep -iE "AdcWidget|Capacitor|AndroidRuntime|FATAL" out/logcat_complet.txt | tail -400 > out/logcat.txt
ls -la out
if grep -qE "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_CODE: 0" out/instrument.txt; then echo "TESTS EN ECHEC"; exit 1; fi
echo "TESTS OK"
