package de.selbstgfaltet.polarlicht;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Liest die Android-Mitteilungen mit, damit Ori sie in seiner eigenen Mitteilungs-Seite zeigen kann
 * (statt der Android-Leiste von oben). Ori schaltet sich als System-App selbst als Zuhörer frei.
 */
public class OriMitteilungen extends NotificationListenerService {
    private static final String TAG = "Polarlicht";
    static volatile OriMitteilungen aktiv;
    static Runnable geaendert;

    @Override
    public void onListenerConnected() {
        aktiv = this;
        melden();
    }

    @Override
    public void onListenerDisconnected() {
        if (aktiv == this) aktiv = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        melden();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        melden();
    }

    private static void melden() {
        Runnable r = geaendert;
        if (r != null) r.run();
    }

    /** Ori als Zuhörer eintragen (geht mit WRITE_SECURE_SETTINGS ohne Android-Seite). */
    static void freischalten(Context c) {
        try {
            String me = new ComponentName(c, OriMitteilungen.class).flattenToString();
            String cur = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            if (cur != null && cur.contains(me)) return;
            String neu = cur == null || cur.isEmpty() ? me : cur + ":" + me;
            Settings.Secure.putString(c.getContentResolver(), "enabled_notification_listeners", neu);
        } catch (RuntimeException e) {
            Log.w(TAG, "Mitteilungen freischalten: " + e);
        }
    }

    static String liste(Context c) {
        JSONArray a = new JSONArray();
        OriMitteilungen s = aktiv;
        if (s == null) return "{\"aus\":true,\"liste\":[]}";
        try {
            PackageManager pm = c.getPackageManager();
            for (StatusBarNotification n : s.getActiveNotifications()) {
                Notification no = n.getNotification();
                if ((no.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                Bundle e = no.extras;
                CharSequence t = e.getCharSequence(Notification.EXTRA_TITLE), x = e.getCharSequence(Notification.EXTRA_TEXT);
                if ((t == null || t.length() == 0) && (x == null || x.length() == 0)) continue;
                String app;
                try { app = pm.getApplicationLabel(pm.getApplicationInfo(n.getPackageName(), 0)).toString(); } catch (Exception ex) { app = n.getPackageName(); }
                JSONObject o = new JSONObject();
                o.put("key", n.getKey());
                o.put("app", app);
                o.put("titel", t == null ? app : t.toString());
                o.put("text", x == null ? "" : x.toString());
                o.put("zeit", n.getPostTime());
                o.put("weg", n.isClearable());
                o.put("laufend", (no.flags & Notification.FLAG_ONGOING_EVENT) != 0);
                a.put(o);
            }
        } catch (Exception ex) {
            Log.w(TAG, "Mitteilungen lesen: " + ex);
        }
        return "{\"aus\":false,\"liste\":" + a + "}";
    }

    static void weg(String key) {
        OriMitteilungen s = aktiv;
        if (s == null) return;
        try {
            if ("*".equals(key)) s.cancelAllNotifications(); else s.cancelNotification(key);
        } catch (RuntimeException e) { /* egal */ }
    }

    static boolean oeffnen(String key) {
        OriMitteilungen s = aktiv;
        if (s == null) return false;
        try {
            for (StatusBarNotification n : s.getActiveNotifications()) {
                if (!n.getKey().equals(key)) continue;
                PendingIntent pi = n.getNotification().contentIntent;
                if (pi == null) return false;
                pi.send();
                if ((n.getNotification().flags & Notification.FLAG_AUTO_CANCEL) != 0) s.cancelNotification(key);
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Mitteilung öffnen: " + e);
        }
        return false;
    }
}
