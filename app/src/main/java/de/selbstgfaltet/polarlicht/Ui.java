package de.selbstgfaltet.polarlicht;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Farben, Schrift und kleine Bausteine für den Polarlicht-Look. */
final class Ui {
    static final int BG = 0xFF0A0D14;
    static final int SURFACE = 0xFF121724;
    static final int SURFACE_FOCUS = 0xFF1A2132;
    static final int LINE = 0xFF1F2533;
    static final int LINE_STRONG = 0xFF3A4358;
    static final int TEXT = 0xFFEEF1F6;
    static final int MUTED = 0xFFA3ABBA;
    static final int[] COLORS = {0xFFFF5D5D, 0xFFFFB347, 0xFFFFE36E, 0xFF5FD38D, 0xFF5AA9FF};

    private static Typeface font;
    private static Typeface monoFont;

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
        if (monoFont == null) {
            try {
                monoFont = Typeface.createFromAsset(c.getAssets(), "JetBrainsMono.ttf");
            } catch (RuntimeException e) {
                monoFont = Typeface.MONOSPACE;
            }
        }
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        t.setTextColor(color);
        t.setTypeface(monoFont);
        t.setFontVariationSettings("'wght' 500");
        t.setLetterSpacing(0.04f);
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

    /** Pille mit Fokus-Ring wie im Canvas: 2 px Ring in Textfarbe mit 3 px Abstand. */
    static final class FocusPill extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float unit;

        FocusPill(float unit) {
            this.unit = unit;
            fill.setColor(TEXT);
            ring.setColor(TEXT);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(2 * unit);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF b = new RectF(getBounds());
            float inset = 5 * unit;
            RectF inner = new RectF(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
            canvas.drawRoundRect(inner, inner.height() / 2f, inner.height() / 2f, fill);
            RectF outer = new RectF(b.left + unit, b.top + unit, b.right - unit, b.bottom - unit);
            canvas.drawRoundRect(outer, outer.height() / 2f, outer.height() / 2f, ring);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Tasten-Hinweis: Taste im Kreis, daneben was sie tut. */
    static LinearLayout hint(Context c, String key, String label, float unit) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView k = mono(c, key, 11 * unit, TEXT);
        k.setFontVariationSettings("'wght' 700");
        k.setLetterSpacing(0f);
        k.setGravity(Gravity.CENTER);
        k.setBackground(round(0x00000000, 11 * unit, MUTED, Math.max(1, Math.round(1.5f * unit))));
        int padX = key.length() > 1 ? Math.round(7 * unit) : 0;
        k.setPadding(padX, 0, padX, 0);
        k.setMinWidth(Math.round(22 * unit));
        k.setMinimumWidth(Math.round(22 * unit));
        row.addView(k, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Math.round(22 * unit)));
        TextView l = text(c, label, 13 * unit, MUTED, 500);
        l.setPadding(Math.round(7 * unit), 0, Math.round(20 * unit), 0);
        row.addView(l);
        return row;
    }

    /** Einfache Strich-Symbole für die Kacheln. */
    static final class Icon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String kind;

        Icon(Context c, String kind, int color) {
            super(c);
            this.kind = kind;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float u = getWidth() / 24f;
            p.setStrokeWidth(1.8f * u);
            canvas.save();
            canvas.scale(u, u);
            p.setStrokeWidth(1.8f);
            Path path = new Path();
            switch (kind) {
                case "wifi":
                    canvas.drawArc(new RectF(2, 7, 22, 27), 225, 90, false, p);
                    canvas.drawArc(new RectF(6, 11, 18, 23), 225, 90, false, p);
                    canvas.drawCircle(12, 18, 0.9f, p);
                    break;
                case "bt":
                    path.moveTo(7, 8);
                    path.lineTo(17, 16);
                    path.lineTo(12, 20);
                    path.lineTo(12, 4);
                    path.lineTo(17, 8);
                    path.lineTo(7, 16);
                    canvas.drawPath(path, p);
                    break;
                case "plane":
                    path.moveTo(3, 12);
                    path.lineTo(21, 4);
                    path.lineTo(14, 21);
                    path.lineTo(11, 13);
                    path.close();
                    canvas.drawPath(path, p);
                    break;
                case "power":
                    canvas.drawArc(new RectF(4, 6, 20, 22), 180, 180, false, p);
                    canvas.drawLine(12, 14, 16, 9, p);
                    canvas.drawLine(4, 14, 20, 14, p);
                    break;
                case "grid":
                    canvas.drawRoundRect(new RectF(4, 4, 10, 10), 1, 1, p);
                    canvas.drawRoundRect(new RectF(14, 4, 20, 10), 1, 1, p);
                    canvas.drawRoundRect(new RectF(4, 14, 10, 20), 1, 1, p);
                    canvas.drawRoundRect(new RectF(14, 14, 20, 20), 1, 1, p);
                    break;
                case "set":
                    canvas.drawLine(4, 7, 13, 7, p);
                    canvas.drawLine(19, 7, 20, 7, p);
                    canvas.drawCircle(16, 7, 2, p);
                    canvas.drawLine(4, 17, 7, 17, p);
                    canvas.drawLine(13, 17, 20, 17, p);
                    canvas.drawCircle(10, 17, 2, p);
                    break;
                case "sun":
                    canvas.drawCircle(12, 12, 3.5f, p);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * i / 4;
                        canvas.drawLine(12 + (float) Math.cos(a) * 7, 12 + (float) Math.sin(a) * 7,
                                12 + (float) Math.cos(a) * 9, 12 + (float) Math.sin(a) * 9, p);
                    }
                    break;
                default:
                    path.moveTo(5, 10);
                    path.lineTo(8, 10);
                    path.lineTo(12, 6);
                    path.lineTo(12, 18);
                    path.lineTo(8, 14);
                    path.lineTo(5, 14);
                    path.close();
                    canvas.drawPath(path, p);
                    canvas.drawArc(new RectF(11, 8, 19, 16), -50, 100, false, p);
                    break;
            }
            canvas.restore();
        }
    }

    /** Regler zum Ziehen: heller Balken, runder Knopf. */
    static final class Slider extends View {
        interface OnChange {
            void changed(float value);
        }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float value = 0.5f;
        private OnChange onChange;

        Slider(Context c) {
            super(c);
        }

        void setValue(float v) {
            value = Math.max(0f, Math.min(1f, v));
            invalidate();
        }

        void setOnChange(OnChange l) {
            onChange = l;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float r = getHeight() / 2f;
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                setValue((e.getX() - r) / Math.max(1f, getWidth() - 2 * r));
                if (onChange != null) {
                    onChange.changed(value);
                }
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight();
            float r = h / 2f;
            float track = h * 10f / 28f;
            float top = (h - track) / 2f;
            float x = r + value * (getWidth() - 2 * r);
            p.setColor(LINE_STRONG);
            canvas.drawRoundRect(0, top, getWidth(), top + track, track / 2, track / 2, p);
            p.setColor(TEXT);
            canvas.drawRoundRect(0, top, x, top + track, track / 2, track / 2, p);
            canvas.drawCircle(x, r, r, p);
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
            int window = 5;
            firstIndex = total <= window ? 0 : (sel / window) * window;
            count = Math.max(1, Math.min(window, total - firstIndex));
            selected = sel - firstIndex;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float gap = h * 0.5f;
            float seg = (w - gap * (count - 1)) / count;
            float r = h * 0.25f;
            for (int i = 0; i < count; i++) {
                paint.setColor(color(firstIndex + i));
                paint.setAlpha(i == selected ? 255 : 56);
                float x = i * (seg + gap);
                canvas.drawRoundRect(x, 0, x + seg, h, r, r, paint);
            }
        }
    }
}
