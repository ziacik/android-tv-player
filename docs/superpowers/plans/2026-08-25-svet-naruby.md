# Svet naruby Channel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add the anonymous SWEET.TV `Svet naruby` live channel with fresh-stream recovery.

**Architecture:** A focused `SweetTvResolver` owns the anonymous SWEET.TV API contract and returns the existing `StreamResolution.Playable` type. `ChannelResolver` routes a new provider to it, while `PlayerController` refreshes the temporary stream after a SWEET.TV playback failure.

**Tech Stack:** Kotlin 2.3, Android/Media3, OkHttp, `org.json`, JUnit 4, kotlinx-coroutines-test

**Spec:** `docs/superpowers/specs/2026-08-25-svet-naruby-design.md`

## Global Constraints

- SWEET.TV channel id is exactly `3257`.
- Resolution must use `without_auth=true`; no login, token, or credential storage.
- Only `HTTP_HLS` is accepted.
- Existing provider playback-error behaviour must not change.

---

### Task 1: Anonymous SWEET.TV resolver

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/SweetTvResolver.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/resolver/SweetTvResolverTest.kt`

**Interfaces:**
- Consumes: `FreeviewHttpClient.postJson(url, body, headers)` and `TvChannel.providerValue`.
- Produces: `SweetTvResolver.resolve(channel: TvChannel): StreamResolution`.

- [x] **Step 1: Write failing tests for the anonymous request and valid response**

```kotlin
@Test
fun `requests anonymous HLS stream and returns playable source`() = runTest {
    var requestUrl = ""
    var requestBody = ""
    var requestHeaders = emptyMap<String, String>()
    val http = object : FreeviewHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>): String = error("unused")

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
        ): String {
            requestUrl = url
            requestBody = body
            requestHeaders = headers
            return """{"result":"OK","scheme":"HTTP_HLS","url":"https://cdn.example/live.m3u8"}"""
        }
    }
    val result = SweetTvResolver(http).resolve(TvChannel.SVET_NARUBY) as StreamResolution.Playable
    assertEquals("https://cdn.example/live.m3u8", result.source.url)
    assertEquals("https://api.sweet.tv/TvService/OpenStream.json", requestUrl)
    assertEquals(true, JSONObject(requestBody).getBoolean("without_auth"))
    assertEquals(3257, JSONObject(requestBody).getInt("channel_id"))
    assertEquals("https://sweet.tv", requestHeaders["Origin"])
}
```

- [x] **Step 2: Run the resolver test and confirm it fails because the resolver/catalogue entry do not exist**

Run: `./gradlew testDebugUnitTest --tests '*SweetTvResolverTest'`
Expected: compilation failure for unresolved `SweetTvResolver` and `SVET_NARUBY`.

- [x] **Step 3: Implement the resolver with strict result/scheme/url validation**

```kotlin
class SweetTvResolver(private val http: FreeviewHttpClient) {
    suspend fun resolve(channel: TvChannel): StreamResolution = try {
        val response = JSONObject(http.postJson(API_URL, requestBody(channel), HEADERS))
        if (response.getString("result") != "OK" || response.getString("scheme") != "HTTP_HLS") {
            throw StreamResolveException("SWEET.TV did not return an HLS stream")
        }
        StreamResolution.Playable(
            ProgramMetadata(channel.displayName, null, null, true),
            StreamSource(response.getString("url"), USER_AGENT, HEADERS),
        )
    } catch (error: StreamResolveException) {
        throw error
    } catch (error: Exception) {
        throw StreamResolveException("SWEET.TV request failed", error)
    }
}
```

- [x] **Step 4: Run resolver tests and confirm they pass**

Run: `./gradlew testDebugUnitTest --tests '*SweetTvResolverTest'`
Expected: all resolver tests pass.

### Task 2: Catalogue and provider routing

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/TvChannel.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/ChannelResolver.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/channel/TvChannelTest.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/resolver/ChannelResolverTest.kt`

**Interfaces:**
- Consumes: `SweetTvResolver.resolve(channel)` from Task 1.
- Produces: `TvChannel.SVET_NARUBY` using `ChannelProvider.SWEET_TV` and route lambda `resolveSweetTv`.

- [x] **Step 1: Extend catalogue and routing tests with SWEET.TV expectations**

```kotlin
assertEquals(41, TvChannel.entries.size)
assertEquals(TvChannel.SVET_NARUBY, TvChannel.BBC_FOOD.next())
assertEquals(TvChannel.JEDNOTKA, TvChannel.SVET_NARUBY.next())
resolver.resolve(TvChannel.SVET_NARUBY)
```

- [x] **Step 2: Run targeted tests and confirm provider/catalogue failures**

Run: `./gradlew testDebugUnitTest --tests '*TvChannelTest' --tests '*ChannelResolverTest'`
Expected: compilation or assertion failures until the provider route is implemented.

- [x] **Step 3: Add catalogue entry, provider route, and application wiring**

```kotlin
SVET_NARUBY("svet-naruby", null, "SVET NARUBY", ChannelProvider.SWEET_TV, "3257")
```

Wire `SweetTvResolver(freeviewHttpClient)::resolve` into `ChannelResolver` in
`MainActivity`.

- [x] **Step 4: Run targeted catalogue and routing tests**

Run: `./gradlew testDebugUnitTest --tests '*TvChannelTest' --tests '*ChannelResolverTest'`
Expected: all targeted tests pass.

### Task 3: Refresh a failed temporary stream

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`

**Interfaces:**
- Consumes: `TvChannel.provider == ChannelProvider.SWEET_TV`.
- Produces: a fresh `resolveCurrentChannel()` call after a current SWEET.TV load reports an error.

- [x] **Step 1: Write a failing controller test for a fresh SWEET.TV resolve**

```kotlin
player.registeredListener.onError(player.latestLoadId, "HTTP 403")
advanceUntilIdle()
assertEquals(2, resolveCalls)
assertEquals(2, player.loadCalls.size)
```

- [x] **Step 2: Run the controller test and confirm the terminal-error behaviour fails the new expectation**

Run: `./gradlew testDebugUnitTest --tests '*PlayerControllerTest*refreshes SWEET*'`
Expected: one resolve/load instead of two.

- [x] **Step 3: Re-resolve only current SWEET.TV playback errors**

In `PlayerPort.Listener.onError`, clear the failed load and call
`resolveCurrentChannel()` when `currentChannel.provider == ChannelProvider.SWEET_TV`;
retain the current terminal `PlayerUiState.Error` branch for all other providers.

- [x] **Step 4: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: all unit tests pass.

- [x] **Step 5: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` and a debug APK under `app/build/outputs/apk/debug/`.
