#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 OUTPUT_DIR" >&2
  exit 2
fi

OUTPUT_DIR="$1"
COMMIT="19cbe60d0533c734ac3f50c7ccfdefe22422b4de"
BASE="https://raw.githubusercontent.com/jopsis/StreamVault-IPTV-Plugin-HaP/${COMMIT}/app/src/main"

FILES=(
  "assets/aceserve/arm64-v8a/ace-arm64-v8a.zip"
  "assets/aceserve/armeabi-v7a/ace-armeabi-v7a.zip"
  "assets/aceserve/main_android.py"
  "jniLibs/arm64-v8a/libacepython.so"
  "jniLibs/armeabi-v7a/libacepython.so"
)

for relative_path in "${FILES[@]}"; do
  destination="${OUTPUT_DIR}/${relative_path}"
  if [[ -s "$destination" ]]; then
    continue
  fi

  mkdir -p "$(dirname "$destination")"
  partial="${destination}.part"
  rm -f "$partial"
  curl \
    --fail \
    --location \
    --silent \
    --show-error \
    --retry 3 \
    --connect-timeout 30 \
    --max-time 300 \
    "${BASE}/${relative_path}" \
    --output "$partial"
  [[ -s "$partial" ]]
  mv "$partial" "$destination"
done
