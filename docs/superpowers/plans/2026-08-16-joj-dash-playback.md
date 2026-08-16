# JOJ DASH Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Play the DASH manifests returned by JOJ's Tivio endpoint while preserving HLS playback for direct JOJ fallback URLs.

**Architecture:** Add a manifest type to `StreamSource`. `JojResolver` marks a resolved Tivio `.mpd` URL as DASH; existing resolvers and JOJ fallback URLs stay HLS. Media3 maps that type to the correct MIME type and includes its DASH module.

**Tech Stack:** Kotlin, Media3 ExoPlayer DASH/HLS, JUnit 4.

---

### Task 1: Preserve the manifest protocol from the JOJ resolver

**Files:**
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/resolver/FreeviewResolversTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StreamSource.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/FreeviewResolvers.kt`

- [ ] Write a failing test where a Tivio URL ending in `.mpd` resolves as `StreamManifest.DASH`, and a fallback URL resolves as `StreamManifest.HLS`.
- [ ] Run: `./gradlew testDebugUnitTest --tests '*FreeviewResolversTest'`; expect failure because `StreamSource` has no manifest type.
- [ ] Add `StreamManifest` and set it only for the JOJ Tivio result.
- [ ] Re-run the focused test; expect PASS.

### Task 2: Let Media3 select DASH

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/Media3PlayerPort.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/player/Media3PlayerPortTest.kt`

- [ ] Write a failing unit test for the source-to-MIME mapping: DASH maps to `APPLICATION_MPD`, HLS maps to `APPLICATION_M3U8`.
- [ ] Run: `./gradlew testDebugUnitTest --tests '*Media3PlayerPortTest'`; expect failure because the mapper does not exist.
- [ ] Add the Media3 DASH dependency and the minimal mapping helper used by `MediaItem.Builder`.
- [ ] Run: `./gradlew testDebugUnitTest assembleDebug`; expect `BUILD SUCCESSFUL`.
