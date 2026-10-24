#!/usr/bin/env bash
# Build Glance, install it on the TV, allow it to switch its own key service back on, and switch that service on.
#   TV_HOST=<tv ip> addons/glance/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.saiminhtet.glance
SVC=$PKG/.GlanceService
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

# Glance shows the Weather add-on's line; without it, it shows the time only, so this is a warning, not a stop.
case "$(tv 'content query --uri content://io.github.sumyabazar1.weather.status/status 2>&1' || true)" in
  *text=*) echo "Weather add-on: ok" ;;
  *) echo "WARNING: no weather line from the Weather add-on (build it with home_status on, addons/weather/README.md)." >&2
     echo "Glance will show the time and date only." >&2 ;;
esac

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=$ANDROID_HOME" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/glance.apk
OUT=$(tv 'pm install -r /data/local/tmp/glance.apk; rm -f /data/local/tmp/glance.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# Only so Glance can put its own service back after a force-stop, when its tile is opened.
tv "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
case "$(tv "dumpsys package $PKG | grep 'WRITE_SECURE_SETTINGS: granted=true'")" in
  *granted=true*) echo "permission granted" ;;
  *) echo "WARNING: WRITE_SECURE_SETTINGS was not granted; after a force-stop, run this script again." >&2 ;;
esac

# Appends to enabled_accessibility_services, keeping any service already there (the launcher's helpers included).
tv "s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$SVC*) ;; null|'') settings put secure enabled_accessibility_services $SVC ;; *) settings put secure enabled_accessibility_services \"\$s:$SVC\" ;; esac; settings put secure accessibility_enabled 1; echo service on"
echo "Installed. Hold Volume Up now: if it only moves one step at a time, switch Glance off on its tile."
echo "Undo: tools/tv.py 'pm uninstall $PKG'"
