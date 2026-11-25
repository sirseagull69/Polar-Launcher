#!/usr/bin/env bash
# Startet die frisch gebaute APK im Emulator und legt Bildschirmfotos nach shots/.
set -x
PKG=de.selbstgfaltet.polarlicht
mkdir -p shots
adb install -r polarlicht.apk
adb shell wm size 1920x1080 || true
adb shell wm density 400 || true
adb shell cmd uimode night yes || true
# Einrichtung überspringen, sonst käme erst der Willkommen-Bildschirm
printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"ui:setup\">1</string>\n</map>\n" > /tmp/polarlicht.xml
adb shell "run-as $PKG mkdir -p shared_prefs" || true
adb shell "run-as $PKG sh -c 'cat > shared_prefs/polarlicht.xml'" < /tmp/polarlicht.xml || true
adb logcat -c || true
adb shell am start -n $PKG/.MainActivity
sleep 14
adb exec-out screencap -p > shots/1-start.png
adb shell input keyevent DPAD_RIGHT; sleep 0.3; adb shell input keyevent DPAD_RIGHT; sleep 1.5
adb exec-out screencap -p > shots/2-geblaettert.png
adb shell input keyevent DPAD_DOWN; sleep 0.3; adb shell input keyevent DPAD_RIGHT; sleep 1.5
adb exec-out screencap -p > shots/3-leiste.png
# Zweiter Bildschirm (wie der untere am Thor)
adb shell settings put global overlay_display_devices "1240x1080/320" || true
sleep 4
adb shell am force-stop $PKG
adb shell am start -n $PKG/.MainActivity
sleep 12
adb shell dumpsys SurfaceFlinger --display-id > shots/displays.txt 2>&1 || true
for id in $(grep -o 'Display [0-9]*' shots/displays.txt | awk '{print $2}'); do
  adb exec-out screencap -d "$id" -p > "shots/display-$id.png" || true
done
adb exec-out screencap -p > shots/4-mit-zweitem.png || true
adb logcat -d -s Polarlicht:V AndroidRuntime:E > shots/log.txt || true
adb shell "run-as $PKG cat shared_prefs/polarlicht.xml" > shots/prefs.xml 2>&1 || true
exit 0
