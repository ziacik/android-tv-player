# Player OSD Symbol Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Use compact right-aligned symbols for switching and recovery instead of text status labels.

**Architecture:** Derive a `PlayerOverlayModel` status mode from the existing state text. `LivePill` renders normal live text, a spinner, or a retry glyph at its natural width.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4, Compose UI tests.

---

### Task 1: Model state indicator

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt`

- [ ] Write a failing test that expects switching to expose `StateIndicator.Switching` and recovery to expose `StateIndicator.Retrying`, while ready exposes `StateIndicator.Live`.
- [ ] Run `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.PlayerOverlayModelTest` and confirm it fails because no indicator type exists.
- [ ] Add `StateIndicator` to `PlayerOverlayModel`, derive it from the existing optional status string, and remove the text conversion used only for the pill.
- [ ] Re-run the focused model test and confirm `BUILD SUCCESSFUL`.

### Task 2: Natural-width indicator UI

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`

- [ ] Write Compose assertions for a switching spinner and retry glyph `↻` and for the absence of `PREPÍNAM…`/countdown text.
- [ ] Compile the test with `./gradlew compileDebugAndroidTestKotlin`.
- [ ] Remove the fixed pill width. Render an indeterminate `CircularProgressIndicator` for `Switching`, `↻` for `Retrying`, and the existing live pill for `Live`; retain the weighted spacer before the indicator.
- [ ] Run `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug` and confirm `BUILD SUCCESSFUL`.

### Task 3: TV handoff

- [ ] Install with `adb -s 192.168.0.200:5555 install -r app/build/outputs/apk/debug/app-debug.apk` and launch the app.
- [ ] Manually confirm the naturally sized indicator remains right-aligned and the OSD does not jump.
