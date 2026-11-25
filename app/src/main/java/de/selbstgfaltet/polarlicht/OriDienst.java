package de.selbstgfaltet.polarlicht;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Einziger Zweck: Ruhezustand. Sperrt auf Wunsch den Bildschirm, liest sonst nichts mit. */
public final class OriDienst extends AccessibilityService {
    static OriDienst aktiv;
    static boolean schlafenWennBereit;

    @Override protected void onServiceConnected() {
        aktiv = this;
        if (schlafenWennBereit) { schlafenWennBereit = false; schlafen(); }
    }

    static boolean schlafen() {
        return aktiv != null && aktiv.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { if (aktiv == this) aktiv = null; super.onDestroy(); }
}
