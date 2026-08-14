# Android TV Player MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and verify a native Android TV app that dynamically resolves STVR Jednotka, plays it fullscreen, and provides deterministic remote controls with symmetric ±10-second seeking and LIVE recovery.

**Architecture:** A single `app` module keeps network resolution, playback control, and Compose UI separate. `StvrResolver` returns a short-lived `StreamSource`; `PlayerController` owns playback state through a small `PlayerPort`; `PlayerScreen` renders Media3 video and a custom TV overlay while `RemoteCommandMapper` keeps remote behavior testable.

**Tech Stack:** Kotlin 2.3.21 through AGP built-in Kotlin, Android Gradle Plugin 9.3.1, Gradle 9.5.0, compile/target SDK 36, min SDK 26, Jetpack Compose BOM 2026.08.00, Media3 1.10.1, OkHttp 5.1.0, kotlinx-coroutines 1.10.2, JUnit 4.

---

## File map

- `settings.gradle.kts` and root `build.gradle.kts`: repositories and plugin versions.
- `gradle/libs.versions.toml`: the only dependency-version registry.
- `app/build.gradle.kts`: Android TV app configuration and dependencies.
- `app/src/main/AndroidManifest.xml`: TV-only launcher, internet permission, banner, and non-touch declaration.
- `app/src/main/java/sk/ziacik/androidtvplayer/resolver/*`: HTTP boundary, JSON parsing, and STVR resolution.
- `app/src/main/java/sk/ziacik/androidtvplayer/player/*`: Media3 adapter, UI state, seek rules, retry, and playback lifecycle.
- `app/src/main/java/sk/ziacik/androidtvplayer/ui/*`: remote mapping, overlay timing, Compose player screen, colors, and typography.
- `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`: composition root only.
- matching `app/src/test/...` files: JVM tests for every non-Android decision.

### Task 1: Bootstrap a buildable Android TV project

**Files:**
- Create: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/drawable/tv_banner.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/values/ic_launcher_background.xml`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`
- Create: Gradle wrapper files through the verified Gradle 9.5.0 distribution

- [ ] **Step 1: Create the Gradle settings and version catalog**

Use `pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }`, dependency repositories `google()` and `mavenCentral()`, and set `rootProject.name = "AndroidTvPlayer"` with `include(":app")`.

The catalog must pin:

```toml
[versions]
agp = "9.3.1"
kotlin = "2.3.21"
composeBom = "2026.08.00"
activityCompose = "1.13.0"
media3 = "1.10.1"
okhttp = "5.1.0"
coroutines = "1.10.2"
junit = "4.13.2"
json = "20250517"

[libraries]
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
androidx-media3-exoplayer-hls = { module = "androidx.media3:media3-exoplayer-hls", version.ref = "media3" }
androidx-media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
json = { module = "org.json:json", version.ref = "json" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Configure the app module**

Apply `android-application` and `compose-compiler`, then configure:

```kotlin
android {
    namespace = "sk.ziacik.androidtvplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "sk.ziacik.androidtvplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

Import the Compose BOM for implementation and tests. Add Activity Compose, Compose UI/Foundation/Material3, Media3 ExoPlayer/HLS/UI, OkHttp, coroutines-android, JUnit, coroutines-test, and JVM `org.json` for unit tests. Add `debugImplementation` for Compose tooling.

- [ ] **Step 3: Add the TV manifest and minimal activity**

Declare `android.permission.INTERNET`, `android.software.leanback` required, `android.hardware.touchscreen` not required, and a launcher activity with both `LEANBACK_LAUNCHER` and `LAUNCHER` categories. Set landscape, `Theme.AndroidTvPlayer`, `@drawable/tv_banner`, and `@mipmap/ic_launcher`.

The first activity is deliberately tiny:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Android TV Player", color = Color.White)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Generate the wrapper from a verified distribution**

Download `gradle-9.5.0-bin.zip` to a temporary directory and verify SHA-256 exactly:

```text
553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746
```

Run that distribution's `gradle wrapper --gradle-version 9.5.0 --distribution-type bin`. Do not commit the downloaded ZIP.

- [ ] **Step 5: Build the blank app**

Run: `./gradlew --version`

Expected: Gradle 9.5.0 running on the local JDK 21.

Run: `./gradlew testDebugUnitTest assembleDebug --stacktrace`

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 6: Commit the bootstrap**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle app
git commit -m "build: bootstrap Android TV app"
```

### Task 2: Resolve a fresh STVR HLS source

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StreamSource.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrJsonParser.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrHttpClient.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/OkHttpStvrClient.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrResolver.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/resolver/StvrJsonParserTest.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/resolver/StvrResolverTest.kt`

- [ ] **Step 1: Write parser tests**

Test that this JSON returns the HLS item and preserves its URL:

```kotlin
private val validJson = """
    {"clip":{"sources":[
      {"src":"https://cdn.example/live.mpd","type":"application/dash+xml"},
      {"src":"https://cdn.example/live.m3u8?auth=secret","type":"application/x-mpegurl"}
    ]}}
""".trimIndent()

@Test fun `selects HLS source`() {
    assertEquals(
        "https://cdn.example/live.m3u8?auth=secret",
        StvrJsonParser().parseHlsUrl(validJson),
    )
}

@Test fun `rejects response without HLS`() {
    assertThrows(StreamResolveException::class.java) {
        StvrJsonParser().parseHlsUrl("""{"clip":{"sources":[]}}""")
    }
}
```

- [ ] **Step 2: Run parser tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*StvrJsonParserTest'`

Expected: compilation failure because `StvrJsonParser` does not exist.

- [ ] **Step 3: Implement the source model and parser**

```kotlin
data class StreamSource(
    val url: String,
    val userAgent: String,
)

class StreamResolveException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class StvrJsonParser {
    fun parseHlsUrl(body: String): String = try {
        val sources = JSONObject(body).getJSONObject("clip").getJSONArray("sources")
        (0 until sources.length())
            .asSequence()
            .map(sources::getJSONObject)
            .firstOrNull { it.optString("type") == "application/x-mpegurl" }
            ?.getString("src")
            ?.takeIf(String::isNotBlank)
            ?: throw StreamResolveException("STVR response does not contain an HLS source")
    } catch (error: StreamResolveException) {
        throw error
    } catch (error: Exception) {
        throw StreamResolveException("STVR returned invalid JSON", error)
    }
}
```

- [ ] **Step 4: Run parser tests and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*StvrJsonParserTest'`

Expected: both tests pass.

- [ ] **Step 5: Write resolver orchestration tests**

Use a recording fake implementing:

```kotlin
interface StvrHttpClient {
    suspend fun get(url: String, headers: Map<String, String>): String
}
```

Assert that `resolve()` calls the landing page first, the `live5f.json` endpoint second, passes the configured User-Agent to both, and returns a `StreamSource` with the same User-Agent. Add a failure test proving a second `resolve()` performs both requests again rather than caching the token.

- [ ] **Step 6: Run resolver tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*StvrResolverTest'`

Expected: compilation failure because `StvrResolver` does not exist.

- [ ] **Step 7: Implement resolver and production HTTP client**

Use these constants:

```kotlin
const val STVR_LANDING_URL = "https://www.rtvs.sk/televizia/tv"
const val STVR_LIVE_URL =
    "https://www.rtvs.sk/json/live5f.json?c=1&ad=1&b=chrome&p=win&v=77&f=0&d=1"
const val STVR_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "Chrome/77.0.3865.90 Safari/537.36"
```

`StvrResolver.resolve()` must always call landing then JSON, parse a new HLS URL, and wrap network errors as `StreamResolveException` without logging the URL token.

`OkHttpStvrClient` uses one `OkHttpClient` with an in-memory `CookieJar`, follows redirects, adds the supplied headers, runs on `Dispatchers.IO`, throws on non-2xx status, and returns the response body. Cookie selection must use `Cookie.matches(url)`.

- [ ] **Step 8: Run all resolver tests and commit**

Run: `./gradlew testDebugUnitTest --tests '*resolver*'`

Expected: all resolver tests pass.

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/resolver app/src/test/java/sk/ziacik/androidtvplayer/resolver
git commit -m "feat: resolve STVR Jednotka stream"
```

### Task 3: Implement deterministic playback control

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerPort.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerUiState.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/player/Media3PlayerPort.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`

- [ ] **Step 1: Write controller tests**

Create a fake `PlayerPort` and cover:

```kotlin
@Test fun `seek back is exactly ten seconds and clamps to zero`() = runTest { /* 5s -> 0 */ }
@Test fun `seek forward is exactly ten seconds and clamps to duration`() = runTest { /* 95s of 100s -> 100s */ }
@Test fun `seek does nothing when current item is not seekable`() = runTest { /* no seek call */ }
@Test fun `go live seeks to default position`() = runTest { /* one goLive call */ }
@Test fun `retry performs a fresh resolve and reload`() = runTest { /* resolver called twice */ }
@Test fun `resolve failure exposes error state`() = runTest { /* PlayerUiState.Error */ }
```

The fake implements this exact boundary:

```kotlin
interface PlayerPort {
    val player: Player
    val currentPositionMs: Long
    val durationMs: Long
    val isSeekable: Boolean
    val isPlaying: Boolean
    fun load(source: StreamSource)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun goLive()
    fun release()
    fun setListener(listener: Listener)

    interface Listener {
        fun onReady(isPlaying: Boolean)
        fun onPlayingChanged(isPlaying: Boolean)
        fun onError(message: String)
    }
}
```

- [ ] **Step 2: Run controller tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*PlayerControllerTest'`

Expected: compilation failure because player contracts do not exist.

- [ ] **Step 3: Implement minimal controller**

Use this state model:

```kotlin
sealed interface PlayerUiState {
    data object Resolving : PlayerUiState
    data object Preparing : PlayerUiState
    data class Ready(val isPlaying: Boolean, val isSeekable: Boolean) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}
```

`PlayerController` receives `CoroutineScope`, a `suspend () -> StreamSource` resolver, and `PlayerPort`. It exposes `val player: Player get() = playerPort.player` for rendering only, owns `MutableStateFlow<PlayerUiState>`, cancels the previous resolve job on retry, sets `Resolving`, resolves, sets `Preparing`, then calls `load`. It implements exact ±10,000 ms clamped with `coerceIn(0, durationMs)` when duration is known, plus play/pause, go-live, and release.

- [ ] **Step 4: Implement Media3 adapter**

`Media3PlayerPort` creates an `ExoPlayer` with `DefaultMediaSourceFactory(DefaultHttpDataSource.Factory())`. For each `StreamSource`, apply its User-Agent to the HTTP factory, set a `MediaItem` with `MimeTypes.APPLICATION_M3U8`, prepare, and set `playWhenReady = true`. Map `Player.Listener` ready, playing, and error callbacks into `PlayerPort.Listener`. Expose the underlying `Player` only for `PlayerView` rendering.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew testDebugUnitTest --tests '*PlayerControllerTest'`

Expected: all controller tests pass.

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/player app/src/test/java/sk/ziacik/androidtvplayer/player
git commit -m "feat: add player control state machine"
```

### Task 4: Implement remote behavior and overlay timeout

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/ui/OverlayController.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapper.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/ui/OverlayControllerTest.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapperTest.kt`

- [ ] **Step 1: Write overlay timeout tests**

With `runTest`, verify `show()` makes the overlay visible immediately, `advanceTimeBy(3_999)` keeps it visible, and the next millisecond hides it. Verify a second interaction restarts the full four-second timeout.

- [ ] **Step 2: Write remote mapping tests**

Use pure commands:

```kotlin
enum class FocusedControl { PLAY_PAUSE, LIVE }
sealed interface RemoteCommand {
    data object ShowOverlay : RemoteCommand
    data object SeekBack : RemoteCommand
    data object SeekForward : RemoteCommand
    data object TogglePlayback : RemoteCommand
    data object GoLive : RemoteCommand
    data object FocusPlayPause : RemoteCommand
    data object FocusLive : RemoteCommand
    data object HideOverlay : RemoteCommand
    data object Exit : RemoteCommand
    data object Ignore : RemoteCommand
}
```

Assert: left/right always map to direct seek; center while hidden maps to show; center while visible activates the focused control; up/down switches between PLAY_PAUSE and LIVE; Back hides a visible overlay and exits only when hidden.

- [ ] **Step 3: Run UI logic tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*ui*'`

Expected: compilation failure because the UI logic classes do not exist.

- [ ] **Step 4: Implement overlay and mapper**

`OverlayController` receives a scope and `autoHideMs = 4_000L`, exposes `StateFlow<Boolean>`, and cancels/restarts one hide job on every interaction. `RemoteCommandMapper.map(keyCode, overlayVisible, focusedControl)` must be a pure function and ignore key-down repeats by being called only for `KeyEventType.KeyUp` from Compose.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew testDebugUnitTest --tests '*ui*'`

Expected: all overlay and mapper tests pass.

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/ui app/src/test/java/sk/ziacik/androidtvplayer/ui
git commit -m "feat: add TV remote interaction rules"
```

### Task 5: Build the fullscreen Compose player

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerTheme.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`

- [ ] **Step 1: Add the Media3 video surface**

Use `AndroidView` with `PlayerView(context).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; player = controller.player }`. In `DisposableEffect`, detach the player when the composition leaves.

- [ ] **Step 2: Add the custom overlay**

Render a bottom `Brush.verticalGradient` from transparent to 85% black. Show `Jednotka`, a thin live-window line, large `−10`, play/pause, `+10`, and `LIVE`. The focused PLAY_PAUSE or LIVE control uses a 1.08 scale and brighter background; no default Media3 controls or heavy focus border.

- [ ] **Step 3: Connect remote commands**

Make the root box focusable and request focus on first composition. On key-up, map the native key code and execute exactly one controller/overlay action. Any seek, play/pause, focus change, or go-live calls `overlayController.show()` to restart the timeout. When `Exit` is returned, call the activity's `finish()` callback.

- [ ] **Step 4: Render loading and error states**

For `Resolving` and `Preparing`, render a centered progress indicator over black/video. For `Error`, render „Stream sa nepodarilo načítať“, a a focused `Retry` action. Retry calls `controller.retry()` and never reuses the previous HLS URL.

- [ ] **Step 5: Turn MainActivity into the composition root**

Create one `OkHttpStvrClient`, `StvrResolver`, `Media3PlayerPort`, activity `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`, `PlayerController`, and `OverlayController`. Start once after `setContent`, cancel the scope and release the player in `onDestroy`.

- [ ] **Step 6: Build and commit integration**

Run: `./gradlew testDebugUnitTest assembleDebug --stacktrace`

Expected: all unit tests pass and debug APK builds.

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer app/src/main/res
git commit -m "feat: add fullscreen TV player UI"
```

### Task 6: Verify the live resolver and APK safely

**Files:**
- Modify only if verification exposes a real defect

- [ ] **Step 1: Run the complete local verification**

Run: `./gradlew clean testDebugUnitTest assembleDebug lintDebug --stacktrace`

Expected: `BUILD SUCCESSFUL`, zero failing tests, and an APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Verify the STVR endpoint outside the app**

Perform the landing request and JSON request with a temporary cookie jar and the configured User-Agent. Assert that `.clip.sources` contains an `application/x-mpegurl` URL, but do not write or print its auth query into committed files or final logs.

- [ ] **Step 3: Inspect connected devices**

Run: `adb devices -l`.

If no device is listed, stop before installation and provide the exact TV-side steps for Developer options, USB/network debugging, and `adb connect <TV-IP>:5555`. No installation claim is allowed without a listed `device`.

- [ ] **Step 4: Install and launch when the Philips is connected**

Run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n sk.ziacik.androidtvplayer/.MainActivity
```

Expected: install success and fullscreen Jednotka playback.

- [ ] **Step 5: Execute the physical acceptance checklist**

- Cold start resolves without a hardcoded stream URL.
- Left from hidden overlay seeks exactly 10 seconds back and shows controls.
- Right seeks exactly 10 seconds forward without an asymmetric jump.
- Center toggles play/pause on PLAY_PAUSE.
- Down selects LIVE; center returns to the live edge.
- Overlay hides after four seconds without input.
- Back hides a visible overlay; the next Back exits.
- Disconnecting network produces the Retry UI; restoring it and selecting Retry performs a new resolve.

- [ ] **Step 6: Commit only verified fixes**

If no changes were necessary, do not create an empty commit. If verification exposed a defect, add its regression test first, apply the smallest fix, rerun the full verification, then commit the exact affected files.
