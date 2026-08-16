# Markiza Session and Stream Errors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep a valid Markiza session across channel changes and show a safe, actionable stream failure reason on TV.

**Architecture:** `MarkizaResolver` records a successful login for the lifetime of its HTTP client, so a later selection fetches the live page directly. `PlayerController` carries a sanitized resolver or Media3 reason in `PlayerUiState.Error`; Compose renders it below the existing generic failure text.

**Tech Stack:** Kotlin, coroutines, JUnit 4, Jetpack Compose, Media3.

---

### Task 1: Reuse an authenticated Markiza session

**Files:**
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/resolver/MarkizaResolverTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/MarkizaResolver.kt`

- [ ] **Step 1: Write a failing resolver test**

```kotlin
val resolver = MarkizaResolver(http) { MarkizaCredentials("user@example.com", "secret") }
resolver.resolve(TvChannel.MARKIZA)
resolver.resolve(TvChannel.MARKIZA)
assertEquals(6, http.calls.size)
assertEquals(HttpCall.Get(MARKIZA_LIVE_URL, liveHeaders), http.calls[4])
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*MarkizaResolverTest'`

Expected: the second resolution requests the login page or exhausts the fake responses.

- [ ] **Step 3: Implement the minimal session state**

```kotlin
private var authenticated = false

private suspend fun ensureAuthenticated(...) {
    if (authenticated) return
    // Existing login-page GET and form POST.
    authenticated = true
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `./gradlew testDebugUnitTest --tests '*MarkizaResolverTest'`

Expected: PASS.

### Task 2: Surface a sanitized reason for resolver and playback failures

**Files:**
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/Media3PlayerPort.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt`

- [ ] **Step 1: Write failing controller tests**

```kotlin
assertEquals(
    PlayerUiState.Error(TvChannel.JEDNOTKA, "Stream sa nepodarilo načítať", "Sieťové pripojenie nie je dostupné"),
    controller.state.value,
)
```

```kotlin
player.registeredListener.onError(player.latestLoadId, "HTTP 403")
assertEquals("Server stream odmietol (HTTP 403)", error.reason)
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*PlayerControllerTest'`

Expected: compilation failure because `PlayerUiState.Error` has no `reason`.

- [ ] **Step 3: Implement only safe reason mapping**

```kotlin
data class Error(..., val reason: String)
```

Map `StreamResolveException` and `IOException` to static messages. Include a Media3 HTTP response code, but never a stream URL, token, credentials, or raw exception message.

- [ ] **Step 4: Render the reason below the generic error title**

```kotlin
Text(text = reason, color = Color.White.copy(alpha = 0.72f), fontSize = 16.sp)
```

- [ ] **Step 5: Run focused tests and the build**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`.
