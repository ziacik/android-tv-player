# Kanálik System EPG TV Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish Kanálik channels and XMLTV programmes in the Philips system TV guide and play a selected channel directly in the system TV application.

**Architecture:** An application-scoped dependency container is shared by the existing activity, the TV input service, and background EPG work. A TV-provider gateway upserts only rows belonging to Kanálik's input ID, while an input session resolves the stable channel identity and renders a session-local Media3 player to the surface supplied by Android.

**Tech Stack:** Kotlin, Android TV Input Framework (`TvInputService`, `TvContract`), Android TV provider, Media3 ExoPlayer, Kotlin coroutines, WorkManager, JUnit, Android instrumentation tests.

**Git authority:** Do not stage or commit as part of this plan unless the user separately grants that authority.

---

## File structure

- Create `app/src/main/java/sk/ziacik/androidtvplayer/AppDependencies.kt`: one app-scoped source of catalogue, resolver, EPG source and refresh dependencies.
- Create `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/EpgSchedule.kt`: schedule domain objects and targeted XMLTV schedule extraction.
- Create `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/TvProviderGateway.kt`: narrow abstraction over `ContentResolver` and `TvContract` rows.
- Create `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/SystemEpgSynchronizer.kt`: idempotent Kanálik-only channel/programme upsert and stale-row cleanup.
- Create `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/SystemEpgSyncWorker.kt`: periodic and one-off guide synchronization.
- Create `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/KanalikTvInputService.kt`: TV input registration and per-session playback lifecycle.
- Create `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/KanalikTvInputSetupActivity.kt`: system-invoked setup that publishes at least one channel before returning success.
- Create `app/src/main/res/xml/kanalik_tv_input.xml`: TV input service metadata.
- Modify `app/src/main/AndroidManifest.xml`: declare the TV input service and initialization receiver.
- Modify `app/build.gradle.kts` and `gradle/libs.versions.toml`: add the WorkManager runtime dependency.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`: consume `AppDependencies`, schedule refresh, retain standalone player behavior.
- Modify `app/src/main/java/sk/ziacik/androidtvplayer/epg/XmltvEpgParser.kt`: expose filtered programme intervals, preserving current-programme behavior.
- Create JVM tests under `app/src/test/java/sk/ziacik/androidtvplayer/systemepg/` for parser, mapping, sync plan, and session decision behavior.
- Create device tests under `app/src/androidTest/java/sk/ziacik/androidtvplayer/systemepg/` for provider rows and input registration.

## Task 1: Share runtime construction

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/AppDependencies.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/AppDependenciesTest.kt`

- [ ] **Step 1: Write the failing factory test**

```kotlin
@Test fun `catalogue channel can be found by stable key after refresh`() {
    val dependencies = testDependencies(catalogue = listOf(channel("joj")))
    assertEquals("joj", dependencies.channel("joj")?.storageKey)
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.AppDependenciesTest`

Expected: compilation failure because `AppDependencies` does not exist.

- [ ] **Step 3: Implement the shared dependency boundary**

```kotlin
class AppDependencies private constructor(
    private val catalogRepository: ChannelCatalogRepository,
    val resolver: ChannelResolver,
    val scheduleRepository: EpgScheduleRepository,
) {
    @Volatile private var catalog = ChannelCatalog(
        catalogRepository.load().channels + TvChannel.sweetTvChannels,
    )
    suspend fun refreshCatalog(): ChannelCatalog = ChannelCatalog(
        catalogRepository.refresh().channels + TvChannel.sweetTvChannels,
    ).also { catalog = it; TvChannel.setRuntimeEntries(it.channels) }
    fun currentCatalog(): ChannelCatalog = catalog
    fun channel(storageKey: String): TvChannel? = currentCatalog().channels
        .firstOrNull { it.storageKey == storageKey }
    suspend fun resolve(channel: TvChannel): StreamResolution = resolver.resolve(channel)
    companion object {
        private val instances = ConcurrentHashMap<String, AppDependencies>()
        fun from(context: Context): AppDependencies = instances.getOrPut(context.packageName) {
            val appContext = context.applicationContext
            val freeview = OkHttpFreeviewClient()
            AppDependencies(
                catalogRepository = ChannelCatalogRepository(
                    seed = { appContext.assets.open("channels.json").bufferedReader().readText() },
                    cacheFile = File(appContext.filesDir, "channels.json"),
                    download = OkHttpChannelCatalogDownloader(CHANNELS_URL)::download,
                ),
                resolver = ChannelResolver(
                    resolveStvr = StvrResolver(OkHttpStvrClient())::resolve,
                    resolveJoj = JojResolver(freeview)::resolve,
                    resolveCt = CtResolver(freeview)::resolve,
                    resolveTa3 = Ta3Resolver(freeview)::resolve,
                    resolveNova = NovaResolver(freeview)::resolve,
                    resolveCnnPrimaNews = CnnPrimaNewsResolver(freeview)::resolve,
                    resolveSweetTv = SweetTvResolver(freeview)::resolve,
                    resolveDirect = DirectResolver()::resolve,
                ),
                scheduleRepository = EpgScheduleRepository(appContext.filesDir),
            )
        }
    }
}
```

Move resolver, catalogue and EPG downloader creation from `MainActivity` without moving player ownership. Update `MainActivity` to use this object and keep its existing refresh/retry flow.

- [ ] **Step 4: Run focused regression tests**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.AppDependenciesTest --tests sk.ziacik.androidtvplayer.channel.ChannelCatalogRepositoryTest`

Expected: `BUILD SUCCESSFUL`.

## Task 2: Extract XMLTV schedules for system guide publication

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/epg/XmltvEpgParser.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/EpgSchedule.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/systemepg/EpgScheduleTest.kt`

- [ ] **Step 1: Write failing schedule tests**

```kotlin
@Test fun `extracts only selected channel programmes with UTC bounds`() {
    val programmes = parser.programmes(xml, setOf("joj.sk"), fromMs, untilMs)
    assertEquals(listOf(EpgProgramme("Správy", startMs, endMs)), programmes["joj.sk"])
}

@Test fun `drops zero length and inverted intervals`() {
    assertTrue(parser.programmes(xmlWithInvalidIntervals, setOf("joj.sk"), 0, Long.MAX_VALUE).isEmpty())
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.systemepg.EpgScheduleTest`

Expected: compilation failure because `programmes` is unavailable.

- [ ] **Step 3: Implement bounded schedule extraction**

Add `XmltvEpgParser.programmes(InputStream, Set<String>, Long, Long)` that streams the XML once, retains only requested XMLTV IDs and intervals overlapping the requested window, and returns valid `EpgProgramme` objects. Add `EpgScheduleRepository.load(channels, fromMs, untilMs)` which downloads/caches existing sources and uses the established source order: Open-EPG first, then Skylink only for channels not supplied by Open-EPG.

- [ ] **Step 4: Run parser and EPG regression tests**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.systemepg.EpgScheduleTest --tests sk.ziacik.androidtvplayer.epg.XmltvEpgParserTest --tests sk.ziacik.androidtvplayer.epg.EpgRepositoryTest`

Expected: `BUILD SUCCESSFUL`.

## Task 3: Implement an idempotent Kanálik-only TV provider synchronizer

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/TvProviderGateway.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/SystemEpgSynchronizer.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/systemepg/SystemEpgSynchronizerTest.kt`

- [ ] **Step 1: Write failing synchronization tests**

```kotlin
@Test fun `upsert retains other input rows and removes only obsolete Kanalik rows`() {
    val result = synchronizer.sync(catalogue, schedule)
    assertEquals(setOf("other-input", "kanalik:joj"), gateway.channelKeys())
}

@Test fun `second identical sync performs no inserts or updates`() {
    synchronizer.sync(catalogue, schedule)
    gateway.resetOperations()
    synchronizer.sync(catalogue, schedule)
    assertTrue(gateway.operations.isEmpty())
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.systemepg.SystemEpgSynchronizerTest`

Expected: compilation failure because the gateway and synchronizer do not exist.

- [ ] **Step 3: Implement provider mapping and diff synchronization**

`TvProviderGateway` must expose only `channelsForInput(inputId)`, `programmesForChannel(channelId, fromMs, untilMs)`, `insert`, `update`, and `delete` operations. `SystemEpgSynchronizer` uses `TvContract.buildInputId(ComponentName)` and:

```kotlin
ChannelValues(
    inputId = inputId,
    displayName = channel.displayName,
    displayNumber = (index + 1).toString(),
    internalProviderId = channel.storageKey,
    type = TvContract.Channels.TYPE_OTHER,
    serviceType = TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO,
)
```

Use a deterministic programme key made from channel `storageKey`, start/end milliseconds and title. Delete only rows returned through the input-specific gateway query. Do not write `COLUMN_BROWSABLE`: the system TV setup flow owns the user's source/channel visibility choice.

- [ ] **Step 4: Run focused synchronization tests**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.systemepg.SystemEpgSynchronizerTest`

Expected: `BUILD SUCCESSFUL`.

## Task 4: Register and schedule the TV input

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/res/xml/kanalik_tv_input.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/SystemEpgSyncWorker.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/KanalikTvInputSetupActivity.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidtvplayer/systemepg/TvInputRegistrationTest.kt`

- [ ] **Step 1: Write the failing registration test**

```kotlin
@Test fun `Kanálik TV input is discoverable`() {
    val inputId = TvContract.buildInputId(ComponentName(context, KanalikTvInputService::class.java))
    assertTrue(context.getSystemService(TvInputManager::class.java).tvInputList.any { it.id == inputId })
}
```

- [ ] **Step 2: Run the device test and verify it fails**

Run: `./gradlew connectedDebugAndroidTest`

Expected: the test does not compile until the input service exists.

- [ ] **Step 3: Add system declarations and scheduling**

Declare `com.android.providers.tv.permission.WRITE_EPG_DATA`. Declare the service with action `android.media.tv.TvInputService`, permission `android.permission.BIND_TV_INPUT`, and `android.media.tv.input` metadata that points to `kanalik_tv_input.xml`. Set `android:canRecord="false"` and `android:setupActivity="sk.ziacik.androidtvplayer.systemepg.KanalikTvInputSetupActivity"` in the metadata XML. The exported setup activity runs the first synchronization off the main thread, returns `Activity.RESULT_OK` only after at least one channel was published, and then enqueues follow-up synchronization. Add an `ACTION_INITIALIZE_PROGRAMS` receiver. Add WorkManager's runtime KTX dependency and have `SystemEpgSyncWorker` call `SystemEpgSynchronizer.sync()` from `Dispatchers.IO`; enqueue unique immediate work after initialization and unique periodic work with `15, TimeUnit.MINUTES`.

- [ ] **Step 4: Run manifest/build verification**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`; `aapt dump xmltree app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml` contains `KanálikTvInputService` and `BIND_TV_INPUT`.

## Task 5: Implement direct system-TV playback

**Files:**
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/KanalikTvInputService.kt`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/systemepg/KanalikTvInputSession.kt`
- Test: `app/src/test/java/sk/ziacik/androidtvplayer/systemepg/KanalikTvInputSessionTest.kt`

- [ ] **Step 1: Write failing playback-decision tests**

```kotlin
@Test fun `playable resolution attaches source to session player`() = runTest {
    session.onTune(channelUri("joj"))
    assertEquals(source, player.loadedSource)
    assertEquals(VideoAvailability.AVAILABLE, session.lastAvailability)
}

@Test fun `restricted resolution reports unavailable and does not load player`() = runTest {
    session.onTune(channelUri("jednotka"))
    assertNull(player.loadedSource)
    assertEquals(VideoAvailability.UNAVAILABLE, session.lastAvailability)
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.systemepg.KanalikTvInputSessionTest`

Expected: compilation failure because the session implementation does not exist.

- [ ] **Step 3: Implement session lifecycle**

Create a small `TvSessionPlayer` interface around the Media3 operations needed by the session so unit tests do not depend on Android surfaces. In `KanalikTvInputSession`:

```kotlin
override fun onTune(channelUri: Uri): Boolean {
    tuneJob?.cancel()
    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING)
    tuneJob = scope.launch { tune(channelUri) }
    return true
}
```

Resolve `TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID` through the provider gateway, map it to `AppDependencies.channel(storageKey)`, resolve it, then configure a new `ExoPlayer` with the resolver's URL, user agent, headers and HLS/DASH MIME type. Attach/detach the platform surface in `onSetSurface`; cancel and release the player in `onRelease`; report `VIDEO_UNAVAILABLE_REASON_UNKNOWN` for resolver/network failures and `VIDEO_UNAVAILABLE_REASON_NOT_CONNECTED` for restricted or credential-required results.

- [ ] **Step 4: Run focused playback tests**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.systemepg.KanalikTvInputSessionTest --tests sk.ziacik.androidtvplayer.player.PlayerControllerTest`

Expected: `BUILD SUCCESSFUL`.

## Task 6: End-to-end device verification

**Files:**
- Modify: `app/src/androidTest/java/sk/ziacik/androidtvplayer/systemepg/TvInputRegistrationTest.kt`
- Test: `app/src/androidTest/java/sk/ziacik/androidtvplayer/systemepg/SystemEpgProviderTest.kt`

- [ ] **Step 1: Add provider ownership test**

```kotlin
@Test fun `sync publishes Kanálik rows carrying stable channel keys`() = runTest {
    synchronizer.sync(testCatalogue, testSchedule)
    assertEquals("joj", queryKanalikChannel("JOJ").internalProviderId)
}
```

- [ ] **Step 2: Run all automated checks**

Run: `./gradlew clean testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install and inspect Philips integration**

Run: `adb connect 192.168.0.200:5555 && adb -s 192.168.0.200:5555 install -r app/build/outputs/apk/debug/app-debug.apk && adb -s 192.168.0.200:5555 shell dumpsys tv_input`

Expected: `Success` from install and a registered `sk.ziacik.androidtvplayer/.systemepg.KanalikTvInputService` input in the dump.

- [ ] **Step 4: Perform the physical-TV acceptance check**

Enable Kanálik in the Philips source settings, open the native guide, verify Kanálik channel titles/programmes, select a known playable direct/HLS channel, and confirm audio/video play inside the native system-TV application. Record restricted-channel behavior separately; it is expected to be unavailable rather than playback success.

## Self-review

Spec coverage: tasks 1-2 cover shared catalogue and XMLTV schedule extraction; task 3 covers isolated provider ownership and idempotence; task 4 covers declaration, initialization and background synchronization; task 5 covers direct system playback/error lifecycle; task 6 covers automated and physical-TV validation.

No placeholders: the plan contains no unfinished requirements. Type boundary consistency: `AppDependencies`, `EpgScheduleRepository`, `TvProviderGateway`, `SystemEpgSynchronizer`, and `KanalikTvInputSession` are introduced in dependency order and each caller uses the names defined here.
