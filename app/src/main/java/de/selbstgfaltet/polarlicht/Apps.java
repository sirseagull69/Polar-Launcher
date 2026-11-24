package de.selbstgfaltet.polarlicht;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Liste der installierten Apps, Favoriten und "zuletzt gestartet". */
final class Apps {
    static final class Entry {
        String label;
        String pkg;
        String cls;
        long last;
        boolean fav;

        String key() {
            return pkg + "/" + cls;
        }

        ComponentName component() {
            return new ComponentName(pkg, cls);
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    final List<Entry> all = new ArrayList<>();

    Apps(Context c) {
        context = c.getApplicationContext();
        prefs = context.getSharedPreferences("polarlicht", Context.MODE_PRIVATE);
    }

    void reload() {
        all.clear();
        PackageManager pm = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> favs = prefs.getStringSet("favs", new HashSet<String>());
        for (ResolveInfo info : pm.queryIntentActivities(main, 0)) {
            if (info.activityInfo == null || context.getPackageName().equals(info.activityInfo.packageName)) {
                continue;
            }
            Entry e = new Entry();
            e.pkg = info.activityInfo.packageName;
            e.cls = info.activityInfo.name;
            CharSequence label = info.loadLabel(pm);
            e.label = label == null ? e.pkg : label.toString();
            e.last = prefs.getLong("last:" + e.key(), 0L);
            e.fav = favs.contains(e.key());
            all.add(e);
        }
        Collections.sort(all, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    /** Was auf dem Startbildschirm steht: Favoriten, sonst alle. Zuletzt gestartete zuerst. */
    List<Entry> home() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (e.fav) {
                out.add(e);
            }
        }
        if (out.isEmpty()) {
            out.addAll(all);
        }
        Collections.sort(out, (a, b) -> {
            if (a.last != b.last) {
                return a.last > b.last ? -1 : 1;
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    boolean hasFavorites() {
        for (Entry e : all) {
            if (e.fav) {
                return true;
            }
        }
        return false;
    }

    void toggleFavorite(Entry e) {
        e.fav = !e.fav;
        Set<String> favs = new HashSet<>(prefs.getStringSet("favs", new HashSet<String>()));
        if (e.fav) {
            favs.add(e.key());
        } else {
            favs.remove(e.key());
        }
        prefs.edit().putStringSet("favs", favs).apply();
    }

    void markStarted(Entry e) {
        e.last = System.currentTimeMillis();
        prefs.edit().putLong("last:" + e.key(), e.last).apply();
    }

    /** -1 = Android entscheidet, sonst die Nummer des Bildschirms, auf dem Apps starten sollen. */
    int launchDisplay() {
        return prefs.getInt("launchDisplay", -1);
    }

    void setLaunchDisplay(int id) {
        prefs.edit().putInt("launchDisplay", id).apply();
    }
}
