# Fast Open-EPG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Open-EPG fast, restore Markíza through Skylink fallback, and remove IPTV-Org.

**Architecture:** Start EPG concurrently with Media3 preparation and cache current programmes per source/channel interval. Keep Open-EPG first and Skylink second; publish all mappings through the remote catalogue.

**Tech Stack:** Kotlin, coroutines, XMLTV, JUnit, Gradle, ADB

---

### Task 1: Start EPG before Media3 readiness

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`

- [ ] Add a failing test that blocks Media3 readiness and asserts that the EPG request has already started.
- [ ] Run the focused test and confirm it fails because requests are currently made from `updateReadyState`.
- [ ] Start the lookup after a playable resolution receives its load ID, retain early results in `activeProgram`, and update an already-ready state when necessary.
- [ ] Run `PlayerControllerTest` and confirm all channel-switch and late-result cases pass.

### Task 2: Cache a programme until its interval ends

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/epg/EpgRepository.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/epg/EpgRepositoryTest.kt`

- [ ] Add a failing test that performs two lookups inside one interval and expects one parse/download.
- [ ] Run the focused test and confirm the second lookup currently repeats feed work.
- [ ] Cache `EpgProgramme` by source/channel and reuse it only while `startsAtMs <= nowMs < endsAtMs`.
- [ ] Run `EpgRepositoryTest` and confirm refresh/fallback behavior remains intact.

### Task 3: Remove IPTV-Org and publish complete mappings

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/TvChannel.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/ChannelCatalog.kt`
- Modify: `channels.json`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/channel/ChannelCatalogTest.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/channel/TvChannelTest.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/epg/EpgRepositoryTest.kt`

- [ ] Replace IPTV-Org test expectations with Open-EPG and Skylink-only expectations.
- [ ] Remove the enum value, JSON parser field, source URL/downloader, cache path, and catalogue fields.
- [ ] Add verified Skylink identifiers to remote catalogue entries that need fallback, especially Markíza.
- [ ] Validate JSON and assert that `iptvOrg` no longer occurs in application sources or catalogue.

### Task 4: Verify and deploy

**Files:**
- Verify all modified files.

- [ ] Run `./gradlew clean testDebugUnitTest assembleDebug` and `git diff --check`.
- [ ] Commit the implementation and push `master` to `origin` so the TV refresh receives the new catalogue.
- [ ] Confirm the GitHub raw catalogue exposes Open-EPG and no IPTV-Org mappings.
- [ ] Install the APK on `192.168.0.200:5555`, launch it, and inspect process state and logcat.
