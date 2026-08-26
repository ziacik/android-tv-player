# Remote Direct-Channel Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load the playable channel list from repository-managed JSON so that all replaceable sources work as direct streams without an APK build, while retaining only Sweet.tv as a bundled resolver.

**Architecture:** `channels.json` is the canonical catalogue at the repository root and is copied into APK assets at build time. `ChannelCatalogRepository` parses the seed/cache/downloaded catalogue and builds an immutable ordered `ChannelCatalog`; runtime navigation and persistence operate on that catalogue instead of enum ordinals. The launch downloads only to cache, so its result is used next launch.

**Tech Stack:** Kotlin, Android assets/SharedPreferences, OkHttp, coroutines, org.json, JUnit 4, Gradle Kotlin DSL.

---

## File structure

- `channels.json`: canonical remote and packaged seed list of all direct entries.
- `app/src/main/java/.../channel/TvChannel.kt`: data model and remaining `DIRECT`/`SWEET_TV` providers.
- `app/src/main/java/.../channel/ChannelCatalog.kt`: ordered lookup, next/previous, one-based lookup, JSON parsing, cache/download repository.
- `app/src/main/java/.../channel/SharedPreferencesChannelStore.kt`: stored key lookup against an injected catalogue.
- `app/src/main/java/.../resolver/ChannelResolver.kt`: routes only direct and Sweet.tv playback.
- `app/src/main/java/.../MainActivity.kt`: seed/cache catalogue creation and best-effort launch refresh.
- `app/src/main/java/.../player/PlayerController.kt`, `ui/NumericChannelInput.kt`, and `ui/PlayerOverlayModel.kt`: catalogue-based navigation/numbering and no credentials state.
- `app/src/main/java/.../ui/PlayerScreen.kt` and `player/PlayerUiState.kt`: delete Markíza credentials handling.
- `app/src/main/res/xml/network_security_config.xml`: permit cleartext streams globally.
- `app/build.gradle.kts`: package root `channels.json` as an asset.

### Task 1: Define and test the JSON catalogue boundary

**Files:**
- Create: `channels.json`
- Create: `app/src/main/java/sk/ziacik/androidtvplayer/channel/ChannelCatalog.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/channel/ChannelCatalogTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/TvChannel.kt`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Write failing parser and navigation tests.** Cover a valid direct entry, HTTP and HTTPS URLs, missing/duplicate IDs, `next`/`previous` wraparound, one-based lookup, and unknown saved keys defaulting to the first channel.

- [ ] **Step 2: Run the new test class.** Run `./gradlew testDebugUnitTest --tests '*ChannelCatalogTest'`; expect compilation failure because `ChannelCatalog` does not exist.

- [ ] **Step 3: Add the canonical `channels.json`.** Populate it with the six former direct channels (using `http://88.212.15.19/live/test_parpika_tv_sd_hevc/playlist.m3u8` for Paprika) plus every approved Free-TV/IPTV replacement. Use separate `ct-d` and `ct-art` records; omit `joj-sport-2` and `svet-naruby`.

- [ ] **Step 4: Implement catalogue types.** Change `TvChannel` from an enum to a value type with `storageKey`, `displayName`, `provider`, `providerValue`, and EPG IDs. Implement `ChannelCatalog(channels)` with `first`, `fromStorageKey`, `fromChannelNumber`, `next`, `previous`, and `numberOf`; reject empty catalogues and duplicate storage keys. Implement a JSON parser that turns every valid record into `ChannelProvider.DIRECT` and skips malformed records rather than returning a partial broken catalogue.

- [ ] **Step 5: Copy the root JSON into assets during every Android build.** Configure the app source set to include a generated assets directory and register a Gradle copy task from `$rootDir/channels.json`; make asset processing depend on it. Do not duplicate the seed file under `app/`.

- [ ] **Step 6: Run the catalogue test again.** Run `./gradlew testDebugUnitTest --tests '*ChannelCatalogTest'`; expect PASS.

### Task 2: Add seed/cache/download selection

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/ChannelCatalog.kt`
- Create: `app/src/test/java/sk/ziacik/androidtvplayer/channel/ChannelCatalogRepositoryTest.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`

- [ ] **Step 1: Write failing repository tests.** Use injected seed bytes, cache file, downloader, and parser to assert: valid cache wins at launch; invalid cache falls back to seed; successful refresh atomically replaces cache; failed download or invalid download keeps the previous valid cache.

- [ ] **Step 2: Run the repository tests.** Run `./gradlew testDebugUnitTest --tests '*ChannelCatalogRepositoryTest'`; expect failure because the repository is absent.

- [ ] **Step 3: Implement `ChannelCatalogRepository`.** Read seed bytes from the packaged asset, load only parseable cache bytes, and write a downloaded catalogue to a temporary file followed by an atomic move. Download the public raw GitHub `channels.json` URL through injected `OkHttpClient`; propagate coroutine cancellation and treat all other failures as refresh failures without erasing a good cache.

- [ ] **Step 4: Wire launch order in `MainActivity`.** Build the runtime catalogue from cache or seed before creating `PlayerController`; start a background refresh afterward that only updates disk for the next launch and logs a sanitized diagnostic on failure.

- [ ] **Step 5: Rerun repository tests.** Run `./gradlew testDebugUnitTest --tests '*ChannelCatalogRepositoryTest'`; expect PASS.

### Task 3: Make playback and navigation catalogue-driven

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/ChannelStore.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/SharedPreferencesChannelStore.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerController.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/NumericChannelInput.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModel.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/ui/NumericChannelInputTest.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/ui/PlayerOverlayModelTest.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/player/PlayerControllerTest.kt`

- [ ] **Step 1: Update tests to construct a small explicit `ChannelCatalog`.** Assert one-based selection and overlay labels use catalogue position, not `ordinal`; assert saved keys reload from that catalogue; assert wraparound works with runtime JSON channels.

- [ ] **Step 2: Run the affected tests.** Run `./gradlew testDebugUnitTest --tests '*NumericChannelInputTest' --tests '*PlayerOverlayModelTest' --tests '*PlayerControllerTest'`; expect failures from enum APIs.

- [ ] **Step 3: Inject `ChannelCatalog` where selection depends on ordering.** Change `ChannelStore.load`, `NumericChannelInput`, `PlayerController.channelUp/channelDown`, and overlay model creation to call catalogue methods. Preserve the selected `storageKey` persistence contract so a cached/remote direct channel restores after restart.

- [ ] **Step 4: Remove STVR-specific refresh behavior.** With STVR now direct JSON, programme-end refresh always uses the ordinary EPG lookup path; retain the Sweet.tv playback retry behavior.

- [ ] **Step 5: Rerun the affected tests.** Use the command from Step 2; expect PASS.

### Task 4: Remove obsolete resolvers and Markíza credentials flow

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/ChannelResolver.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/player/PlayerUiState.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/ui/PlayerScreen.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`
- Delete: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/FreeviewHttpClient.kt`
- Delete: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/FreeviewResolvers.kt`
- Delete: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/MarkizaCredentialsProvider.kt`
- Delete: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/MarkizaCredentialsStore.kt`
- Delete: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/MarkizaResolver.kt`
- Delete: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/OkHttpMarkizaClient.kt`
- Delete: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/OkHttpStvrClient.kt`
- Delete: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrHttpClient.kt`
- Delete: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrJsonParser.kt`
- Delete: `app/src/main/java/sk/ziacik/androidtvplayer/resolver/StvrResolver.kt`
- Delete: corresponding resolver tests, including `MarkizaResolverTest`, `MarkizaCredentialsProviderTest`, `FreeviewResolversTest`, `OkHttpStvrClientTest`, `StvrJsonParserTest`, and `StvrResolverTest`

- [ ] **Step 1: Change `ChannelResolverTest` to assert only `DIRECT` and `SWEET_TV` routing.** It must use direct catalogue objects and verify a direct channel never reaches the Sweet.tv resolver.

- [ ] **Step 2: Run it and expect failure.** Run `./gradlew testDebugUnitTest --tests '*ChannelResolverTest'`; expect compilation failure until constructor branches are removed.

- [ ] **Step 3: Remove the old integration.** Reduce `ChannelProvider` and `ChannelResolver` to `DIRECT` and `SWEET_TV`; delete all listed provider/Markíza code and tests. Remove `StreamResolution.RequiresCredentials`, `PlayerUiState.CredentialsRequired`, Markíza panel imports/composables/keyboard gate, and the credentials callback from `MainActivity` and `PlayerScreen`.

- [ ] **Step 4: Update remaining tests and fixtures.** Replace references to enum constants with test channels or catalogue lookups, retaining only Sweet.tv coverage for `svet-naruby`.

- [ ] **Step 5: Run resolver and UI tests.** Run `./gradlew testDebugUnitTest --tests '*ChannelResolverTest' --tests '*PlayerControllerTest' --tests '*PlayerOverlayTimelineVisibilityTest'`; expect PASS.

### Task 5: Enable HTTP streams and verify the assembled app

**Files:**
- Modify: `app/src/main/res/xml/network_security_config.xml`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/epg/EpgRepositoryTest.kt`
- Modify: `docs/superpowers/specs/2026-08-26-remote-direct-channel-catalog-design.md` only if implementation changes an approved detail

- [ ] **Step 1: Change network security configuration to globally allow cleartext traffic.** Keep the XML otherwise minimal; this allows the approved Czech and Paprika HTTP playlists to reach Media3.

- [ ] **Step 2: Run the full unit suite.** Run `./gradlew testDebugUnitTest`; expect BUILD SUCCESSFUL with no remaining Markíza, STVR, JOJ, ČT, Nova, or CNN resolver references.

- [ ] **Step 3: Build the debug APK.** Run `./gradlew assembleDebug`; expect `app/build/outputs/apk/debug/app-debug.apk` and packaged `channels.json` in the generated assets.

- [ ] **Step 4: Perform deployment smoke checks.** Install with `adb -s 192.168.0.200:5555 install -r app/build/outputs/apk/debug/app-debug.apk`, launch the main activity, and inspect logcat for catalogue parse/load errors. Check playback separately for one HTTPS proxy playlist, Paprika HTTP, and one remaining Sweet.tv channel; report these as physical playback results, not build results.

## Plan review

- All approved requirements map to Tasks 1-5: root JSON/seed/cache (1-2), runtime order and persistence (3), removal of Markíza and replaced providers (4), HTTP and device verification (5).
- No placeholders, unresolved decisions, or contradictory type names remain.
- No commit step is included because the user has not authorized staging or committing.
