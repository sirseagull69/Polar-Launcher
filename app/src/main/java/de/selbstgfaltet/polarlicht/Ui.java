package de.selbstgfaltet.polarlicht;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Farben, Schrift und kleine Bausteine für den Polarlicht-Look. */
final class Ui {
    static final int BG = 0xFF0A0D14;
    static final int SURFACE = 0xFF121724;
    static final int LINE = 0xFF1F2533;
    static final int TEXT = 0xFFEEF1F6;
    static final int MUTED = 0xFFA3ABBA;
    static final int[] COLORS = {0xFFFF5D5D, 0xFFFFB347, 0xFFFFE36E, 0xFF5FD38D, 0xFF5AA9FF};

    private static Typeface font;

    private Ui() {}

    static int color(int index) {
        return COLORS[((index % COLORS.length) + COLORS.length) % COLORS.length];
    }

    static Typeface font(Context c) {
        if (font == null) {
            try {
                font = Typeface.createFromAsset(c.getAssets(), "SpaceGrotesk.ttf");
            } catch (RuntimeException e) {
                font = Typeface.SANS_SERIF;
            }
        }
        return font;
    }

    /** Text in Space Grotesk. size ist in Bildschirm-Pixeln, weight 300 bis 700. */
    static TextView text(Context c, String s, float sizePx, int color, int weight) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        t.setTextColor(color);
        t.setTypeface(font(c));
        t.setFontVariationSettings("'wght' " + weight);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView mono(Context c, String s, float sizePx, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE);
        t.setIncludeFontPadding(false);
        return t;
    }

    static GradientDrawable round(int fill, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            d.setStroke(strokePx, strokeColor);
        }
        return d;
    }

    static TextView pill(Context c, String s, float sizePx, float padX, float height) {
        TextView t = text(c, s, sizePx, BG, 700);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(TEXT, height / 2f, 0, 0));
        t.setPadding((int) padX, 0, (int) padX, 0);
        t.setMinHeight((int) height);
        t.setMinimumHeight((int) height);
        return t;
    }

    /** Start-Animation: die fünf Farbfelder gehen nacheinander an, dann erscheint der Name. */
    static final class Intro extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint word = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 0f;

        Intro(Context c) {
            super(c);
            setBackgroundColor(BG);
            word.setTypeface(font(c));
            word.setFontVariationSettings("'wght' 700");
            word.setColor(TEXT);
            word.setTextAlign(Paint.Align.CENTER);
            word.setLetterSpacing(-0.04f);
        }

        /** 0 = dunkel, 1 = alles sichtbar. */
        void setProgress(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float unit = h / 374f;
            float stripW = 260 * unit;
            float stripH = 8 * unit;
            float gap = 5 * unit;
            float seg = (stripW - gap * 4) / 5f;
            float left = (w - stripW) / 2f;
            float top = h / 2f + 14 * unit;
            for (int i = 0; i < 5; i++) {
                float on = Math.max(0f, Math.min(1f, progress * 8f - i));
                paint.setColor(COLORS[i]);
                paint.setAlpha(Math.round(40 + 215 * on));
                float x = left + i * (seg + gap);
                canvas.drawRect(x, top, x + seg, top + stripH, paint);
            }
            float text = Math.max(0f, Math.min(1f, (progress - 0.55f) / 0.3f));
            word.setTextSize(56 * unit);
            word.setAlpha(Math.round(255 * text));
            canvas.drawText("Polarlicht", w / 2f, h / 2f - 6 * unit - (1f - text) * 8 * unit, word);
        }
    }

    /** Der Farbstreifen: ein Feld pro Eintrag, das ausgewählte leuchtet. */
    static final class Strip extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int count = 5;
        private int selected = 0;
        private int firstIndex = 0;

        Strip(Context c) {
            super(c);
        }

        /** total = Anzahl aller Einträge, sel = ausgewählter Eintrag. Es werden höchstens 12 Felder gezeigt. */
        void set(int total, int sel) {
            int window = 12;
            firstIndex = total <= window ? 0 : (sel / window) * window;
            count = Math.max(1, Math.min(window, total - firstIndex));
            selected = sel - firstIndex;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float gap = h * 0.7f;
            float seg = (w - gap * (count - 1)) / count;
            for (int i = 0; i < count; i++) {
                paint.setColor(color(firstIndex + i));
                paint.setAlpha(i == selected ? 255 : 72);
                float x = i * (seg + gap);
                canvas.drawRect(x, 0, x + seg, h, paint);
            }
        }
    }
}
