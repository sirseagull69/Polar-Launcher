package de.selbstgfaltet.polarlicht;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.StatFs;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Was Ori selbst an WLAN, Bluetooth und Speicher anzeigen und schalten kann, damit dafür keine Android-Seite aufgeht.
 * Ori steckt als privilegierte System-App im OS: WLAN schalten und Netzwerke hinzufügen ist System-Apps erlaubt.
 * Alles, was trotzdem nicht klappt, meldet "nein", dann bietet die Ori-Seite den Weg über Android an.
 */
@SuppressLint("MissingPermission")
final class Systemseiten {
    private static final String TAG = "Polarlicht";
    private final Context c;
    private final Map<String, JSONObject> gefunden = new HashMap<>(); // Bluetooth-Geräte aus der Suche
    private boolean empfaenger = false;
    private Runnable geaendert;

    Systemseiten(Context c) {
        this.c = c.getApplicationContext();
    }

    void beiAenderung(Runnable r) {
        geaendert = r;
    }

    private void melden() {
        if (geaendert != null) geaendert.run();
    }

    /* ---------- WLAN ---------- */

    String wlan() {
        JSONObject o = new JSONObject();
        try {
            WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
            o.put("an", wm.isWifiEnabled());
            WifiInfo info = wm.getConnectionInfo();
            String jetzt = info == null ? "" : ohneAnf(info.getSSID());
            if ("<unknown ssid>".equals(jetzt)) jetzt = "";
            o.put("verbunden", jetzt);
            o.put("ort", ortAn());
            Set<String> gespeichert = new HashSet<>();
            try {
                List<WifiConfiguration> conf = wm.getConfiguredNetworks();
                if (conf != null) for (WifiConfiguration w : conf) gespeichert.add(ohneAnf(w.SSID));
            } catch (RuntimeException e) { /* ohne Recht leer */ }
            Map<String, ScanResult> beste = new HashMap<>();
            try {
                List<ScanResult> rs = wm.getScanResults();
                if (rs != null) for (ScanResult r : rs) {
                    if (r.SSID == null || r.SSID.isEmpty()) continue;
                    ScanResult alt = beste.get(r.SSID);
                    if (alt == null || r.level > alt.level) beste.put(r.SSID, r);
                }
            } catch (RuntimeException e) { /* ohne Standort leer */ }
            List<ScanResult> liste = new ArrayList<>(beste.values());
            liste.sort((a, b) -> b.level - a.level);
            JSONArray netze = new JSONArray();
            for (ScanResult r : liste) {
                JSONObject n = new JSONObject();
                n.put("name", r.SSID);
                n.put("balken", WifiManager.calculateSignalLevel(r.level, 4));
                n.put("offen", !(r.capabilities.contains("WPA") || r.capabilities.contains("WEP") || r.capabilities.contains("SAE")));
                n.put("bekannt", gespeichert.contains(r.SSID));
                n.put("jetzt", r.SSID.equals(jetzt));
                netze.put(n);
            }
            o.put("netze", netze);
        } catch (Exception e) {
            Log.w(TAG, "wlan: " + e);
        }
        return o.toString();
    }

    String wlanSchalten(boolean an) {
        try {
            WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
            boolean ok = wm.setWifiEnabled(an);
            return ok ? "ok" : "nein";
        } catch (RuntimeException e) {
            return "nein";
        }
    }

    String wlanSuchen() {
        try {
            if (!ortAn()) ortEinschalten();
            WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
            empfangen();
            return wm.startScan() ? "ok" : "nein";
        } catch (RuntimeException e) {
            return "nein";
        }
    }

    /** Mit einem Netz verbinden. Bekannte Netze gehen ohne Passwort, neue werden mit Passwort hinzugefügt. */
    String wlanVerbinden(String name, String passwort) {
        try {
            WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
            int id = -1;
            List<WifiConfiguration> conf = wm.getConfiguredNetworks();
            if (conf != null) for (WifiConfiguration w : conf) if (name.equals(ohneAnf(w.SSID))) id = w.networkId;
            if (id < 0 || (passwort != null && !passwort.isEmpty())) {
                WifiConfiguration w = new WifiConfiguration();
                w.SSID = "\"" + name + "\"";
                if (passwort == null || passwort.isEmpty()) {
                    w.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                } else {
                    w.preSharedKey = "\"" + passwort + "\"";
                }
                id = wm.addNetwork(w);
            }
            if (id < 0) return "nein";
            wm.disconnect();
            boolean ok = wm.enableNetwork(id, true);
            wm.reconnect();
            return ok ? "ok" : "nein";
        } catch (RuntimeException e) {
            Log.w(TAG, "wlanVerbinden: " + e);
            return "nein";
        }
    }

    String wlanVergessen(String name) {
        try {
            WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
            List<WifiConfiguration> conf = wm.getConfiguredNetworks();
            if (conf != null) for (WifiConfiguration w : conf) if (name.equals(ohneAnf(w.SSID))) wm.removeNetwork(w.networkId);
            return "ok";
        } catch (RuntimeException e) {
            return "nein";
        }
    }

    private boolean ortAn() {
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            return lm.isLocationEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** WLAN-Suche liefert unter Android nur mit eingeschaltetem Standort Ergebnisse. Ori darf ihn als System-App setzen. */
    private void ortEinschalten() {
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            UserHandle me = android.os.Process.myUserHandle();
            LocationManager.class.getMethod("setLocationEnabledForUser", boolean.class, UserHandle.class).invoke(lm, true, me);
        } catch (Exception e) {
            try {
                Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
            } catch (RuntimeException e2) {
                Log.w(TAG, "Standort: " + e2);
            }
        }
    }

    /* ---------- Bluetooth ---------- */

    private BluetoothAdapter bt() {
        BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    String bluetooth() {
        JSONObject o = new JSONObject();
        try {
            BluetoothAdapter a = bt();
            o.put("da", a != null);
            if (a == null) return o.toString();
            o.put("an", a.isEnabled());
            o.put("sucht", a.isDiscovering());
            JSONArray gekoppelt = new JSONArray();
            Set<String> schon = new HashSet<>();
            Set<BluetoothDevice> b = a.getBondedDevices();
            if (b != null) for (BluetoothDevice d : b) {
                JSONObject n = new JSONObject();
                n.put("name", name(d));
                n.put("adr", d.getAddress());
                n.put("art", art(d));
                n.put("verbunden", verbunden(d));
                gekoppelt.put(n);
                schon.add(d.getAddress());
            }
            o.put("gekoppelt", gekoppelt);
            JSONArray neu = new JSONArray();
            synchronized (gefunden) {
                for (JSONObject n : gefunden.values()) if (!schon.contains(n.optString("adr"))) neu.put(n);
            }
            o.put("gefunden", neu);
        } catch (Exception e) {
            Log.w(TAG, "bluetooth: " + e);
            try { o.put("fehlt", true); } catch (Exception ignored) { /* egal */ }
        }
        return o.toString();
    }

    String btSchalten(boolean an) {
        try {
            BluetoothAdapter a = bt();
            if (a == null) return "nein";
            boolean ok = an ? a.enable() : a.disable();
            return ok ? "ok" : "nein";
        } catch (RuntimeException e) {
            return "nein";
        }
    }

    String btSuchen() {
        try {
            BluetoothAdapter a = bt();
            if (a == null || !a.isEnabled()) return "nein";
            empfangen();
            synchronized (gefunden) { gefunden.clear(); }
            if (a.isDiscovering()) a.cancelDiscovery();
            return a.startDiscovery() ? "ok" : "nein";
        } catch (RuntimeException e) {
            return "nein";
        }
    }

    String btKoppeln(String adr) {
        try {
            BluetoothAdapter a = bt();
            if (a == null) return "nein";
            a.cancelDiscovery();
            return a.getRemoteDevice(adr).createBond() ? "ok" : "nein";
        } catch (RuntimeException e) {
            return "nein";
        }
    }

    String btEntkoppeln(String adr) {
        try {
            BluetoothDevice d = bt().getRemoteDevice(adr);
            Object r = d.getClass().getMethod("removeBond").invoke(d);
            return Boolean.TRUE.equals(r) ? "ok" : "nein";
        } catch (Exception e) {
            return "nein";
        }
    }

    private static String name(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null || n.isEmpty() ? d.getAddress() : n;
        } catch (RuntimeException e) {
            return d.getAddress();
        }
    }

    private static String art(BluetoothDevice d) {
        try {
            int k = d.getBluetoothClass() == null ? 0 : d.getBluetoothClass().getMajorDeviceClass();
            switch (k) {
                case 0x0400: return "kopfhoerer";
                case 0x0500: return "eingabe";
                case 0x0200: return "telefon";
                case 0x0100: return "computer";
                default: return "geraet";
            }
        } catch (RuntimeException e) {
            return "geraet";
        }
    }

    private static boolean verbunden(BluetoothDevice d) {
        try {
            Object r = d.getClass().getMethod("isConnected").invoke(d);
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized void empfangen() {
        if (empfaenger) return;
        empfaenger = true;
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                if (BluetoothDevice.ACTION_FOUND.equals(i.getAction())) {
                    BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (d != null) {
                        try {
                            String n = name(d);
                            if (!n.equals(d.getAddress())) { // Geräte ohne Namen zeigen wir nicht, die findet niemand wieder
                                JSONObject o = new JSONObject();
                                o.put("name", n);
                                o.put("adr", d.getAddress());
                                o.put("art", art(d));
                                synchronized (gefunden) { gefunden.put(d.getAddress(), o); }
                            }
                        } catch (Exception ignored) { /* nächstes Gerät */ }
                    }
                }
                melden();
            }
        };
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            c.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        } else {
            c.registerReceiver(r, f);
        }
    }

    /** Einmal beim Start: damit WLAN- und Bluetooth-Änderungen die offene Ori-Seite auffrischen. */
    void start() {
        try { empfangen(); } catch (RuntimeException e) { Log.w(TAG, "Empfänger: " + e); }
    }

    /* ---------- Flugmodus, Bildwiederholrate ---------- */

    String flug(boolean an) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.getClass().getMethod("setAirplaneMode", boolean.class).invoke(cm, an);
            return "ok";
        } catch (Exception e) {
            Log.w(TAG, "flug: " + e);
            return "nein";
        }
    }

    String hz(int hz) {
        try {
            Settings.System.putFloat(c.getContentResolver(), "peak_refresh_rate", hz);
            Settings.System.putFloat(c.getContentResolver(), "min_refresh_rate", hz);
            return "ok";
        } catch (RuntimeException e) {
            return "nein";
        }
    }

    int hzJetzt() {
        try {
            return Math.round(Settings.System.getFloat(c.getContentResolver(), "peak_refresh_rate", 120f));
        } catch (RuntimeException e) {
            return 120;
        }
    }

    /* ---------- Speicher ---------- */

    String speicher() {
        JSONObject o = new JSONObject();
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long gesamt = fs.getTotalBytes(), frei = fs.getAvailableBytes();
            o.put("gesamt", gesamt);
            o.put("frei", frei);
            File intern = Environment.getExternalStorageDirectory();
            long medien = groesse(new File(intern, "DCIM"), 4) + groesse(new File(intern, "Pictures"), 4) + groesse(new File(intern, "Movies"), 4) + groesse(new File(intern, "Music"), 4);
            long downloads = groesse(new File(intern, "Download"), 4);
            long spieleIntern = groesse(new File(intern, "ROMs"), 6);
            o.put("medien", medien);
            o.put("downloads", downloads);
            o.put("spieleIntern", spieleIntern);
            JSONArray karten = new JSONArray();
            StorageManager sm = (StorageManager) c.getSystemService(Context.STORAGE_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 30) for (StorageVolume v : sm.getStorageVolumes()) {
                if (v.isPrimary() || v.getDirectory() == null) continue;
                File d = v.getDirectory();
                StatFs k = new StatFs(d.getPath());
                JSONObject n = new JSONObject();
                n.put("name", v.getDescription(c));
                n.put("gesamt", k.getTotalBytes());
                n.put("frei", k.getAvailableBytes());
                n.put("spiele", groesse(new File(d, "ROMs"), 6));
                karten.put(n);
            }
            o.put("karten", karten);
        } catch (Exception e) {
            Log.w(TAG, "speicher: " + e);
        }
        return o.toString();
    }

    private static long groesse(File f, int tiefe) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        if (tiefe <= 0) return 0;
        long s = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) s += groesse(k, tiefe - 1);
        return s;
    }

    private static String ohneAnf(String s) {
        if (s == null) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }
}
