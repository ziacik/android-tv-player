# Markíza Channel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Markíza live channel using the same authenticated web-player resolution flow as Freeview.sk.

**Architecture:** Keep the STVR path unchanged. Add a Markíza resolver with its own cookie-preserving HTTP client and route by channel. The resolved Media3 source forwards Markíza's required request headers.

**Tech Stack:** Kotlin, OkHttp, coroutines, Media3 HLS, JUnit 4.

---

### Task 1: Add the Markíza resolver

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/MarkizaResolver.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/OkHttpMarkizaClient.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/resolver/MarkizaResolverTest.kt`

- [ ] **Step 1: Write failing resolver tests**

Test the form-token GET, login POST, live-page iframe GET, embed HLS GET and the returned headers. Also test that missing credentials fail before a request.

- [ ] **Step 2: Run resolver tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*MarkizaResolverTest'`

Expected: FAIL because the resolver does not exist.

- [ ] **Step 3: Implement the resolver and HTTP client**

Implement `MarkizaResolver.resolve()` to log in, extract `data-src` from the live page and the HLS `src` from the embed page. Return a `StreamSource` with `User-Agent`, `Referer`, and `Origin` headers. Use the public Freeview defaults only when no locally saved credentials exist.

- [ ] **Step 4: Run resolver tests and verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*MarkizaResolverTest'`

Expected: PASS.

### Task 2: Route and play Markíza

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/TvChannel.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/ChannelResolver.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StreamSource.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/Media3PlayerPort.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/channel/TvChannelTest.kt`

- [ ] **Step 1: Write failing catalogue and routing tests**

Assert that channel order is Jednotka, Dvojka, Markíza and that the router dispatches Markíza to its resolver.

- [ ] **Step 2: Run tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*TvChannelTest' --tests '*ChannelResolverTest'`

Expected: FAIL because `MARKIZA` and the router do not exist.

- [ ] **Step 3: Implement channel routing and request-header forwarding**

Add `MARKIZA`, route by channel in `ChannelResolver`, construct it in `MainActivity`, and set `StreamSource.headers` as Media3's default request properties.

- [ ] **Step 4: Run tests and verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*TvChannelTest' --tests '*ChannelResolverTest'`

Expected: PASS.

### Task 3: Verify the app

**Files:**
- Modify: `docs/superpowers/plans/2026-08-15-markiza-channel.md`

- [ ] **Step 1: Run the unit suite**

Run: `./gradlew testDebugUnitTest`

Expected: PASS.

- [ ] **Step 2: Build and install the debug APK**

Run: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: BUILD SUCCESSFUL and APK installed.
