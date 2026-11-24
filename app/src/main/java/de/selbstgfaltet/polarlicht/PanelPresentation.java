package de.selbstgfaltet.polarlicht;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

/** Das Bedienfeld auf dem unteren Bildschirm, nach Canvas 2.1 (unten). */
final class PanelPresentation extends Presentation {
    interface Host {
        void toggleAllApps();

        void toggleSettings();

        void toggleInfo();

        void cycleLaunchDisplay();

        String launchLabel();

        int battery();

        float brightness();

        boolean setBrightness(float value);

        float volume();

        void setVolume(float value);

        boolean airplaneMode();

        void openSettings(String action);
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Host host;
    private float g = 1f;
    private TextView airplaneSub;
    private LinearLayout airplaneTile;
    private Ui.Slider brightness;
    private Ui.Slider volume;

    PanelPresentation(Context outer, Display display, Host host) {
        super(outer, display);
        this.host = host;
    }

    private int q(float canvas) {
        return Math.round(canvas * g);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Point size = new Point();
        getDisplay().getRealSize(size);
        g = Math.min(size.y / 540f, size.x / 620f);
        Context c = getContext();

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(q(24), q(24), q(24), q(24));

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(q(44));
        head.addView(Ui.text(c, "Bedienfeld", q(22), Ui.TEXT, 700), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView info = Ui.mono(c, "INFO", q(11), Ui.MUTED);
        info.setGravity(Gravity.CENTER);
        info.setMinWidth(q(60));
        info.setMinimumHeight(q(44));
        info.setOnClickListener(v -> host.toggleInfo());
        head.addView(info, new LinearLayout.LayoutParams(WRAP, q(44)));
        root.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(3);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(MATCH, WRAP);
        gridParams.topMargin = q(9);
        gridParams.leftMargin = -q(5);
        gridParams.rightMargin = -q(5);
        root.addView(grid, gridParams);

        tile(grid, c, "wifi", "WLAN", "Öffnen").setOnClickListener(v ->
                host.openSettings(Build.VERSION.SDK_INT >= 29 ? "android.settings.panel.action.WIFI" : Settings.ACTION_WIFI_SETTINGS));
        tile(grid, c, "bt", "Bluetooth", "Öffnen").setOnClickListener(v -> host.openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        airplaneTile = tile(grid, c, "plane", "Flugmodus", "Aus");
        airplaneSub = (TextView) ((LinearLayout) airplaneTile.getChildAt(2)).getChildAt(1);
        airplaneTile.setOnClickListener(v -> host.openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
        LinearLayout power = tile(grid, c, "power", "Leistung", "Kommt mit dem OS");
        power.setAlpha(0.35f);
        tile(grid, c, "grid", "Bibliothek", "Alle Apps").setOnClickListener(v -> host.toggleAllApps());
        tile(grid, c, "set", "Einstellungen", "").setOnClickListener(v -> host.toggleSettings());

        brightness = slider(root, c, "sun", "Helligkeit");
        brightness.setOnChange(value -> {
            if (!host.setBrightness(value)) {
                brightness.setValue(host.brightness());
            }
        });
        volume = slider(root, c, "vol", "Lautstärke");
        volume.setOnChange(host::setVolume);

        root.addView(new Space(c), new LinearLayout.LayoutParams(MATCH, 0, 1f));
        setContentView(root);
        refresh();
    }

    /** Kachel: Symbol oben, Name und Zustand unten. */
    private LinearLayout tile(GridLayout grid, Context c, String icon, String label, String sub) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(q(16), q(14), q(16), q(14));
        t.setBackground(Ui.round(Ui.SURFACE, q(14), 0, 0));
        t.setMinimumHeight(q(88));
        t.addView(new Ui.Icon(c, icon, Ui.TEXT), new LinearLayout.LayoutParams(q(22), q(22)));
        t.addView(new Space(c), new LinearLayout.LayoutParams(MATCH, 0, 1f));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(c, label, q(15), Ui.TEXT, 700);
        name.setMaxLines(1);
        texts.addView(name);
        TextView s = Ui.text(c, sub, q(12), Ui.MUTED, 500);
        s.setMaxLines(1);
        s.setPadding(0, q(2), 0, 0);
        s.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
        texts.addView(s);
        t.addView(texts);
        GridLayout.LayoutParams p = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        p.width = 0;
        p.height = q(88);
        p.setMargins(q(5), q(5), q(5), q(5));
        grid.addView(t, p);
        return t;
    }

    private Ui.Slider slider(LinearLayout root, Context c, String icon, String label) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.round(Ui.SURFACE, q(14), 0, 0));
        row.setPadding(q(18), 0, q(18), 0);
        row.addView(new Ui.Icon(c, icon, Ui.TEXT), new LinearLayout.LayoutParams(q(22), q(22)));
        TextView name = Ui.text(c, label, q(14), Ui.TEXT, 500);
        name.setPadding(q(14), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(q(106), WRAP));
        Ui.Slider s = new Ui.Slider(c);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, q(28), 1f);
        sp.leftMargin = q(14);
        row.addView(s, sp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(MATCH, q(64));
        rp.topMargin = q(10);
        root.addView(row, rp);
        return s;
    }

    void refresh() {
        if (brightness == null) {
            return;
        }
        boolean flying = host.airplaneMode();
        airplaneSub.setText(flying ? "An" : "Aus");
        airplaneTile.setBackground(Ui.round(flying ? Ui.SURFACE_FOCUS : Ui.SURFACE, q(14), 0, 0));
        brightness.setValue(host.brightness());
        volume.setValue(host.volume());
    }
}
