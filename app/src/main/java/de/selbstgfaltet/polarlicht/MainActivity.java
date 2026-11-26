package de.selbstgfaltet.polarlicht;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Polarlicht. Die Oberfläche kommt aus dem Design-Canvas (assets/ui) und läuft in zwei Web-Ansichten:
 * oben für den Controller, unten für Touch. Diese Klasse liefert die echten Daten und führt aus,
 * was die Oberfläche auslöst.
 */
public class MainActivity extends ComponentActivity implements DisplayManager.DisplayListener, Updater.Listener {
    private static final String TAG = "Polarlicht";
    private static final String BASE = "file:///android_asset/ui/host.html?side=";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayList<String> stack = new ArrayList<>();
    private final HashMap<String, String> pages = new HashMap<>();
    private final Bridge bridge = new Bridge();
    private DisplayManager displayManager;
    private SharedPreferences prefs;
    private Apps apps;
    private Updater updater;
    private WebView top;
    private PanelPresentation panel;
    private Systemseiten sys;
    private String current = "";
    private String query = "";
    private boolean topReady = false;
    private boolean longFired = false;
    private String lastKey = "";
    private long lastKeyAt = 0L;
    private String stickDir = "";
    private String holdDir = "";
    private long holdSince = 0L;
    private final HashMap<String, byte[]> tiles = new HashMap<>();
    /** Neuer Startbildschirm (Compose). null, wenn er abgeschaltet ist: dann läuft überall die Web-Ansicht. */
    HomeUi home;
    private View homeTop;
    volatile boolean homeShowing = false;
    private boolean started = false;
    private boolean bottomFocus = false;
    private volatile long keysQuietUntil = 0;
    private boolean lockOverGame = false;
    private final android.content.BroadcastReceiver screenOn = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            if (!"0".equals(prefs.getString("ui:sw:Ori-Sperrbildschirm", "1"))) {
                lockOverGame = !started;
                if (!started) {
                    startActivity(new Intent(MainActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                }
                ui.postDelayed(() -> both("window.plLock&&plLock()"), 120);
            }
        }
    };
    private int homeTotal = 0;
    private final Roms roms = new Roms();
    private volatile List<Roms.Game> games = new ArrayList<>();
    private volatile boolean scanning = false;
    private long lastScan = 0L;
    private boolean l2Down = false;
    private boolean r2Down = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("polarlicht", MODE_PRIVATE);
        apps = new Apps(this);
        apps.reload();
        updater = new Updater(this, this);
        sys = new Systemseiten(this);
        sys.beiAenderung(() -> ui.post(() -> both("window.plSys&&plSys()")));
        sys.start();
        OriMitteilungen.freischalten(this);
        OriMitteilungen.geaendert = () -> ui.post(() -> both("window.plMit&&plMit()"));
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        int pending = prefs.getInt("pendingBuild", 0);
        if (pending > 0 && updater.localBuild() >= pending) {
            prefs.edit().remove("pendingBuild").apply();
            current = "6.6";
        } else if (firstStartSinceBoot()) {
            current = "1.18";
        } else {
            current = prefs.getString("ui:setup", "").equals("1") ? homeKey() : "1.7";
        }
        top = makeWeb(this, "oben");
        FrameLayout root = new FrameLayout(this);
        root.addView(top, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        guardHome();
        // Der native Startbildschirm (dunkler Material-Look) ist seit 0.11 abgeschaltet: Vorgabe ist der Ori-OS-Look der Web-Ansicht.
        // Wieder einschalten: Einstellung "nativ" auf 1.
        if ("1".equals(prefs.getString("ui:nativ", "0")) && prefs.getInt("homeCrashBuild", -1) != updater.localBuild()) {
            try {
                home = new HomeUi(homeHost);
                homeTop = home.topView(this);
                homeTop.setVisibility(View.GONE);
                root.addView(homeTop, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            } catch (Throwable t) {
                Log.w(TAG, "Startbildschirm neu: " + t);
                home = null;
                homeTop = null;
            }
        }
        setContentView(root);
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        registerReceiver(new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context c, Intent i) {
                /* Nach dem Entsperren das untere Fenster neu aufbauen: sonst bleibt es hinter der Android-Sperre und nimmt keinen Touch an */
                ui.postDelayed(() -> {
                    hidePanel();
                    if (started) showPanel();
                }, 300);
            }
        }, new IntentFilter(Intent.ACTION_USER_PRESENT));
        if (!"0".equals(prefs.getString("ui:sw:Ori-Sperrbildschirm", "1"))) {
            setShowWhenLocked(true); // Ori zeigt seinen eigenen Sperrbildschirm über dem von Android
        }
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("keyguardOff", false)) {
            try {
                /* Ohne PIN zeigt Android sonst seinen eigenen Sperrbildschirm vor dem von Ori */
                Settings.Secure.putInt(getContentResolver(), "lockscreen.disabled", 1);
                prefs.edit().putBoolean("keyguardOff", true).apply();
            } catch (RuntimeException e) {
                Log.w(TAG, "Sperrbildschirm: " + e);
            }
        }
        updater.check(false);
        scanGames();
        /* Nach dem Start die gemerkten Einstellungen wieder setzen (das System vergisst Takt und Lüfter beim Neustart) */
        Runnable wieder = () -> {
            String pr = prefs.getString("profil", "");
            if (!pr.isEmpty()) Leistung.profil(this, pr, null);
            String fn = prefs.getString("fan", "");
            if (!fn.isEmpty() && !"Automatisch".equals(fn)) bridge.fan(fn);
            both("window.plRgb&&plRgb()");
        };
        ui.postDelayed(wieder, 8000);
        ui.postDelayed(wieder, 25000);
    }

    /**
     * Sicherung: stürzt die App ab, solange der neue Startbildschirm eingeschaltet ist, wird er für diesen Build
     * abgeschaltet. Beim nächsten Start läuft dann wieder die Web-Ansicht, der Launcher bleibt benutzbar.
     */
    private void guardHome() {
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                if (home != null) {
                    prefs.edit().putInt("homeCrashBuild", updater.localBuild()).commit();
                }
            } catch (Throwable ignored) {
                // dann eben ohne Merker
            }
            if (old != null) {
                old.uncaughtException(thread, error);
            }
        });
    }

    private boolean isHome(String key) {
        return home != null && key.matches("2\\.[124567]");
    }

    /** Die Kacheln der Startreihe: Favoriten zuerst, dann zuletzt gestartet. */
    private List<HomeItem> homeItems() {
        List<HomeItem> out = new ArrayList<>();
        synchronized (apps) {
            for (Apps.Entry e : apps.all) {
                out.add(new HomeItem(e.key(), e.label, "App", "", e.fav, e.last, false));
            }
        }
        Set<String> favs = prefs.getStringSet("gfavs", new HashSet<String>());
        for (Roms.Game g : games) {
            out.add(new HomeItem(g.key(), g.label, g.sysFull, g.sys.toUpperCase(Locale.ROOT), favs.contains(g.key()),
                    prefs.getLong("last:" + g.key(), 0L), true));
        }
        Collections.sort(out, (a, b) -> {
            if (a.fav != b.fav) {
                return a.fav ? -1 : 1;
            }
            if (a.last != b.last) {
                return a.last > b.last ? -1 : 1;
            }
            if (a.game != b.game) {
                return a.game ? -1 : 1;
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        homeTotal = out.size();
        return new ArrayList<>(out.subList(0, Math.min(12, out.size())));
    }

    private void syncHome() {
        if (home != null && homeShowing) {
            home.set(homeItems(), homeTotal, prefs.getString("ui:sel", ""));
        }
    }

    private final HomeHost homeHost = new HomeHost() {
        @Override
        public Bitmap picture(String key) {
            try {
                Roms.Game g = game(key);
                if (g != null) {
                    String p = g.cover != null ? g.cover : g.art;
                    if (p == null) {
                        File own = imageFile(new File(g.path).getName());
                        p = own == null ? null : own.getPath();
                    }
                    return p == null ? null : decodeSmall(p);
                }
                Apps.Entry e = find(key);
                if (e == null) {
                    return null;
                }
                File f = imageFile(e.pkg);
                return f != null ? decodeSmall(f.getPath()) : tileBitmap(e);
            } catch (Throwable t) {
                Log.w(TAG, "Kachelbild " + key + ": " + t);
                return null;
            }
        }

        @Override
        public String stateJson() {
            return bridge.state();
        }

        @Override
        public void launch(String key) {
            bridge.launch(key);
        }

        @Override
        public void open(String screen) {
            ui.post(() -> MainActivity.this.open(screen, true));
        }

        @Override
        public void details(String key, String screen) {
            prefs.edit().putString("ui:sel", key).apply();
            ui.post(() -> MainActivity.this.open(screen, true));
        }

        @Override
        public void selected(String key) {
            prefs.edit().putString("ui:sel", key).apply();
            // Unten soll sofort dasselbe Spiel stehen wie oben
            ui.post(() -> { if (panel != null && panel.web != null) panel.web.evaluateJavascript("window.plSync&&plSync('')", null); });
        }

        @Override
        public void fav(String key) {
            bridge.fav(key);
            ui.post(() -> syncHome());
        }

        @Override
        public void android(String what) {
            ui.post(() -> openAndroid(what));
        }

        @Override
        public void brightness(int percent) {
            ui.post(() -> setBrightness(percent));
        }

        @Override
        public void volume(int percent) {
            bridge.setVolume(percent);
        }

        @Override
        public void sound(String name) {
            if (top != null && topReady && name.matches("[a-z]{1,12}")) {
                top.evaluateJavascript("window.plSnd&&plSnd('" + name + "')", null);
            }
        }
    };

    private static Bitmap decodeSmall(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 400) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, options);
    }

    /** Wahr beim ersten Öffnen nach dem Einschalten des Geräts. */
    private boolean firstStartSinceBoot() {
        int boots = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boots < 0 || prefs.getInt("introBoot", -2) == boots) {
            return false;
        }
        prefs.edit().putInt("introBoot", boots).apply();
        return true;
    }

    WebView makeWeb(Context context, final String side) {
        WebView web = new WebView(context);
        web.setBackgroundColor(0xFF0A0D14);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setFocusable(false);
        web.setHapticFeedbackEnabled(false);
        web.setOnLongClickListener(v -> true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        web.addJavascriptInterface(bridge, "PL");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"polarlicht.local".equals(uri.getHost())) {
                    return null;
                }
                return picture(uri.getPath(), uri.getQueryParameter("k"));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }
        });
        web.loadUrl(BASE + side);
        return web;
    }


    private List<File> medienCache = null;
    private long medienCacheAt = 0;

    /** Bildschirmfotos und Clips: Screenshot-Ordner intern und auf der microSD-Karte, neueste zuerst. */
    private synchronized List<File> medienDateien() {
        if (medienCache != null && System.currentTimeMillis() - medienCacheAt < 10000) return medienCache;
        List<File> roots = new ArrayList<>();
        roots.add(Environment.getExternalStorageDirectory());
        File[] ext = getExternalFilesDirs(null);
        if (ext != null) for (File e : ext) {
            if (e == null) continue;
            String p = e.getPath();
            int a = p.indexOf("/Android/");
            if (a > 0) {
                File r = new File(p.substring(0, a));
                if (!roots.contains(r)) roots.add(r);
            }
        }
        List<File> out = new ArrayList<>();
        String[] dirs = {"Pictures/Screenshots", "DCIM/Screenshots", "Pictures", "DCIM/Camera", "Movies", "DCIM/ScreenRecorder", "Movies/Screenrecords", "Pictures/Ori"};
        Set<String> seen = new HashSet<>();
        for (File r : roots) for (String d : dirs) {
            File[] fs = new File(r, d).listFiles();
            if (fs == null) continue;
            for (File f : fs) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!f.isFile() || n.startsWith(".")) continue;
                if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".mp4"))) continue;
                if (seen.add(f.getAbsolutePath())) out.add(f);
            }
        }
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        if (out.size() > 200) out = new ArrayList<>(out.subList(0, 200));
        medienCache = out;
        medienCacheAt = System.currentTimeMillis();
        return out;
    }

    /** App-Symbol oder Hintergrundbild für die Oberfläche. */
    private WebResourceResponse picture(String path, String key) {
        try {
            if ("/foto".equals(path) || "/fotogross".equals(path)) {
                List<File> f = medienDateien();
                int i = Integer.parseInt(key);
                if (i < 0 || i >= f.size()) return null;
                File x = f.get(i);
                Bitmap b;
                if (x.getName().toLowerCase(Locale.ROOT).endsWith(".mp4") && "/fotogross".equals(path)) {
                    return new WebResourceResponse("video/mp4", null, new FileInputStream(x));
                } else if (x.getName().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
                    b = android.media.ThumbnailUtils.createVideoThumbnail(x, new android.util.Size(480, 270), null);
                } else if ("/foto".equals(path)) {
                    b = decodeSmall(x.getPath());
                } else {
                    return new WebResourceResponse(x.getName().toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg", null, new FileInputStream(x));
                }
                if (b == null) return null;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG, 85, out);
                return new WebResourceResponse("image/jpeg", null, new ByteArrayInputStream(out.toByteArray()));
            }
            Roms.Game g = game(key);
            if (g != null) {
                String p = "/image".equals(path) ? (g.art != null ? g.art : g.cover) : (g.cover != null ? g.cover : g.art);
                if (p == null) {
                    File own = imageFile(new File(g.path).getName());
                    p = own == null ? null : own.getPath();
                }
                if (p != null) {
                    String n = p.toLowerCase(Locale.ROOT);
                    String type = n.endsWith(".png") ? "image/png" : n.endsWith(".webp") ? "image/webp" : "image/jpeg";
                    return new WebResourceResponse(type, null, new FileInputStream(p));
                }
            }
            Apps.Entry e = find(key);
            if (e != null && "/image".equals(path)) {
                File f = imageFile(e.pkg);
                if (f != null) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    String type = n.endsWith(".png") ? "image/png" : n.endsWith(".webp") ? "image/webp" : "image/jpeg";
                    return new WebResourceResponse(type, null, new FileInputStream(f));
                }
            }
            if (e != null && "/tile".equals(path)) {
                return new WebResourceResponse("image/png", null, new ByteArrayInputStream(tile(e)));
            }
            if (e != null && "/icon".equals(path)) {
                Drawable d = getPackageManager().getActivityIcon(e.component());
                Bitmap b = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, 144, 144);
                d.draw(new Canvas(b));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.PNG, 100, out);
                return new WebResourceResponse("image/png", null, new ByteArrayInputStream(out.toByteArray()));
            }
        } catch (Exception ex) {
            Log.w(TAG, "Bild " + path + " " + key + ": " + ex);
        }
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    /**
     * App-Symbol als volle, quadratische Kachel. Neue Symbole (zwei Ebenen) füllen die ganze Fläche,
     * alte bekommen einen Grund in ihrer eigenen Farbe. Einmal gebaut, bleibt die Kachel im Speicher.
     */
    private byte[] tile(Apps.Entry e) throws Exception {
        synchronized (tiles) {
            byte[] cached = tiles.get(e.key());
            if (cached != null) {
                return cached;
            }
        }
        Bitmap b = tileBitmap(e);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        b.recycle();
        byte[] bytes = out.toByteArray();
        synchronized (tiles) {
            tiles.put(e.key(), bytes);
        }
        return bytes;
    }

    private Bitmap tileBitmap(Apps.Entry e) throws Exception {
        final int size = 288;
        Drawable d = getPackageManager().getActivityIcon(e.component());
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF1E2029);
        if (d instanceof AdaptiveIconDrawable) {
            // Die Ebenen sind anderthalbmal so groß wie der sichtbare Teil.
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            int o = size / 4;
            Drawable bg = a.getBackground();
            Drawable fg = a.getForeground();
            if (bg != null) {
                bg.setBounds(-o, -o, size + o, size + o);
                bg.draw(c);
            }
            if (fg != null) {
                fg.setBounds(-o, -o, size + o, size + o);
                fg.draw(c);
            }
        } else {
            Bitmap small = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 32, 32);
            d.draw(new Canvas(small));
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    int p = small.getPixel(x, y);
                    if (Color.alpha(p) > 128) {
                        r += Color.red(p);
                        g += Color.green(p);
                        bl += Color.blue(p);
                        n++;
                    }
                }
            }
            small.recycle();
            if (n > 0) {
                float[] hsv = new float[3];
                Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (bl / n), hsv);
                hsv[1] = Math.min(hsv[1], 0.6f);
                hsv[2] = Math.max(0.2f, Math.min(0.42f, hsv[2] * 0.6f));
                c.drawColor(Color.HSVToColor(hsv));
            }
            int m = size * 16 / 100;
            d.setBounds(m, m, size - m, size - m);
            d.draw(c);
        }
        return b;
    }

    /** Ein Bild aus dem Ordner "Bilder", das wie das Paket der App heißt. */
    private File imageFile(String pkg) {
        File dir = getExternalFilesDir("Bilder");
        if (dir == null) {
            return null;
        }
        for (String ext : new String[] {".jpg", ".jpeg", ".png", ".webp"}) {
            File f = new File(dir, pkg + ext);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    private Apps.Entry find(String key) {
        if (key == null) {
            return null;
        }
        for (Apps.Entry e : apps.all) {
            if (e.key().equals(key)) {
                return e;
            }
        }
        return null;
    }

    /* ---------- Welche Ansicht ---------- */

    private String homeKey() {
        if (apps.all.isEmpty()) {
            return "2.3";
        }
        if (updater != null && updater.available()) {
            return "2.6";
        }
        Intent b = battery();
        if (b != null && level(b) <= 15 && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) {
            return "2.7";
        }
        if (!games.isEmpty()) {
            return "2.2";
        }
        int n = apps.home().size();
        return n == 1 ? "2.4" : n > 5 ? "2.5" : "2.2";
    }

    private void open(String key, boolean push) {
        if ("home".equals(key)) {
            stack.clear();
            key = homeKey();
        } else if (push && !current.isEmpty() && !current.equals(key)) {
            stack.add(current);
            if (stack.size() > 40) {
                stack.remove(0);
            }
        }
        current = key;
        showBoth();
    }

    private void showBoth() {
        boolean h = isHome(current);
        homeShowing = h;
        if (home != null) {
            if (h) {
                home.set(homeItems(), homeTotal, prefs.getString("ui:sel", ""));
            }
            homeTop.setVisibility(h ? View.VISIBLE : View.GONE);
            top.setVisibility(h ? View.INVISIBLE : View.VISIBLE);
            if (panel != null) {
                panel.showHome(h);
            }
        }
        String js = "window.plShow&&plShow('" + current + "')";
        if (top != null && topReady) {
            top.evaluateJavascript(js, null);
        }
        if (panel != null && panel.web != null) {
            panel.web.evaluateJavascript(js, null);
        }
    }

    private void both(String js) {
        if (top != null && topReady) {
            top.evaluateJavascript(js, null);
        }
        if (panel != null && panel.web != null) {
            panel.web.evaluateJavascript(js, null);
        }
    }

    private void key(String name) {
        long now = System.currentTimeMillis();
        if (now < keysQuietUntil) {
            return;
        }
        if (name.equals(lastKey) && now - lastKeyAt < 110) {
            return;
        }
        lastKey = name;
        lastKeyAt = now;
        if (bottomFocus && panel != null && panel.web != null) {
            panel.web.evaluateJavascript("window.plKey&&plKey('" + name + "')", null);
            return;
        }
        if (homeShowing && home != null) {
            home.press(name);
            return;
        }
        if (top != null && topReady) {
            top.evaluateJavascript("window.plKey&&plKey('" + name + "')", null);
        }
    }

    /* ---------- Brücke zur Oberfläche ---------- */

    private final class Bridge {
        @JavascriptInterface
        public void ready(final String side) {
            ui.post(() -> {
                String js = "window.plShow&&plShow('" + current + "')";
                if ("oben".equals(side)) {
                    topReady = true;
                    if (top != null) {
                        top.evaluateJavascript(js, null);
                    }
                } else if (panel != null && panel.web != null) {
                    panel.web.evaluateJavascript(js, null);
                }
            });
        }

        @JavascriptInterface
        public String html(String name) {
            if (name == null || !name.matches("[0-9a-z.\\-]{1,40}")) {
                return "";
            }
            synchronized (pages) {
                String cached = pages.get(name);
                if (cached != null) {
                    return cached;
                }
                try (InputStream in = getAssets().open("ui/s/" + name + ".html")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16384];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                    String text = out.toString("UTF-8");
                    pages.put(name, text);
                    return text;
                } catch (Exception e) {
                    return "";
                }
            }
        }

        @JavascriptInterface
        public String state() {
            try {
                return MainActivity.this.state().toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String apps() {
            JSONArray out = new JSONArray();
            try {
                synchronized (apps) {
                    for (Apps.Entry e : apps.all) {
                        JSONObject o = new JSONObject();
                        o.put("label", e.label);
                        o.put("key", e.key());
                        o.put("fav", e.fav);
                        o.put("last", e.last);
                        o.put("img", imageFile(e.pkg) != null);
                        out.put(o);
                    }
                }
                Set<String> favs = prefs.getStringSet("gfavs", new HashSet<String>());
                for (Roms.Game g : games) {
                    JSONObject o = new JSONObject();
                    o.put("label", g.label);
                    o.put("key", g.key());
                    o.put("fav", favs.contains(g.key()));
                    o.put("last", prefs.getLong("last:" + g.key(), 0L));
                    o.put("img", g.art != null || g.cover != null);
                    o.put("cov", g.cover != null || g.art != null);
                    o.put("game", true);
                    o.put("sys", g.sysFull);
                    o.put("sysid", g.sys);
                    out.put(o);
                }
            } catch (Exception ignored) {
                // leere Liste
            }
            return out.toString();
        }

        @JavascriptInterface
        public String pref(String k) {
            return prefs.getString("ui:" + k, "");
        }

        @JavascriptInterface
        public void setPref(String k, String v) {
            prefs.edit().putString("ui:" + k, v).apply();
        }

        @JavascriptInterface
        public void go(final String key) {
            ui.post(() -> open(key, true));
        }

        @JavascriptInterface
        public void swap(final String key) {
            ui.post(() -> open(key, false));
        }

        @JavascriptInterface
        public void back() {
            ui.post(() -> {
                if (stack.isEmpty()) {
                    open("home", false);
                } else {
                    current = stack.remove(stack.size() - 1);
                    if (current.startsWith("2.")) {
                        current = homeKey();
                    }
                    showBoth();
                }
            });
        }

        /** Angaben zu einem Spiel: Datei, Größe, System und die Emulatoren, die es starten können. */
        @JavascriptInterface
        public String gameInfo(String key) {
            JSONObject o = new JSONObject();
            try {
                Roms.Game g = game(key);
                if (g == null) {
                    return "{}";
                }
                java.io.File f = new java.io.File(g.path);
                o.put("sys", g.sys);
                o.put("sysFull", g.sysFull);
                o.put("file", f.getName());
                o.put("folder", f.getParent());
                o.put("size", f.length());
                o.put("changed", f.lastModified());
                o.put("chosen", prefs.getString("ui:emu:" + g.sys, ""));
                JSONArray list = new JSONArray();
                for (Roms.Launch l : emulators(g)) {
                    list.put(l.label);
                }
                o.put("emus", list);
            } catch (Exception ignored) {
                // leere Angaben
            }
            return o.toString();
        }

        @JavascriptInterface
        public void launch(final String key) {
            ui.post(() -> {
                Roms.Game g = game(key);
                if (g != null) {
                    launchGame(g);
                    return;
                }
                Apps.Entry e = find(key);
                if (e != null) {
                    MainActivity.this.launch(e);
                }
            });
        }

        @JavascriptInterface
        public void fav(final String key) {
            if (game(key) != null) {
                Set<String> favs = new HashSet<>(prefs.getStringSet("gfavs", new HashSet<String>()));
                if (!favs.remove(key)) {
                    favs.add(key);
                }
                prefs.edit().putStringSet("gfavs", favs).apply();
                return;
            }
            Apps.Entry e = find(key);
            if (e != null) {
                apps.toggleFavorite(e);
            }
        }

        @JavascriptInterface
        public void android(final String what) {
            ui.post(() -> openAndroid(what));
        }

        /** Ruhezustand, Neustart, Ausschalten. Klappt nur, wenn Ori als System-App im eigenen OS steckt; sonst "nein". */
        @JavascriptInterface
        public String power(String what) {
            try {
                if ("reboot".equals(what)) {
                    if (checkSelfPermission("android.permission.REBOOT") != PackageManager.PERMISSION_GRANTED) return "nein";
                    ((android.os.PowerManager) getSystemService(POWER_SERVICE)).reboot(null);
                    return "ok";
                }
                if ("off".equals(what)) {
                    if (checkSelfPermission("android.permission.SHUTDOWN") != PackageManager.PERMISSION_GRANTED) return "nein";
                    Intent off = new Intent("com.android.internal.intent.action.REQUEST_SHUTDOWN");
                    off.putExtra("android.intent.extra.KEY_CONFIRM", false);
                    off.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(off);
                    return "ok";
                }
                if ("sleep".equals(what)) {
                    if (OriDienst.schlafen()) return "ok";
                    if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return "nein";
                    String me = new ComponentName(MainActivity.this, OriDienst.class).flattenToString();
                    String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                    if (on == null || on.isEmpty()) on = me; else if (!on.contains(me)) on = on + ":" + me;
                    OriDienst.schlafenWennBereit = true;
                    Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, on);
                    Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                    return "ok";
                }
            } catch (Exception e) {
                Log.w("Polarlicht", "power " + what, e);
            }
            return "nein";
        }

        @JavascriptInterface
        public void setBrightness(final int percent) {
            ui.post(() -> MainActivity.this.setBrightness(percent));
        }

        @JavascriptInterface
        public void setVolume(int percent) {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * Math.max(0, Math.min(100, percent)) / 100f), 0);
        }

        @JavascriptInterface
        public void unlock() {
            keysQuietUntil = System.currentTimeMillis() + 800; // die A-Taste zum Entsperren soll nicht gleich ein Spiel starten
            ui.post(() -> {
                android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km != null && km.isKeyguardLocked()) km.requestDismissKeyguard(MainActivity.this, null); // Android-Sperre ohne PIN gleich mit weg
            });
            if (lockOverGame) {
                lockOverGame = false;
                ui.postDelayed(() -> moveTaskToBack(true), 150); // zurück ins Spiel, das vorher offen war
            }
        }

        @JavascriptInterface
        public void lockOpen() {
            ui.post(() -> both("window.plLockTap&&plLockTap()"));
        }

        @JavascriptInterface
        public String bruecke(String zeile) {
            // Nur bekannte Befehle an orid geben
            if (zeile == null || !zeile.matches("(fx (fest|atmen|regenbogen|welle|aus) [0-9a-fA-F]{6} [0-9]{1,3}|ort (an|aus)|flug (an|aus)|layout (nintendo|xbox)|sb (none|statusbar-expansion))")) return "nein";
            return Leistung.anBruecke(MainActivity.this, zeile + "\n") ? "ok" : "fehler";
        }

        @JavascriptInterface
        public String rgb(String hex, int hell) {
            try {
                int c = Integer.parseInt(hex.replace("#", ""), 16);
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                int br = Math.max(0, Math.min(255, Math.round(hell * 2.55f)));
                StringBuilder sb = new StringBuilder();
                for (String side : new String[]{"left", "right"}) {
                    for (int i = 0; i < 4; i++) {
                        String led = "/sys/class/leds/" + side + ":stick:" + i;
                        sb.append("w ").append(led).append("/multi_intensity ").append(r).append(' ').append(g).append(' ').append(b).append('\n');
                        sb.append("w ").append(led).append("/brightness ").append(br).append('\n');
                    }
                }
                return Leistung.anBruecke(MainActivity.this, sb.toString()) ? "ok" : "fehler";
            } catch (Exception e) {
                return "fehler";
            }
        }

        @JavascriptInterface
        public String fan(String stufe) {
            prefs.edit().putString("fan", stufe).apply();
            String z;
            switch (stufe) {
                case "Stark": z = "w /sys/class/hwmon/hwmon0/pwm1 255\n"; break;
                case "Leise": z = "w /sys/class/hwmon/hwmon0/pwm1 65\n"; break;
                default: z = "w /sys/class/thermal/cooling_device28/cur_state 1\nw /sys/class/thermal/cooling_device28/cur_state 0\n"; break; // Temperatur regelt wieder
            }
            return Leistung.anBruecke(MainActivity.this, z) ? "ok" : "fehler";
        }

        @JavascriptInterface
        public String profil(String name) {
            prefs.edit().putString("profil", name).apply();
            double[] eigene = null;
            if ("Eigene".equals(name)) {
                eigene = new double[4];
                String[] k = {"k1", "k2", "k3", "gpu"};
                for (int i = 0; i < 4; i++) {
                    try { eigene[i] = Math.max(30, Math.min(100, Integer.parseInt(prefs.getString("ui:e:leistung:" + k[i], "100")))) / 100.0; } catch (NumberFormatException e) { eigene[i] = 1; }
                }
            }
            return Leistung.profil(MainActivity.this, name, eigene);
        }

        /** Nur obtainium://-Links und Webseiten öffnen, damit Ori Emulatoren über Obtainium holen kann. */
        @JavascriptInterface
        public String oeffne(String url) {
            if (url == null || !(url.startsWith("obtainium://") || url.startsWith("https://"))) return "nein";
            final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(getPackageManager()) == null) return "nein";
            ui.post(() -> {
                hidePanel();
                try { startActivity(i); } catch (RuntimeException e) { Log.w(TAG, "oeffne: " + e); }
            });
            return "ok";
        }

        /** Das zuletzt gestartete Spiel beenden (läuft im Hintergrund, sobald Ori vorne ist). */
        @JavascriptInterface
        public String beenden() {
            String pkg = prefs.getString("runningPkg", "");
            if (pkg.isEmpty() || pkg.equals(getPackageName())) return "nein";
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                try {
                    am.getClass().getMethod("forceStopPackage", String.class).invoke(am, pkg);
                } catch (Exception e) {
                    am.killBackgroundProcesses(pkg);
                }
                prefs.edit().remove("running").remove("runningPkg").remove("runningLabel").apply();
                return "ok";
            } catch (RuntimeException e) {
                return "nein";
            }
        }

        /** App-Infos für die Ori-Seite statt der Android-App-Info. */
        @JavascriptInterface
        public String appInfo(String key) {
            JSONObject o = new JSONObject();
            try {
                String pkg = key.contains("/") ? key.substring(0, key.indexOf('/')) : key;
                PackageManager pm = getPackageManager();
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                o.put("pkg", pkg);
                o.put("name", pm.getApplicationLabel(pi.applicationInfo).toString());
                o.put("version", pi.versionName == null ? "" : pi.versionName);
                o.put("installiert", pi.firstInstallTime);
                o.put("aktualisiert", pi.lastUpdateTime);
                o.put("system", (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0);
                long groesse = 0;
                try { groesse = new File(pi.applicationInfo.sourceDir).length(); } catch (Exception e) { /* egal */ }
                if (pi.applicationInfo.splitSourceDirs != null) for (String sp : pi.applicationInfo.splitSourceDirs) groesse += new File(sp).length();
                o.put("groesse", groesse);
                o.put("laeuft", pkg.equals(prefs.getString("runningPkg", "")));
            } catch (Exception e) {
                Log.w(TAG, "appInfo: " + e);
            }
            return o.toString();
        }

        @JavascriptInterface
        public String appTun(String was, String key) {
            String pkg = key.contains("/") ? key.substring(0, key.indexOf('/')) : key;
            if (pkg.equals(getPackageName())) return "nein";
            try {
                if ("beenden".equals(was)) {
                    ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).killBackgroundProcesses(pkg);
                    return "ok";
                }
                if ("entfernen".equals(was)) {
                    Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ui.post(() -> { try { startActivity(i); } catch (RuntimeException e) { Log.w(TAG, "entfernen: " + e); } });
                    return "ok";
                }
            } catch (RuntimeException e) {
                return "nein";
            }
            return "nein";
        }

        @JavascriptInterface
        public String mitteilungen() {
            return OriMitteilungen.liste(MainActivity.this);
        }

        @JavascriptInterface
        public void mitteilungWeg(String key) {
            OriMitteilungen.weg(key);
        }

        @JavascriptInterface
        public String mitteilungOeffnen(final String key) {
            ui.post(this::panelFrei);
            return OriMitteilungen.oeffnen(key) ? "ok" : "nein";
        }

        private void panelFrei() { hidePanel(); }

        @JavascriptInterface
        public String medien() {
            JSONArray a = new JSONArray();
            try {
                List<File> f = medienDateien();
                for (int i = 0; i < f.size(); i++) {
                    File x = f.get(i);
                    JSONObject o = new JSONObject();
                    o.put("k", i);
                    o.put("name", x.getName());
                    o.put("zeit", x.lastModified());
                    o.put("clip", x.getName().toLowerCase(Locale.ROOT).endsWith(".mp4"));
                    o.put("sd", !x.getPath().startsWith("/storage/emulated"));
                    a.put(o);
                }
            } catch (Exception e) {
                Log.w(TAG, "medien: " + e);
            }
            return a.toString();
        }

        @JavascriptInterface
        public String sysGet(String what) {
            if (sys == null) return "{}";
            switch (what) {
                case "wlan": return sys.wlan();
                case "bt": return sys.bluetooth();
                case "speicher": return sys.speicher();
                default: return "{}";
            }
        }

        /** WLAN, Bluetooth schalten und verbinden; "nein" heißt: Ori darf das nicht, die Seite bietet dann Android an. */
        @JavascriptInterface
        public String sysDo(String cmd, String a, String b) {
            if (sys == null) return "nein";
            switch (cmd) {
                case "wlan": return sys.wlanSchalten("1".equals(a));
                case "wlanSuchen": rechteHolen(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION); Leistung.anBruecke(MainActivity.this, "ort an\n"); return sys.wlanSuchen();
                case "wlanVerbinden": return sys.wlanVerbinden(a, b);
                case "wlanVergessen": return sys.wlanVergessen(a);
                case "bt": return sys.btSchalten("1".equals(a));
                case "btSuchen": if (Build.VERSION.SDK_INT >= 31) rechteHolen(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.ACCESS_FINE_LOCATION); return sys.btSuchen();
                case "btKoppeln": return sys.btKoppeln(a);
                case "btEntkoppeln": return sys.btEntkoppeln(a);
                case "flug": { String r = sys.flug("1".equals(a)); if (!"ok".equals(r) && Leistung.anBruecke(MainActivity.this, "flug " + ("1".equals(a) ? "an" : "aus") + "\n")) r = "ok"; return r; }
                case "hz": try { return sys.hz(Integer.parseInt(a)); } catch (NumberFormatException e) { return "nein"; }
                default: return "nein";
            }
        }

        @JavascriptInterface
        public String perf() {
            try {
                JSONObject o = Leistung.lesen(MainActivity.this, prefs.getString("running", ""));
                o.put("hinten", prefs.getString("runningLabel", ""));
            o.put("profil", prefs.getString("profil", "Ausgewogen"));
            o.put("fanStufe", prefs.getString("fan", "Automatisch"));
            return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void updateCheck() {
            ui.post(() -> updater.check(true));
        }

        @JavascriptInterface
        public void updateInstall() {
            ui.post(() -> {
                if (updater.available()) {
                    prefs.edit().putInt("pendingBuild", updater.remoteBuild()).apply();
                    updater.install(MainActivity.this);
                } else {
                    updater.check(true);
                }
            });
        }

        @JavascriptInterface
        public void type(final String text) {
            ui.post(() -> {
                if ("\b".equals(text)) {
                    query = query.isEmpty() ? "" : query.substring(0, query.length() - 1);
                } else if (text != null && text.length() == 1 && query.length() < 30) {
                    query += text;
                }
                if (top != null && topReady) {
                    top.evaluateJavascript("window.plEvent&&plEvent('q'," + JSONObject.quote(query) + ")", null);
                }
            });
        }

        @JavascriptInterface
        public void sync(final String what) {
            ui.post(() -> both("window.plSync&&plSync('" + ("reload".equals(what) ? "reload" : "") + "')"));
        }

        @JavascriptInterface
        public void rescan() {
            ui.post(() -> {
                lastScan = 0L;
                scanGames();
            });
        }

        @JavascriptInterface
        public void log(String message) {
            Log.w(TAG, String.valueOf(message));
        }
    }

    /* ---------- Echte Daten ---------- */

    private Intent battery() {
        return registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private static int level(Intent b) {
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return scale <= 0 ? 0 : b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale;
    }

    private JSONObject state() throws Exception {
        JSONObject o = new JSONObject();
        Date now = new Date();
        o.put("time", new SimpleDateFormat("HH:mm", Locale.GERMANY).format(now));
        if (sys != null) o.put("hz", sys.hzJetzt());
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            boolean kh = false;
            for (android.media.AudioDeviceInfo d : am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET || t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == android.media.AudioDeviceInfo.TYPE_USB_HEADSET) kh = true;
            }
            o.put("kopfhoerer", kh);
        } catch (RuntimeException e) { /* egal */ }
        o.put("date", new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(now));
        o.put("weekday", new SimpleDateFormat("EEEE", Locale.GERMANY).format(now));
        Intent b = battery();
        o.put("battery", b == null ? 0 : level(b));
        o.put("temp", b == null ? "–" : (b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10) + " °C");
        String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        o.put("version", versionName + " · Build " + updater.localBuild());
        o.put("android", "Android " + Build.VERSION.RELEASE);
        o.put("model", Build.MANUFACTURER + " " + Build.MODEL);
        o.put("tz", TimeZone.getDefault().getID());
        o.put("region", Locale.getDefault().getDisplayCountry(Locale.GERMANY));
        o.put("wifi", Settings.Global.getInt(getContentResolver(), "wifi_on", 0) != 0);
        o.put("bt", Settings.Global.getInt(getContentResolver(), "bluetooth_on", 0) != 0);
        o.put("airplane", Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0);
        o.put("brightness", Math.round(Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255f));
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        o.put("volume", Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max));
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long total = fs.getTotalBytes();
        long free = fs.getAvailableBytes();
        o.put("total", gb(total));
        o.put("free", gb(free));
        o.put("used", gb(total - free));
        File images = getExternalFilesDir("Bilder");
        o.put("images", images == null ? "" : images.getPath());
        JSONObject u = new JSONObject();
        u.put("busy", updater.busy());
        u.put("available", updater.available());
        u.put("remote", updater.remoteBuild());
        u.put("error", updater.failed());
        u.put("msg", updater.message());
        o.put("upd", u);
        o.put("files", allFiles());
        o.put("games", games.size());
        o.put("scanning", scanning);
        return o;
    }

    /* ---------- Spiele ---------- */

    private boolean allFiles() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private Roms.Game game(String key) {
        if (key == null || !key.startsWith("g:")) {
            return null;
        }
        for (Roms.Game g : games) {
            if (g.key().equals(key)) {
                return g;
            }
        }
        return null;
    }

    /** Alle Speicher des Geräts: interner Speicher und SD-Karten. */
    private List<File> volumes() {
        List<File> out = new ArrayList<>();
        File[] dirs = getExternalFilesDirs(null);
        if (dirs != null) {
            for (File d : dirs) {
                if (d == null) {
                    continue;
                }
                int cut = d.getPath().indexOf("/Android/");
                if (cut > 0) {
                    out.add(new File(d.getPath().substring(0, cut)));
                }
            }
        }
        return out;
    }

    private String readAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private void scanGames() {
        long now = System.currentTimeMillis();
        if (scanning || !allFiles() || now - lastScan < 60000L) {
            return;
        }
        scanning = true;
        lastScan = now;
        final String chosen = prefs.getString("ui:romdir", "");
        new Thread(() -> {
            List<Roms.Game> found = new ArrayList<>();
            try {
                if (roms.systems.isEmpty()) {
                    roms.load(readAsset("es_find_rules.xml"), readAsset("es_systems.xml"));
                }
                List<File> vols = volumes();
                List<File> roots = Roms.findRoots(vols, chosen);
                List<File> media = new ArrayList<>();
                for (File v : vols) {
                    media.add(new File(v, "ES-DE/downloaded_media"));
                }
                for (File r : roots) {
                    RomProvider.ROOTS.add(r.getCanonicalPath());
                    media.add(new File(r.getParentFile(), "ES-DE/downloaded_media"));
                }
                found = roms.scan(roots, media);
                File covers = new File(getFilesDir(), "covers");
                for (Roms.Game g : found) {
                    File c = Covers.file(covers, g.sys, g.path);
                    if (g.cover == null && c.isFile() && c.length() > 0) {
                        g.cover = c.getAbsolutePath();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Spielesuche: " + e);
            }
            final List<Roms.Game> result = found;
            loadCovers(result);
            ui.post(() -> {
                boolean changed = result.size() != games.size();
                games = result;
                scanning = false;
                if (changed) {
                    if (current.startsWith("2.")) {
                        current = homeKey();
                        showBoth();
                    } else {
                        both("window.plSync&&plSync('reload')");
                    }
                }
            });
        }).start();
    }

    private volatile boolean coverRun;

    /** Holt fehlende Spielbilder im Hintergrund aus dem Netz. Abschaltbar über die Einstellung "covers". */
    private void loadCovers(final List<Roms.Game> list) {
        if (coverRun || "0".equals(prefs.getString("ui:covers", "1"))) {
            return;
        }
        coverRun = true;
        final File dir = new File(getFilesDir(), "covers");
        new Thread(() -> {
            int got = 0;
            long shown = System.currentTimeMillis();
            try {
                for (Roms.Game g : list) {
                    if (g.cover != null || !Covers.knows(g.sys)) {
                        continue;
                    }
                    File f = Covers.fetch(dir, g.sys, g.path);
                    if (f != null) {
                        g.cover = f.getAbsolutePath();
                        got++;
                        if (System.currentTimeMillis() - shown > 4000L) {
                            shown = System.currentTimeMillis();
                            ui.post(() -> {
                                both("window.plSync&&plSync('reload')");
                                syncHome();
                            });
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Spielbilder: " + e);
            }
            coverRun = false;
            if (got > 0) {
                ui.post(() -> {
                    both("window.plSync&&plSync('reload')");
                    syncHome();
                });
            }
        }).start();
    }

    /** Alle installierten Emulatoren, die dieses Spiel starten können. */
    private List<Roms.Launch> emulators(Roms.Game g) {
        Set<String> installed = new HashSet<>();
        for (PackageInfo p : getPackageManager().getInstalledPackages(0)) {
            installed.add(p.packageName);
        }
        String volume = g.path.startsWith("/storage/emulated/") ? "primary" : g.path.split("/").length > 2 ? g.path.split("/")[2] : "primary";
        return roms.options(g, installed, volume);
    }

    private void launchGame(Roms.Game g) {
        List<Roms.Launch> options = emulators(g);
        if (options.isEmpty()) {
            if (top != null && topReady && g.sys.matches("[a-z0-9]{1,16}")) {
                top.evaluateJavascript("window.plEmuFehlt&&plEmuFehlt('" + g.sys + "')", null);
            } else {
                Toast.makeText(this, "Für " + g.sysFull + " ist kein passender Emulator installiert.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        String wanted = prefs.getString("ui:emu:" + g.sys, "");
        Roms.Launch pick = options.get(0);
        for (Roms.Launch l : options) {
            if (l.label.equals(wanted)) {
                pick = l;
            }
        }
        try {
            String pkg = pick.component.substring(0, pick.component.indexOf('/'));
            String cls = pick.component.substring(pick.component.indexOf('/') + 1);
            Intent intent = new Intent(pick.action == null ? Intent.ACTION_MAIN : pick.action);
            intent.setComponent(new ComponentName(pkg, cls.startsWith(".") ? pkg + cls : cls));
            int flags = Intent.FLAG_ACTIVITY_NEW_TASK;
            if (pick.clearTask) {
                flags |= Intent.FLAG_ACTIVITY_CLEAR_TASK;
            }
            if (pick.clearTop) {
                flags |= Intent.FLAG_ACTIVITY_CLEAR_TOP;
            }
            if (pick.category != null) {
                intent.addCategory(pick.category);
            }
            if (pick.data != null) {
                Uri data = pick.dataIsProvider ? RomProvider.uriFor(pick.data) : Uri.parse(pick.data);
                if (pick.dataIsProvider) {
                    flags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
                }
                if (pick.mime != null) {
                    intent.setDataAndType(data, pick.mime);
                } else {
                    intent.setData(data);
                }
            }
            intent.setFlags(flags);
            for (String k : pick.extras.keySet()) {
                intent.putExtra(k, pick.extras.get(k));
            }
            for (String k : pick.bools.keySet()) {
                intent.putExtra(k, pick.bools.get(k).booleanValue());
            }
            for (String k : pick.ints.keySet()) {
                intent.putExtra(k, pick.ints.get(k).intValue());
            }
            for (String k : pick.arrays.keySet()) {
                intent.putExtra(k, pick.arrays.get(k));
            }
            // Den unteren Bildschirm freigeben, bevor der Emulator startet: Dual-Screen-Emulatoren
            // (WatermelonDS, Azahar, Cemu) legen dort ihre eigene Presentation an und stürzen sonst ab.
            hidePanel();
            int display = apps.launchDisplay();
            if (display >= 0 && displayManager.getDisplay(display) != null) {
                ActivityOptions o = ActivityOptions.makeBasic();
                o.setLaunchDisplayId(display);
                startActivity(intent, o.toBundle());
            } else {
                startActivity(intent);
            }
            prefs.edit().putLong("last:" + g.key(), System.currentTimeMillis()).putString("running", g.label).putString("runningPkg", pkg).putString("runningLabel", g.label).putLong("runningAt", System.currentTimeMillis()).apply();
        } catch (RuntimeException e) {
            Log.w(TAG, "Start " + pick.component + ": " + e);
            Toast.makeText(this, pick.label + " ließ sich nicht starten.", Toast.LENGTH_LONG).show();
        }
    }

    private static String gb(long bytes) {
        return Math.round(bytes / 1073741824.0) + " GB";
    }

    private void setBrightness(int percent) {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Bitte einmal erlauben, dann geht der Regler.", Toast.LENGTH_LONG).show();
            startSafely(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
            return;
        }
        int value = Math.max(4, Math.min(255, Math.round(percent * 255 / 100f)));
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
    }

    private void launch(Apps.Entry e) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(e.component());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        int display = apps.launchDisplay();
        hidePanel(); // auch Apps (Emulatoren direkt, Spiele) dürfen den unteren Bildschirm nutzen
        try {
            if (display >= 0 && displayManager.getDisplay(display) != null) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(display);
                startActivity(intent, options.toBundle());
            } else {
                startActivity(intent);
            }
            apps.markStarted(e);
        } catch (RuntimeException first) {
            try {
                startActivity(intent);
                apps.markStarted(e);
            } catch (RuntimeException second) {
                Toast.makeText(this, e.label + " lässt sich nicht starten.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startSafely(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
                Toast.makeText(this, "Das lässt sich hier nicht öffnen.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Für alles, was Polarlicht als normale App nicht selbst schalten darf: die passende Android-Seite. */
    private void openAndroid(String what) {
        if (what.equals("files")) {
            if (Build.VERSION.SDK_INT >= 30) {
                lastScan = 0L;
                startSafely(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
            }
            return;
        }
        if (what.startsWith("app:")) {
            Apps.Entry e = find(what.substring(4));
            if (e != null) {
                startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg)));
            }
            return;
        }
        String action;
        switch (what) {
            case "wifi": action = Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS; break;
            case "wifisettings": action = Settings.ACTION_WIFI_SETTINGS; break;
            case "bt": action = Settings.ACTION_BLUETOOTH_SETTINGS; break;
            case "airplane": action = Settings.ACTION_AIRPLANE_MODE_SETTINGS; break;
            case "display": action = Settings.ACTION_DISPLAY_SETTINGS; break;
            case "sound": action = Settings.ACTION_SOUND_SETTINGS; break;
            case "battery": action = Intent.ACTION_POWER_USAGE_SUMMARY; break;
            case "storage": action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS; break;
            case "apps": action = Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS; break;
            case "defaultapps": action = Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS; break;
            case "unknown": action = Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES; break;
            case "location": action = Settings.ACTION_LOCATION_SOURCE_SETTINGS; break;
            case "notifications": action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS; break;
            case "overlay": action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION; break;
            case "locale": action = Settings.ACTION_LOCALE_SETTINGS; break;
            case "date": action = Settings.ACTION_DATE_SETTINGS; break;
            case "ime": action = Settings.ACTION_INPUT_METHOD_SETTINGS; break;
            case "a11y": action = Settings.ACTION_ACCESSIBILITY_SETTINGS; break;
            case "about": action = Settings.ACTION_DEVICE_INFO_SETTINGS; break;
            default: action = Settings.ACTION_SETTINGS; break;
        }
        startSafely(new Intent(action));
    }

    /* ---------- Tasten ---------- */

    private static String name(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R1";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2";
            case KeyEvent.KEYCODE_BUTTON_START: case KeyEvent.KEYCODE_MENU: return "START";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "SELECT";
            case 337: return "AYN"; // AYN-Knopf (F24)
            default: return null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            ui.postDelayed(() -> {
                both("window.plSync&&plSync('')");
                if (home != null && homeShowing) {
                    home.refresh();
                }
            }, 150);
            return super.dispatchKeyEvent(e);
        }
        String k = name(code);
        int scan = e.getScanCode();
        if (scan == 317 || scan == 318) {
            /* Sticks reindrücken: das System meldet sie als DPAD_CENTER, Ori soll sie nicht für A halten */
            k = scan == 317 ? "L3" : "R3";
        }
        if (code == KeyEvent.KEYCODE_BUTTON_THUMBL) k = "L3";
        if (code == KeyEvent.KEYCODE_BUTTON_THUMBR) k = "R3";
        if ("R3".equals(k)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                /* Rechten Stick drücken: Controller steuert den unteren Bildschirm, nochmal drücken zurück nach oben */
                setBottomFocus(!bottomFocus, true);
            }
            return true;
        }
        if (k == null) {
            return super.dispatchKeyEvent(e);
        }
        boolean hold = k.equals("START") || k.equals("SELECT");
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (hold) {
                if (e.getRepeatCount() == 0) {
                    longFired = false;
                } else if (e.getRepeatCount() >= 10 && !longFired) {
                    longFired = true;
                    key(k.equals("SELECT") ? "INDEX" : "STARTLONG");
                }
            } else if (Character.isLowerCase(k.charAt(0))) {
                if (e.getRepeatCount() == 0) {
                    hold(k); // das Wiederholen beim Halten macht hold() selbst, gleichmäßig und schneller als Android
                }
            } else if (e.getRepeatCount() == 0) {
                key(k);
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP && hold && !longFired) {
            key(k);
        } else if (e.getAction() == KeyEvent.ACTION_UP && k.equals(holdDir)) {
            hold("");
        }
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && e.getAction() == MotionEvent.ACTION_MOVE) {
            float x = e.getAxisValue(MotionEvent.AXIS_HAT_X);
            float y = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (Math.abs(x) < 0.5f && Math.abs(y) < 0.5f) {
                x = e.getAxisValue(MotionEvent.AXIS_X);
                y = e.getAxisValue(MotionEvent.AXIS_Y);
            }
            String dir = "";
            if (Math.abs(x) > 0.65f && Math.abs(x) > Math.abs(y) * 1.5f) {
                dir = x > 0 ? "right" : "left";
            } else if (Math.abs(y) > 0.65f && Math.abs(y) > Math.abs(x) * 1.5f) {
                dir = y > 0 ? "down" : "up";
            } else if (Math.abs(x) > 0.4f || Math.abs(y) > 0.4f) {
                dir = stickDir; // zwischen den Richtungen: bei der alten bleiben, nicht springen
            }
            if (!dir.equals(stickDir)) {
                stickDir = dir;
                hold(dir);
            }
            trigger(e.getAxisValue(MotionEvent.AXIS_LTRIGGER) + e.getAxisValue(MotionEvent.AXIS_BRAKE), true);
            trigger(e.getAxisValue(MotionEvent.AXIS_RTRIGGER) + e.getAxisValue(MotionEvent.AXIS_GAS), false);
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    /** Die Schultertasten hinten kommen bei manchen Geräten nur als Achse an. */
    private void trigger(float value, boolean left) {
        boolean down = value > 0.5f;
        if (left && down != l2Down) {
            l2Down = down;
            if (down) {
                key("L2");
            }
        } else if (!left && down != r2Down) {
            r2Down = down;
            if (down) {
                key("R2");
            }
        }
    }

    /**
     * Eine Richtung wird gehalten (Stick oder Steuerkreuz): erst ein Schritt, nach kurzer Pause zügig weiter,
     * nach einer Sekunde noch etwas schneller. Eine leere Richtung beendet das Halten.
     */
    private void hold(String dir) {
        if (dir.equals(holdDir)) {
            return;
        }
        holdDir = dir;
        ui.removeCallbacks(holdRepeat);
        if (!dir.isEmpty()) {
            holdSince = SystemClock.uptimeMillis();
            step(dir);
            ui.postDelayed(holdRepeat, 240);
        }
    }

    private void step(String dir) {
        if (bottomFocus && panel != null && panel.web != null) {
            panel.web.evaluateJavascript("window.plKey&&plKey('" + dir + "')", null);
            return;
        }
        if (homeShowing && home != null) {
            home.press(dir);
            return;
        }
        if (top != null && topReady) {
            top.evaluateJavascript("window.plKey&&plKey('" + dir + "')", null);
        }
    }

    private final Runnable holdRepeat = new Runnable() {
        @Override
        public void run() {
            if (!holdDir.isEmpty()) {
                step(holdDir);
                ui.postDelayed(this, SystemClock.uptimeMillis() - holdSince > 1000 ? 60 : 85);
            }
        }
    };

    /* ---------- Lebenslauf ---------- */

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && started) {
            ui.postDelayed(this::showPanel, 300); // unterer Bildschirm kommt zurück, sobald Ori wieder vorne ist
        }
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }



    /** Fehlende Laufzeitrechte einmal erfragen. Im fertigen Ori OS sind sie vorab erteilt, das hier ist nur der Notnagel. */
    private void rechteHolen(String... rechte) {
        java.util.ArrayList<String> fehlt = new java.util.ArrayList<>();
        for (String r : rechte) if (checkSelfPermission(r) != android.content.pm.PackageManager.PERMISSION_GRANTED) fehlt.add(r);
        if (fehlt.isEmpty()) return;
        runOnUiThread(() -> { try { requestPermissions(fehlt.toArray(new String[0]), 7); } catch (Exception ignored) { } });
    }

    /** Controller unten oder oben steuern lassen; oben zeigt ein Schildchen, solange unten aktiv ist. */
    private void setBottomFocus(boolean on, boolean ansagen) {
        on = on && panel != null && panel.web != null;
        if (on == bottomFocus && !ansagen) return;
        bottomFocus = on;
        if (panel != null) {
            panel.showHome(homeShowing && !bottomFocus);
            if (panel.web != null) panel.web.evaluateJavascript("window.plBotFocus&&plBotFocus(" + bottomFocus + ")", null);
        }
        if (top != null && topReady) top.evaluateJavascript("window.plBotChip&&plBotChip(" + bottomFocus + ")" + (ansagen ? ";window.plSnd&&plSnd('" + (bottomFocus ? "an" : "aus") + "')" : ""), null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        if (prefs != null && System.currentTimeMillis() - prefs.getLong("runningAt", 0L) > 3000) {
            prefs.edit().remove("running").apply(); // zurück in Ori: kein Spiel mehr im Vordergrund
        }
        synchronized (apps) {
            apps.reload();
        }
        displayManager.registerDisplayListener(this, ui);
        showPanel();
        scanGames();
        if (current.startsWith("2.")) {
            current = homeKey();
        }
        showBoth();
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        displayManager.unregisterDisplayListener(this);
        ui.removeCallbacks(holdRepeat);
        stickDir = "";
        holdDir = "";
        bottomFocus = false;
        hidePanel();
    }

    @Override
    protected void onDestroy() {
        hidePanel();
        if (top != null) {
            top.destroy();
            top = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Updater.ACTION_INSTALL_STATUS.equals(intent.getAction())) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    try {
                        startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException ex) {
                        updater.fail("Android hat die Installation nicht geöffnet.");
                    }
                }
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                prefs.edit().remove("pendingBuild").apply();
                String why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                updater.fail("Update nicht installiert" + (why == null ? "" : ": " + why));
            }
            return;
        }
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            setBottomFocus(false, false);
            if (current.startsWith("1.")) {
                /* Home-Taste während der Einrichtung: Einrichtung überspringen, nie festhängen */
                prefs.edit().putString("ui:setup", "1").apply();
            }
            open("home", false);
        }
    }

    @Override
    public void onUpdateState() {
        if (home != null && homeShowing) {
            home.refresh();
        }
        if (top != null && topReady) {
            top.evaluateJavascript("window.plEvent&&plEvent('update')", null);
        }
    }

    /* ---------- Unterer Bildschirm ---------- */

    private Display otherDisplay() {
        int own = getWindowManager().getDefaultDisplay().getDisplayId();
        for (Display d : displayManager.getDisplays()) {
            if (d.getDisplayId() != own && d.getState() != Display.STATE_OFF) {
                return d;
            }
        }
        return null;
    }

    private void showPanel() {
        Display other = otherDisplay();
        if (other == null) {
            hidePanel();
            return;
        }
        if (panel != null && panel.getDisplay().getDisplayId() == other.getDisplayId() && panel.isShowing()) {
            return;
        }
        hidePanel();
        try {
            panel = new PanelPresentation(this, other, this);
            panel.show();
        } catch (RuntimeException ex) {
            panel = null;
        }
    }

    private void hidePanel() {
        if (panel != null) {
            panel.close();
            panel = null;
        }
        // Falls der Start nicht klappt oder Ori vorne bleibt: unteren Bildschirm nach kurzer Zeit wieder zeigen
        ui.postDelayed(() -> { if (started && hasWindowFocus() && panel == null) showPanel(); }, 4000);
    }

    @Override
    public void onDisplayAdded(int id) {
        showPanel();
    }

    @Override
    public void onDisplayRemoved(int id) {
        showPanel();
    }

    @Override
    public void onDisplayChanged(int id) {
        // nichts zu tun
    }
}
