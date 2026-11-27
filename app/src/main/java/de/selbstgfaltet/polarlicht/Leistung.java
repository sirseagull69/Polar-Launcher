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
        // Echte Last kann nur das System (/proc/stat) messen, das liefert orid ab dem nächsten OS-Bild.
        // cpuidle zählt laufende Leerlaufphasen nicht mit und zeigt viel zu hohe Werte, deshalb lieber "–".
        o.put("cpu", -1);
        o.put("cpuTakt", cpuProzent());
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
        o.put("strom", b != null && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
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
        JSONObject b2 = brueckeStatus(c);
        if (b2 != null) {
            o.put("bruecke", true);
            int cl = num(b2.optString("cpu"));
            if (cl >= 0) o.put("cpu", cl);
            int g = num(b2.optString("gpu"));
            if (g >= 0) o.put("gpu", g);
            int clk = num(b2.optString("gpuclk"));
            if (clk > 0) o.put("gpuMhz", Math.round(clk / 1e6));
            int f = num(b2.optString("fan"));
            if (f >= 0) { o.put("fan", f); o.put("fanPfad", b2.optString("fanPath")); }
        }
        return o;
    }

    private static int num(String t) {
        if (t == null || t.isEmpty()) return -1;
        try {
            return Integer.parseInt(t.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /** Was die Systembrücke orid zuletzt gemeldet hat, wenn es frisch ist. */
    static JSONObject brueckeStatus(Context c) {
        File f = new File(c.getFilesDir(), "ori-status.json");
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 6000) return null;
        try {
            return new JSONObject(text(f.getPath()));
        } catch (Exception e) {
            return null;
        }
    }

    /** Wunsch an orid: Zeilen wie "w /sys/... wert". orid setzt sie innerhalb einer Sekunde um. */
    static boolean anBruecke(Context c, String zeilen) {
        try {
            File tmp = new File(c.getFilesDir(), "ori-ctl.tmp");
            Files.write(tmp.toPath(), zeilen.getBytes(StandardCharsets.UTF_8));
            return tmp.renameTo(new File(c.getFilesDir(), "ori-ctl"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Leistungsprofil: Höchsttakt je CPU-Cluster und GPU als Anteil vom Maximum. */
    static String profil(Context c, String name, double[] eigene) {
        JSONObject b = brueckeStatus(c);
        if (b == null) return "keine-bruecke";
        double cpu, gpu;
        switch (name) {
            case "Sparsam": cpu = 0.55; gpu = 0.5; break;
            case "Leistung": cpu = 1.0; gpu = 0.85; break;
            case "Turbo": cpu = 1.0; gpu = 1.0; break;
            case "Eigene": cpu = -1; gpu = eigene != null && eigene.length > 3 ? eigene[3] : 1; break;
            default: cpu = 0.8; gpu = 0.7; break;
        }
        StringBuilder sb = new StringBuilder();
        String[] pols = b.optString("policies").trim().split("\\s+");
        int i = 0;
        for (String p : pols) {
            String[] a = p.split(":"); // policy0:0,1,2:min-max:cur
            if (a.length < 3) continue;
            String[] mm = a[2].split("-");
            if (mm.length < 2) continue;
            long max = Long.parseLong(mm[1].trim());
            double share = cpu >= 0 ? cpu : (eigene != null && i < eigene.length ? eigene[i] : 1);
            sb.append("w /sys/devices/system/cpu/cpufreq/").append(a[0]).append("/scaling_max_freq ").append(Math.round(max * share)).append('\n');
            i++;
        }
        String[] gf = b.optString("gpuFreqs").trim().split("\\s+");
        long gmax = 0;
        for (String g : gf) {
            try { gmax = Math.max(gmax, Long.parseLong(g)); } catch (Exception e) { /* leer */ }
        }
        if (gmax > 0) {
            long want = Math.round(gmax * gpu), pick = gmax;
            for (String g : gf) {
                try { long v = Long.parseLong(g); if (v >= want && v < pick) pick = v; } catch (Exception e) { /* leer */ }
            }
            sb.append("w /sys/class/kgsl/kgsl-3d0/max_gpuclk ").append(pick).append('\n');
        }
        return anBruecke(c, sb.toString()) ? "ok" : "fehler";
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

    private static long prevIdle = -1, prevWall = -1;
    private static int lastLoad = -1;

    /** Echte Auslastung: Leerlaufzeit aller Kerne (cpuidle) gegen die vergangene Zeit. */
    private static int cpuLast() {
        long idle = 0;
        int n = 0;
        for (int i = 0; i < 16; i++) {
            File[] st = new File("/sys/devices/system/cpu/cpu" + i + "/cpuidle").listFiles();
            if (st == null) continue;
            boolean any = false;
            for (File f : st) {
                if (!f.getName().startsWith("state")) continue;
                String t = text(f.getPath() + "/time");
                if (t == null) continue;
                try { idle += Long.parseLong(t.trim()); any = true; } catch (Exception e) { /* weiter */ }
            }
            if (any) n++;
        }
        if (n == 0) return -1;
        long wall = System.nanoTime() / 1000;
        if (prevIdle >= 0 && wall - prevWall > 300000) {
            double busy = 1.0 - (idle - prevIdle) / (double) ((wall - prevWall) * n);
            lastLoad = (int) Math.round(Math.max(0, Math.min(1, busy)) * 100);
        }
        if (prevIdle < 0 || wall - prevWall > 300000) { prevIdle = idle; prevWall = wall; }
        return lastLoad;
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

    /** Stellgröße des Lüfters: liegt im selben hwmon-Ordner wie die Drehzahl. Sonst der bekannte Pfad vom Thor. */
    static String pwmPfad(Context c) {
        luefter();
        String p = fanPath;
        JSONObject b = brueckeStatus(c);
        if (b != null && !b.optString("fanPath").isEmpty()) p = b.optString("fanPath");
        if (p != null && p.matches("/sys/class/hwmon/hwmon\\d+/fan\\d+_input")) {
            return p.substring(0, p.lastIndexOf('/')) + "/pwm1";
        }
        return "/sys/class/hwmon/hwmon0/pwm1";
    }

    private static String kuehlPfad;

    /** Kühl-Regler des Lüfters: der mit dem Typ pwm-fan (so heißt er auf dem Thor). Sonst die bisher feste Nummer 28. */
    static String kuehlPfad() {
        if (kuehlPfad != null) return kuehlPfad;
        String gefunden = null;
        File[] ds = new File("/sys/class/thermal").listFiles();
        if (ds != null) {
            for (File d : ds) {
                if (!d.getName().matches("cooling_device\\d+")) continue;
                if ("pwm-fan".equals(text(d.getPath() + "/type"))) { gefunden = d.getPath() + "/cur_state"; break; }
            }
        }
        kuehlPfad = gefunden != null ? gefunden : "/sys/class/thermal/cooling_device28/cur_state";
        return kuehlPfad;
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
