# Oberfläche ohne Gerät prüfen

`pl.js` bringt eine Ersatz-Brücke mit (`stub()`), deshalb läuft die ganze Oberfläche auch in einem normalen Chrome.

    cd app/src/main/assets && python3 -m http.server 8765 &
    cd tools/vorschau-browser && npm i puppeteer-core
    node alle.js              # alle Ansichten oben und unten, Fehler auf der Konsole, Bilder nach shots/
    node alle.js '^dc:'       # nur passende Schlüssel
    node eine.js unten dc::UntenLeistung 1500 bild.png        # eine Ansicht, optional danach Tasten: ... right A
    node wellen.js            # misst, ob der Hintergrund beim Neuzeichnen springt

Der Pfad zu Chrome steht oben in den Skripten (`/usr/bin/google-chrome`). Was das nicht prüft: alles hinter der echten
Brücke (WLAN, Bluetooth, Lüfter, RGB, Spiele starten) und das Zusammenspiel der beiden Bildschirme.
