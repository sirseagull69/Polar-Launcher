package de.selbstgfaltet.polarlicht;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Das Bedienfeld auf dem zweiten Bildschirm. */
final class PanelPresentation extends Presentation {
    interface Host {
        void toggleAllApps();

        void toggleSettings();

        void toggleInfo();

        void cycleLaunchDisplay();

        String launchLabel();

        int battery();

        void openSettings(String action);
    }

    private final Host host;
    private TextView clock;
    private TextView launchTile;
    private TextView batteryTile;

    PanelPresentation(Context outer, Display display, Host host) {
        super(outer, display);
        this.host = host;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Point size = new Point();
        getDisplay().getRealSize(size);
        float f = Math.min(size.y / 374f, size.x / 430f);
        Context c = getContext();

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int pad = Math.round(24 * f);
        root.setPadding(pad, Math.round(20 * f), pad, Math.round(20 * f));

        clock = Ui.mono(c, "", 12 * f, Ui.MUTED);
        root.addView(clock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(2);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridParams.topMargin = Math.round(10 * f);
        root.addView(grid, gridParams);

        tile(grid, c, f, "Alle Apps").setOnClickListener(v -> host.toggleAllApps());
        tile(grid, c, f, "Einstellungen").setOnClickListener(v -> host.toggleSettings());
        tile(grid, c, f, "WLAN").setOnClickListener(v -> host.openSettings(Settings.ACTION_WIFI_SETTINGS));
        launchTile = tile(grid, c, f, "");
        launchTile.setOnClickListener(v -> {
            host.cycleLaunchDisplay();
            refresh();
        });
        tile(grid, c, f, "Bildschirm-Info").setOnClickListener(v -> host.toggleInfo());
        batteryTile = tile(grid, c, f, "");

        setContentView(root);
        refresh();
    }

    private TextView tile(GridLayout grid, Context c, float f, String label) {
        TextView t = Ui.text(c, label, 16 * f, Ui.TEXT, 500);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Math.round(16 * f), 0, Math.round(12 * f), 0);
        t.setBackground(Ui.round(Ui.SURFACE, 14 * f, 0, 0));
        t.setMaxLines(2);
        GridLayout.LayoutParams p = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        p.width = 0;
        p.height = 0;
        int m = Math.round(6 * f);
        p.setMargins(m, m, m, m);
        grid.addView(t, p);
        return t;
    }

    void refresh() {
        if (clock == null) {
            return;
        }
        clock.setText(new SimpleDateFormat("HH:mm · EEEE, d. MMMM", Locale.GERMANY).format(new Date()));
        launchTile.setText("Start: " + host.launchLabel());
        batteryTile.setText("Akku " + host.battery() + " %");
    }
}
