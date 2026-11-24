#!/usr/bin/env bash
# Polarlicht OS: richtet die Bauumgebung in WSL2 (Ubuntu) ein und lädt den Quellcode von LineageOS 24.0.
# Mehrfach startbar: Was schon erledigt ist, wird übersprungen, ein abgebrochener Download läuft weiter.
# Befehle und Paketliste stammen aus der Bauanleitung von LineageOS (wiki.lineageos.org).
set -euo pipefail

ZWEIG="lineage-24.0"          # laut LineageOS der Zweig fürs AYN Thor (odin2thor)
ZIEL="$HOME/android/lineage"

sag() { printf '\n\033[1;36m== %s ==\033[0m\n' "$1"; }

sag "1/6 Prüfe Platz und Arbeitsspeicher"
FREI=$(df -BG --output=avail "$HOME" | tail -1 | tr -dc '0-9')
RAM=$(awk '/MemTotal/ {printf "%d", $2/1048576}' /proc/meminfo)
echo "Frei in Linux: ${FREI} GB · Arbeitsspeicher für Linux: ${RAM} GB"
if [ "$FREI" -lt 200 ]; then
  echo "Zu wenig Platz (unter 200 GB). Abbruch, damit die Platte nicht vollläuft."
  exit 1
fi

sag "2/6 Stelle WSL auf viel Arbeitsspeicher und Auslagerung"
WINHOME=$(wslpath "$(cmd.exe /c 'echo %USERPROFILE%' 2>/dev/null | tr -d '\r')")
CONF="$WINHOME/.wslconfig"
if [ -f "$CONF" ]; then
  echo "Es gibt schon eine $CONF, die lasse ich unverändert:"
  cat "$CONF"
else
  printf '[wsl2]\nmemory=28GB\nswap=24GB\n' > "$CONF"
  echo "Geschrieben: $CONF (28 GB Arbeitsspeicher, 24 GB Auslagerung)."
  NEUSTART=1
fi

sag "3/6 Installiere die Bauwerkzeuge (fragt einmal nach deinem Linux-Passwort)"
sudo apt-get update
sudo apt-get install -y bc bison build-essential ccache curl erofs-utils flex g++-multilib gcc-multilib git git-lfs \
  gnupg gperf imagemagick kmod protobuf-compiler python3-protobuf lib32readline-dev lib32z1-dev libdw-dev libelf-dev \
  libgnutls28-dev lz4 libsdl1.2-dev libssl-dev libxml2 libxml2-utils lzop pngcrush rsync schedtool squashfs-tools \
  xsltproc xxd yasm zip zlib1g-dev python-is-python3 unzip

sag "4/6 Richte repo, git und den Zwischenspeicher ein"
mkdir -p "$HOME/bin" "$ZIEL"
if [ ! -x "$HOME/bin/repo" ]; then
  curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo > "$HOME/bin/repo"
  chmod a+x "$HOME/bin/repo"
fi
export PATH="$HOME/bin:$PATH"
grep -q 'HOME/bin' "$HOME/.profile" || echo 'export PATH="$HOME/bin:$PATH"' >> "$HOME/.profile"
git config --global user.name  >/dev/null || git config --global user.name "Polarlicht"
git config --global user.email >/dev/null || git config --global user.email "polarlicht@users.noreply.github.com"
git config --global trailer.changeid.key "Change-Id"
git config --global color.ui false
git lfs install
grep -q USE_CCACHE "$HOME/.bashrc" || printf 'export USE_CCACHE=1\nexport CCACHE_EXEC=/usr/bin/ccache\n' >> "$HOME/.bashrc"
ccache -M 15G >/dev/null
ccache -o compression=true

sag "5/6 Melde den Quellcode an ($ZWEIG, sparsame Variante ohne Verlauf)"
cd "$ZIEL"
if [ ! -d .repo ]; then
  repo init -u https://github.com/LineageOS/android.git -b "$ZWEIG" --git-lfs --no-clone-bundle --depth=1
fi

sag "6/6 Lade den Quellcode. Das sind sehr viele Gigabyte und dauert Stunden."
echo "Fenster offen lassen, der PC darf nicht in den Energiesparmodus gehen."
echo "Bricht es ab: dasselbe Skript noch einmal starten, es macht dort weiter."
repo sync -c -j4 --no-tags --optimized-fetch

sag "Fertig"
df -h "$HOME" | tail -1
du -sh "$ZIEL" 2>/dev/null || true
if [ "${NEUSTART:-0}" = 1 ]; then
  echo "Vor dem ersten Bau einmal in PowerShell:  wsl --shutdown   und Ubuntu neu öffnen."
fi
echo "Der Quellcode liegt in $ZIEL. Sag Claude Bescheid, dann geht es mit dem Bau weiter."
