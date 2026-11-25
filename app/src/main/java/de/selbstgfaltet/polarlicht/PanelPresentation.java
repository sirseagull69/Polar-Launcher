package de.selbstgfaltet.polarlicht;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;

/** Der untere Bildschirm: zeigt dieselbe Ansicht wie oben, aber die Touch-Seite aus dem Canvas. */
final class PanelPresentation extends Presentation {
    private final MainActivity host;
    WebView web;
    private View home;

    PanelPresentation(Context outer, Display display, MainActivity host) {
        super(outer, display);
        this.host = host;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getWindow() != null) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        web = host.makeWeb(getContext(), "unten");
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

    /** Auf dem Startbildschirm liegt das neue Bedienfeld über der Web-Ansicht, sonst ist es weg. */
    void showHome(boolean on) {
        if (home != null) {
            home.setVisibility(on ? View.VISIBLE : View.GONE);
        }
        if (web != null) {
            web.setVisibility(on && home != null ? View.INVISIBLE : View.VISIBLE);
        }
    }

    void close() {
        try {
            dismiss();
        } catch (RuntimeException ignored) {
            // Bildschirm ist schon weg
        }
        if (web != null) {
            web.destroy();
            web = null;
        }
    }
}
