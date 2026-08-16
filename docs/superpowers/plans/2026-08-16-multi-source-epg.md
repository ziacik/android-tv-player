# Multi-Source XMLTV EPG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve programme data from ordered XMLTV feeds, with Skylink preferred and iptv-org retained as fallback.

**Architecture:** `TvChannel` carries source-specific XMLTV identifiers. An `XmltvEpgSource` owns one source's URL/download function, cache file and IDs. `CachedXmltvEpgRepository` checks configured sources in priority order and isolates cache/download/parse failure to the individual source.

**Tech Stack:** Kotlin, coroutines, OkHttp, SAX, JUnit.

---

### Task 1: Model source-specific channel identifiers

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/channel/TvChannel.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/channel/TvChannelTest.kt`

- [ ] **Step 1: Write failing channel-ID assertions**

```kotlin
assertEquals("336e46bf4276e77a716e494c6285d5db", TvChannel.MARKIZA.epgIds[EpgSourceId.SKYLINK])
assertEquals("84a2364ade7443e6d6afe03f9aa2361a", TvChannel.JOJ.epgIds[EpgSourceId.SKYLINK])
assertEquals("MARKÍZA.cz", TvChannel.MARKIZA.epgIds[EpgSourceId.IPTV_ORG])
assertEquals(null, TvChannel.WATERBEAR.epgIds[EpgSourceId.SKYLINK])
```

- [ ] **Step 2: Run the channel test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.channel.TvChannelTest --no-daemon`

Expected: compilation failure because `epgIds` and `EpgSourceId` do not exist.

- [ ] **Step 3: Implement the source ID map**

```kotlin
enum class EpgSourceId { SKYLINK, IPTV_ORG }

val epgIds: Map<EpgSourceId, String> = emptyMap()
```

Populate known Skylink IDs, preserve all existing iptv-org IDs under `IPTV_ORG`, and leave unsupported channels empty.

- [ ] **Step 4: Run the channel test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.channel.TvChannelTest --no-daemon`

Expected: `BUILD SUCCESSFUL`.

### Task 2: Make the repository source-aware and compression-agnostic

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/epg/EpgRepository.kt`
- Modify: `app/src/test/java/sk/ziacik/androidtvplayer/epg/EpgRepositoryTest.kt`

- [ ] **Step 1: Write failing priority and plain-XML tests**

```kotlin
val repository = repository(
    sources = listOf(
        source(EpgSourceId.SKYLINK, mapOf(TvChannel.MARKIZA to "skylink"), xml("skylink", "...", "Skylink")),
        source(EpgSourceId.IPTV_ORG, mapOf(TvChannel.MARKIZA to "iptv"), gzip(xml("iptv", "...", "Iptv"))),
    ),
)
assertEquals("Skylink", repository.currentProgram(TvChannel.MARKIZA, NOW_MS)?.title)
```

Also test fallback when the first source has no current programme and when its downloader throws.

- [ ] **Step 2: Run the EPG repository test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.epg.EpgRepositoryTest --no-daemon`

Expected: compilation failure because the repository accepts only one cache and downloader.

- [ ] **Step 3: Implement independently cached `XmltvEpgSource` values**

```kotlin
data class XmltvEpgSource(
    val id: EpgSourceId,
    val cacheFile: File,
    val download: suspend () -> ByteArray,
)
```

Have the repository derive IDs from `channel.epgIds[source.id]`, cache parsed programmes per source, continue to the next source after missing data/failure, and detect gzip bytes by their `1f 8b` magic header before parsing.

- [ ] **Step 4: Parameterize the HTTP downloader**

```kotlin
class OkHttpEpgDownloader(
    private val url: String,
    private val client: OkHttpClient = OkHttpClient(),
)
```

Remove the fixed URL constant so each `XmltvEpgSource` controls its own URL.

- [ ] **Step 5: Run the EPG repository test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.epg.EpgRepositoryTest --no-daemon`

Expected: `BUILD SUCCESSFUL`.

### Task 3: Configure Skylink followed by iptv-org

**Files:**
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/MainActivity.kt`
- Modify: `app/src/main/java/sk/ziacik/androidtvplayer/epg/EpgRepository.kt`

- [ ] **Step 1: Configure independent source cache files in priority order**

```kotlin
sources = listOf(
    XmltvEpgSource(EpgSourceId.SKYLINK, File(epgDir, "skylink-a3b-a1.xml"), OkHttpEpgDownloader(SKYLINK_URL)::download),
    XmltvEpgSource(EpgSourceId.IPTV_ORG, File(epgDir, "iptv-org-cz.xml.gz"), OkHttpEpgDownloader(IPTV_ORG_URL)::download),
)
```

Use `https://raw.githubusercontent.com/370network/skylink-xmltv/refs/heads/main/a3b_a1.xml` for Skylink and retain `https://iptv-epg.org/files/epg-cz.xml.gz` as secondary fallback.

- [ ] **Step 2: Run focused EPG and controller tests**

Run: `./gradlew testDebugUnitTest --tests sk.ziacik.androidtvplayer.epg.EpgRepositoryTest --tests sk.ziacik.androidtvplayer.player.PlayerControllerTest --no-daemon`

Expected: `BUILD SUCCESSFUL`.

### Task 4: Verify the integrated change

**Files:**
- Verify only.

- [ ] **Step 1: Run the full Android JVM suite**

Run: `./gradlew testDebugUnitTest --no-daemon`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build the debug APK and check the patch**

Run: `./gradlew assembleDebug --no-daemon && git diff --check`

Expected: `BUILD SUCCESSFUL` and no diff-check output.
