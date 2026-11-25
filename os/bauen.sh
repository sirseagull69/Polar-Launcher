#!/usr/bin/env bash
# Polarlicht OS: erster Bau von LineageOS 24.0 fürs AYN Thor (odin2thor), noch ohne eigene Änderungen.
# Mehrfach startbar: ein abgebrochener Bau macht dort weiter, wo er war.
set -euo pipefail

ZIEL="$HOME/android/lineage"
PROTOKOLL="$HOME/android/bau.log"
sag() { printf '\n\033[1;36m== %s ==\033[0m\n' "$1"; }

if [ ! -f "$ZIEL/build/envsetup.sh" ]; then
  echo "Der Quellcode ist noch nicht vollständig da. Erst einrichten.sh bis \"Fertig\" durchlaufen lassen."
  exit 1
fi
cd "$ZIEL"
export PATH="$HOME/bin:$PATH"

sag "1/4 Prüfe Platz und Arbeitsspeicher"
FREI=$(df -BG --output=avail "$HOME" | tail -1 | tr -dc '0-9')
RAM=$(awk '/MemTotal/ {printf "%d", $2/1048576}' /proc/meminfo)
echo "Frei: ${FREI} GB · Arbeitsspeicher für Linux: ${RAM} GB"
if [ "$RAM" -lt 20 ]; then
  echo "Linux hat zu wenig Arbeitsspeicher. In PowerShell 'wsl --shutdown' ausführen, Ubuntu neu öffnen, dann noch einmal starten."
  exit 1
fi
if [ "$FREI" -lt 90 ]; then
  echo "Unter 90 GB frei, das reicht für den Bau nicht. Abbruch, damit die Platte nicht vollläuft."
  exit 1
fi

sag "2/4 Hole die Herstellerdateien fürs Thor (öffentliches Archiv TheMuppets)"
mkdir -p .repo/local_manifests
cat > .repo/local_manifests/polarlicht.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <project name="TheMuppets/proprietary_vendor_ayn_odin2thor" path="vendor/ayn/odin2thor" remote="github" revision="lineage-24.0" clone-depth="1" />
  <project name="TheMuppets/proprietary_vendor_ayn_qcs8550-common" path="vendor/ayn/qcs8550-common" remote="github" revision="lineage-24.0" clone-depth="1" />
</manifest>
XML
repo sync -c -j4 --no-tags vendor/ayn/odin2thor vendor/ayn/qcs8550-common

sag "3/4 Hole Gerätebaum und Kernel"
set +u
# shellcheck disable=SC1091
source build/envsetup.sh
breakfast odin2thor

sag "4/4 Baue. Das dauert viele Stunden. Protokoll: $PROTOKOLL"
export USE_CCACHE=1
export CCACHE_EXEC=/usr/bin/ccache
KERNE=$(nproc)
JOBS=$(( KERNE > 8 ? 8 : KERNE ))
echo "Baue mit $JOBS gleichzeitigen Schritten (gedrosselt wegen 32 GB Arbeitsspeicher)."
if m bacon -j"$JOBS" 2>&1 | tee "$PROTOKOLL"; then
  sag "Fertig"
  ls -lh out/target/product/odin2thor/lineage-*.zip 2>/dev/null || true
  df -h "$HOME" | tail -1
  echo "Schick Claude ein Bild von diesen Zeilen."
else
  sag "Der Bau ist stehen geblieben"
  echo "Die letzten Zeilen des Protokolls:"
  tail -n 40 "$PROTOKOLL"
  echo
  echo "Schick Claude ein Bild davon. Zum Weitermachen dieselbe Zeile noch einmal starten."
  exit 1
fi
