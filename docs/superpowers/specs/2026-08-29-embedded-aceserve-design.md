# Embedded AceServe Experiment Design

## Goal

Make `acestream://` channels playable without installing a separate Ace Stream application by starting an AceServe runtime packaged inside the Kanálik APK and feeding its localhost HLS output to the existing Media3 player.

## Scope

This is an experimental implementation on `feature/acestream-support`.

The first success criterion is narrower than full playback: on a real Android TV device, the application can unpack and start the embedded AceServe runtime and its HTTP API becomes reachable on `127.0.0.1:6878`. Once that works, the existing Ace resolver can request the HLS manifest for the channel content ID.

## Architecture

- Keep the existing channel catalog representation: Ace channels remain `ChannelProvider.DIRECT` with an `acestream://<content-id>` URL.
- Add `AceEngineController` responsible only for preparing, starting, health-checking, and stopping the embedded runtime.
- `DirectResolver` continues to translate `acestream://<content-id>` to `http://127.0.0.1:6878/ace/manifest.m3u8?content_id=<content-id>`.
- The player flow ensures the engine is ready before resolving an Ace source. Normal HTTP/HLS/DASH channels do not start AceServe.
- Stop AceServe when the app/player lifecycle stops so it does not keep downloading or uploading in the background.

## Runtime source

For the experiment, reuse only the minimal Android AceServe runtime technique from `jopsis/StreamVault-IPTV-Plugin-HaP`, pinned to commit `19cbe60d0533c734ac3f50c7ccfdefe22422b4de`.

Required upstream artifacts:

- `app/src/main/assets/aceserve/arm64-v8a/ace-arm64-v8a.zip`
- `app/src/main/assets/aceserve/armeabi-v7a/ace-armeabi-v7a.zip`
- `app/src/main/assets/aceserve/main_android.py`
- `app/src/main/jniLibs/arm64-v8a/libacepython.so`
- `app/src/main/jniLibs/armeabi-v7a/libacepython.so`

Do not vendor those binary artifacts into this repository. A Gradle preparation task downloads the exact pinned files at build time into generated assets/jniLibs, and `preBuild` depends on that task.

## Supported devices

- `arm64-v8a`
- `armeabi-v7a`
- minSdk remains 26.
- The borrowed AceServe runtime currently requires a 4 KiB Android page size. If `Os.sysconf(_SC_PAGESIZE)` reports more than 4096 bytes, fail with a clear unsupported-device error rather than attempting to start it.

## Runtime preparation

On first Ace playback:

1. Select the ABI matching the current app process bitness and `Build.SUPPORTED_ABIS`.
2. Copy the matching runtime ZIP from APK assets to cache.
3. Unzip it into `filesDir/aceserve/<abi>` using zip-slip protection.
4. Copy `main_android.py` from assets into that directory.
5. Write `android-runtime.json` with app/device/memory/storage information required by the Android bootstrap.
6. Execute `libacepython.so <root>/main_android.py` with the environment expected by AceServe (`ACE_ROOT`, `ACE_CACHE_DIR`, `ACE_ANDROID_INFO`, `ACESTREAM_HOME`, `PYTHONHOME`, `PYTHONPATH`, `LD_LIBRARY_PATH`, Android paths, and pure-Python fallbacks).
7. Poll the local Ace HTTP API until it answers or a bounded startup timeout expires.

## Error behavior

- Unsupported ABI: return a descriptive error including `Build.SUPPORTED_ABIS`.
- 16 KiB page-size device: return a descriptive unsupported-runtime error.
- Missing/corrupt generated runtime asset: fail before starting Media3.
- Engine process exits during startup: surface the most recent process-output line when possible.
- Health check timeout: stop the process and fail playback instead of leaving a stray engine running.

Normal non-Ace channel behavior must remain unchanged.

## Testing

Unit-test pure decisions without requiring Android native execution:

- Ace URL detection.
- ABI selection from supported ABI list + process bitness.
- Local HLS URL generation remains unchanged.
- Engine readiness orchestration is invoked only for Ace URLs.

GitHub Actions continues to run `testDebugUnitTest` and `assembleDebug`. A successful APK build proves generated upstream artifacts can be downloaded and packaged. Actual engine startup/stream playback requires the Android TV device smoke test.

## Distribution note

This branch is an experiment. The AceServe runtime contains third-party closed-source components. Do not merge or publish a release containing it until redistribution/licensing terms are reviewed separately.
