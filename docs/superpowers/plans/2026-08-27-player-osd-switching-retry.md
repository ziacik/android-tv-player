# Player OSD Switching and Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the normal player OSD, target-channel EPG, and automated recovery state during switching, ordinary stream failures, and STVR online restrictions while preserving the last frame.

**Architecture:** Make OSD display data independent of `Ready` playback state, then make every non-credential player state provide that display data and an optional status. `PlayerController` owns a single retry backoff job and exposes the next attempt time; it cancels and resets that job together with the existing load-generation guards.

**Tech Stack:** Kotlin, coroutines/StateFlow, Jetpack Compose, Media3, JUnit4, Compose UI tests.

---

## File structure

- `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerUiState.kt`: state data for programme metadata and scheduled retry time.
- `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`: target-channel EPG lookup, ordinary-error exponential backoff, and cancellation/reset rules.
- `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt`: common OSD model for ready and non-ready states.
- `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt`: render an optional small state line inside the existing bottom OSD.
- `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt`: always render the OSD for switching/recovery states; retain Media3 content after reset.
- `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`: state and retry timing tests using the existing coroutine test scheduler and fake player.
- `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt`: common-model tests for absent/valid programme information.
- `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`: Compose assertions for the replacement OSD and absence of legacy panels.

### Task 1: Define display and retry state

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerUiState.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt`

- [ ] **Step 1: Write failing model tests for recovery presentation.**

  Add tests that construct an error/recovery display state with `ProgramMetadata("Večerný program", 0L, 100_000L, true)` and assert the model exposes the target channel label, title, programme bounds, `0.4f` progress at `40_000L`, and `"Prepínam…"`/recovery status. Add a second test with `program = null` that asserts a channel label, no timeline, and no fabricated programme interval.

- [ ] **Step 2: Run the model test class and verify the missing API fails.**

  Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.PlayerOverlayModelTest`

  Expected: compilation failure because the recovery display-state/model factory does not exist.

- [ ] **Step 3: Add the minimal state fields and common model factory.**

  Change resolving and preparing states to retain `program: ProgramMetadata? = null`; change error and unavailable states to retain `program: ProgramMetadata?` and `nextRetryAtMs: Long?`. Keep `Ready` unchanged. Add a model factory that accepts channel, nullable programme, nullable playback, and nullable status:

  ```kotlin
  fun from(
      channel: TvChannel,
      program: ProgramMetadata?,
      playback: PlaybackSnapshot?,
      statusText: String?,
      nowMs: Long,
  ): PlayerOverlayModel
  ```

  It must use the existing programme-interval calculation only when both timestamps are valid, use disabled/non-playing transport values when `playback` is null, and set `programTitle` to the programme title only when nonblank. Add `statusText: String?` to `PlayerOverlayModel`. Keep the existing `from(Ready, nowMs)` as a delegating convenience factory so current call sites remain straightforward.

- [ ] **Step 4: Run the model tests and verify they pass.**

  Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.PlayerOverlayModelTest`

  Expected: `BUILD SUCCESSFUL` and all `PlayerOverlayModelTest` tests pass.

### Task 2: Implement EPG-first transition states and recovery backoff

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`

- [ ] **Step 1: Write failing controller tests for ordinary-error retry.**

  Add one test that makes the resolver throw twice and records invocation times; assert automatic calls occur at 0 ms, 1,000 ms, 3,000 ms, 7,000 ms, and that the emitted error carries `nextRetryAtMs`. Add one test that reaches `Ready`, then emits `onError`, then reaches `Ready` again and emits a second error; assert the second recovery starts again at 1,000 ms. Add a manual-retry test asserting `retry()` cancels the pending automatic job and starts one immediate resolve.

- [ ] **Step 2: Run the focused controller test class and verify failure.**

  Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.player.PlayerControllerTest`

  Expected: the new timing/state assertions fail because ordinary errors are not scheduled.

- [ ] **Step 3: Replace the restricted-only job with one recovery scheduler.**

  Rename `restrictedRetryJob` to `retryJob` and cancel it from `resolveCurrentChannel`, `switchTo`, and `release`. Add `retryAttempt` and a helper that calculates `1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L`, then `60_000L` for all later attempts. Before publishing an ordinary `Error`, calculate `nextRetryAtMs = nowMs() + delay`, publish it, and schedule a guarded `retry()` for that delay. Reset `retryAttempt` only in `updateReadyState` and on manual retry.

  For `StreamResolution.Unavailable`, retain the programme end-time retry when its end is in the future; otherwise use the same ordinary backoff helper. Publish the actual scheduled `nextRetryAtMs` in `Unavailable`. Do not alter the STVR resolver's `internetAllowed` decision.

- [ ] **Step 4: Start and apply target-channel EPG lookup independently of playback readiness.**

  At the start of a resolve generation, launch an EPG request bound to `(channel, generation)` and update the current resolving/preparing/error state only when that pair still matches. When `StreamResolution.Playable` supplies programme metadata, publish it in `Preparing`; prefer an EPG result with a valid interval when it returns. This lookup must not depend on `activeLoadId`, because the OSD needs programme data before Media3 reports ready. Preserve cancellation on channel switch, resolve retry, and release.

- [ ] **Step 5: Run controller tests and the full JVM suite.**

  Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.player.PlayerControllerTest`

  Expected: `BUILD SUCCESSFUL` and all controller tests pass.

  Run: `./gradlew testDebugUnitTest`

  Expected: `BUILD SUCCESSFUL`.

### Task 3: Render one OSD and retain the last frame

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`

- [ ] **Step 1: Replace legacy-panel Compose tests with failing OSD tests.**

  Replace `resolvingDvojkaStateShowsChannelWhileOverlayIsHidden` and both restricted-panel tests. Assert resolving shows `2 · DVOJKA · NAŽIVO` and `Prepínam…`; assert an unavailable state shows its programme/title, restriction text inside the OSD, and `Vysielanie skúsime obnoviť o 12:54`; assert neither `NAČÍTAVAM` nor `Skúsiť znova` appears. Add an error-state assertion for its recovery-time text.

- [ ] **Step 2: Run the Compose test class and verify it fails.**

  Run: `./gradlew connectedDebugAndroidTest`

  Expected: the updated assertions fail until the state layer no longer renders legacy panels. This command requires an attached/emulated Android device and cannot be narrowed with `--tests`.

- [ ] **Step 3: Render the status within `PlayerOverlay` and route all recovery states through it.**

  In `PlayerOverlay`, render `model.statusText` as a muted, single-line label between programme title and timeline. In `PlayerStateLayer`, build the common model for resolving, preparing, error, and unavailable regardless of `overlayVisible`; use `Prepínam…`, a concise error/retry label, and a restriction/retry label respectively. Delete `LoadingChannelPanel`, `ErrorPanel`, and `RestrictedProgramPanel` once no call site remains. Keep `CredentialsRequired` on its dedicated credential panel.

  In the `PlayerView` factory set:

  ```kotlin
  keepContentOnPlayerReset = true
  ```

  This preserves the previous frame across `stop()` during channel changes. Retain the black root background only for the first launch before any video frame exists.

- [ ] **Step 4: Run JVM, instrumented, build, and static-diff checks.**

  Run: `./gradlew testDebugUnitTest assembleDebug`

  Expected: `BUILD SUCCESSFUL`.

  Run: `./gradlew connectedDebugAndroidTest`

  Expected: `BUILD SUCCESSFUL` on a connected target.

  Run: `git diff --check && git status --short`

  Expected: no whitespace errors; only the intended source, test, and documentation changes are listed.

### Task 4: Device smoke test and handoff

**Files:**
- No source changes required.

- [ ] **Step 1: Install the debug APK on the selected Android TV.**

  Run: `adb -s 192.168.0.200:5555 install -r app/build/outputs/apk/debug/app-debug.apk`

  Expected: `Success`.

- [ ] **Step 2: Smoke-test state transitions.**

  Launch `sk.ziacik.androidtvplayer/.MainActivity`, switch between two channels, and force/observe one ordinary stream error plus one STVR unavailable programme if currently available. Confirm visually that the prior frame remains behind the OSD, target channel/EPG appears during the switch, and recovery timing is displayed.

- [ ] **Step 3: Report precise evidence.**

  Report unit/build/install results separately from observed remote interaction and live-playback recovery. Do not claim a provider restriction was bypassed or a stream played merely because the application launched.

## Plan self-review

- Spec coverage: Task 1 provides one shared OSD model; Task 2 covers target EPG, 1-to-60-second recovery, manual retry, cancellation, success reset, and STVR unavailable timing; Task 3 removes the black panels and preserves the frame; Task 4 establishes device evidence.
- Placeholder scan: no incomplete tasks or undefined interfaces remain. `ProgramMetadata`, `PlaybackSnapshot`, `PlayerUiState`, `PlayerOverlayModel`, and the controller's existing fake-player test harness are named at their actual paths.
- Type consistency: the model factory and `statusText` are introduced in Task 1 before Task 3 consumes them; `nextRetryAtMs` is introduced in Task 1 before Task 2 publishes it and Task 3 formats it.
