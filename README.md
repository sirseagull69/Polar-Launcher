# Ori OS

Eigene Oberfläche und eigenes Android fürs AYN Thor. Die App in diesem Repo ist der Startbildschirm, die Einstellungen,
der Sperrbildschirm und das Bedienfeld auf dem unteren Bildschirm. Im eigenen OS (LineageOS 24, Gerät `odin2thor`)
steckt sie als privilegierte System-App.

Paketname und Ordner heißen aus der Anfangszeit noch `polarlicht`. Das bleibt so, sonst ließe sich die App nicht
über die installierte Version aktualisieren.

## Aufbau

| Ort | Inhalt |
| --- | --- |
| `app/src/main/java/.../MainActivity.java` | Fenster oben, Tasten, Brücke zwischen Oberfläche und System |
| `PanelPresentation.java` | Fenster auf dem unteren Bildschirm |
| `Leistung.java` | Messwerte, Leistungsprofile, Verbindung zum Systemdienst `orid` |
| `Systemseiten.java` | WLAN, Bluetooth, Speicher |
| `Roms.java`, `Covers.java`, `RomProvider.java` | Spiele finden, Bilder holen, Emulatoren starten |
| `OriMitteilungen.java`, `OriDienst.java` | Mitteilungen mitlesen, Ruhezustand |
| `Updater.java` | App-Update aus den GitHub-Releases |
| `app/src/main/assets/ui/` | Die Oberfläche selbst: `pl.js`, `pl.css`, Seiten in `s/` |
| `os/` | Alter Entwurf für den OS-Bau. Der echte Stand liegt noch auf dem Bau-PC, siehe unten. |

## Bauen

GitHub Actions, Workflow „APK bauen“, von Hand auf dem Zweig `v0.5` starten. Mit Haken bei „Release“ entsteht ein
Release `build-<Nummer>`, das die App auf dem Gerät als Update anbietet. Ohne Haken wird nur gebaut.

## Wichtig

- **Bootschleife:** Das OS läuft mit `ro.control_privapp_permissions=enforce`. Ein privilegiertes Recht im Manifest,
  das nicht in `privapp-permissions-ori.xml` des OS steht, lässt das Thor nicht mehr starten. Erst das OS, dann das Manifest.
- **Schlüssel:** Signiert wird mit `polarlicht-debug.keystore` aus dem Repo, Passwort bekannt. Für das eigene Gerät in
  Ordnung. Vor einer Weitergabe an andere braucht es einen geheimen Schlüssel.
- **Noch nicht im Repo:** `orid`, die Rechteliste, die SELinux-Regeln, `UE.java` und `flash.sh` liegen nur auf dem Bau-PC
  (`~/android/lineage`, `C:\Users\nilsp\polarlicht`). Sie gehören hierher nach `os/`.

## Spiele

Ori liest Spiele aus einem Ordner `ROMs` (auch `Roms`, `roms`, `Games`, `Spiele`) auf dem internen Speicher oder der
SD-Karte, mit einem Unterordner je System. Welche Endung zu welchem System gehört und wie jeder Emulator gestartet wird,
steht in `es_systems.xml` und `es_find_rules.xml` aus [ES-DE](https://es-de.org) (MIT-Lizenz, siehe `LIZENZ-ES-DE.txt`).
