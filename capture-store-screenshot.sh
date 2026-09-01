#!/bin/sh
set -eu

name="${1:-screenshot}"
target="${2:-192.168.0.200:5555}"
out="store/uptodown/screenshots/${name}.png"
remote="/sdcard/kanalik-store-screenshot.png"

mkdir -p "$(dirname "$out")"
adb -s "$target" shell screencap -p "$remote"
adb -s "$target" pull "$remote" "$out"
adb -s "$target" shell rm -f "$remote"
echo "$out"
