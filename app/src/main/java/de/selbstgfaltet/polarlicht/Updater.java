package de.selbstgfaltet.polarlicht;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Updates über das Netz: schaut im GitHub-Repo nach einem neueren Build und installiert ihn. */
final class Updater {
    /** Das Repo, in dem die Builds liegen. Nach einem Umbenennen hier anpassen. */
    static final String REPO = "sirseagull69/Polar-Launcher";
    static final String ACTION_INSTALL_STATUS = "de.selbstgfaltet.polarlicht.INSTALL_STATUS";

    interface Listener {
        void onUpdateState();
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private volatile boolean busy = false;
    private long lastCheck = 0L;
    private int remoteBuild = 0;
    private String apkUrl = "";
    private String message = "";

    Updater(Context c, Listener l) {
        context = c.getApplicationContext();
        listener = l;
    }

    int localBuild() {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    boolean available() {
        return remoteBuild > localBuild() && !apkUrl.isEmpty();
    }

    boolean busy() {
        return busy;
    }

    String status() {
        if (!message.isEmpty()) {
            return message;
        }
        if (available()) {
            return "Build " + remoteBuild + " ist da · A installiert";
        }
        return "Build " + localBuild() + (remoteBuild > 0 ? " · aktuell" : " · A sucht nach Updates");
    }

    private void tell(String text) {
        message = text;
        main.post(listener::onUpdateState);
    }

    /** Fragt GitHub nach dem neuesten Build. Ohne force höchstens einmal pro Stunde. */
    void check(boolean force) {
        long now = System.currentTimeMillis();
        if (busy || (!force && now - lastCheck < 3600000L)) {
            return;
        }
        lastCheck = now;
        busy = true;
        if (force) {
            tell("Suche nach Updates …");
        }
        new Thread(() -> {
            try {
                String body = new String(read(open("https://api.github.com/repos/" + REPO + "/releases/latest"), null), "UTF-8");
                JSONObject release = new JSONObject(body);
                String tag = release.optString("tag_name", "");
                int build = tag.startsWith("build-") ? Integer.parseInt(tag.substring(6)) : 0;
                String url = "";
                JSONArray assets = release.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (asset.optString("name", "").endsWith(".apk")) {
                        url = asset.optString("browser_download_url", "");
                    }
                }
                remoteBuild = build;
                apkUrl = url.startsWith("https://") ? url : "";
                busy = false;
                tell("");
            } catch (Exception e) {
                busy = false;
                tell(force ? "Update-Suche ging nicht (kein Netz?)" : "");
            }
        }).start();
    }

    /** Lädt die APK und übergibt sie Android zur Installation. Android fragt einmal nach. */
    void install(final Activity activity) {
        if (busy || !available()) {
            return;
        }
        busy = true;
        tell("Lade Build " + remoteBuild + " …");
        new Thread(() -> {
            File file = new File(context.getCacheDir(), "polarlicht-update.apk");
            try {
                read(open(apkUrl), file);
                PackageInstaller installer = context.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(context.getPackageName());
                int id = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(id)) {
                    try (OutputStream out = session.openWrite("polarlicht", 0, file.length());
                         InputStream in = new FileInputStream(file)) {
                        byte[] buffer = new byte[65536];
                        int n;
                        while ((n = in.read(buffer)) > 0) {
                            out.write(buffer, 0, n);
                        }
                        session.fsync(out);
                    }
                    Intent back = new Intent(activity, MainActivity.class).setAction(ACTION_INSTALL_STATUS);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= 31) {
                        flags |= PendingIntent.FLAG_MUTABLE;
                    }
                    session.commit(PendingIntent.getActivity(activity, 7, back, flags).getIntentSender());
                }
                busy = false;
                tell("Android fragt gleich, ob installiert werden soll.");
            } catch (Exception e) {
                busy = false;
                tell("Update ging nicht: " + e.getClass().getSimpleName());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }).start();
    }

    void clearMessage() {
        message = "";
    }

    void fail(String text) {
        tell(text);
    }

    private static HttpURLConnection open(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "Polarlicht-Launcher");
        if (c.getResponseCode() != 200) {
            throw new IllegalStateException("HTTP " + c.getResponseCode());
        }
        return c;
    }

    /** Liest die Antwort in eine Datei oder, wenn target null ist, in den Speicher (höchstens 60 MB bzw. 1 MB). */
    private static byte[] read(HttpURLConnection c, File target) throws Exception {
        long limit = target == null ? 1048576L : 62914560L;
        try (InputStream in = c.getInputStream();
             OutputStream out = target == null ? new ByteArrayOutputStream() : new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > limit) {
                    throw new IllegalStateException("zu groß");
                }
                out.write(buffer, 0, n);
            }
            return target == null ? ((ByteArrayOutputStream) out).toByteArray() : null;
        } finally {
            c.disconnect();
        }
    }
}
