package de.selbstgfaltet.polarlicht;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Echte Werte für die Leistungsanzeige, direkt aus dem System gelesen. Was fehlt, bleibt leer statt geraten. */
final class Leistung {
    private static String fanPath;
    private static boolean fanSearched;

    private Leistung() {
    }

    static JSONObject lesen(Context c, String running) throws Exception {
        JSONObject o = new JSONObject();
        o.put("temp", temperatur());
        o.put("gpu", gpuProzent());
        o.put("cpu", cpuProzent());
        o.put("cpuGhz", cpuGhz());
        int gclk = zahl("/sys/class/kgsl/kgsl-3d0/gpuclk");
        if (gclk <= 0) gclk = zahl("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq");
        if (gclk <= 0) gclk = devfreqKgsl();
        int mhz = zahl("/sys/class/kgsl/kgsl-3d0/clock_mhz");
        o.put("gpuMhz", gclk > 0 ? Math.round(gclk / 1e6) : mhz > 0 ? mhz : -1);
        BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int volt = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0); // mV
        long ua = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW); // µA, Vorzeichen je nach Gerät
        boolean laedt = b != null && b.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_CHARGING;
        double watt = Math.abs(ua) / 1e6 * volt / 1e3;
        if (Math.abs(ua) < 1000 && Math.abs(ua) > 0) watt = Math.abs(ua) / 1e3 * volt / 1e3; // manche melden mA
        o.put("watt", watt);
        o.put("laedt", laedt);
        o.put("akku", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
        long charge = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER); // µAh
        if (!laedt && Math.abs(ua) > 50000 && charge > 0) o.put("minuten", Math.round(charge / (double) Math.abs(ua) * 60));
        int rpm = luefter();
        o.put("fan", rpm);
        o.put("fanPfad", fanPath == null ? "" : fanPath);
        o.put("spiel", running == null ? "" : running);
        return o;
    }

    private static String text(String p) {
        try {
            return new String(Files.readAllBytes(new File(p).toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static int zahl(String p) {
        String t = text(p);
        if (t == null) return -1;
        try {
            return Integer.parseInt(t.split("\\s+")[0].replace("%", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    /** Höchste Temperatur von CPU/GPU/SoC-Fühlern, in °C. */
    private static double temperatur() {
        double best = -1;
        File[] zones = new File("/sys/class/thermal").listFiles();
        if (zones == null) return best;
        for (File z : zones) {
            if (!z.getName().startsWith("thermal_zone")) continue;
            String type = text(z.getPath() + "/type");
            if (type == null) continue;
            String t = type.toLowerCase();
            if (!(t.contains("cpu") || t.contains("gpu") || t.contains("soc") || t.contains("tsens"))) continue;
            int v = zahl(z.getPath() + "/temp");
            if (v <= 0) continue;
            double c = v > 1000 ? v / 1000.0 : v;
            if (c > 10 && c < 130 && c > best) best = c;
        }
        return best;
    }

    /** Takt als Annäherung an die Last: aktueller Takt durch Höchsttakt, gemittelt über alle Kerne. */
    private static int cpuProzent() {
        double sum = 0;
        int n = 0;
        for (int i = 0; i < 16; i++) {
            int cur = zahl("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            int max = zahl("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (cur <= 0 || max <= 0) continue;
            sum += cur / (double) max;
            n++;
        }
        return n == 0 ? -1 : (int) Math.round(sum / n * 100);
    }

    private static int gpuProzent() {
        int p = zahl("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
        if (p >= 0) return p;
        String t = text("/sys/class/kgsl/kgsl-3d0/gpubusy"); // "busy total"
        if (t != null) {
            try {
                String[] a = t.trim().split("\\s+");
                long busy = Long.parseLong(a[0]), total = Long.parseLong(a[1]);
                if (total > 0) return (int) Math.round(busy * 100.0 / total);
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    private static int devfreqKgsl() {
        File[] d = new File("/sys/class/devfreq").listFiles();
        if (d == null) return -1;
        for (File f : d) {
            if (f.getName().contains("kgsl") || f.getName().contains("gpu")) {
                int v = zahl(f.getPath() + "/cur_freq");
                if (v > 0) return v;
            }
        }
        return -1;
    }

    /** Höchster aktueller Takt aller Kerne, in GHz. */
    private static double cpuGhz() {
        int best = 0;
        for (int i = 0; i < 16; i++) {
            int cur = zahl("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (cur > best) best = cur;
        }
        return best <= 0 ? -1 : Math.round(best / 1e5) / 10.0;
    }

    /** Lüfter-Drehzahl. Sucht einmal, wo das System sie meldet. */
    private static int luefter() {
        if (!fanSearched) {
            fanSearched = true;
            File[] hw = new File("/sys/class/hwmon").listFiles();
            if (hw != null) {
                for (File h : hw) {
                    File[] fs = h.listFiles();
                    if (fs == null) continue;
                    for (File f : fs) {
                        if (f.getName().matches("fan\\d+_input")) {
                            fanPath = f.getPath();
                            break;
                        }
                    }
                    if (fanPath != null) break;
                }
            }
            if (fanPath == null) {
                String[] tries = {"/sys/devices/platform/fan/fan_speed", "/sys/class/fan/fan/speed", "/sys/devices/platform/gpio-fan/hwmon/hwmon0/fan1_input"};
                for (String t : tries) {
                    if (text(t) != null) {
                        fanPath = t;
                        break;
                    }
                }
            }
        }
        return fanPath == null ? -1 : zahl(fanPath);
    }
}
