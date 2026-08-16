# Markiza Credentials Channel Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let CH+/CH- and numeric remote input select another channel while the Markiza credentials panel is shown.

**Architecture:** Keep `CredentialsRequired` and its login form unchanged. Give `RemoteCommand` one explicit predicate for commands that select a channel; `PlayerScreen` maps the key first and suppresses only non-selection commands in credentials-required state. The existing `NumericChannelInput` and `PlayerController` perform selection, so switching automatically replaces the credentials state with normal resolution.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 4, Gradle Android plugin.

---

### Task 1: Specify commands that remain active during credential entry

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapper.kt:10-57`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapperTest.kt:7-92`

- [x] **Step 1: Write the failing test**

Add to `RemoteCommandMapperTest`:

```kotlin
@Test
fun `only channel selection commands are enabled while credentials are required`() {
    assertTrue(RemoteCommand.ChannelUp.isChannelSelection())
    assertTrue(RemoteCommand.ChannelDown.isChannelSelection())
    assertTrue(RemoteCommand.NumericDigit(2).isChannelSelection())
    assertFalse(RemoteCommand.TogglePlayback.isChannelSelection())
    assertFalse(RemoteCommand.Exit.isChannelSelection())
}
```

Add these imports:

```kotlin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
```

- [x] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.RemoteCommandMapperTest
```

Expected: compilation fails because `isChannelSelection` does not exist.

- [x] **Step 3: Add the minimal command predicate**

Add below the `RemoteCommand` declaration in `RemoteCommandMapper.kt`:

```kotlin
fun RemoteCommand.isChannelSelection(): Boolean =
    this is RemoteCommand.NumericDigit ||
        this == RemoteCommand.ChannelUp ||
        this == RemoteCommand.ChannelDown
```

- [x] **Step 4: Run the focused test and verify it passes**

Run:

```bash
./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.RemoteCommandMapperTest
```

Expected: `RemoteCommandMapperTest` passes with no failures.

### Task 2: Keep channel selection active in credentials-required UI state

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt:104-163`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapperTest.kt:7-103`

- [x] **Step 1: Use the new predicate at the credential-state gate**

Replace the current early state return:

```kotlin
if (state is PlayerUiState.CredentialsRequired) {
    return@onPreviewKeyEvent false
}
```

with a command-first gate:

```kotlin
val command = commandMapper.map(
    keyCode = keyCode,
    overlayVisible = overlayVisible,
    focusedControl = focusedControl,
)
if (state is PlayerUiState.CredentialsRequired && !command.isChannelSelection()) {
    return@onPreviewKeyEvent false
}
```

Then change the existing `when` header to use that value:

```kotlin
when (command) {
```

No other command branch changes. In particular, do not alter `BackHandler`, the login-form controls, or `NumericChannelInput`.

- [x] **Step 2: Run the focused regression suite**

Run:

```bash
./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.ui.RemoteCommandMapperTest
```

Expected: all command-mapping and credential-selection predicate tests pass.

- [x] **Step 3: Run the full debug unit-test suite**

Run:

```bash
./gradlew testDebugUnitTest
```

Expected: exit code 0 with no failing tests.

- [x] **Step 4: Inspect the final diff**

Run:

```bash
git diff --check
git diff -- app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt app/src/main/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapper.kt app/src/test/java/sk/ziacik/androidtvplayer/ui/RemoteCommandMapperTest.kt
```

Expected: no whitespace errors; diff changes only the credentials command gate, its predicate, and the regression test.

**Commit:** Do not commit unless the user explicitly requests it.
