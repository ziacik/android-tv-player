# Cinematic Player Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the MVP controls with the approved Cinematic overlay, backed by real STVR program availability and Media3 live-window data.

**Architecture:** `StvrResolver` returns a typed playable or unavailable result with non-secret program metadata. `PlayerController` owns retry scheduling and immutable playback snapshots, while a pure overlay-model mapper converts those snapshots into display values. Compose renders that model and never sees the tokenized HLS URL.

**Tech Stack:** Kotlin 2.3, Android SDK 36/minSdk 26, Jetpack Compose Material 3, AndroidX Media3 1.10.1, OkHttp 5.1, Kotlin coroutines 1.10.2, JUnit 4, Compose UI tests.

---

## File structure

**Create:**

- `app/src/main/java/sk/ziacik/androidtvplayer/resolver/ProgramMetadata.kt` — public resolver result types and non-secret program metadata.
- `app/src/main/java/sk/ziacik/androidtvplayer/player/PlaybackSnapshot.kt` — immutable Media3 playback metrics.
- `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt` — pure timeline calculations and display formatting.
- `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt` — local tests for progress, live threshold, and delay formatting.
- `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt` — device-side Compose semantics tests for the cinematic and restricted states.

**Modify:**

- `gradle/libs.versions.toml` and `app/build.gradle.kts` — Compose device-test dependencies and runner.
- `StvrJsonParser.kt` and its tests — parse metadata, availability, timestamps, and optional HLS source.
- `StvrResolver.kt` and its tests — return `Playable` or `Unavailable` without leaking the tokenized URL.
- `PlayerPort.kt`, `Media3PlayerPort.kt`, `PlayerUiState.kt`, `PlayerController.kt`, and controller tests — expose live metrics, metadata, and scheduled retry.
- `PlayerOverlay.kt` — render the approved Cinematic overlay.
- `PlayerScreen.kt` — refresh timeline data while visible and render the restricted-program state.

## Task 1: Typed STVR program resolution

**Files:**

- Create: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/ProgramMetadata.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrJsonParser.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrResolver.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/resolver/StvrJsonParserTest.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/resolver/StvrResolverTest.kt`

- [ ] **Step 1: Replace parser tests with metadata and availability cases**

Keep the existing invalid-JSON and missing-HLS assertions, then add these cases using a fixed `ZoneId`:

```kotlin
private val parser = StvrJsonParser(ZoneId.of("Europe/Bratislava"))

@Test
fun `parses program metadata and HLS source`() {
    val parsed = parser.parse(
        response(
            internet = "Y",
            series = "Ordinácia v Eifeli",
            subtitle = "Šance",
            timestart = "1786785969000",
            timestop = "1786791292000",
            source = "https://cdn.example/live.m3u8?auth=secret",
        ),
    )

    assertEquals("Ordinácia v Eifeli: Šance", parsed.program.title)
    assertEquals(1_786_785_969_000L, parsed.program.startsAtMs)
    assertEquals(1_786_791_292_000L, parsed.program.endsAtMs)
    assertEquals(true, parsed.program.internetAllowed)
    assertEquals("https://cdn.example/live.m3u8?auth=secret", parsed.hlsUrl)
}

@Test
fun `uses ISO timestamp fallback and title fallback order`() {
    val parsed = parser.parse(
        """{"clip":{
          "title":"Fallback title",
          "titleorig":"Original title",
          "internet":"N",
          "dateTimeStart":"2026-08-15T11:25:00",
          "dateTimeStop":"2026-08-15T12:53:44",
          "sources":[]
        }}""",
    )

    assertEquals("Original title", parsed.program.title)
    assertEquals(false, parsed.program.internetAllowed)
    assertEquals(null, parsed.hlsUrl)
    assertEquals(
        LocalDateTime.parse("2026-08-15T12:53:44")
            .atZone(ZoneId.of("Europe/Bratislava"))
            .toInstant()
            .toEpochMilli(),
        parsed.program.endsAtMs,
    )
}

@Test
fun `unknown internet flag stays unknown`() {
    val parsed = parser.parse(response(internet = "", source = "https://cdn.example/live.m3u8"))
    assertEquals(null, parsed.program.internetAllowed)
}
```

Use this helper; it emits an HLS source only when `source != null` and does not print the source during test execution:

```kotlin
private fun response(
    internet: String,
    series: String = "",
    subtitle: String = "",
    timestart: String = "",
    timestop: String = "",
    source: String?,
): String {
    val sourceJson = source?.let {
        """{"src":"$it","type":"application/x-mpegurl"}"""
    }.orEmpty()
    return """{"clip":{
      "internet":"$internet",
      "series":"$series",
      "subtitle":"$subtitle",
      "timestart":"$timestart",
      "timestop":"$timestop",
      "sources":[$sourceJson]
    }}"""
}
```

- [ ] **Step 2: Run parser tests and confirm the new API fails to compile**

Run:

```bash
./gradlew testDebugUnitTest --tests '*StvrJsonParserTest'
```

Expected: failure because `StvrJsonParser.parse`, `ProgramMetadata`, and `ParsedStvrClip` do not exist.

- [ ] **Step 3: Add the typed resolver domain**

Create `ProgramMetadata.kt`:

```kotlin
package sk.ziacik.androidtvplayer.resolver

data class ProgramMetadata(
    val title: String,
    val startsAtMs: Long?,
    val endsAtMs: Long?,
    val internetAllowed: Boolean?,
)

sealed interface StreamResolution {
    data class Playable(
        val program: ProgramMetadata,
        val source: StreamSource,
    ) : StreamResolution

    data class Unavailable(
        val program: ProgramMetadata,
    ) : StreamResolution
}

internal data class ParsedStvrClip(
    val program: ProgramMetadata,
    val hlsUrl: String?,
)
```

Replace the parser entry point with `fun parse(body: String): ParsedStvrClip`. Parse `series`, `subtitle`, `titleorig`, and `title` in that order, composing `series: subtitle` when both are nonblank. Parse `internet` case-insensitively as `N -> false`, `Y -> true`, otherwise `null`. Prefer numeric epoch values from `timestart` and `timestop`; fall back to `LocalDateTime.parse(dateTimeStart/dateTimeStop).atZone(zoneId)`.

Use these focused helpers so malformed optional metadata cannot invalidate an otherwise playable source:

```kotlin
private fun JSONObject.programTitle(): String {
    val series = optString("series").trim()
    val subtitle = optString("subtitle").trim()
    return when {
        series.isNotEmpty() && subtitle.isNotEmpty() -> "$series: $subtitle"
        series.isNotEmpty() -> series
        optString("titleorig").isNotBlank() -> optString("titleorig").trim()
        optString("title").isNotBlank() -> optString("title").trim()
        else -> "Aktuálny program"
    }
}

private fun JSONObject.timestampMs(epochKey: String, isoKey: String): Long? =
    optString(epochKey).toLongOrNull()?.takeIf { it > 0L }
        ?: optString(isoKey)
            .takeIf(String::isNotBlank)
            ?.let { value ->
                runCatching {
                    LocalDateTime.parse(value).atZone(zoneId).toInstant().toEpochMilli()
                }.getOrNull()
            }
```

- [ ] **Step 4: Add failing resolver tests for both result variants**

Update `StvrResolverTest` so the successful assertion unwraps `StreamResolution.Playable`, and add:

```kotlin
@Test
fun `internet N returns unavailable without requiring HLS`() = runTest {
    val http = RecordingHttpClient(
        ArrayDeque(
            listOf(
                "landing",
                """{"clip":{
                  "series":"Ordinácia v Eifeli",
                  "subtitle":"Šance",
                  "internet":"N",
                  "timestop":"1786791292000",
                  "sources":[]
                }}""",
            ),
        ),
    )

    assertEquals(
        StreamResolution.Unavailable(
            ProgramMetadata(
                title = "Ordinácia v Eifeli: Šance",
                startsAtMs = null,
                endsAtMs = 1_786_791_292_000L,
                internetAllowed = false,
            ),
        ),
        StvrResolver(http).resolve(),
    )
}

@Test
fun `missing internet flag remains playable when HLS exists`() = runTest {
    val result = StvrResolver(
        RecordingHttpClient(ArrayDeque(listOf("landing", responseWith("https://cdn.example/live.m3u8")))),
    ).resolve()

    assertTrue(result is StreamResolution.Playable)
}
```

- [ ] **Step 5: Implement resolver branching and run resolver tests**

Change `resolve()` to return `StreamResolution`:

```kotlin
val parsed = parser.parse(body)
if (parsed.program.internetAllowed == false) {
    return StreamResolution.Unavailable(parsed.program)
}
val hlsUrl = parsed.hlsUrl
    ?: throw StreamResolveException("STVR response does not contain an HLS source")
return StreamResolution.Playable(
    program = parsed.program,
    source = StreamSource(url = hlsUrl, userAgent = STVR_USER_AGENT),
)
```

Run:

```bash
./gradlew testDebugUnitTest --tests '*StvrJsonParserTest' --tests '*StvrResolverTest'
```

Expected: all parser and resolver tests pass.

- [ ] **Step 6: Commit typed resolution**

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/resolver \
  app/src/test/java/sk/ziacik/androidtvplayer/resolver
git commit -m "feat: model STVR program availability"
```

## Task 2: Playback snapshots and restricted-program retry

**Files:**

- Create: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlaybackSnapshot.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerPort.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/Media3PlayerPort.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerUiState.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`

- [ ] **Step 1: Add failing controller tests for metadata, snapshots, and retry timing**

Change test resolvers to return `StreamResolution.Playable(program, source)`. Add a mutable snapshot to `FakePlayerPort`, then add:

```kotlin
private val PROGRAM = ProgramMetadata(
    title = "Večerný program",
    startsAtMs = 1_000L,
    endsAtMs = 100_000L,
    internetAllowed = true,
)

private fun playableResolution() = StreamResolution.Playable(
    program = PROGRAM,
    source = StreamSource("https://cdn.example/live.m3u8", "ua"),
)

@Test
fun `ready state contains program and latest playback snapshot`() = runTest {
    val player = FakePlayerPort(
        snapshot = PlaybackSnapshot(
            currentPositionMs = 40_000L,
            durationMs = 100_000L,
            liveOffsetMs = 60_000L,
            isSeekable = true,
            isPlaying = true,
        ),
    )
    val controller = controller(player, this)

    controller.start()
    advanceUntilIdle()
    player.registeredListener.onReady(isPlaying = true)
    controller.refreshPlaybackSnapshot()

    assertEquals(PROGRAM, (controller.state.value as PlayerUiState.Ready).program)
    assertEquals(player.snapshot, (controller.state.value as PlayerUiState.Ready).playback)
}

@Test
fun `restricted program retries two seconds after announced end`() = runTest {
    var resolveCalls = 0
    val controller = PlayerController(
        scope = this,
        resolve = {
            resolveCalls += 1
            if (resolveCalls == 1) {
                StreamResolution.Unavailable(PROGRAM.copy(endsAtMs = 20_000L))
            } else {
                playableResolution()
            }
        },
        playerPort = FakePlayerPort(),
        nowMs = { 10_000L },
    )

    controller.start()
    runCurrent()
    assertTrue(controller.state.value is PlayerUiState.Unavailable)
    advanceTimeBy(11_999L)
    runCurrent()
    assertEquals(1, resolveCalls)
    advanceTimeBy(1L)
    runCurrent()
    assertEquals(2, resolveCalls)
}

@Test
fun `missing or stale end time uses one minute retry`() = runTest {
    var resolveCalls = 0
    val controller = PlayerController(
        scope = this,
        resolve = {
            resolveCalls += 1
            StreamResolution.Unavailable(PROGRAM.copy(endsAtMs = null))
        },
        playerPort = FakePlayerPort(),
        nowMs = { 10_000L },
    )

    controller.start()
    runCurrent()
    advanceTimeBy(59_999L)
    runCurrent()
    assertEquals(1, resolveCalls)
    advanceTimeBy(1L)
    runCurrent()
    assertEquals(2, resolveCalls)
}

@Test
fun `manual retry cancels scheduled restricted retry`() = runTest {
    var resolveCalls = 0
    val controller = PlayerController(
        scope = this,
        resolve = {
            resolveCalls += 1
            if (resolveCalls == 1) StreamResolution.Unavailable(PROGRAM.copy(endsAtMs = 70_000L))
            else playableResolution()
        },
        playerPort = FakePlayerPort(),
        nowMs = { 10_000L },
    )

    controller.start()
    runCurrent()
    controller.retry()
    runCurrent()
    advanceTimeBy(62_000L)
    runCurrent()
    assertEquals(2, resolveCalls)
}

private class FakePlayerPort(
    var snapshot: PlaybackSnapshot = PlaybackSnapshot(
        currentPositionMs = 30_000L,
        durationMs = 100_000L,
        liveOffsetMs = 70_000L,
        isSeekable = true,
        isPlaying = true,
    ),
) : PlayerPort {
    lateinit var registeredListener: PlayerPort.Listener
    val loadedSources = mutableListOf<StreamSource>()
    val seekPositions = mutableListOf<Long>()
    var goLiveCalls = 0

    override fun snapshot() = snapshot
    override fun load(source: StreamSource) { loadedSources += source }
    override fun play() { snapshot = snapshot.copy(isPlaying = true) }
    override fun pause() { snapshot = snapshot.copy(isPlaying = false) }
    override fun seekTo(positionMs: Long) { seekPositions += positionMs }
    override fun goLive() { goLiveCalls += 1 }
    override fun release() = Unit
    override fun setListener(listener: PlayerPort.Listener) { registeredListener = listener }
}
```

- [ ] **Step 2: Run the controller tests and verify failure**

```bash
./gradlew testDebugUnitTest --tests '*PlayerControllerTest'
```

Expected: compile failures for `PlaybackSnapshot`, `Unavailable`, and `refreshPlaybackSnapshot`.

- [ ] **Step 3: Add the immutable playback snapshot and port contract**

Create `PlaybackSnapshot.kt`:

```kotlin
package sk.ziacik.androidtvplayer.player

data class PlaybackSnapshot(
    val currentPositionMs: Long,
    val durationMs: Long?,
    val liveOffsetMs: Long?,
    val isSeekable: Boolean,
    val isPlaying: Boolean,
)
```

Replace the four scalar read properties on `PlayerPort` with:

```kotlin
fun snapshot(): PlaybackSnapshot
```

Retain `load`, `play`, `pause`, `seekTo`, `goLive`, `release`, and the listener. Update `Media3PlayerPort.snapshot()`:

```kotlin
override fun snapshot() = PlaybackSnapshot(
    currentPositionMs = player.currentPosition.coerceAtLeast(0L),
    durationMs = player.duration.takeUnless { it == C.TIME_UNSET || it < 0L },
    liveOffsetMs = player.currentLiveOffset.takeUnless { it == C.TIME_UNSET || it < 0L },
    isSeekable = player.isCurrentMediaItemSeekable,
    isPlaying = player.isPlaying,
)
```

- [ ] **Step 4: Expand player UI state and implement controlled retry**

Use these states:

```kotlin
sealed interface PlayerUiState {
    data object Resolving : PlayerUiState
    data class Preparing(val program: ProgramMetadata) : PlayerUiState
    data class Ready(
        val program: ProgramMetadata,
        val playback: PlaybackSnapshot,
    ) : PlayerUiState
    data class Unavailable(val program: ProgramMetadata) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}
```

Change `PlayerController.resolve` to `suspend () -> StreamResolution`. Store `activeProgram`, `resolveJob`, and `restrictedRetryJob`. The branching and scheduler must be:

```kotlin
private fun applyResolution(resolution: StreamResolution) {
    when (resolution) {
        is StreamResolution.Playable -> {
            activeProgram = resolution.program
            mutableState.value = PlayerUiState.Preparing(resolution.program)
            playerPort.load(resolution.source)
        }
        is StreamResolution.Unavailable -> {
            activeProgram = null
            mutableState.value = PlayerUiState.Unavailable(resolution.program)
            scheduleRestrictedRetry(resolution.program.endsAtMs)
        }
    }
}

private fun scheduleRestrictedRetry(endsAtMs: Long?) {
    restrictedRetryJob?.cancel()
    val untilEnd = endsAtMs?.minus(nowMs())
    val delayMs = if (untilEnd != null && untilEnd > 0L) {
        untilEnd + RETRY_AFTER_END_PADDING_MS
    } else {
        RESTRICTED_RETRY_FALLBACK_MS
    }
    restrictedRetryJob = scope.launch {
        delay(delayMs)
        retry()
    }
}
```

`retry()` cancels both existing jobs before launching a fresh resolve. `release()` cancels both jobs and releases the port. `refreshPlaybackSnapshot()` updates only a current `Ready` state. Listener callbacks use `activeProgram` plus `playerPort.snapshot()`; no source URL enters UI state or diagnostics.

Update seek, play/pause, and live methods to use one local `val snapshot = playerPort.snapshot()` per action.

- [ ] **Step 5: Run controller and complete unit tests**

```bash
./gradlew testDebugUnitTest
```

Expected: all unit tests pass with zero failures.

- [ ] **Step 6: Commit controller and playback metrics**

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/player \
  app/src/test/java/sk/ziacik/androidtvplayer/player
git commit -m "feat: track live playback and restricted programs"
```

## Task 3: Pure cinematic overlay model

**Files:**

- Create: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt`

- [ ] **Step 1: Write failing model tests**

```kotlin
package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.player.PlaybackSnapshot
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

class PlayerOverlayModelTest {
    private val program = ProgramMetadata("Večerný program", null, null, true)

    @Test
    fun `uses real progress and live offset`() {
        val model = PlayerOverlayModel.from(
            ready(position = 40_000L, duration = 100_000L, offset = 60_000L),
        )
        assertEquals(0.4f, model.progress!!, 0.0001f)
        assertEquals("−1:00", model.delayText)
        assertFalse(model.isLive)
        assertEquals("NA LIVE", model.liveActionText)
    }

    @Test
    fun `ten seconds or less counts as live`() {
        val model = PlayerOverlayModel.from(
            ready(position = 97_000L, duration = 100_000L, offset = 3_000L),
        )
        assertTrue(model.isLive)
        assertNull(model.delayText)
        assertEquals("NAŽIVO", model.liveActionText)
    }

    @Test
    fun `falls back to duration minus position`() {
        val model = PlayerOverlayModel.from(
            ready(position = 3_661_000L, duration = 7_200_000L, offset = null),
        )
        assertEquals("−58:59", model.delayText)
    }

    @Test
    fun `non-seekable item has inactive timeline`() {
        val model = PlayerOverlayModel.from(
            ready(position = 0L, duration = null, offset = null, seekable = false),
        )
        assertNull(model.progress)
        assertNull(model.delayText)
        assertFalse(model.isSeekable)
    }

    private fun ready(
        position: Long,
        duration: Long?,
        offset: Long?,
        seekable: Boolean = true,
    ) = PlayerUiState.Ready(
        program = program,
        playback = PlaybackSnapshot(
            currentPositionMs = position,
            durationMs = duration,
            liveOffsetMs = offset,
            isSeekable = seekable,
            isPlaying = true,
        ),
    )
}
```

- [ ] **Step 2: Run the model test and verify missing type failure**

```bash
./gradlew testDebugUnitTest --tests '*PlayerOverlayModelTest'
```

Expected: compile failure because `PlayerOverlayModel` is absent.

- [ ] **Step 3: Implement the pure UI model**

Create:

```kotlin
package sk.ziacik.androidtvplayer.ui

import java.util.Locale
import sk.ziacik.androidtvplayer.player.PlayerUiState

data class PlayerOverlayModel(
    val channelLabel: String,
    val programTitle: String,
    val progress: Float?,
    val delayText: String?,
    val isLive: Boolean,
    val isPlaying: Boolean,
    val isSeekable: Boolean,
    val liveActionText: String,
) {
    companion object {
        fun from(state: PlayerUiState.Ready): PlayerOverlayModel {
            val playback = state.playback
            val duration = playback.durationMs
            val validWindow = playback.isSeekable && duration != null && duration > 0L
            val progress = if (validWindow) {
                (playback.currentPositionMs.toDouble() / duration.toDouble())
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            } else null
            val offset = playback.liveOffsetMs
                ?: duration?.minus(playback.currentPositionMs)?.coerceAtLeast(0L)
            val isLive = offset != null && offset <= LIVE_THRESHOLD_MS

            return PlayerOverlayModel(
                channelLabel = "JEDNOTKA · NAŽIVO",
                programTitle = state.program.title,
                progress = progress,
                delayText = offset?.takeUnless { isLive || !validWindow }?.let(::formatDelay),
                isLive = isLive,
                isPlaying = playback.isPlaying,
                isSeekable = playback.isSeekable,
                liveActionText = if (isLive) "NAŽIVO" else "NA LIVE",
            )
        }

        private fun formatDelay(offsetMs: Long): String {
            val totalSeconds = offsetMs / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = (totalSeconds % 3_600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.ROOT, "−%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.ROOT, "−%d:%02d", minutes, seconds)
            }
        }

        private const val LIVE_THRESHOLD_MS = 10_000L
    }
}
```

- [ ] **Step 4: Run model and all unit tests**

```bash
./gradlew testDebugUnitTest
```

Expected: all tests pass.

- [ ] **Step 5: Commit the overlay model**

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt \
  app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt
git commit -m "feat: derive cinematic live overlay state"
```

## Task 4: Cinematic Compose UI and restricted state

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt`
- Create: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`

- [ ] **Step 1: Configure Compose device tests**

Add versions and libraries:

```toml
[versions]
androidxTestRunner = "1.7.0"

[libraries]
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }
```

In `defaultConfig` add:

```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```

In dependencies add:

```kotlin
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
androidTestImplementation(libs.androidx.test.runner)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

- [ ] **Step 2: Write failing Compose semantics tests**

Create `PlayerOverlayTest.kt` with `createComposeRule()` and two tests:

```kotlin
@get:Rule
val compose = createComposeRule()

@Test
fun cinematicOverlayExposesProgramTimelineAndActions() {
    compose.setContent {
        AndroidTvPlayerTheme {
            PlayerOverlay(
                model = PlayerOverlayModel(
                    channelLabel = "JEDNOTKA · NAŽIVO",
                    programTitle = "Večerný program",
                    progress = 0.4f,
                    delayText = "−1:00",
                    isLive = false,
                    isPlaying = true,
                    isSeekable = true,
                    liveActionText = "NA LIVE",
                ),
                focusedControl = FocusedControl.PLAY_PAUSE,
            )
        }
    }

    compose.onNodeWithText("JEDNOTKA · NAŽIVO").assertIsDisplayed()
    compose.onNodeWithText("Večerný program").assertIsDisplayed()
    compose.onNodeWithText("−1:00").assertIsDisplayed()
    compose.onNodeWithText("↶ 10").assertIsDisplayed()
    compose.onNodeWithText("10 ↷").assertIsDisplayed()
    compose.onNodeWithText("NA LIVE").assertIsDisplayed()
    compose.onNodeWithTag("live-window-progress").assertIsDisplayed()
}

@Test
fun restrictedPanelShowsProgramAndRetryTime() {
    compose.setContent {
        AndroidTvPlayerTheme {
            RestrictedProgramPanel(
                programTitle = "Ordinácia v Eifeli: Šance",
                retryTime = "12:54",
            )
        }
    }

    compose.onNodeWithText("Tento program nie je dostupný online").assertIsDisplayed()
    compose.onNodeWithText("Ordinácia v Eifeli: Šance").assertIsDisplayed()
    compose.onNodeWithText("Vysielanie skúsime obnoviť o 12:54").assertIsDisplayed()
    compose.onNodeWithText("Skúsiť znova").assertIsDisplayed()
}
```

- [ ] **Step 3: Run the instrumented test and verify the new API fails**

With the Philips connected:

```bash
ANDROID_SERIAL=192.168.0.200:5555 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=sk.ziacik.androidtvplayer.ui.PlayerOverlayTest
```

Expected: compile failure because the redesigned signature, tag, and restricted panel do not exist.

- [ ] **Step 4: Replace `PlayerOverlay` with the Cinematic layout**

Implement these exact visual rules:

- Root `Box(fillMaxSize())` with a vertical gradient from transparent at 45% to `Color.Black.copy(alpha = 0.94f)` at 100%.
- Bottom content uses `padding(horizontal = 56.dp, vertical = 36.dp)` and `Arrangement.spacedBy(10.dp)`.
- Channel label is 13sp, bold, uppercase, letter-spaced, and `Color.White.copy(alpha = 0.68f)`.
- Program title is 23sp semibold, one line, ellipsized.
- Timeline is 4dp high with a translucent track, a white filled fraction, and an 8dp white position marker. Put `Modifier.testTag("live-window-progress")` on its root. When progress is null, omit fill and marker.
- Timeline labels are 12sp and 70% white. The left label uses `model.delayText.orEmpty()` and the right label is `LIVE`.
- Controls use 40dp-high rounded pills. Unfocused fill is 12% white; focused fill is white, text is near-black, scale is 1.06, and elevation is 8dp.
- The three transport labels are exactly `↶ 10`, `Ⅱ` or `▶`, and `10 ↷`.
- The live action is right-aligned with `Spacer(weight = 1f)`. Show a 7dp red dot only when `model.isLive`; its text is `model.liveActionText`.
- Disabled seek labels use 32% white and never receive focus styling.

Use this concrete structure; keep dimensions and strings in named private constants so the file remains readable:

```kotlin
@Composable
fun PlayerOverlay(
    model: PlayerOverlayModel,
    focusedControl: FocusedControl,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.94f),
            ),
        ),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(horizontal = 56.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = model.channelLabel,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = model.programTitle,
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LiveTimeline(model.progress, model.delayText)
            TransportControls(model, focusedControl)
        }
    }
}

@Composable
private fun LiveTimeline(progress: Float?, delayText: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier.fillMaxWidth().height(8.dp).testTag("live-window-progress"),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.24f)),
            )
            if (progress != null) {
                Box(
                    Modifier.fillMaxWidth(progress).height(4.dp)
                        .clip(RoundedCornerShape(99.dp)).background(Color.White),
                )
                Box(
                    Modifier.fillMaxWidth(progress).wrapContentWidth(Alignment.End)
                        .size(8.dp).clip(CircleShape).background(Color.White),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(delayText.orEmpty(), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text("LIVE", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun TransportControls(model: PlayerOverlayModel, focus: FocusedControl) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ControlPill("↶ 10", model.isSeekable, false)
        Spacer(Modifier.width(10.dp))
        ControlPill(if (model.isPlaying) "Ⅱ" else "▶", true, focus == FocusedControl.PLAY_PAUSE)
        Spacer(Modifier.width(10.dp))
        ControlPill("10 ↷", model.isSeekable, false)
        Spacer(Modifier.weight(1f))
        LivePill(model.liveActionText, model.isLive, focus == FocusedControl.LIVE)
    }
}

@Composable
private fun ControlPill(text: String, enabled: Boolean, focused: Boolean) {
    val fill = if (focused) Color.White else Color.White.copy(alpha = 0.12f)
    val ink = when {
        focused -> Color(0xFF0B0E11)
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.32f)
    }
    Box(
        Modifier.scale(if (focused) 1.06f else 1f)
            .shadow(if (focused) 8.dp else 0.dp, RoundedCornerShape(99.dp))
            .height(40.dp).clip(RoundedCornerShape(99.dp)).background(fill)
            .padding(horizontal = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LivePill(text: String, isLive: Boolean, focused: Boolean) {
    val fill = if (focused) Color.White else Color.White.copy(alpha = 0.12f)
    val ink = if (focused) Color(0xFF0B0E11) else Color.White
    Row(
        modifier = Modifier.scale(if (focused) 1.06f else 1f)
            .shadow(if (focused) 8.dp else 0.dp, RoundedCornerShape(99.dp))
            .height(40.dp).clip(RoundedCornerShape(99.dp)).background(fill)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (isLive) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF4054)))
        }
        Text(text, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
```

Implement `RestrictedProgramPanel` in the same file. Its signature and user-visible string selection are fixed:

```kotlin
@Composable
fun RestrictedProgramPanel(
    programTitle: String,
    retryTime: String?,
    modifier: Modifier = Modifier,
) {
    val retryMessage = retryTime?.let { "Vysielanie skúsime obnoviť o $it" }
        ?: "Vysielanie budeme skúšať obnoviť automaticky"
    Column(
        modifier = modifier.fillMaxWidth(0.72f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF4054)))
        Text(
            "Tento program nie je dostupný online",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(programTitle, color = Color.White.copy(alpha = 0.78f), fontSize = 19.sp)
        Text(retryMessage, color = Color.White.copy(alpha = 0.62f), fontSize = 16.sp)
        ControlPill("Skúsiť znova", enabled = true, focused = true)
    }
}
```

It is presentational only; `PlayerScreen` handles the center key.

- [ ] **Step 5: Run Compose tests on the TV**

```bash
ANDROID_SERIAL=192.168.0.200:5555 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=sk.ziacik.androidtvplayer.ui.PlayerOverlayTest
```

Expected: `BUILD SUCCESSFUL`, two tests pass.

- [ ] **Step 6: Commit the cinematic UI**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt \
  app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt
git commit -m "feat: render cinematic TV controls"
```

## Task 5: Screen wiring, refresh loop, and retry presentation

**Files:**

- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`

- [ ] **Step 1: Add failing state-layer UI assertions**

Extend `PlayerOverlayTest` with the state-only renderer used by `PlayerScreen`:

```kotlin
@Test
fun unavailableStateRendersRestrictedPanel() {
    compose.setContent {
        AndroidTvPlayerTheme {
            PlayerStateLayer(
                state = PlayerUiState.Unavailable(
                    ProgramMetadata("Ordinácia v Eifeli: Šance", null, 20_000L, false),
                ),
                overlayVisible = false,
                focusedControl = FocusedControl.PLAY_PAUSE,
                formatTime = { "12:54" },
            )
        }
    }

    compose.onNodeWithText("Tento program nie je dostupný online").assertIsDisplayed()
    compose.onNodeWithText("Vysielanie skúsime obnoviť o 12:54").assertIsDisplayed()
}

@Test
fun readyStateHidesOverlayWhenVisibilityIsFalse() {
    val ready = PlayerUiState.Ready(
        program = ProgramMetadata("Večerný program", null, null, true),
        playback = PlaybackSnapshot(90_000L, 100_000L, 10_000L, true, true),
    )
    compose.setContent {
        AndroidTvPlayerTheme {
            PlayerStateLayer(
                state = ready,
                overlayVisible = false,
                focusedControl = FocusedControl.PLAY_PAUSE,
                formatTime = { "12:54" },
            )
        }
    }

    compose.onNodeWithText("Večerný program").assertDoesNotExist()
}
```

- [ ] **Step 2: Run tests and confirm the extracted content is missing**

```bash
./gradlew testDebugUnitTest
ANDROID_SERIAL=192.168.0.200:5555 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=sk.ziacik.androidtvplayer.ui.PlayerOverlayTest
```

Expected: unit tests pass; instrumented compilation fails until `PlayerStateLayer` is added.

- [ ] **Step 3: Wire the ready and unavailable states**

In `PlayerScreen`, refresh only while a ready overlay is visible. Key the effect on the Boolean state class rather than the changing snapshot value so each refresh does not restart the loop:

```kotlin
LaunchedEffect(overlayVisible, state is PlayerUiState.Ready) {
    if (!overlayVisible || state !is PlayerUiState.Ready) return@LaunchedEffect
    while (true) {
        controller.refreshPlaybackSnapshot()
        delay(1_000L)
    }
}
```

Extract and use this exact renderer:

```kotlin
@Composable
internal fun PlayerStateLayer(
    state: PlayerUiState,
    overlayVisible: Boolean,
    focusedControl: FocusedControl,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
) {
when (val current = state) {
    PlayerUiState.Resolving,
    is PlayerUiState.Preparing,
    -> CircularProgressIndicator(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
    )

    is PlayerUiState.Ready -> if (overlayVisible) {
        PlayerOverlay(
            model = PlayerOverlayModel.from(current),
            focusedControl = focusedControl,
        )
    }

    is PlayerUiState.Unavailable -> RestrictedProgramPanel(
        programTitle = current.program.title,
        retryTime = current.program.endsAtMs?.let(formatTime),
        modifier = modifier,
    )

    is PlayerUiState.Error -> ErrorPanel(
        message = current.message,
        actionText = "Skúsiť znova",
        modifier = modifier,
    )
}
}
```

In `PlayerScreen`, create `val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }`, pass `{ millis -> timeFormat.format(Date(millis)) }`, and align `PlayerStateLayer` at the center. When the timestamp is absent, `RestrictedProgramPanel` renders `Vysielanie budeme skúšať obnoviť automaticky`.

Before normal remote mapping, make center/enter on either `Unavailable` or `Error` call `controller.retry()` and consume the event. All other existing mappings stay intact and continue to call `overlayController.show()`, thereby resetting the four-second timer.

```kotlin
val keyCode = event.nativeKeyEvent.keyCode
val retryable = state is PlayerUiState.Unavailable || state is PlayerUiState.Error
if (retryable && keyCode.isCenterKey()) {
    controller.retry()
    return@onPreviewKeyEvent true
}
```

Change `ErrorPanel` to accept `actionText: String` and render that value in the existing focused pill instead of the English `Retry` literal.

- [ ] **Step 4: Keep dependency construction type-safe**

Confirm that `MainActivity` still compiles unchanged with `resolver::resolve`; its inferred type now matches `suspend () -> StreamResolution`. Do not add a service locator, ViewModel, navigation framework, or dependency-injection library.

- [ ] **Step 5: Run all automated checks**

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
ANDROID_SERIAL=192.168.0.200:5555 ./gradlew connectedDebugAndroidTest
```

Expected: every command exits 0 with no failed tests or lint errors.

- [ ] **Step 6: Commit screen wiring**

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt \
  app/src/androidTest/java/sk/ziacik/androidtvplayer/ui
git commit -m "feat: present live timeline and restricted programs"
```

## Task 6: Philips installation and visual acceptance

**Files:**

- Verify: `app/build/outputs/apk/debug/app-debug.apk`
- Capture outside the repository: `/home/ziacik/Documents/Codex/2026-08-14/referenced-chatgpt-conversation-this-is-an/outputs/cinematic-player-overlay.png`

- [ ] **Step 1: Produce a fresh verified APK**

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`; APK exists at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Install and launch on the Philips**

```bash
ADB=/home/ziacik/Android/Sdk/platform-tools/adb
SERIAL=192.168.0.200:5555
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$SERIAL" shell am force-stop sk.ziacik.androidtvplayer
"$ADB" -s "$SERIAL" shell am start -W -n sk.ziacik.androidtvplayer/.MainActivity
```

Expected: install reports `Success`; activity becomes the resumed foreground activity.

- [ ] **Step 3: Exercise the physical behavior through ADB key events**

Send center, left, right, down, center, and back separately. After each event inspect `dumpsys activity` and filtered application/Media3 logs. Verify the overlay appears, seeks exactly ten seconds where permitted, returns live, hides after four seconds, and does not crash.

```bash
"$ADB" -s "$SERIAL" shell input keyevent 23
sleep 1
"$ADB" -s "$SERIAL" shell input keyevent 21
sleep 1
"$ADB" -s "$SERIAL" shell input keyevent 22
sleep 1
"$ADB" -s "$SERIAL" shell input keyevent 20
sleep 1
"$ADB" -s "$SERIAL" shell input keyevent 23
sleep 5
"$ADB" -s "$SERIAL" shell dumpsys activity activities | rg 'mResumedActivity|androidtvplayer'
"$ADB" -s "$SERIAL" logcat -d -t 500 | rg -i 'AndroidTvPlayer|ExoPlayer|FATAL EXCEPTION'
```

Expected: the app remains resumed and logs contain no fatal exception or player failure.

- [ ] **Step 4: Capture and visually inspect the real overlay**

Show the overlay, wait one second, capture a 1920×1080 PNG on the TV, pull it to the declared output path, and inspect it visually. Confirm safe margins, one-line title truncation, real timeline marker, readable delay, compact transport pills, white focus, and live indicator.

```bash
OUT=/home/ziacik/Documents/Codex/2026-08-14/referenced-chatgpt-conversation-this-is-an/outputs/cinematic-player-overlay.png
"$ADB" -s "$SERIAL" shell input keyevent 23
sleep 1
"$ADB" -s "$SERIAL" shell screencap -p /sdcard/cinematic-player-overlay.png
"$ADB" -s "$SERIAL" pull /sdcard/cinematic-player-overlay.png "$OUT"
"$ADB" -s "$SERIAL" shell rm /sdcard/cinematic-player-overlay.png
identify "$OUT"
```

Expected: a valid 1920×1080 PNG. Open it with the local image viewer and compare it with the approved A mockup.

- [ ] **Step 5: Verify the restricted state without waiting for another blocked program**

Run the `Unavailable` Compose device test and inspect its rendered semantics. Do not add a production debug switch or hard-coded fake endpoint merely to force this state. If STVR is naturally reporting `internet: "N"`, also restart the installed app and capture the real restricted panel.

- [ ] **Step 6: Final repository check**

```bash
git status --short
git log --oneline -6
```

Expected: no uncommitted implementation changes; recent commits correspond to resolver availability, playback snapshots, overlay model, cinematic UI, and screen wiring.
