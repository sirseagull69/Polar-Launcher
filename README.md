# Polarlicht

Eigener Launcher für den AYN Thor. Version 0.3.0.

## Was er kann

- Startbildschirm im Polarlicht-Look auf dem oberen Bildschirm: Name groß, Farbstreifen, zuletzt gestartet.
- Bedienfeld auf dem unteren Bildschirm: Alle Apps, Einstellungen, WLAN, Start-Bildschirm wählen, Bildschirm-Info, Akku.
- Liste aller Apps, Favoriten festlegen. Der Startbildschirm zeigt die Favoriten, zuletzt gestartete zuerst.
- Hintergrundbild pro App: Bild in den Ordner `Bilder` legen, Dateiname = Paketname der App, z. B. `com.beispiel.spiel.jpg`. Der Pfad des Ordners steht in der Bildschirm-Info.
- Apps wahlweise oben oder unten starten (Kachel "Start: ...").
- Eigene Einstellungen (Start-Taste oder Kachel unten): Helligkeit, Lautstärke, WLAN, Bluetooth, Start-Bildschirm, Update.
- Updates über das Netz: Die App schaut im Repo nach einem neueren Build, lädt ihn und lässt Android einmal bestätigen.
- Start-Animation: beim ersten Öffnen nach dem Einschalten gehen die fünf Farbfelder nacheinander an.

## Als Startbildschirm festlegen

Nach der Installation die Home-Taste drücken und Polarlicht mit "Immer" wählen, oder in den Android-Einstellungen unter Apps > Standard-Apps > Start-App. Danach erscheint Polarlicht nach jedem Einschalten.

## Bedienung

| Taste | Startbildschirm | Alle Apps |
| --- | --- | --- |
| Steuerkreuz links/rechts, linker Stick | blättern | – |
| Steuerkreuz hoch/runter | – | blättern |
| A | starten | starten |
| Y | Alle Apps öffnen | zurück zum Start |
| X | – | Favorit an/aus |
| B | – | zurück |
| Select | Bildschirm-Info | Bildschirm-Info |

Alles geht auch per Tippen. Lange auf eine App tippen setzt den Favoriten.

## Bauen

Jeder Push auf `main` baut die APK über GitHub Actions und legt ein Release an.
Die neueste APK liegt immer unter `releases/latest/download/polarlicht.apk`.

Lokal, mit Android Studio oder installiertem Android-SDK und Gradle 8.9:

    gradle assembleDebug

Die APK liegt dann unter `app/build/outputs/apk/debug/`.

## Hinweise

- Signiert wird mit dem Schlüssel `polarlicht-debug.keystore` im Projekt, damit sich neue Versionen über alte installieren lassen. Das ist ein Bastel-Schlüssel mit bekanntem Passwort: für ein öffentliches Release einen eigenen, geheimen Schlüssel verwenden.
- Schrift: Space Grotesk, SIL Open Font License (`app/src/main/assets/SpaceGrotesk-OFL.txt`).
- Noch nicht drin: Spielebibliothek mit Emulatoren, Bilder aus dem Netz, Ori.

## Spiele

Polarlicht liest Spiele aus einem Ordner `ROMs` (auch `Roms`, `roms`, `Games`, `Spiele`) auf dem internen Speicher oder der SD-Karte,
mit einem Unterordner je System (`ROMs/nds`, `ROMs/gba`, `ROMs/switch` …). Cover und Hintergründe werden aus
`ES-DE/downloaded_media` übernommen, wenn es den Ordner gibt.

Welche Dateiendungen zu welchem System gehören und wie jeder Emulator gestartet wird, steht in
`app/src/main/assets/es_systems.xml` und `es_find_rules.xml`. Diese beiden Dateien stammen aus
[ES-DE](https://es-de.org) (MIT-Lizenz, siehe `LIZENZ-ES-DE.txt`).

## Oberfläche

Die Ansichten kommen aus dem Design-Canvas (`tools/canvas2ui.py` baut sie neu). Start, Bibliothek, Suche,
Bedienfeld und Akzentfarbe sind in `app/src/main/assets/ui/pl.js` direkt gebaut.
