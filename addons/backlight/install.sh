#!/usr/bin/env bash
# Build Backlight, install it on the TV and allow it to change the TV's backlight setting.
#   TV_HOST=<tv ip> addons/backlight/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.saiminhtet.backlight
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

# Without the setting there is nothing for the slider to move; say so before building anything.
if [ "$(tv 'settings get global picture_backlight' | tr -d '\r')" = null ]; then
  echo "ERROR: this TV has no global picture_backlight setting, so Backlight cannot change it." >&2
  echo "It was written for MediaTek boards that keep the backlight there; nothing was installed." >&2
  exit 1
fi

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=$ANDROID_HOME" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/backlight.apk
OUT=$(tv 'pm install -r /data/local/tmp/backlight.apk; rm -f /data/local/tmp/backlight.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# The backlight is a global setting; only ADB can allow an app to write those.
tv "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
case "$(tv "dumpsys package $PKG | grep 'WRITE_SECURE_SETTINGS: granted=true'")" in
  *granted=true*) echo "permission granted" ;;
  *) echo "ERROR: the TV did not grant WRITE_SECURE_SETTINGS; the slider will say so and change nothing." >&2; exit 1 ;;
esac
tv "am start -n $PKG/.BacklightActivity >/dev/null; echo opened"
echo "Installed. Backlight is open on the TV; press Home and answer Show card for the card."
