# OSD programme labels Implementation Plan

> **For agentic workers:** Execute this plan inline, task by task, with the test-first steps below. Work directly on the current `master` checkout; do not create a worktree, stage files, or commit unless the user explicitly asks.

**Goal:** Replace stream-relative OSD timing with EPG programme start, current, and end times; show the current channel number; and use Slovak live-control wording.

**Architecture:** `PlayerOverlayModel` remains the pure conversion from ready player state to an immutable display model. It exposes EPG timestamps only when the programme interval is valid. `PlayerOverlay` renders those timestamps with the existing TV-local time formatter and owns the marker-label layout, clamping its horizontal position to prevent clipping at either endpoint.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 4, Android Compose test.

---

## File map

- `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt` — derive the programme-timing values, channel-number heading, and Slovak go-live label; remove stream delay text.
- `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt` — render the three EPG times around the programme marker and remove the stream-offset/LIVE legend.
- `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt` — pass the existing local time formatter into `PlayerOverlay`.
- `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt` — regression tests for model timing invariants and wording.
- `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt` — compose assertions for all three displayed time labels and removed stream labels.

### Task 1: Define EPG-only overlay data

**Files:**
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt`

- [x] **Step 1: Write failing model tests for timestamps, channel number, and label wording.**

  Replace the stream-delay assertions in `uses programme progress and live offset` with:

  ```kotlin
  assertEquals(0L, model.programmeStartMs)
  assertEquals(40_000L, model.programmeNowMs)
  assertEquals(100_000L, model.programmeEndMs)
  assertEquals("NAŽIVO", model.liveActionText)
  ```

  Change the Dvojka heading assertion to:

  ```kotlin
  assertEquals("2 · DVOJKA · NAŽIVO", model.channelLabel)
  ```

  In `missing or reversed programme timestamps hide the progress marker`, add:

  ```kotlin
  assertNull(missing.programmeStartMs)
  assertNull(missing.programmeNowMs)
  assertNull(missing.programmeEndMs)
  assertNull(reversed.programmeStartMs)
  assertNull(reversed.programmeNowMs)
  assertNull(reversed.programmeEndMs)
  ```

- [x] **Step 2: Run the model test to verify it fails.**

  Run:

  ```bash
  ./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.PlayerOverlayModelTest
  ```

  Expected: compilation failure because the three `programme*Ms` properties do not exist; existing assertions also still expose `NA LIVE` and the heading without a number.

- [x] **Step 3: Implement the minimal immutable model change.**

  Remove `delayText` and `formatDelay`. Add these fields after `progress`:

  ```kotlin
  val programmeStartMs: Long?,
  val programmeNowMs: Long?,
  val programmeEndMs: Long?,
  ```

  Derive a valid programme interval once:

  ```kotlin
  val hasProgrammeInterval =
      startsAtMs != null && endsAtMs != null && endsAtMs > startsAtMs
  ```

  Keep the existing clamped progress calculation, but make all three timestamp
  properties `null` when `hasProgrammeInterval` is false. Otherwise populate
  them with `startsAtMs`, `nowMs`, and `endsAtMs`. Build the heading and live
  action as:

  ```kotlin
  channelLabel = "${state.channel.ordinal + 1} · ${state.channel.displayName} · NAŽIVO"
  liveActionText = "NAŽIVO"
  ```

  Retain `isLive` because `LivePill` still uses it for its own red-dot status.

- [x] **Step 4: Run the model test to verify it passes.**

  Run:

  ```bash
  ./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.PlayerOverlayModelTest
  ```

  Expected: `BUILD SUCCESSFUL` and every `PlayerOverlayModelTest` passes.

### Task 2: Render the labelled programme timeline

**Files:**
- Modify: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt`

- [x] **Step 1: Write the failing Compose test for the EPG labels.**

  In `cinematicOverlayExposesProgramTimelineAndActions`, construct the model
  with:

  ```kotlin
  programmeStartMs = 1_000L,
  programmeNowMs = 2_000L,
  programmeEndMs = 3_000L,
  liveActionText = "NAŽIVO",
  ```

  Pass a deterministic formatter:

  ```kotlin
  formatTime = { milliseconds ->
      mapOf(1_000L to "20:15", 2_000L to "20:42", 3_000L to "21:20").getValue(milliseconds)
  }
  ```

  Assert `20:15`, `20:42`, `21:20`, `1 · JEDNOTKA · NAŽIVO`, and `NAŽIVO` are
  displayed. Remove the `−1:00` and `NA LIVE` assertions; the production
  timeline no longer renders either string.

- [x] **Step 2: Run the Compose test to verify it fails.**

  Run on the connected Android test device:

  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

  Expected: compilation failure until `PlayerOverlay` accepts `formatTime`, or
  assertion failures because the three EPG labels are not rendered.

- [x] **Step 3: Render the labels with collision-safe placement.**

  Change `PlayerOverlay` to accept:

  ```kotlin
  formatTime: (Long) -> String,
  ```

  Pass it into `LiveTimeline`, together with `model.programmeStartMs`,
  `model.programmeNowMs`, and `model.programmeEndMs`. In `PlayerScreen`, pass
  the existing `formatTime` argument from `PlayerStateLayer` to `PlayerOverlay`.

  Replace the old `Row` containing `delayText` and `LIVE` with endpoint labels
  below the bar and a current-time `Text` above the marker. Use
  `BoxWithConstraints`, `LocalDensity`, and `onTextLayout` to compute:

  ```kotlin
  val desiredPx = maxWidth.toPx() * clampedProgress - markerLabelWidthPx / 2f
  val clampedPx = desiredPx.coerceIn(0f, maxWidth.toPx() - markerLabelWidthPx)
  ```

  Apply the resulting horizontal offset to the current-time label. This keeps
  its centre on the marker whenever it fits and moves it inward at the two
  endpoints rather than clipping or overlapping endpoint labels. Render start
  and end labels in a full-width row below the bar, aligned `Start` and `End`.
  Render no time labels when any of the three model timestamp values is null.

- [x] **Step 4: Run the Compose test to verify it passes.**

  Run:

  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

  Expected: the test reports success, including all three EPG times and no
  `LIVE` legend.

### Task 3: Run the complete regression checks

**Files:**
- No additional files.

- [x] **Step 1: Run all local unit tests.**

  Run:

  ```bash
  ./gradlew testDebugUnitTest
  ```

  Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [x] **Step 2: Build the debug APK.**

  Run:

  ```bash
  ./gradlew assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [x] **Step 3: Inspect the final diff.**

  Run:

  ```bash
  git diff --check && git diff -- app/src/main app/src/test app/src/androidTest
  ```

  Expected: no whitespace errors and changes limited to the model, overlay,
  screen wiring, and their tests.
