#!/bin/sh
set -eu

target="${1:-192.168.0.200:5555}"
package="sk.ziacik.androidtvplayer"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
keystore="${ANDROID_DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
unsigned_apk="app/build/outputs/apk/release/app-release-unsigned.apk"
aligned_apk="app/build/outputs/apk/release/app-release-aligned.apk"
signed_apk="app/build/outputs/apk/release/app-release-debug-signed.apk"

if [ -z "$sdk_root" ]; then
    echo "ANDROID_SDK_ROOT or ANDROID_HOME must point to the Android SDK" >&2
    exit 1
fi

build_tools="$(find "$sdk_root/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
zipalign="$build_tools/zipalign"
apksigner="$build_tools/apksigner"

if [ ! -x "$zipalign" ] || [ ! -x "$apksigner" ]; then
    echo "zipalign/apksigner not found under $sdk_root/build-tools" >&2
    exit 1
fi

./gradlew assembleRelease

if unzip -l "$unsigned_apk" | grep -q 'assets/channels.json'; then
    echo "Release APK unexpectedly contains assets/channels.json" >&2
    exit 1
fi

if [ ! -f "$keystore" ]; then
    mkdir -p "$(dirname "$keystore")"
    keytool -genkeypair \
        -keystore "$keystore" \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000
fi

rm -f "$aligned_apk" "$signed_apk"
"$zipalign" -f -p 4 "$unsigned_apk" "$aligned_apk"
"$apksigner" sign \
    --ks "$keystore" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$signed_apk" \
    "$aligned_apk"
"$apksigner" verify --verbose "$signed_apk"

adb -s "$target" install -r "$signed_apk"
adb -s "$target" shell am force-stop "$package"
adb -s "$target" shell monkey -p "$package" 1
