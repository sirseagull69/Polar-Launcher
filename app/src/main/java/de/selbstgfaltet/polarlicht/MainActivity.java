package de.selbstgfaltet.polarlicht;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Startbildschirm im Polarlicht-Look. Läuft auf dem Hauptbildschirm, das Bedienfeld auf dem zweiten. */
public class MainActivity extends Activity implements PanelPresentation.Host, DisplayManager.DisplayListener, Updater.Listener {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 20000);
        }
    };

    private Apps apps;
    private List<Apps.Entry> home = new ArrayList<>();
    private int sel = 0;
    private float f = 1f;
    /** Faktor vom Canvas (oben 960 x 540) auf das Gerät. */
    private float g = 1f;
    private int stickDir = 0;

    private ImageView bgImage;
    private View scrim;
    private LinearLayout homeLayer;
    private LinearLayout allLayer;
    private TextView updateCard;
    private TextView clockView;
    private TextView batteryView;
    private TextView labelView;
    private TextView nameView;
    private TextView metaView;
    private TextView infoView;
    private Ui.Strip strip;
    private ListView allList;
    private AllAdapter allAdapter;
    private String shownImage = "";
    private LinearLayout setLayer;
    private ListView setList;
    private SetAdapter setAdapter;
    private final List<Item> items = new ArrayList<>();
    private Updater updater;

    /** Eine Zeile der eigenen Einstellungen. */
    private abstract static class Item {
        final String label;
        final boolean adjustable;

        Item(String label, boolean adjustable) {
            this.label = label;
            this.adjustable = adjustable;
        }

        abstract String value();

        void click() {
        }

        void adjust(int step) {
        }
    }

    private DisplayManager displayManager;
    private PanelPresentation panel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        apps = new Apps(this);
        updater = new Updater(this, this);
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        f = Math.min(size.y / 374f, size.x / 664f);
        g = Math.min(size.y / 540f, size.x / 960f);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        bgImage = new ImageView(this);
        bgImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgImage.setVisibility(View.GONE);
        root.addView(bgImage, new FrameLayout.LayoutParams(MATCH, MATCH));

        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xFF0A0D14, 0xFF0A0D14, 0x8C0A0D14, 0x1F0A0D14}));
        scrim.setVisibility(View.GONE);
        root.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));

        buildHome(root, size.x);
        buildAll(root);
        buildSettings(root);

        infoView = Ui.mono(this, "", px(11), Ui.TEXT);
        infoView.setBackgroundColor(0xEE0A0D14);
        infoView.setPadding(px(20), px(16), px(20), px(16));
        infoView.setVisibility(View.GONE);
        infoView.setOnClickListener(v -> toggleInfo());
        root.addView(infoView, new FrameLayout.LayoutParams(MATCH, MATCH));

        setContentView(root);
        if (firstStartSinceBoot()) {
            playIntro(root);
        }
    }

    /** Wahr beim ersten Öffnen nach dem Einschalten des Geräts. */
    private boolean firstStartSinceBoot() {
        int boots = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        android.content.SharedPreferences prefs = getSharedPreferences("polarlicht", MODE_PRIVATE);
        if (boots < 0 || prefs.getInt("introBoot", -2) == boots) {
            return false;
        }
        prefs.edit().putInt("introBoot", boots).apply();
        return true;
    }

    private void playIntro(final FrameLayout root) {
        final Ui.Intro intro = new Ui.Intro(this);
        intro.setClickable(true);
        root.addView(intro, new FrameLayout.LayoutParams(MATCH, MATCH));
        ValueAnimator run = ValueAnimator.ofFloat(0f, 1f);
        run.setDuration(1900);
        run.addUpdateListener(a -> intro.setProgress((Float) a.getAnimatedValue()));
        run.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                intro.animate().alpha(0f).setStartDelay(350).setDuration(400)
                        .withEndAction(() -> root.removeView(intro)).start();
            }
        });
        run.start();
    }

    private int px(float design) {
        return Math.round(design * f);
    }

    private int q(float canvas) {
        return Math.round(canvas * g);
    }

    /** Startbildschirm nach Canvas 2.1 / 2.2. */
    private void buildHome(FrameLayout root, int screenWidth) {
        homeLayer = new LinearLayout(this);
        homeLayer.setOrientation(LinearLayout.VERTICAL);
        homeLayer.setPadding(q(44), q(32), q(44), q(32));

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        clockView = Ui.mono(this, "", q(11), Ui.MUTED);
        batteryView = Ui.mono(this, "", q(11), Ui.MUTED);
        status.addView(clockView, new LinearLayout.LayoutParams(0, WRAP, 1f));
        status.addView(batteryView, new LinearLayout.LayoutParams(WRAP, WRAP));
        homeLayer.addView(status, new LinearLayout.LayoutParams(MATCH, WRAP));

        homeLayer.addView(new Space(this), new LinearLayout.LayoutParams(MATCH, 0, 1f));

        labelView = Ui.mono(this, "", q(12), Ui.MUTED);
        homeLayer.addView(labelView, new LinearLayout.LayoutParams(WRAP, WRAP));

        nameView = Ui.text(this, "", q(88), Ui.TEXT, 700);
        nameView.setLetterSpacing(-0.02f);
        nameView.setLineSpacing(0f, 1.02f);
        nameView.setMaxLines(2);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        nameView.setMaxWidth(q(640));
        nameView.setOnClickListener(v -> launchSelected());
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(WRAP, WRAP);
        nameParams.topMargin = q(12);
        homeLayer.addView(nameView, nameParams);

        metaView = Ui.text(this, "", q(17), Ui.MUTED, 500);
        metaView.setMaxWidth(q(520));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(WRAP, WRAP);
        metaParams.topMargin = q(12);
        homeLayer.addView(metaView, metaParams);

        strip = new Ui.Strip(this);
        LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(q(180), q(8));
        stripParams.topMargin = q(16);
        homeLayer.addView(strip, stripParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        TextView start = Ui.text(this, "Starten", q(15), Ui.BG, 700);
        start.setGravity(Gravity.CENTER);
        start.setBackground(new Ui.FocusPill(g));
        start.setPadding(q(29), 0, q(29), 0);
        start.setOnClickListener(v -> launchSelected());
        buttons.addView(start, new LinearLayout.LayoutParams(WRAP, q(54)));
        TextView library = Ui.text(this, "Bibliothek", q(15), Ui.TEXT, 700);
        library.setGravity(Gravity.CENTER);
        library.setBackground(Ui.round(0x00000000, q(22), Ui.LINE_STRONG, Math.max(1, q(1.5f))));
        library.setPadding(q(24), 0, q(24), 0);
        library.setOnClickListener(v -> showAll(true));
        LinearLayout.LayoutParams libraryParams = new LinearLayout.LayoutParams(WRAP, q(44));
        libraryParams.leftMargin = q(7);
        buttons.addView(library, libraryParams);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(WRAP, WRAP);
        buttonParams.topMargin = q(19);
        buttonParams.leftMargin = -q(5);
        homeLayer.addView(buttons, buttonParams);

        homeLayer.addView(new Space(this), new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout hints = new LinearLayout(this);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        hints.setGravity(Gravity.CENTER_VERTICAL);
        hints.addView(Ui.hint(this, "A", "Starten", g));
        hints.addView(Ui.hint(this, "Y", "Bibliothek", g));
        hints.addView(Ui.hint(this, "Start", "Einstellungen", g));
        hints.addView(Ui.hint(this, "\u2190 \u2192", "Wechseln", g));
        homeLayer.addView(hints, new LinearLayout.LayoutParams(WRAP, WRAP));

        root.addView(homeLayer, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Einblendung "Update verfügbar" nach Canvas 2.6
        updateCard = Ui.text(this, "", q(13), Ui.TEXT, 700);
        updateCard.setBackground(Ui.round(Ui.SURFACE, q(14), Ui.LINE_STRONG, Math.max(1, q(1))));
        updateCard.setPadding(q(16), q(12), q(16), q(12));
        updateCard.setLineSpacing(q(3), 1f);
        updateCard.setVisibility(View.GONE);
        updateCard.setOnClickListener(v -> showSettings(true));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
        cardParams.topMargin = q(64);
        cardParams.rightMargin = q(44);
        root.addView(updateCard, cardParams);
    }

    private void buildAll(FrameLayout root) {
        allLayer = new LinearLayout(this);
        allLayer.setOrientation(LinearLayout.VERTICAL);
        allLayer.setBackgroundColor(Ui.BG);
        allLayer.setPadding(px(30), px(20), px(30), 0);
        allLayer.setVisibility(View.GONE);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        TextView title = Ui.text(this, "Alle Apps", px(26), Ui.TEXT, 700);
        TextView hint = Ui.mono(this, "A starten · X Favorit · B zurück", px(11), Ui.MUTED);
        head.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.addView(hint, new LinearLayout.LayoutParams(WRAP, WRAP));
        allLayer.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        allList = new ListView(this);
        allList.setDivider(null);
        allList.setSelector(Ui.round(Ui.SURFACE, px(10), 0, 0));
        allList.setVerticalScrollBarEnabled(false);
        allAdapter = new AllAdapter();
        allList.setAdapter(allAdapter);
        allList.setOnItemClickListener((parent, view, position, id) -> launch(apps.all.get(position)));
        allList.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleFavoriteAt(position);
            return true;
        });
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        listParams.topMargin = px(10);
        allLayer.addView(allList, listParams);

        root.addView(allLayer, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    private void buildSettings(FrameLayout root) {
        items.add(new Item("Helligkeit", true) {
            @Override
            String value() {
                int v = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
                return Math.round(v * 100f / 255f) + " %";
            }

            @Override
            void adjust(int step) {
                if (!Settings.System.canWrite(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, "Bitte einmal erlauben, dass Polarlicht die Helligkeit ändern darf.", Toast.LENGTH_LONG).show();
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
                    } catch (RuntimeException ex) {
                        openSettings(Settings.ACTION_DISPLAY_SETTINGS);
                    }
                    return;
                }
                int v = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
                v = Math.max(8, Math.min(255, v + step * 25));
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, v);
            }
        });
        items.add(new Item("Lautstärke", true) {
            @Override
            String value() {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                return Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max) + " %";
            }

            @Override
            void adjust(int step) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, step > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            }
        });
        items.add(new Item("WLAN", false) {
            @Override
            String value() {
                return "öffnen";
            }

            @Override
            void click() {
                openSettings(Build.VERSION.SDK_INT >= 29 ? "android.settings.panel.action.WIFI" : Settings.ACTION_WIFI_SETTINGS);
            }
        });
        items.add(new Item("Bluetooth", false) {
            @Override
            String value() {
                return "öffnen";
            }

            @Override
            void click() {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS);
            }
        });
        items.add(new Item("Apps starten auf", false) {
            @Override
            String value() {
                return launchLabel();
            }

            @Override
            void click() {
                cycleLaunchDisplay();
            }
        });
        items.add(new Item("Als Startbildschirm festlegen", false) {
            @Override
            String value() {
                return "öffnen";
            }

            @Override
            void click() {
                openSettings(Settings.ACTION_HOME_SETTINGS);
            }
        });
        items.add(new Item("Update", false) {
            @Override
            String value() {
                return updater.status();
            }

            @Override
            void click() {
                updater.clearMessage();
                if (updater.available()) {
                    updater.install(MainActivity.this);
                } else {
                    updater.check(true);
                }
            }
        });
        items.add(new Item("Bildschirm-Info", false) {
            @Override
            String value() {
                return "anzeigen";
            }

            @Override
            void click() {
                toggleInfo();
            }
        });
        items.add(new Item("Android-Einstellungen", false) {
            @Override
            String value() {
                return "alles Weitere";
            }

            @Override
            void click() {
                openSettings(Settings.ACTION_SETTINGS);
            }
        });

        setLayer = new LinearLayout(this);
        setLayer.setOrientation(LinearLayout.VERTICAL);
        setLayer.setBackgroundColor(Ui.BG);
        setLayer.setPadding(px(30), px(20), px(30), 0);
        setLayer.setVisibility(View.GONE);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        head.addView(Ui.text(this, "Einstellungen", px(26), Ui.TEXT, 700), new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.addView(Ui.mono(this, "A wählen · links/rechts ändern · B zurück", px(11), Ui.MUTED), new LinearLayout.LayoutParams(WRAP, WRAP));
        setLayer.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        setList = new ListView(this);
        setList.setDivider(null);
        setList.setSelector(Ui.round(Ui.SURFACE, px(10), 0, 0));
        setList.setVerticalScrollBarEnabled(false);
        setAdapter = new SetAdapter();
        setList.setAdapter(setAdapter);
        setList.setOnItemClickListener((parent, view, position, id) -> {
            Item item = items.get(position);
            if (item.adjustable) {
                item.adjust(1);
            } else {
                item.click();
            }
            setAdapter.notifyDataSetChanged();
            if (panel != null) {
                panel.refresh();
            }
        });
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        listParams.topMargin = px(10);
        setLayer.addView(setList, listParams);
        root.addView(setLayer, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    private boolean settingsOpen() {
        return setLayer.getVisibility() == View.VISIBLE;
    }

    private void showSettings(boolean show) {
        if (show) {
            allLayer.setVisibility(View.GONE);
            homeLayer.setVisibility(View.GONE);
            setLayer.setVisibility(View.VISIBLE);
            setAdapter.notifyDataSetChanged();
            setList.requestFocus();
            setList.setSelection(0);
        } else {
            setLayer.setVisibility(View.GONE);
            homeLayer.setVisibility(View.VISIBLE);
            render();
        }
    }

    @Override
    public void toggleSettings() {
        showSettings(!settingsOpen());
    }

    @Override
    public void onUpdateState() {
        updateStatus();
        if (setAdapter != null) {
            setAdapter.notifyDataSetChanged();
        }
    }

    // ---------- Lebenszyklus ----------

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        apps.reload();
        home = firstFive(apps.home());
        if (sel >= home.size()) {
            sel = 0;
        }
        allAdapter.notifyDataSetChanged();
        render();
        handler.removeCallbacks(tick);
        tick.run();
        displayManager.registerDisplayListener(this, handler);
        showPanel();
        updater.check(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        displayManager.unregisterDisplayListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        hidePanel();
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
                String why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                updater.fail("Update nicht installiert" + (why == null ? "" : ": " + why));
            }
            return;
        }
        if (settingsOpen()) {
            showSettings(false);
        } else if (allLayer.getVisibility() == View.VISIBLE) {
            showAll(false);
        } else {
            sel = 0;
            render();
        }
        infoView.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (infoView.getVisibility() == View.VISIBLE) {
            infoView.setVisibility(View.GONE);
        } else if (settingsOpen()) {
            showSettings(false);
        } else if (allLayer.getVisibility() == View.VISIBLE) {
            showAll(false);
        }
        // Auf dem Startbildschirm macht "Zurück" nichts: ein Launcher wird nicht geschlossen.
    }

    // ---------- Anzeige ----------

    private void render() {
        if (home.isEmpty()) {
            labelView.setText("NOCH NICHTS HIER");
            nameView.setText("Polarlicht");
            metaView.setText("Y öffnet die Bibliothek mit allen Apps.");
            strip.set(1, 0);
            showImage(null);
            return;
        }
        Apps.Entry e = home.get(sel);
        labelView.setText(e.last > 0 ? "APP · ZULETZT GESTARTET" : e.fav ? "APP · FAVORIT" : "APP");
        nameView.setText(e.label);
        metaView.setText(lastText(e.last));
        strip.set(home.size(), sel);
        showImage(e);
    }

    private static List<Apps.Entry> firstFive(List<Apps.Entry> list) {
        return list.size() > 5 ? new ArrayList<>(list.subList(0, 5)) : list;
    }

    private static String lastText(long last) {
        if (last <= 0) {
            return "noch nicht von hier gestartet";
        }
        long days = (System.currentTimeMillis() - last) / 86400000L;
        if (days <= 0) {
            return "zuletzt heute gestartet";
        }
        if (days == 1) {
            return "zuletzt gestern gestartet";
        }
        return "zuletzt vor " + days + " Tagen gestartet";
    }

    /** Zeigt ein Bild aus dem Ordner "Bilder", wenn es eins mit dem Paketnamen der App gibt. */
    private void showImage(Apps.Entry e) {
        String key = e == null ? "" : e.pkg;
        if (key.equals(shownImage)) {
            return;
        }
        shownImage = key;
        Bitmap bitmap = null;
        File dir = getExternalFilesDir("Bilder");
        if (e != null && dir != null) {
            for (String ext : new String[] {".jpg", ".jpeg", ".png", ".webp"}) {
                File file = new File(dir, e.pkg + ext);
                if (file.isFile()) {
                    bitmap = decode(file);
                    break;
                }
            }
        }
        bgImage.setImageBitmap(bitmap);
        bgImage.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);
        scrim.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1920) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (RuntimeException | OutOfMemoryError ex) {
            return null;
        }
    }

    private void updateStatus() {
        clockView.setText(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date()));
        batteryView.setText(battery() + " %");
        boolean update = updater.available() && homeLayer.getVisibility() == View.VISIBLE;
        updateCard.setText("Update verfügbar\nBuild " + updater.remoteBuild() + " · Start: Einstellungen");
        updateCard.setVisibility(update ? View.VISIBLE : View.GONE);
        if (panel != null) {
            panel.refresh();
        }
    }

    private void showAll(boolean show) {
        setLayer.setVisibility(View.GONE);
        allLayer.setVisibility(show ? View.VISIBLE : View.GONE);
        homeLayer.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) {
            allList.requestFocus();
            allList.setSelection(0);
        } else {
            home = firstFive(apps.home());
            if (sel >= home.size()) {
                sel = 0;
            }
            render();
        }
    }

    private void toggleFavoriteAt(int position) {
        if (position < 0 || position >= apps.all.size()) {
            return;
        }
        apps.toggleFavorite(apps.all.get(position));
        allAdapter.notifyDataSetChanged();
    }

    // ---------- Starten ----------

    private void launchSelected() {
        if (!home.isEmpty()) {
            launch(home.get(sel));
        }
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
                Toast.makeText(this, "Auf Bildschirm " + display + " ging es nicht, normal gestartet.", Toast.LENGTH_LONG).show();
            } catch (RuntimeException second) {
                Toast.makeText(this, e.label + " lässt sich nicht starten.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------- Tasten und Stick ----------

    private void move(int step) {
        if (home.isEmpty() || allLayer.getVisibility() == View.VISIBLE || settingsOpen()) {
            return;
        }
        sel = (sel + step + home.size()) % home.size();
        render();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean all = allLayer.getVisibility() == View.VISIBLE;
        if (settingsOpen()) {
            int position = setList.getSelectedItemPosition();
            Item item = position >= 0 && position < items.size() ? items.get(position) : null;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (item != null && item.adjustable) {
                        item.adjust(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                        setAdapter.notifyDataSetChanged();
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    if (item != null) {
                        if (item.adjustable) {
                            item.adjust(1);
                        } else {
                            item.click();
                        }
                        setAdapter.notifyDataSetChanged();
                        if (panel != null) {
                            panel.refresh();
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                case KeyEvent.KEYCODE_BUTTON_START:
                    showSettings(false);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_SELECT:
                    toggleInfo();
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!all) {
                    move(-1);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!all) {
                    move(1);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (all) {
                    int position = allList.getSelectedItemPosition();
                    if (position >= 0 && position < apps.all.size()) {
                        launch(apps.all.get(position));
                    }
                } else {
                    launchSelected();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                showAll(!all);
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (all) {
                    toggleFavoriteAt(allList.getSelectedItemPosition());
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
                onBackPressed();
                return true;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                toggleInfo();
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
                showSettings(true);
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && event.getAction() == MotionEvent.ACTION_MOVE) {
            float x = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            if (Math.abs(x) < 0.5f) {
                x = event.getAxisValue(MotionEvent.AXIS_X);
            }
            int dir = x > 0.6f ? 1 : x < -0.6f ? -1 : Math.abs(x) < 0.3f ? 0 : stickDir;
            if (dir != stickDir) {
                stickDir = dir;
                if (dir != 0) {
                    move(dir);
                }
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    // ---------- Zweiter Bildschirm ----------

    private int myDisplayId() {
        return getWindowManager().getDefaultDisplay().getDisplayId();
    }

    private Display otherDisplay() {
        for (Display d : displayManager.getDisplays()) {
            if (d.getDisplayId() != myDisplayId() && d.getState() != Display.STATE_OFF) {
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
            panel.refresh();
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
            try {
                panel.dismiss();
            } catch (RuntimeException ignored) {
                // Der Bildschirm war schon weg.
            }
            panel = null;
        }
    }

    @Override
    public void onDisplayAdded(int displayId) {
        showPanel();
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        showPanel();
    }

    @Override
    public void onDisplayChanged(int displayId) {
        // Größenänderungen übernimmt Android selbst.
    }

    // ---------- Aufrufe vom Bedienfeld ----------

    @Override
    public void toggleAllApps() {
        showAll(allLayer.getVisibility() != View.VISIBLE);
    }

    @Override
    public void toggleInfo() {
        if (infoView.getVisibility() == View.VISIBLE) {
            infoView.setVisibility(View.GONE);
            return;
        }
        StringBuilder s = new StringBuilder("Bildschirme (tippen oder B schließt)\n\n");
        for (Display d : displayManager.getDisplays()) {
            Point p = new Point();
            d.getRealSize(p);
            s.append("Nr. ").append(d.getDisplayId()).append(" · ").append(d.getName()).append(" · ")
                    .append(p.x).append(" x ").append(p.y).append(" · Flags ").append(d.getFlags())
                    .append(d.getDisplayId() == myDisplayId() ? " · hier läuft der Launcher" : "")
                    .append('\n');
        }
        s.append("\nBedienfeld auf zweitem Bildschirm: ").append(panel != null && panel.isShowing() ? "ja" : "nein");
        s.append("\nApps starten auf: ").append(launchLabel());
        s.append("\nBilder-Ordner: ").append(getExternalFilesDir("Bilder"));
        infoView.setText(s.toString());
        infoView.setVisibility(View.VISIBLE);
    }

    @Override
    public void cycleLaunchDisplay() {
        Display[] displays = displayManager.getDisplays();
        int current = apps.launchDisplay();
        int next = -1;
        if (current < 0) {
            next = displays.length > 0 ? displays[0].getDisplayId() : -1;
        } else {
            for (int i = 0; i < displays.length; i++) {
                if (displays[i].getDisplayId() == current && i + 1 < displays.length) {
                    next = displays[i + 1].getDisplayId();
                }
            }
        }
        apps.setLaunchDisplay(next);
    }

    @Override
    public String launchLabel() {
        int id = apps.launchDisplay();
        if (id < 0) {
            return "automatisch";
        }
        return id == myDisplayId() ? "oben (Nr. " + id + ")" : "unten (Nr. " + id + ")";
    }

    @Override
    public int battery() {
        Intent status = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) {
            return 0;
        }
        int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return scale <= 0 ? 0 : Math.round(level * 100f / scale);
    }

    @Override
    public float brightness() {
        return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128) / 255f;
    }

    @Override
    public boolean setBrightness(float value) {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Bitte einmal erlauben, dass Polarlicht die Helligkeit ändern darf.", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException ex) {
                openSettings(Settings.ACTION_DISPLAY_SETTINGS);
            }
            return false;
        }
        int v = Math.max(8, Math.min(255, Math.round(value * 255f)));
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, v);
        return true;
    }

    @Override
    public float volume() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    @Override
    public void setVolume(float value) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(value * max), 0);
    }

    @Override
    public boolean airplaneMode() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    @Override
    public void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ex) {
            Toast.makeText(this, "Das ließ sich nicht öffnen.", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Liste der eigenen Einstellungen ----------

    private final class SetAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Item item = items.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(12), 0, px(12), 0);
            row.setMinimumHeight(px(44));
            TextView name = Ui.text(MainActivity.this, item.label, px(19), Ui.TEXT, 500);
            row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
            String value = item.adjustable ? "‹  " + item.value() + "  ›" : item.value();
            row.addView(Ui.mono(MainActivity.this, value, px(12), Ui.MUTED), new LinearLayout.LayoutParams(WRAP, WRAP));
            return row;
        }
    }

    // ---------- Liste "Alle Apps" ----------

    private final class AllAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.all.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.all.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(12), 0, px(12), 0);
            row.setMinimumHeight(px(44));
            Apps.Entry e = apps.all.get(position);
            View dot = new View(MainActivity.this);
            dot.setBackground(Ui.round(e.fav ? Ui.color(position) : Ui.LINE, px(5), 0, 0));
            row.addView(dot, new LinearLayout.LayoutParams(px(10), px(10)));
            TextView name = Ui.text(MainActivity.this, e.label, px(19), e.fav ? Ui.TEXT : Ui.MUTED, e.fav ? 700 : 500);
            name.setPadding(px(14), 0, 0, 0);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
            if (e.fav) {
                row.addView(Ui.mono(MainActivity.this, "Favorit", px(11), Ui.MUTED), new LinearLayout.LayoutParams(WRAP, WRAP));
            }
            return row;
        }
    }
}
