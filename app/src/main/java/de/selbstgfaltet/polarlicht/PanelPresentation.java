package de.selbstgfaltet.polarlicht;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;

/** Der untere Bildschirm: zeigt dieselbe Ansicht wie oben, aber die Touch-Seite aus dem Canvas. */
final class PanelPresentation extends Presentation {
    private final MainActivity host;
    WebView web;
    private View home;

    /** web: die untere Web-Ansicht von vorher, damit sie nicht bei jeder Rückkehr neu laden muss; null beim ersten Mal. */
    PanelPresentation(Context outer, Display display, MainActivity host, WebView web) {
        super(outer, display);
        this.host = host;
        this.web = web;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getWindow() != null) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (web == null) {
            web = host.makeWeb(getContext(), "unten");
        } else if (web.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) web.getParent()).removeView(web);
        }
        web.onResume();
        FrameLayout root = new FrameLayout(getContext());
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (host.home != null && getWindow() != null) {
            try {
                home = host.home.panelView(getContext(), host, getWindow().getDecorView());
                root.addView(home, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            } catch (Throwable t) {
                Log.w("Polarlicht", "Bedienfeld neu: " + t);
                home = null;
            }
        }
        setContentView(root);
        showHome(host.homeShowing);
    }

    /**
     * Wer unten tippt, holt sich bei Android den Tastaturfokus auf diesen Bildschirm. Der Controller
     * soll trotzdem dort wirken, wo Ori es sagt (oben, oder unten nach R3), also alles an die Hauptansicht.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int c = e.getKeyCode();
        boolean pad = KeyEvent.isGamepadButton(c) || c == KeyEvent.KEYCODE_BACK || c == KeyEvent.KEYCODE_ESCAPE
                || (c >= KeyEvent.KEYCODE_DPAD_UP && c <= KeyEvent.KEYCODE_DPAD_CENTER) || c == KeyEvent.KEYCODE_ENTER;
        if (pad && host != null) return host.dispatchKeyEvent(e);
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (host != null && (e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return host.onGenericMotionEvent(e);
        return super.dispatchGenericMotionEvent(e);
    }

    /** Auf dem Startbildschirm liegt das neue Bedienfeld über der Web-Ansicht, sonst ist es weg. */
    void showHome(boolean on) {
        if (home != null) {
            home.setVisibility(on ? View.VISIBLE : View.GONE);
        }
        if (web != null) {
            web.setVisibility(on && home != null ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /**
     * Fenster schließen, die Web-Ansicht aber behalten: sie wird beim nächsten Öffnen wieder eingehängt.
     * Früher wurde sie jedes Mal weggeworfen und neu geladen, das machte den unteren Bildschirm träge.
     */
    WebView close() {
        try {
            dismiss();
        } catch (RuntimeException ignored) {
            // Bildschirm ist schon weg
        }
        WebView w = web;
        web = null;
        if (w != null) {
            if (w.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) w.getParent()).removeView(w);
            }
            w.onPause();
        }
        return w;
    }
}
