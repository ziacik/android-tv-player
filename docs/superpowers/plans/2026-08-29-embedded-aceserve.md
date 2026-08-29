# Embedded AceServe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Start an AceServe runtime from inside the Kanálik APK on Ace channel playback and make its localhost HTTP API available to the existing Media3 flow.

**Architecture:** Keep `acestream://` as a direct channel source. A small Android `AceEngineController` lazily unpacks and launches a pinned HaP AceServe runtime, waits for port 6878 to become healthy, and is stopped from the activity lifecycle. Binary runtime files are downloaded at build time from a pinned upstream commit instead of being committed to this repository.

**Tech Stack:** Kotlin, Android SDK 26+, Gradle Kotlin DSL, OkHttp, Media3, generated Android assets/jniLibs.

**Spec:** `docs/superpowers/specs/2026-08-29-embedded-aceserve-design.md`

## Global Constraints

- Keep `ChannelProvider.DIRECT` and current `acestream://` catalog format.
- Support `arm64-v8a` and `armeabi-v7a`.
- minSdk remains 26.
- Fail on Android page sizes greater than 4096 bytes.
- Runtime source is pinned to HaP commit `19cbe60d0533c734ac3f50c7ccfdefe22422b4de`.
- Do not commit third-party runtime binary blobs to this repository.
- Do not change normal HTTP/HLS/DASH channel behavior.
- Stop the embedded engine when the activity stops.

---

### Task 1: Runtime platform decisions

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/acestream/AceRuntimePlatform.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/acestream/AceRuntimePlatformTest.kt`

**Interfaces:**
- Produces: `AceRuntimePlatform.selectAbi(supportedAbis: List<String>, is64Bit: Boolean): String?`
- Produces: `AceRuntimePlatform.isAceSource(url: String?): Boolean`

- [ ] **Step 1: Write failing JVM tests**

```kotlin
@Test fun `selects arm64 for a 64 bit process`() {
    assertEquals("arm64-v8a", AceRuntimePlatform.selectAbi(listOf("arm64-v8a", "armeabi-v7a"), true))
}

@Test fun `selects armv7 for a 32 bit process`() {
    assertEquals("armeabi-v7a", AceRuntimePlatform.selectAbi(listOf("arm64-v8a", "armeabi-v7a"), false))
}

@Test fun `detects acestream source`() {
    assertTrue(AceRuntimePlatform.isAceSource("acestream://abc"))
    assertFalse(AceRuntimePlatform.isAceSource("https://example.test/live.m3u8"))
}
```

- [ ] **Step 2: Run `./gradlew testDebugUnitTest` and verify RED because `AceRuntimePlatform` does not exist.**

- [ ] **Step 3: Implement the pure helper.**

```kotlin
object AceRuntimePlatform {
    const val ABI_ARM64 = "arm64-v8a"
    const val ABI_ARM32 = "armeabi-v7a"

    fun selectAbi(supportedAbis: List<String>, is64Bit: Boolean): String? {
        val preferred = if (is64Bit) ABI_ARM64 else ABI_ARM32
        return preferred.takeIf(supportedAbis::contains)
    }

    fun isAceSource(url: String?): Boolean = url?.startsWith("acestream://") == true
}
```

- [ ] **Step 4: Run `./gradlew testDebugUnitTest` and verify GREEN.**

- [ ] **Step 5: Commit `test/feat: add Ace runtime platform decisions`.**

### Task 2: Build-time runtime packaging

**Files:**
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces generated assets at `build/generated/ace/assets/aceserve/...`.
- Produces generated jni libs at `build/generated/ace/jniLibs/<abi>/libacepython.so`.

- [ ] **Step 1: Add a `prepareAceServeRuntime` Gradle task** that downloads the five exact files from raw GitHub URLs pinned to `19cbe60d0533c734ac3f50c7ccfdefe22422b4de`, skipping existing non-empty files.

```kotlin
val aceUpstreamCommit = "19cbe60d0533c734ac3f50c7ccfdefe22422b4de"
val aceUpstreamBase = "https://raw.githubusercontent.com/jopsis/StreamVault-IPTV-Plugin-HaP/$aceUpstreamCommit/app/src/main"
```

The task downloads:

```text
assets/aceserve/arm64-v8a/ace-arm64-v8a.zip
assets/aceserve/armeabi-v7a/ace-armeabi-v7a.zip
assets/aceserve/main_android.py
jniLibs/arm64-v8a/libacepython.so
jniLibs/armeabi-v7a/libacepython.so
```

- [ ] **Step 2: Add generated directories to the main source set and make `preBuild` depend on `prepareAceServeRuntime`.**

- [ ] **Step 3: Add `ndk.abiFilters` for both supported ABIs and legacy JNI packaging so the native runner has a directly executable path under `nativeLibraryDir`.**

- [ ] **Step 4: Run `./gradlew assembleDebug`; verify the download succeeds and the APK contains the Ace assets and both `libacepython.so` files.**

- [ ] **Step 5: Commit `build: package pinned AceServe runtime`.**

### Task 3: Embedded engine controller

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/acestream/AceAndroidRuntimeInfo.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/acestream/AceEngineController.kt`

**Interfaces:**
- Produces: `suspend fun AceEngineController.ensureReady()`
- Produces: `fun AceEngineController.stop()`

- [ ] **Step 1: Implement Android runtime metadata writer** with the fields used by HaP `main_android.py`: paths, memory, storage, device, and app sections. Persist one generated device ID in private SharedPreferences.

- [ ] **Step 2: Implement runtime preparation:** select ABI using Task 1, reject page size >4096, unzip the matching asset with canonical-path zip-slip protection, and copy `main_android.py`.

- [ ] **Step 3: Implement process startup:** execute `applicationInfo.nativeLibraryDir/libacepython.so` with `main_android.py`, `--bind-all`, in-memory live cache, disabled sentry/UPnP, stdout logging, and the HaP-compatible environment variables.

- [ ] **Step 4: Implement readiness polling:** request `http://127.0.0.1:6878/webui/api/service?method=get_version` until a successful response or a 20-second deadline. If the process exits or timeout occurs, stop it and throw an `IOException` containing useful diagnostics.

- [ ] **Step 5: Implement `stop()` with graceful destroy followed by forced destroy after 3 seconds.**

- [ ] **Step 6: Run `./gradlew testDebugUnitTest assembleDebug`.**

- [ ] **Step 7: Commit `feat: start embedded AceServe runtime`.**

### Task 4: Lazy playback integration and lifecycle

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`

**Interfaces:**
- Consumes: `AceRuntimePlatform.isAceSource`, `AceEngineController.ensureReady`, `AceEngineController.stop`.

- [ ] **Step 1: Create one `AceEngineController` in `MainActivity`.**

- [ ] **Step 2: Wrap the resolver passed to `PlayerController`:**

```kotlin
resolve = { channel ->
    if (AceRuntimePlatform.isAceSource(channel.providerValue)) {
        aceEngineController.ensureReady()
    }
    resolver.resolve(channel)
}
```

- [ ] **Step 3: Call `aceEngineController.stop()` from `onStop()` and again safely from `onDestroy()`.**

- [ ] **Step 4: Run `./gradlew testDebugUnitTest assembleDebug`.**

- [ ] **Step 5: Commit `feat: prepare embedded Ace engine for Ace channels`.**

### Task 5: Device smoke test

**Files:** none.

- [ ] **Step 1: Install the debug APK from `feature/acestream-support` on the Android TV device.**
- [ ] **Step 2: Open `JEDNOTKA ACE` (`e72b5c0d4ab0a0b906b5866b81551e929643339f`).**
- [ ] **Step 3: Inspect logcat for `AceEngine` startup/readiness.**
- [ ] **Step 4: Confirm localhost port 6878 becomes reachable and Media3 receives the generated HLS URL.**
- [ ] **Step 5: Leave the app and confirm the Ace process stops and playback audio does not continue.**
