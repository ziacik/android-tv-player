# Dvojka Channel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add STVR Dvojka, switch cyclically with P+/P−, and restore the last selected channel after restart.

**Architecture:** Keep one Media3 player and introduce a two-entry `TvChannel` catalogue. Make resolution and every UI state channel-aware, cancel stale work during channel changes, and persist the selected channel behind a small `ChannelStore` interface.

**Tech Stack:** Kotlin, Android Compose for TV, Media3 ExoPlayer, OkHttp, coroutines and `StateFlow`, SharedPreferences, JUnit, kotlinx-coroutines-test, Compose UI tests, ADB.

**Execution constraint:** Work directly in `/home/ziacik/Workspaces/Personal/android-tv-player` on `main`, as explicitly requested. Do not create a Git worktree.

---

## File map

- Create `app/src/main/java/sk/ziacik/androidtvplayer/channel/TvChannel.kt` — ordered channel catalogue and wraparound navigation.
- Create `app/src/main/java/sk/ziacik/androidtvplayer/channel/ChannelStore.kt` — persistence boundary.
- Create `app/src/main/java/sk/ziacik/androidtvplayer/channel/SharedPreferencesChannelStore.kt` — Android persistence implementation.
- Create `app/src/test/java/sk/ziacik/androidtvplayer/channel/TvChannelTest.kt` — catalogue, navigation, and decoding tests.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrResolver.kt` — channel-specific live URL.
- Modify `app/src/test/java/sk/ziacik/androidtvplayer/resolver/StvrResolverTest.kt` — Jednotka and Dvojka request assertions.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerUiState.kt` — attach a channel to every state.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt` — switch, cancel, retry, and persist channels.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerPort.kt` — stop the old source on channel change.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/player/Media3PlayerPort.kt` — Media3 stop implementation.
- Modify `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt` — channel state, switching, cancellation, and retry tests.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapper.kt` — P+/P− commands.
- Modify `app/src/test/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapperTest.kt` — channel-key mapping tests.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt` — dynamic channel label.
- Modify `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt` — Dvojka label test.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt` — channel-aware restricted panel.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt` — switching commands and channel loading/error presentation.
- Modify `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt` — on-device channel presentation tests.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt` — construct the persisted initial selection.

## Task 1: Channel catalogue and persistence boundary

**Files:**

- Create: `app/src/main/java/sk/ziacik/androidtvplayer/channel/TvChannel.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/channel/ChannelStore.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/channel/SharedPreferencesChannelStore.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/channel/TvChannelTest.kt`

- [ ] **Step 1: Write the failing catalogue tests**

Create `TvChannelTest.kt`:

```kotlin
package sk.ziacik.androidtvplayer.channel

import org.junit.Assert.assertEquals
import org.junit.Test

class TvChannelTest {
    @Test
    fun `catalogue contains Jednotka then Dvojka`() {
        assertEquals(
            listOf(TvChannel.JEDNOTKA, TvChannel.DVOJKA),
            TvChannel.entries,
        )
        assertEquals("1", TvChannel.JEDNOTKA.stvrId)
        assertEquals("JEDNOTKA", TvChannel.JEDNOTKA.displayName)
        assertEquals("2", TvChannel.DVOJKA.stvrId)
        assertEquals("DVOJKA", TvChannel.DVOJKA.displayName)
    }

    @Test
    fun `next and previous wrap through catalogue`() {
        assertEquals(TvChannel.DVOJKA, TvChannel.JEDNOTKA.next())
        assertEquals(TvChannel.JEDNOTKA, TvChannel.DVOJKA.next())
        assertEquals(TvChannel.DVOJKA, TvChannel.JEDNOTKA.previous())
        assertEquals(TvChannel.JEDNOTKA, TvChannel.DVOJKA.previous())
    }

    @Test
    fun `storage keys restore channels and invalid input falls back`() {
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey("jednotka"))
        assertEquals(TvChannel.DVOJKA, TvChannel.fromStorageKey("dvojka"))
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey("obsolete"))
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey(null))
    }
}
```

- [ ] **Step 2: Run the focused test and verify the missing type**

Run:

```bash
./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.channel.TvChannelTest
```

Expected: Kotlin compilation fails because `TvChannel` does not exist.

- [ ] **Step 3: Implement the ordered catalogue**

Create `TvChannel.kt`:

```kotlin
package sk.ziacik.androidtvplayer.channel

enum class TvChannel(
    val storageKey: String,
    val stvrId: String,
    val displayName: String,
) {
    JEDNOTKA("jednotka", "1", "JEDNOTKA"),
    DVOJKA("dvojka", "2", "DVOJKA");

    fun next(): TvChannel = entries[(ordinal + 1) % entries.size]

    fun previous(): TvChannel = entries[(ordinal - 1 + entries.size) % entries.size]

    companion object {
        fun fromStorageKey(key: String?): TvChannel =
            entries.firstOrNull { it.storageKey == key } ?: JEDNOTKA
    }
}
```

- [ ] **Step 4: Add the persistence boundary and Android implementation**

Create `ChannelStore.kt`:

```kotlin
package sk.ziacik.androidtvplayer.channel

interface ChannelStore {
    fun load(): TvChannel
    fun save(channel: TvChannel)
}
```

Create `SharedPreferencesChannelStore.kt`:

```kotlin
package sk.ziacik.androidtvplayer.channel

import android.content.Context

class SharedPreferencesChannelStore(context: Context) : ChannelStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): TvChannel =
        TvChannel.fromStorageKey(preferences.getString(CHANNEL_KEY, null))

    override fun save(channel: TvChannel) {
        preferences.edit().putString(CHANNEL_KEY, channel.storageKey).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "player_preferences"
        const val CHANNEL_KEY = "selected_channel"
    }
}
```

- [ ] **Step 5: Run the focused and full unit suites**

Run:

```bash
./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.channel.TvChannelTest
./gradlew testDebugUnitTest
```

Expected: both commands exit 0.

- [ ] **Step 6: Commit the channel foundation**

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/channel \
  app/src/test/java/sk/ziacik/androidtvplayer/channel
git commit -m "feat: model TV channel catalogue"
```

## Task 2: Resolve Jednotka and Dvojka through the shared STVR pipeline

**Files:**

- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrResolver.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/resolver/StvrResolverTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`

- [ ] **Step 1: Add a failing Dvojka URL test**

Import `TvChannel` and add this test to `StvrResolverTest`:

```kotlin
@Test
fun `Dvojka resolve requests channel two`() = runTest {
    val http = RecordingHttpClient(
        ArrayDeque(
            listOf(
                "landing",
                responseWith("https://cdn.example/dvojka.m3u8"),
            ),
        ),
    )

    val result = StvrResolver(http).resolve(TvChannel.DVOJKA)

    assertEquals(STVR_LANDING_URL, http.calls[0].url)
    assertEquals(
        "https://www.rtvs.sk/json/live5f.json?c=2&ad=1&b=chrome&p=win&v=77&f=0&d=1",
        http.calls[1].url,
    )
    assertEquals(
        "https://cdn.example/dvojka.m3u8",
        (result as StreamResolution.Playable).source.url,
    )
}
```

Change every existing resolver call in that test file to pass `TvChannel.JEDNOTKA`. Replace the fixed URL assertion with `stvrLiveUrl(TvChannel.JEDNOTKA)`.

- [ ] **Step 2: Run the resolver test and verify the missing parameterized API**

Run:

```bash
./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.resolver.StvrResolverTest
```

Expected: compilation fails because `resolve(TvChannel)` and `stvrLiveUrl` do not exist.

- [ ] **Step 3: Parameterize the live request**

Replace the fixed `STVR_LIVE_URL` in `StvrResolver.kt` with:

```kotlin
private const val STVR_LIVE_BASE_URL = "https://www.rtvs.sk/json/live5f.json"

internal fun stvrLiveUrl(channel: TvChannel): String =
    "$STVR_LIVE_BASE_URL?c=${channel.stvrId}&ad=1&b=chrome&p=win&v=77&f=0&d=1"
```

Import `TvChannel`, change the resolver signature to:

```kotlin
suspend fun resolve(channel: TvChannel): StreamResolution
```

and use:

```kotlin
val body = httpClient.get(stvrLiveUrl(channel), headers)
```

All parser, availability, and error paths remain unchanged.

- [ ] **Step 4: Keep application construction compiling until controller switching lands**

Import `TvChannel` in `MainActivity` and temporarily change the controller resolver argument to:

```kotlin
resolve = { resolver.resolve(TvChannel.JEDNOTKA) },
```

This temporary adapter is removed in Task 4 when `PlayerController` accepts a channel-aware resolver.

- [ ] **Step 5: Run resolver and full unit tests**

```bash
./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.resolver.StvrResolverTest
./gradlew testDebugUnitTest
```

Expected: both commands exit 0, and the Dvojka assertion confirms `c=2`.

- [ ] **Step 6: Commit the shared channel resolver**

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrResolver.kt \
  app/src/test/java/sk/ziacik/androidtvplayer/resolver/StvrResolverTest.kt \
  app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt
git commit -m "feat: resolve selected STVR channel"
```

## Task 3: Carry the selected channel through every UI state

**Files:**

- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerUiState.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt`
- Modify: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`

- [ ] **Step 1: Add failing channel-state and label assertions**

In `PlayerControllerTest`, add `initialChannel = TvChannel.JEDNOTKA` to the shared controller helper and direct constructor calls. Add:

```kotlin
@Test
fun `initial and ready states retain selected channel`() = runTest {
    val player = FakePlayerPort()
    val controller = PlayerController(
        scope = this,
        initialChannel = TvChannel.DVOJKA,
        resolve = { playableResolution() },
        playerPort = player,
    )

    assertEquals(TvChannel.DVOJKA, controller.state.value.channel)
    controller.start()
    advanceUntilIdle()
    player.registeredListener.onReady(isPlaying = true)

    assertEquals(
        TvChannel.DVOJKA,
        (controller.state.value as PlayerUiState.Ready).channel,
    )
}
```

In `PlayerOverlayModelTest`, pass `channel = TvChannel.JEDNOTKA` from the `ready` helper and add:

```kotlin
@Test
fun `ready Dvojka state supplies Dvojka label`() {
    val model = PlayerOverlayModel.from(
        ready(
            position = 90_000L,
            duration = 100_000L,
            offset = 10_000L,
            channel = TvChannel.DVOJKA,
        ),
    )

    assertEquals("DVOJKA · NAŽIVO", model.channelLabel)
}
```

Extend the helper signature with `channel: TvChannel = TvChannel.JEDNOTKA` and pass it into `PlayerUiState.Ready`.

- [ ] **Step 2: Run unit tests and verify channel-aware state is missing**

```bash
./gradlew testDebugUnitTest
```

Expected: compilation fails on the missing `initialChannel` argument and `PlayerUiState.channel` fields.

- [ ] **Step 3: Replace the UI state hierarchy with channel-bearing states**

Use this complete structure in `PlayerUiState.kt`:

```kotlin
package sk.ziacik.androidtvplayer.player

import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

sealed interface PlayerUiState {
    val channel: TvChannel

    data class Resolving(override val channel: TvChannel) : PlayerUiState
    data class Preparing(override val channel: TvChannel) : PlayerUiState
    data class Ready(
        override val channel: TvChannel,
        val program: ProgramMetadata,
        val playback: PlaybackSnapshot,
    ) : PlayerUiState
    data class Unavailable(
        override val channel: TvChannel,
        val program: ProgramMetadata,
    ) : PlayerUiState
    data class Error(
        override val channel: TvChannel,
        val message: String,
    ) : PlayerUiState
}
```

- [ ] **Step 4: Thread the initial channel through the controller**

Add this constructor argument to `PlayerController`:

```kotlin
private val initialChannel: TvChannel,
```

Initialize state with `PlayerUiState.Resolving(initialChannel)`. For this task, retries still use the no-argument resolver, but every state transition carries `initialChannel`:

```kotlin
mutableState.value = PlayerUiState.Resolving(initialChannel)
mutableState.value = PlayerUiState.Preparing(initialChannel)
mutableState.value = PlayerUiState.Unavailable(initialChannel, resolution.program)
mutableState.value = PlayerUiState.Error(initialChannel, ERROR_MESSAGE)
```

Build ready state with:

```kotlin
PlayerUiState.Ready(
    channel = initialChannel,
    program = program,
    playback = playerPort.snapshot().copy(isPlaying = isPlaying),
)
```

- [ ] **Step 5: Make the overlay label dynamic and update UI state matches**

In `PlayerOverlayModel.from`, replace the fixed label with:

```kotlin
channelLabel = "${state.channel.displayName} · NAŽIVO",
```

In `PlayerScreen`, update state matches to use type checks for the new data classes:

```kotlin
is PlayerUiState.Resolving,
is PlayerUiState.Preparing,
```

Update all unit and Compose test state construction with explicit `channel = TvChannel.JEDNOTKA` except the new Dvojka-specific assertion. Keep presentation otherwise unchanged in this task.

- [ ] **Step 6: Pass Jednotka from `MainActivity` and run all tests**

Add:

```kotlin
initialChannel = TvChannel.JEDNOTKA,
```

to controller construction. Run:

```bash
./gradlew testDebugUnitTest
ANDROID_SERIAL=192.168.0.200:5555 ./gradlew connectedDebugAndroidTest
```

Expected: all unit tests and the existing four Compose tests pass.

- [ ] **Step 7: Commit channel-aware player state**

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/player \
  app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt \
  app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt \
  app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt \
  app/src/test/java/sk/ziacik/androidtvplayer/player \
  app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt \
  app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt
git commit -m "feat: attach channels to player state"
```

## Task 4: Switch safely, cancel stale work, and persist the selection

**Files:**

- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerPort.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/Media3PlayerPort.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`

- [ ] **Step 1: Add failing switch, persistence, and stale-result tests**

Change controller test resolver lambdas to accept a `TvChannel`. Add `stopCalls` to `FakePlayerPort` and implement its future `stop()` by incrementing it. Add:

```kotlin
@Test
fun `channel up stops old source loads Dvojka and persists it`() = runTest {
    val resolvedChannels = mutableListOf<TvChannel>()
    val savedChannels = mutableListOf<TvChannel>()
    val player = FakePlayerPort()
    val controller = PlayerController(
        scope = this,
        initialChannel = TvChannel.JEDNOTKA,
        resolve = { channel ->
            resolvedChannels += channel
            playableResolution(channel.storageKey)
        },
        playerPort = player,
        onChannelSelected = savedChannels::add,
    )

    controller.start()
    advanceUntilIdle()
    controller.channelUp()
    advanceUntilIdle()

    assertEquals(listOf(TvChannel.JEDNOTKA, TvChannel.DVOJKA), resolvedChannels)
    assertEquals(listOf(TvChannel.DVOJKA), savedChannels)
    assertEquals(1, player.stopCalls)
    assertEquals(TvChannel.DVOJKA, controller.state.value.channel)
    assertEquals("https://cdn.example/dvojka.m3u8", player.loadedSources.last().url)
}

@Test
fun `channel down wraps from Jednotka to Dvojka`() = runTest {
    val controller = controller(FakePlayerPort(), this)

    controller.channelDown()
    advanceUntilIdle()

    assertEquals(TvChannel.DVOJKA, controller.state.value.channel)
}

@Test
fun `stale cancelled resolve cannot replace latest channel`() = runTest {
    val jednotka = CompletableDeferred<StreamResolution>()
    val dvojka = CompletableDeferred<StreamResolution>()
    val player = FakePlayerPort()
    val controller = PlayerController(
        scope = this,
        initialChannel = TvChannel.JEDNOTKA,
        resolve = { channel ->
            if (channel == TvChannel.JEDNOTKA) jednotka.await() else dvojka.await()
        },
        playerPort = player,
    )

    controller.start()
    runCurrent()
    controller.channelUp()
    runCurrent()
    dvojka.complete(playableResolution("dvojka"))
    advanceUntilIdle()
    jednotka.complete(playableResolution("jednotka"))
    advanceUntilIdle()

    assertEquals(TvChannel.DVOJKA, controller.state.value.channel)
    assertEquals(
        listOf("https://cdn.example/dvojka.m3u8"),
        player.loadedSources.map(StreamSource::url),
    )
}

@Test
fun `old player callbacks cannot overwrite a channel being resolved`() = runTest {
    val dvojka = CompletableDeferred<StreamResolution>()
    val diagnostics = mutableListOf<String>()
    val player = FakePlayerPort()
    val controller = PlayerController(
        scope = this,
        initialChannel = TvChannel.JEDNOTKA,
        resolve = { channel ->
            if (channel == TvChannel.JEDNOTKA) {
                playableResolution("jednotka")
            } else {
                dvojka.await()
            }
        },
        playerPort = player,
        diagnostics = { message, _ -> diagnostics += message },
    )

    controller.start()
    advanceUntilIdle()
    player.registeredListener.onReady(isPlaying = true)
    controller.channelUp()
    runCurrent()
    player.registeredListener.onReady(isPlaying = true)
    player.registeredListener.onError("OLD_SOURCE_ERROR")

    assertEquals(PlayerUiState.Resolving(TvChannel.DVOJKA), controller.state.value)
    assertTrue(diagnostics.isEmpty())
    dvojka.complete(playableResolution("dvojka"))
    advanceUntilIdle()
}

@Test
fun `switching cancels restricted retry for old channel`() = runTest {
    val calls = mutableListOf<TvChannel>()
    val controller = PlayerController(
        scope = this,
        initialChannel = TvChannel.JEDNOTKA,
        resolve = { channel ->
            calls += channel
            if (channel == TvChannel.JEDNOTKA) {
                StreamResolution.Unavailable(PROGRAM.copy(endsAtMs = null))
            } else {
                playableResolution("dvojka")
            }
        },
        playerPort = FakePlayerPort(),
    )

    controller.start()
    runCurrent()
    controller.channelUp()
    runCurrent()
    advanceTimeBy(60_000L)
    runCurrent()

    assertEquals(listOf(TvChannel.JEDNOTKA, TvChannel.DVOJKA), calls)
}
```

Import `CompletableDeferred`. Change `playableResolution` to accept a suffix and generate `https://cdn.example/$suffix.m3u8`.

- [ ] **Step 2: Run controller tests and verify switching APIs are missing**

```bash
./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.player.PlayerControllerTest
```

Expected: compilation fails on the channel-aware resolver, channel switching methods, and `PlayerPort.stop()`.

- [ ] **Step 3: Add stop support to the player boundary**

Add to `PlayerPort`:

```kotlin
fun stop()
```

Implement in `Media3PlayerPort`:

```kotlin
override fun stop() {
    player.stop()
    player.clearMediaItems()
}
```

- [ ] **Step 4: Implement channel-aware resolution and guarded switching**

Change controller arguments to:

```kotlin
private val initialChannel: TvChannel,
private val resolve: suspend (TvChannel) -> StreamResolution,
private val playerPort: PlayerPort,
private val nowMs: () -> Long = System::currentTimeMillis,
private val onChannelSelected: (TvChannel) -> Unit = {},
private val diagnostics: (message: String, cause: Throwable?) -> Unit = { _, _ -> },
```

Add fields:

```kotlin
private var currentChannel = initialChannel
private var activePlaybackChannel: TvChannel? = null
private var resolveGeneration = 0L
```

Expose switching:

```kotlin
fun channelUp() = switchTo(currentChannel.next())

fun channelDown() = switchTo(currentChannel.previous())

private fun switchTo(channel: TvChannel) {
    if (channel == currentChannel) return
    currentChannel = channel
    onChannelSelected(channel)
    activeProgram = null
    activePlaybackChannel = null
    playerPort.stop()
    resolveCurrentChannel()
}
```

Make `start()` and `retry()` call `resolveCurrentChannel()`. Its complete control flow is:

```kotlin
private fun resolveCurrentChannel() {
    resolveJob?.cancel()
    restrictedRetryJob?.cancel()
    restrictedRetryJob = null
    val channel = currentChannel
    val generation = ++resolveGeneration
    resolveJob = scope.launch {
        mutableState.value = PlayerUiState.Resolving(channel)
        try {
            val resolution = resolve(channel)
            if (generation != resolveGeneration || channel != currentChannel) return@launch
            applyResolution(channel, resolution)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation != resolveGeneration || channel != currentChannel) return@launch
            diagnostics("STVR resolve failed", error)
            mutableState.value = PlayerUiState.Error(channel, ERROR_MESSAGE)
        }
    }
}
```

Change `applyResolution` to accept `channel`. On playable resolution, set both `activeProgram` and `activePlaybackChannel`, emit `Preparing(channel)`, and load. On unavailable resolution, clear both, emit `Unavailable(channel, program)`, and schedule the retry. The scheduled retry captures the channel and only calls `retry()` when it still equals `currentChannel`.

In player callbacks, ignore ready, playing, and error events unless `activePlaybackChannel == currentChannel`. Build ready and error states with `currentChannel`. Clear `activePlaybackChannel` before emitting a player error.

- [ ] **Step 5: Construct persistence and the channel-aware resolver**

In `MainActivity`, create:

```kotlin
val channelStore = SharedPreferencesChannelStore(this)
```

Construct the controller with:

```kotlin
initialChannel = channelStore.load(),
resolve = resolver::resolve,
onChannelSelected = channelStore::save,
```

Remove the temporary hard-coded Jednotka resolver lambda.

- [ ] **Step 6: Run controller and full unit suites**

```bash
./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.player.PlayerControllerTest
./gradlew testDebugUnitTest
```

Expected: both commands exit 0. The switching test records exactly one stop, and the stale-result test loads only Dvojka.

- [ ] **Step 7: Commit safe channel switching**

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/player \
  app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt \
  app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt
git commit -m "feat: switch and remember TV channels"
```

## Task 5: Map P+/P− and present channel-aware loading and restriction states

**Files:**

- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapper.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapperTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlay.kt`
- Modify: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`

- [ ] **Step 1: Add failing channel-key mapping tests**

Add to `RemoteCommandMapperTest`:

```kotlin
@Test
fun `channel keys switch regardless of overlay visibility`() {
    assertEquals(
        RemoteCommand.ChannelUp,
        mapper.map(KeyEvent.KEYCODE_CHANNEL_UP, false, FocusedControl.PLAY_PAUSE),
    )
    assertEquals(
        RemoteCommand.ChannelUp,
        mapper.map(KeyEvent.KEYCODE_CHANNEL_UP, true, FocusedControl.LIVE),
    )
    assertEquals(
        RemoteCommand.ChannelDown,
        mapper.map(KeyEvent.KEYCODE_CHANNEL_DOWN, false, FocusedControl.PLAY_PAUSE),
    )
    assertEquals(
        RemoteCommand.ChannelDown,
        mapper.map(KeyEvent.KEYCODE_CHANNEL_DOWN, true, FocusedControl.LIVE),
    )
}
```

- [ ] **Step 2: Add failing Compose state assertions**

Import `sk.ziacik.androidtvplayer.channel.TvChannel` and add these tests to `PlayerOverlayTest`:

```kotlin
@Test
fun readyDvojkaShowsDvojkaInCinematicOverlay() {
    compose.setContent {
        AndroidTvPlayerTheme {
            PlayerStateLayer(
                state = PlayerUiState.Ready(
                    channel = TvChannel.DVOJKA,
                    program = ProgramMetadata("Večerný program", null, null, true),
                    playback = PlaybackSnapshot(
                        90_000L,
                        100_000L,
                        10_000L,
                        true,
                        true,
                    ),
                ),
                overlayVisible = true,
                focusedControl = FocusedControl.PLAY_PAUSE,
                formatTime = { "12:54" },
            )
        }
    }

    compose.onNodeWithText("DVOJKA · NAŽIVO").assertIsDisplayed()
    compose.onNodeWithText("Večerný program").assertIsDisplayed()
}

@Test
fun resolvingDvojkaShowsImmediateChannelFeedback() {
    compose.setContent {
        AndroidTvPlayerTheme {
            PlayerStateLayer(
                state = PlayerUiState.Resolving(TvChannel.DVOJKA),
                overlayVisible = false,
                focusedControl = FocusedControl.PLAY_PAUSE,
                formatTime = { "12:54" },
            )
        }
    }

    compose.onNodeWithText("DVOJKA · NAČÍTAVAM").assertIsDisplayed()
}

@Test
fun unavailableDvojkaIdentifiesChannel() {
    compose.setContent {
        AndroidTvPlayerTheme {
            PlayerStateLayer(
                state = PlayerUiState.Unavailable(
                    channel = TvChannel.DVOJKA,
                    program = ProgramMetadata(
                        "V tieni vlkov: Boj v éteri",
                        null,
                        20_000L,
                        false,
                    ),
                ),
                overlayVisible = false,
                focusedControl = FocusedControl.PLAY_PAUSE,
                formatTime = { "17:15" },
            )
        }
    }

    compose.onNodeWithText("DVOJKA").assertIsDisplayed()
    compose.onNodeWithText("Tento program nie je dostupný online").assertIsDisplayed()
    compose.onNodeWithText("Vysielanie skúsime obnoviť o 17:15").assertIsDisplayed()
}
```

Also change the direct `RestrictedProgramPanel` test call to pass `channelLabel = "JEDNOTKA"`.

- [ ] **Step 3: Run focused tests and verify the UI and commands are missing**

```bash
./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.RemoteCommandMapperTest
ANDROID_SERIAL=192.168.0.200:5555 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=sk.ziacik.androidtvplayer.ui.PlayerOverlayTest
```

Expected: unit compilation fails on `ChannelUp` and `ChannelDown`; Compose compilation fails on the missing channel-aware presentation.

- [ ] **Step 4: Add channel commands to the remote mapper**

Add to `RemoteCommand`:

```kotlin
data object ChannelUp : RemoteCommand
data object ChannelDown : RemoteCommand
```

Add these cases before D-pad handling:

```kotlin
KeyEvent.KEYCODE_CHANNEL_UP -> RemoteCommand.ChannelUp
KeyEvent.KEYCODE_CHANNEL_DOWN -> RemoteCommand.ChannelDown
```

- [ ] **Step 5: Wire the commands in `PlayerScreen`**

Add command branches:

```kotlin
RemoteCommand.ChannelUp -> {
    controller.channelUp()
    overlayController.show()
}
RemoteCommand.ChannelDown -> {
    controller.channelDown()
    overlayController.show()
}
```

Keep these branches inside the existing exhaustive `when`; they apply from ready, resolving, unavailable, and error states.

- [ ] **Step 6: Render channel-aware loading, restriction, and error panels**

Replace the resolving/preparing spinner branch in `PlayerStateLayer` with:

```kotlin
is PlayerUiState.Resolving,
is PlayerUiState.Preparing,
-> LoadingChannelPanel(
    channelLabel = current.channel.displayName,
    modifier = modifier,
)
```

Add this composable to `PlayerScreen.kt`:

```kotlin
@Composable
private fun LoadingChannelPanel(
    channelLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = "$channelLabel · NAČÍTAVAM",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
```

Add `channelLabel: String` to `RestrictedProgramPanel` and render it above the red status dot:

```kotlin
Text(
    text = channelLabel,
    color = Color.White.copy(alpha = 0.68f),
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.4.sp,
)
```

Pass `current.channel.displayName` from the unavailable branch. Add `channelLabel` to `ErrorPanel`, render it with the same typography, and pass the current error channel. Do not change the retry key behavior or Slovak messages.

- [ ] **Step 7: Run all automated checks**

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
ANDROID_SERIAL=192.168.0.200:5555 ./gradlew connectedDebugAndroidTest
```

Expected: every command exits 0, with seven Compose tests and no lint errors.

- [ ] **Step 8: Commit remote and presentation work**

```bash
git add app/src/main/java/sk/ziacik/androidtvplayer/ui \
  app/src/test/java/sk/ziacik/androidtvplayer/ui \
  app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt
git commit -m "feat: switch channels with TV remote"
```

## Task 6: Clean build, Philips installation, and visual acceptance

**Files:**

- Verify: `app/build/outputs/apk/debug/app-debug.apk`
- Create outside repository: `/home/ziacik/Documents/Codex/2026-08-14/referenced-chatgpt-conversation-this-is-an/outputs/dvojka-channel.png`

- [ ] **Step 1: Run the complete verification from a clean build**

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
ANDROID_SERIAL=192.168.0.200:5555 ./gradlew connectedDebugAndroidTest
```

Expected: both commands exit 0; unit tests, lint, APK assembly, and all connected Compose tests succeed.

- [ ] **Step 2: Install and cold-launch the verified APK**

```bash
ADB_BIN=/home/ziacik/Android/Sdk/platform-tools/adb
TV_SERIAL=192.168.0.200:5555
"$ADB_BIN" -s "$TV_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB_BIN" -s "$TV_SERIAL" shell am force-stop sk.ziacik.androidtvplayer
"$ADB_BIN" -s "$TV_SERIAL" shell am start -W \
  -n sk.ziacik.androidtvplayer/.MainActivity
```

Expected: install reports `Success`, launch status is `ok`, and the activity is resumed.

- [ ] **Step 3: Exercise both physical channel keys**

Clear logs, send channel-up, wait for resolution, inspect the screen, then send channel-down and repeat:

```bash
"$ADB_BIN" -s "$TV_SERIAL" logcat -c
"$ADB_BIN" -s "$TV_SERIAL" shell input keyevent KEYCODE_CHANNEL_UP
sleep 3
"$ADB_BIN" -s "$TV_SERIAL" shell input keyevent KEYCODE_CHANNEL_DOWN
sleep 3
"$ADB_BIN" -s "$TV_SERIAL" shell input keyevent KEYCODE_CHANNEL_UP
sleep 3
```

Expected: labels cycle Jednotka → Dvojka → Jednotka → Dvojka; the app remains in the foreground. If the current Dvojka programme has `internet: N`, the native restricted panel is the expected Dvojka result.

- [ ] **Step 4: Verify persistence across a cold restart**

With Dvojka selected:

```bash
"$ADB_BIN" -s "$TV_SERIAL" shell am force-stop sk.ziacik.androidtvplayer
"$ADB_BIN" -s "$TV_SERIAL" shell am start -W \
  -n sk.ziacik.androidtvplayer/.MainActivity
sleep 3
```

Expected: the app starts on Dvojka without first resolving Jednotka.

- [ ] **Step 5: Capture and inspect Dvojka presentation**

```bash
"$ADB_BIN" -s "$TV_SERIAL" shell input keyevent 23
sleep 1
"$ADB_BIN" -s "$TV_SERIAL" shell screencap -p /sdcard/dvojka-channel.png
"$ADB_BIN" -s "$TV_SERIAL" pull /sdcard/dvojka-channel.png \
  /home/ziacik/Documents/Codex/2026-08-14/referenced-chatgpt-conversation-this-is-an/outputs/dvojka-channel.png
"$ADB_BIN" -s "$TV_SERIAL" shell rm /sdcard/dvojka-channel.png
```

Inspect the local image at original resolution. Confirm safe TV margins, readable Dvojka identity, correct programme or restriction text, and no overlap or clipping.

- [ ] **Step 6: Check foreground state, crash logs, and repository cleanliness**

```bash
"$ADB_BIN" -s "$TV_SERIAL" shell dumpsys activity activities | \
  rg 'mResumedActivity|sk.ziacik.androidtvplayer'
"$ADB_BIN" -s "$TV_SERIAL" logcat -d -t 500 | \
  rg -i 'AndroidTvPlayer|ExoPlayer|FATAL EXCEPTION'
git status --short
```

Expected: `MainActivity` is resumed, no app fatal exception is present, and `git status --short` is empty.

- [ ] **Step 7: Apply the completion procedures**

Use `superpowers:verification-before-completion` before reporting success. Then use `superpowers:finishing-a-development-branch`; because this work is intentionally performed directly on `main` without a worktree, preserve `main` and do not create or remove a worktree.
