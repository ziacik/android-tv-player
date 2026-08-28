# Stable Player OSD Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep OSD controls in one fixed position while moving playback status into the right-hand pill and preserving a timeline row without EPG.

**Architecture:** `PlayerOverlayModel` provides an always-present display time and the right-pill text. `PlayerOverlay` reserves a single timeline geometry for both programme and no-EPG states; it does not insert a variable status row.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4, Compose UI tests.

---

### Task 1: Make the OSD model express fixed layout data

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt`

- [ ] **Step 1: Write failing model tests.**

  Add one test asserting a no-programme model at `40_000L` has channel label `2 · DVOJKA`, `progress == null`, an always-present display time of `40_000L`, and `liveActionText == "PREPÍNAM…"` when passed that state. Add a normal-ready assertion that the right-pill text remains `NAŽIVO`.

- [ ] **Step 2: Run the focused test and verify it fails.**

  Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.PlayerOverlayModelTest`

  Expected: failure because the label still contains `NAŽIVO` and no always-present display-time property exists.

- [ ] **Step 3: Implement the model contract.**

  Add `displayNowMs: Long`; set it for every model. Change `channelLabel` to `"${channel.ordinal + 1} · ${channel.displayName}"`. Set `liveActionText = statusText ?: "NAŽIVO"` and `isLive` false when a status replaces the live pill. Remove `statusText` from the model after this mapping so it cannot create another layout row.

- [ ] **Step 4: Run the focused model test.**

  Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.PlayerOverlayModelTest`

  Expected: `BUILD SUCCESSFUL`.

### Task 2: Render an always-reserved timeline and fixed status pill

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`

- [ ] **Step 1: Write failing Compose assertions.**

  Add a no-EPG overlay model with `displayNowMs = 2_000L` and assert `live-window-progress` plus `current-programme-time` are displayed, while `programme-start-boundary` and `programme-end-boundary` do not exist. Assert a switching model displays `PREPÍNAM…` in the right pill and never renders the status beneath the programme title.

- [ ] **Step 2: Compile the instrumented test and verify missing behavior.**

  Run: `./gradlew compileDebugAndroidTestKotlin`

  Expected: compilation succeeds, but the assertions fail when run because no-EPG timelines are currently omitted.

- [ ] **Step 3: Implement the fixed geometry.**

  Always call `LiveTimeline`. When `progress` is null, draw the neutral track, centre the current-time pill using `displayNowMs`, omit endpoint labels and marker/fill. With a valid interval retain current endpoint/marker behavior. Remove the composable status line below the programme title. Give `LivePill` a fixed width with centred, ellipsized text so `NAŽIVO`, switching, and recovery labels do not shift controls.

- [ ] **Step 4: Run build and test compilation.**

  Run: `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug`

  Expected: `BUILD SUCCESSFUL`.

### Task 3: TV verification

**Files:**
- No source changes required.

- [ ] **Step 1: Install and manually inspect the debug APK.**

  Run: `adb -s 192.168.0.200:5555 install -r app/build/outputs/apk/debug/app-debug.apk`

  Confirm switching, error/retry, EPG arrival, and no-EPG states do not move the transport controls vertically.

## Plan self-review

- Task 1 removes duplicate live wording and defines fixed display data.
- Task 2 covers each visual state and reserves the timeline geometry without fabricated programme times.
- Task 3 separates build proof from TV observation.
