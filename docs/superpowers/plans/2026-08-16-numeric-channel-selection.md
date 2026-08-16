# Priamy výber kanála číslicami Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepínať prehrávač na kanál s poradovým číslom zadaným číslicami na TV ovládači, vrátane viacciferných čísel.

**Architecture:** `RemoteCommandMapper` preloží numerické klávesy na príkaz s číslicou. `NumericChannelInput` bude vlastniť rozpracované číslo a 1-sekundový timeout, po ktorom vyberie kanál cez nový verejný vstup `PlayerController.selectChannel`. `PlayerScreen` objekt pripojí k príkazom ovládača a ukáže jeho aktuálnu hodnotu v samostatnej vrstve nad prehrávačom.

**Tech Stack:** Kotlin, Kotlin coroutines/StateFlow, Jetpack Compose, JUnit 4, kotlinx-coroutines-test.

---

## File structure

- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/TvChannel.kt` — bezpečný prevod 1-založeného poradového čísla na kanál.
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt` — verejné priame prepnutie, ktoré používa existujúci `switchTo`.
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapper.kt` — numerický príkaz a dekódovanie Android keycodes.
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/ui/NumericChannelInput.kt` — timeout, rozpracované číslo a validácia výberu.
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt` — napojenie vstupu a indikátor nad prehrávačom.
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/channel/TvChannelTest.kt` — test výberu podľa poradia.
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt` — test priameho prepnutia.
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapperTest.kt` — test mapovania číslic.
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/ui/NumericChannelInputTest.kt` — virtuálny čas pre zloženie, reset timeoutu, neplatný a zrušený vstup.
- Modify: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt` — render test indikátora čísla.

### Task 1: Map a catalogue position and switch to it

**Files:**

- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/TvChannel.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/channel/TvChannelTest.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`

- [ ] **Step 1: Write failing catalogue and controller tests**

  Add the following tests. Put the controller test beside the existing channel-up/down tests and reuse its `FakePlayerPort` and `playableResolution` helpers.

  ```kotlin
  @Test
  fun `catalogue position is one based and rejects unavailable positions`() {
      assertEquals(TvChannel.JEDNOTKA, TvChannel.fromChannelNumber(1))
      assertEquals(TvChannel.DVOJKA, TvChannel.fromChannelNumber(2))
      assertEquals(TvChannel.entries[11], TvChannel.fromChannelNumber(12))
      assertEquals(null, TvChannel.fromChannelNumber(0))
      assertEquals(null, TvChannel.fromChannelNumber(TvChannel.entries.size + 1))
  }
  ```

  ```kotlin
  @Test
  fun `direct selection stops old source loads requested channel and persists it`() = runTest {
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
      controller.selectChannel(TvChannel.entries[11])
      advanceUntilIdle()

      assertEquals(listOf(TvChannel.JEDNOTKA, TvChannel.entries[11]), resolvedChannels)
      assertEquals(listOf(TvChannel.entries[11]), savedChannels)
      assertEquals(1, player.stopCalls)
      assertEquals(TvChannel.entries[11], controller.state.value.channel)
  }
  ```

- [ ] **Step 2: Run the new tests and verify the expected red state**

  Run:

  ```bash
  ./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.channel.TvChannelTest --tests sk.ziacik.androidtvplayer.player.PlayerControllerTest
  ```

  Expected: compilation fails because `fromChannelNumber` and `selectChannel` do not exist.

- [ ] **Step 3: Implement the smallest public APIs**

  Add this companion function to `TvChannel`; do not make an invalid channel wrap around.

  ```kotlin
  fun fromChannelNumber(number: Int): TvChannel? =
      entries.getOrNull(number - 1)
  ```

  Add this method next to `channelUp` and `channelDown` in `PlayerController`:

  ```kotlin
  fun selectChannel(channel: TvChannel) = switchTo(channel)
  ```

  This deliberately reuses `switchTo`, preserving persistence, cancellation, player stop and resolver state transitions.

- [ ] **Step 4: Run the focused unit tests and verify green**

  Run the command from Step 2.

  Expected: both test classes pass.

### Task 2: Decode the numeric remote keys

**Files:**

- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapper.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapperTest.kt`

- [ ] **Step 1: Write the failing mapper test**

  Add this test. It covers the standard Android TV number keys and numpad fallback keys.

  ```kotlin
  @Test
  fun `numeric keys map to their decimal digit`() {
      (0..9).forEach { digit ->
          assertEquals(
              RemoteCommand.NumericDigit(digit),
              mapper.map(KeyEvent.KEYCODE_0 + digit, false, FocusedControl.PLAY_PAUSE),
          )
          assertEquals(
              RemoteCommand.NumericDigit(digit),
              mapper.map(KeyEvent.KEYCODE_NUMPAD_0 + digit, true, FocusedControl.LIVE),
          )
      }
  }
  ```

- [ ] **Step 2: Run the mapper test and verify red**

  Run:

  ```bash
  ./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.RemoteCommandMapperTest
  ```

  Expected: compilation fails because `NumericDigit` does not exist.

- [ ] **Step 3: Add the command and keycode decoding**

  Add a numeric data command to `RemoteCommand`:

  ```kotlin
  data class NumericDigit(val digit: Int) : RemoteCommand
  ```

  Before the existing `when (keyCode)` in `map`, return a numeric command when possible:

  ```kotlin
  keyCode.toNumericDigit()?.let(RemoteCommand::NumericDigit)?.let { return it }
  ```

  Add this private extension in the same file:

  ```kotlin
  private fun Int.toNumericDigit(): Int? = when (this) {
      in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> this - KeyEvent.KEYCODE_0
      in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> this - KeyEvent.KEYCODE_NUMPAD_0
      else -> null
  }
  ```

- [ ] **Step 4: Run the mapper test and verify green**

  Run the command from Step 2.

  Expected: `RemoteCommandMapperTest` passes, including existing non-numeric mappings.

### Task 3: Buffer digits and select once the timeout expires

**Files:**

- Create: `app/src/main/java/sk/ziacik/androidtvplayer/ui/NumericChannelInput.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/ui/NumericChannelInputTest.kt`

- [ ] **Step 1: Write failing timeout tests**

  Create `NumericChannelInputTest.kt` with these coroutine tests. Use `runTest`, `runCurrent` and `advanceTimeBy` from `kotlinx.coroutines.test`.

  ```kotlin
  @Test
  fun `two digits select one based catalogue channel after the final timeout`() = runTest {
      val selected = mutableListOf<TvChannel>()
      val input = NumericChannelInput(this, 1_000L, selected::add)

      input.append(1)
      advanceTimeBy(999L)
      input.append(2)
      advanceTimeBy(999L)
      runCurrent()
      assertEquals("12", input.digits.value)
      assertTrue(selected.isEmpty())

      advanceTimeBy(1L)
      runCurrent()
      assertEquals(null, input.digits.value)
      assertEquals(listOf(TvChannel.entries[11]), selected)
  }

  @Test
  fun `zero invalid number and disposal do not select a channel`() = runTest {
      val selected = mutableListOf<TvChannel>()
      val input = NumericChannelInput(this, 1_000L, selected::add)

      input.append(0)
      advanceTimeBy(1_000L)
      runCurrent()
      input.append(1)
      input.cancel()
      advanceTimeBy(1_000L)
      runCurrent()

      assertEquals(null, input.digits.value)
      assertTrue(selected.isEmpty())
  }
  ```

- [ ] **Step 2: Run the input tests and verify red**

  Run:

  ```bash
  ./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.NumericChannelInputTest
  ```

  Expected: compilation fails because `NumericChannelInput` does not exist.

- [ ] **Step 3: Implement the input owner**

  Create `NumericChannelInput.kt` with the following behavior. It owns only numeric input and timing; resolution and playback remain in `PlayerController`.

  ```kotlin
  class NumericChannelInput(
      private val scope: CoroutineScope,
      private val selectionDelayMs: Long = 1_000L,
      private val onChannelSelected: (TvChannel) -> Unit,
  ) {
      private val mutableDigits = MutableStateFlow<String?>(null)
      val digits: StateFlow<String?> = mutableDigits.asStateFlow()
      private var selectionJob: Job? = null

      fun append(digit: Int) {
          require(digit in 0..9)
          mutableDigits.value = "${mutableDigits.value.orEmpty()}$digit"
          selectionJob?.cancel()
          selectionJob = scope.launch {
              delay(selectionDelayMs)
              val completedDigits = mutableDigits.value
              mutableDigits.value = null
              completedDigits
                  ?.toIntOrNull()
                  ?.let(TvChannel::fromChannelNumber)
                  ?.let(onChannelSelected)
          }
      }

      fun cancel() {
          selectionJob?.cancel()
          selectionJob = null
          mutableDigits.value = null
      }
  }
  ```

  Import `CoroutineScope`, `Job`, `delay`, `MutableStateFlow`, `StateFlow`, `asStateFlow`, `launch`, and `TvChannel`.

- [ ] **Step 4: Run the input tests and verify green**

  Run the command from Step 2.

  Expected: the final digit resets the timer, `12` selects the twelfth entry, and invalid/cancelled input selects nothing.

### Task 4: Connect input to the player screen and render it

**Files:**

- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt`
- Modify: `app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt`

- [ ] **Step 1: Write the failing visual test**

  Add a test for a small, independently renderable indicator composable. This keeps the visual assertion separate from Media3 setup.

  ```kotlin
  @Test
  fun numericChannelIndicatorShowsPendingDigits() {
      compose.setContent {
          AndroidTvPlayerTheme {
              NumericChannelIndicator(digits = "12")
          }
      }

      compose.onNodeWithText("12").assertIsDisplayed()
      compose.onNodeWithTag("numeric-channel-indicator").assertIsDisplayed()
  }
  ```

- [ ] **Step 2: Run the instrumented test and verify red**

  Run on an available emulator/device:

  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

  Expected: compilation fails because `NumericChannelIndicator` does not exist. If no Android test device is available, record this as not run and proceed with the unit-test/build verification in Task 5.

- [ ] **Step 3: Wire the controller into `PlayerScreen` and add the indicator**

  In `PlayerScreen`, create the input with the Compose lifecycle scope and collect its state:

  ```kotlin
  val numericInputScope = rememberCoroutineScope()
  val numericInput = remember(controller, numericInputScope) {
      NumericChannelInput(
          scope = numericInputScope,
          onChannelSelected = controller::selectChannel,
      )
  }
  val numericDigits by numericInput.digits.collectAsState()
  DisposableEffect(numericInput) {
      onDispose(numericInput::cancel)
  }
  ```

  Add imports for `DisposableEffect`, `rememberCoroutineScope`, `Alignment.TopEnd`, `padding`, `testTag`, plus any layout or styling APIs used below. In the `when` over mapped commands, handle the new branch before `Ignore`:

  ```kotlin
  is RemoteCommand.NumericDigit -> numericInput.append(command.digit)
  ```

  Bind the `when` result to `val command` so `command.digit` is available. Keep numeric input before the retry-center handling only if a numeric command must work while a retryable error is visible; retain the existing credentials guard so credential form text entry is not intercepted.

  In the root `Box`, after `PlayerStateLayer`, add:

  ```kotlin
  numericDigits?.let { digits ->
      NumericChannelIndicator(
          digits = digits,
          modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(36.dp),
      )
  }
  ```

  Define `NumericChannelIndicator` as an `internal @Composable` in the same file. It should be a compact `Text` with `testTag("numeric-channel-indicator")`, white bold content, and a translucent rounded black background. It must not request focus or alter the existing transport overlay.

- [ ] **Step 4: Run the visual test and verify green when a device is available**

  Run the command from Step 2.

  Expected: `12` is visible in `numeric-channel-indicator`.

### Task 5: Run regression checks and inspect the change

**Files:**

- Verify only; no additional source files.

- [ ] **Step 1: Run all local unit tests**

  Run:

  ```bash
  ./gradlew testDebugUnitTest
  ```

  Expected: `BUILD SUCCESSFUL` and all unit tests pass.

- [ ] **Step 2: Build the debug APK**

  Run:

  ```bash
  ./gradlew assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Inspect the scoped diff**

  Run:

  ```bash
  git diff -- app/src/main/java/sk/ziacik/androidtvplayer/channel/TvChannel.kt app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt app/src/main/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapper.kt app/src/main/java/sk/ziacik/androidtvplayer/ui/NumericChannelInput.kt app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt app/src/test/java/sk/ziacik/androidtvplayer/channel/TvChannelTest.kt app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt app/src/test/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapperTest.kt app/src/test/java/sk/ziacik/androidtvplayer/ui/NumericChannelInputTest.kt app/src/androidTest/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayTest.kt
  ```

  Expected: only direct numeric selection, its tests, and its indicator are included. Do not stage or commit: the user has not authorized either action.
