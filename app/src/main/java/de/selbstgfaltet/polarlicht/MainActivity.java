package de.selbstgfaltet.polarlicht;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Polarlicht. Die Oberfläche kommt aus dem Design-Canvas (assets/ui) und läuft in zwei Web-Ansichten:
 * oben für den Controller, unten für Touch. Diese Klasse liefert die echten Daten und führt aus,
 * was die Oberfläche auslöst.
 */
public class MainActivity extends Activity implements DisplayManager.DisplayListener, Updater.Listener {
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
    private String current = "";
    private String query = "";
    private boolean topReady = false;
    private boolean longFired = false;
    private String lastKey = "";
    private long lastKeyAt = 0L;
    private String stickDir = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("polarlicht", MODE_PRIVATE);
        apps = new Apps(this);
        apps.reload();
        updater = new Updater(this, this);
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
        setContentView(top);
        updater.check(false);
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

    /** App-Symbol oder Hintergrundbild für die Oberfläche. */
    private WebResourceResponse picture(String path, String key) {
        try {
            Apps.Entry e = find(key);
            if (e != null && "/image".equals(path)) {
                File f = imageFile(e.pkg);
                if (f != null) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    String type = n.endsWith(".png") ? "image/png" : n.endsWith(".webp") ? "image/webp" : "image/jpeg";
                    return new WebResourceResponse(type, null, new FileInputStream(f));
                }
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
        if (name.equals(lastKey) && now - lastKeyAt < 45) {
            return;
        }
        lastKey = name;
        lastKeyAt = now;
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

        @JavascriptInterface
        public void launch(final String key) {
            ui.post(() -> {
                Apps.Entry e = find(key);
                if (e != null) {
                    MainActivity.this.launch(e);
                }
            });
        }

        @JavascriptInterface
        public void fav(final String key) {
            Apps.Entry e = find(key);
            if (e != null) {
                apps.toggleFavorite(e);
            }
        }

        @JavascriptInterface
        public void android(final String what) {
            ui.post(() -> openAndroid(what));
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
        return o;
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
            default: return null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            ui.postDelayed(() -> both("window.plSync&&plSync('')"), 150);
            return super.dispatchKeyEvent(e);
        }
        String k = name(code);
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
            } else if (e.getRepeatCount() == 0 || (Character.isLowerCase(k.charAt(0)) && e.getRepeatCount() % 2 == 0)) {
                lastKey = "";
                key(k);
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP && hold && !longFired) {
            key(k);
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
            String dir = Math.abs(x) > Math.abs(y) ? (x > 0.6f ? "right" : x < -0.6f ? "left" : "") : (y > 0.6f ? "down" : y < -0.6f ? "up" : "");
            if (!dir.equals(stickDir)) {
                stickDir = dir;
                ui.removeCallbacks(stickRepeat);
                if (!dir.isEmpty()) {
                    lastKey = "";
                    key(dir);
                    ui.postDelayed(stickRepeat, 380);
                }
            }
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    private final Runnable stickRepeat = new Runnable() {
        @Override
        public void run() {
            if (!stickDir.isEmpty()) {
                lastKey = "";
                key(stickDir);
                ui.postDelayed(this, 140);
            }
        }
    };

    /* ---------- Lebenslauf ---------- */

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        synchronized (apps) {
            apps.reload();
        }
        displayManager.registerDisplayListener(this, ui);
        showPanel();
        if (current.startsWith("2.")) {
            current = homeKey();
        }
        showBoth();
    }

    @Override
    protected void onStop() {
        super.onStop();
        displayManager.unregisterDisplayListener(this);
        ui.removeCallbacks(stickRepeat);
        stickDir = "";
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
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && !current.startsWith("1.")) {
            open("home", false);
        }
    }

    @Override
    public void onUpdateState() {
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
