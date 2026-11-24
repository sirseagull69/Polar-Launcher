# Polarlicht OS

Eigenes Android fürs AYN Thor auf Basis von LineageOS 24.0 (Gerätename `odin2thor`).
Stand: Bauumgebung eingerichtet, Quellcode wird geladen. Gebaut wurde noch nichts.

## Was hier liegt

| Datei | Zweck | Stand |
|---|---|---|
| `einrichten.sh` | Richtet WSL2 ein und lädt den Quellcode | läuft auf dem PC |
| `bootanimation.zip` | Boot-Animation aus dem Canvas, 1920 × 1080, 30 Bilder pro Sekunde | fertig, am Gerät ungetestet |
| `vendor/polarlicht/` | Bindet Polarlicht und die Boot-Animation ins System ein | Entwurf, noch nie gebaut |

## Reihenfolge

1. Quellcode laden (`einrichten.sh`).
2. Herstellerdateien fürs Thor holen: aus dem offiziellen LineageOS-Paket für `odin2thor` auspacken
   (Anleitung von LineageOS: „Extracting proprietary blobs from LineageOS zip files“).
3. Erster Bau ohne Änderungen: `source build/envsetup.sh`, `breakfast odin2thor`, `brunch odin2thor`.
   Erst wenn der durchläuft, kommt Polarlicht dazu.
4. `vendor/polarlicht` in den Quellcode kopieren, `polarlicht.apk` und `bootanimation.zip` dazulegen,
   im Gerätebaum einbinden, neu bauen.
5. Thor sichern, Android 13 als Werksfirmware sicherstellen, Bootloader entsperren, per fastboot aufspielen.
6. Danach: Polarlicht als festen Startbildschirm setzen, System-Updates über eigene Adresse, AYN-Extras
   (Lüfter, Stick-Beleuchtung) nachrüsten.

## Offen

- Polarlicht als Standard-Startbildschirm festlegen (Schritt 6) ist noch nicht ausgearbeitet.
- Der PC hat 32 GB Arbeitsspeicher und rund 250 GB frei. LineageOS nennt 64 GB und 400 GB als Richtwert.
  Ob es reicht, zeigt der erste Bau.
