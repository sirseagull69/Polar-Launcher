#!/system/bin/sh
# Auf dem Thor ausführen (adb shell sh /data/local/tmp/geraet-check.sh). Sammelt, was für die offenen Punkte fehlt.
P=de.selbstgfaltet.polarlicht
echo "== Version"; dumpsys package $P | grep -m2 -E "versionName|versionCode"; getprop ro.lineage.version; getprop ro.build.date
echo "== Lüfter: hwmon"; for h in /sys/class/hwmon/hwmon*; do echo "$h $(cat $h/name 2>/dev/null) $(ls $h | grep -E 'fan|pwm' | tr '\n' ' ')"; done
echo "== Lüfter: Kühl-Regler (welcher ist Nr. 28?)"; for c in /sys/class/thermal/cooling_device*; do echo "$c $(cat $c/type 2>/dev/null) $(cat $c/cur_state 2>/dev/null)/$(cat $c/max_state 2>/dev/null)"; done | grep -iE "fan|device28 "
echo "== Rechte, die fehlen"; dumpsys package $P | grep -E "android.permission.*granted=false"
echo "== Mitteilungen-Zuhörer"; settings get secure enabled_notification_listeners
echo "== orid"; ps -A | grep -i orid; ls -l /data/data/$P/files/ori-status.json 2>/dev/null; cat /data/data/$P/files/ori-status.json 2>/dev/null | head -c 600; echo
echo "== Versteckte Funktionen (forceStopPackage)"; logcat -d -t 3000 | grep -iE "hidden (api|method)|forceStopPackage" | tail -5
echo "== Abstürze und Ruckler von Ori"; logcat -d -t 5000 | grep -E "Polarlicht|AndroidRuntime.*$P|Skipped [0-9]+ frames" | tail -25
echo "== Bildrate Ori"; dumpsys gfxinfo $P | grep -E "Total frames|Janky frames|90th|95th|99th" | head -12
