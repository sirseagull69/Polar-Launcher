# Polarlicht

Eigener Launcher für den AYN Thor. Version 0.1.0.

## Was er kann

- Startbildschirm im Polarlicht-Look auf dem oberen Bildschirm: Name groß, Farbstreifen, zuletzt gestartet.
- Bedienfeld auf dem unteren Bildschirm: Alle Apps, Einstellungen, WLAN, Start-Bildschirm wählen, Bildschirm-Info, Akku.
- Liste aller Apps, Favoriten festlegen. Der Startbildschirm zeigt die Favoriten, zuletzt gestartete zuerst.
- Hintergrundbild pro App: Bild in den Ordner `Bilder` legen, Dateiname = Paketname der App, z. B. `com.beispiel.spiel.jpg`. Der Pfad des Ordners steht in der Bildschirm-Info.
- Apps wahlweise oben oder unten starten (Kachel "Start: ...").

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

Jeder Push auf `main` baut die APK über GitHub Actions (Reiter "Actions", Artefakt `polarlicht-apk`).
Ein Tag wie `v0.1.0` legt zusätzlich ein Release mit der APK an.

Lokal, mit Android Studio oder installiertem Android-SDK und Gradle 8.9:

    gradle assembleDebug

Die APK liegt dann unter `app/build/outputs/apk/debug/`.

## Hinweise

- Signiert wird mit dem Schlüssel `polarlicht-debug.keystore` im Projekt, damit sich neue Versionen über alte installieren lassen. Das ist ein Bastel-Schlüssel mit bekanntem Passwort: für ein öffentliches Release einen eigenen, geheimen Schlüssel verwenden.
- Schrift: Space Grotesk, SIL Open Font License (`app/src/main/assets/SpaceGrotesk-OFL.txt`).
- Noch nicht drin: Spielebibliothek mit Emulatoren, Bilder aus dem Netz, Ori.
