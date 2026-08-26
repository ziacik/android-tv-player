# Programme Timeline Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Place programme bounds legibly above the OSD timeline and eliminate the current-time label jump on one-second updates.

**Architecture:** `LiveTimeline` owns the geometry: its labels sit in a dedicated row above the bar, while start and end ticks mark the programme interval bounds. The current label retains its measured width across timestamp updates so its horizontal offset never resets to zero.

**Tech Stack:** Kotlin, Jetpack Compose, Android Compose test, Gradle.

---

## File map

- `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt` — timeline label and bar geometry.
- `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt` — Compose assertions for the labelled programme interval.

### Task 1: Stabilize the current-time label measurement

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt`

- [ ] **Step 1: Preserve the measured label width across timestamp changes.**

  Replace `remember(nowMs) { mutableStateOf(0) }` with `remember { mutableStateOf(0) }`, and update the value only when `onTextLayout` receives a different width:

  ```kotlin
  var currentTimeWidthPx by remember { mutableStateOf(0) }

  onTextLayout = { layoutResult ->
      if (currentTimeWidthPx != layoutResult.size.width) {
          currentTimeWidthPx = layoutResult.size.width
      }
  }
  ```

- [ ] **Step 2: Run the Compose test suite.**

  Run `./gradlew connectedDebugAndroidTest`. Expected: successful execution of the overlay Compose tests.

### Task 2: Make programme boundaries readable

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt`
- Modify: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`

- [ ] **Step 1: Write failing Compose assertions for the interval-boundary markers.**

  Add test tags `programme-start-boundary` and `programme-end-boundary` to the start and end ticks, then assert both tags in `cinematicOverlayExposesProgramTimelineAndActions`.

- [ ] **Step 2: Run the Compose test to verify it fails.**

  Run `./gradlew connectedDebugAndroidTest`. Expected: the two new nodes are absent.

- [ ] **Step 3: Render labels above and ticks at the bar bounds.**

  Give the timeline `BoxWithConstraints` enough height for a label row and a bar below it. Align the three labels at the top; align the track and marker at the bottom. Add two 6 dp vertical tick `Box` elements, aligned to the bar's left and right ends, with the boundary test tags. Keep the marker and current label horizontally clamped within the same bounds.

- [ ] **Step 4: Run verification.**

  Run `./gradlew testDebugUnitTest && ./gradlew assembleDebug && git diff --check`. Expected: all commands succeed with no whitespace errors.
