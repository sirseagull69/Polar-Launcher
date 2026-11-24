package de.selbstgfaltet.polarlicht;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;
import android.webkit.WebView;

/** Der untere Bildschirm: zeigt dieselbe Ansicht wie oben, aber die Touch-Seite aus dem Canvas. */
final class PanelPresentation extends Presentation {
    private final MainActivity host;
    WebView web;

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
        setContentView(web);
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
