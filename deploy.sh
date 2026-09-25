#!/bin/sh
set -eu

target=""
package="sk.ziacik.androidtvplayer"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
keystore="${ANDROID_DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
unsigned_apk="app/build/outputs/apk/release/app-release-unsigned.apk"
aligned_apk="app/build/outputs/apk/release/app-release-aligned.apk"
signed_apk="app/build/outputs/apk/release/app-release-debug-signed.apk"

select_target() {
    requested_target="${1:-}"
    if [ -n "$requested_target" ]; then
        target="$requested_target"
        return
    fi

    targets=""
    labels=""

    while IFS= read -r line; do
        serial=$(printf '%s\n' "$line" | sed -n 's/^\(.*[^[:space:]]\)[[:space:]][[:space:]]*device\([[:space:]].*\)\{0,1\}$/\1/p')
        [ -n "$serial" ] || continue

        if [ -z "$targets" ]; then
            targets="$serial"
            labels="$line"
        else
            targets="$targets
$serial"
            labels="$labels
$line"
        fi
    done <<EOF
$(adb devices -l)
EOF

    count=$(printf '%s\n' "$targets" | sed '/^$/d' | wc -l | tr -d ' ')

    if [ "$count" -eq 0 ]; then
        echo "No usable ADB device found." >&2
        exit 1
    fi

    if [ "$count" -eq 1 ]; then
        target="$targets"
        return
    fi

    echo "Multiple ADB targets found:"
    i=1
    while IFS= read -r label; do
        printf '  %d) %s\n' "$i" "$label"
        i=$((i + 1))
    done <<EOF
$labels
EOF

    while :; do
        printf 'Select target [1-%s]: ' "$count"
        IFS= read -r choice
        case "$choice" in
            ''|*[!0-9]*)
                echo "Invalid selection." >&2
                continue
                ;;
        esac

        if [ "$choice" -ge 1 ] && [ "$choice" -le "$count" ]; then
            target=$(printf '%s\n' "$targets" | sed -n "${choice}p")
            return
        fi

        echo "Invalid selection." >&2
    done
}

select_target "${1:-}"

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
adb -s "$target" shell am start -n "$package/.MainActivity"
