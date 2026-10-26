#!/usr/bin/env bash
# Captures the Play Store / README screenshots from a running "Wear OS Large Round" emulator
# (454 x 454 px) with the debug build installed. The debug build's synthetic input stands in for
# the microphone, so no instrument (and no host microphone) is needed.
#
# Usage: tools/capture-screenshots.sh <adb-serial> <locale, e.g. en-US> <output-dir>
#
# The tap coordinates below are for the 454 px large round device.
set -euo pipefail

serial=${1:?adb serial}
locale=${2:?locale}
out=${3:?output dir}
pkg=net.devemperor.tunertogo.debug
activity=$pkg/net.devemperor.tunertogo.MainActivity
tile=$pkg/net.devemperor.tunertogo.tile.TunerTileService

adb_() { adb -s "$serial" "$@"; }
pause() { sleep "${1:-2}"; }
shot() { pause "${2:-2}"; adb_ exec-out screencap -p > "$out/$1.png"; echo "$out/$1.png"; }
start() { adb_ shell am force-stop "$pkg"; adb_ shell am start -W -n "$activity" "$@" > /dev/null; }
tap() { adb_ shell input tap "$1" "$2"; pause 1.5; }

mkdir -p "$out"

# Fresh settings, microphone granted, app in the requested language.
adb_ shell pm clear "$pkg" > /dev/null
adb_ shell pm grant "$pkg" android.permission.RECORD_AUDIO
adb_ shell cmd locale set-app-locales "$pkg" --locales "$locale"

# Guitar, low E tuned 41 cents flat.
start --ef demo_hz 80.5
shot 01_tuner 4
# Guitar, low E in tune.
start --ef demo_hz 82.45
shot 02_in_tune 4

# The tile, while the guitar is still selected.
adb_ shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation remove-tile --ecn component "$tile" > /dev/null || true
adb_ shell input keyevent KEYCODE_HOME
pause 2
adb_ shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation add-tile --ecn component "$tile" > /dev/null
pause 1
adb_ shell input swipe 400 227 50 227 250
shot 07_tile 4

# Menu, instrument list and reference pitch.
start --es routes menu
shot 04_menu
start --es routes menu,instruments
shot 05_instruments
start --es routes menu,reference_pitch
shot 06_reference_pitch

# Strobe display in chromatic mode.
start --es routes menu,option/DISPLAY
pause 2
tap 227 308
start --es routes menu,instruments
pause 2
tap 227 179
start --ef demo_hz 468.5 --ef demo_drift 1.5
shot 03_strobe 4

adb_ shell am force-stop "$pkg"
