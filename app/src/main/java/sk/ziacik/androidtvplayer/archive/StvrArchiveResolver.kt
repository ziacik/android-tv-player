package sk.ziacik.androidtvplayer.archive

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sk.ziacik.androidtvplayer.channel.ArchiveProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.STVR_USER_AGENT
import sk.ziacik.androidtvplayer.resolver.StvrHttpClient
import sk.ziacik.androidtvplayer.resolver.StvrJsonParser
import sk.ziacik.androidtvplayer.resolver.StreamResolveException
import sk.ziacik.androidtvplayer.resolver.StreamSource

class StvrArchiveResolver(
    private val httpClient: StvrHttpClient,
    private val parser: StvrJsonParser = StvrJsonParser(),
    private val zoneId: ZoneId = ZoneId.of("Europe/Bratislava"),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val archiveListingMutex = Mutex()
    private val archiveListings = mutableMapOf<String, ArchiveListingCacheEntry>()
    private val archiveRedirectMutex = Mutex()
    private val archiveRedirects = mutableMapOf<String, String?>()

    suspend fun resolve(
        channel: TvChannel,
        startsAtMs: Long,
        title: String,
        originalStartsAtMs: Long? = null,
    ): StreamSource {
        val headers = mapOf("User-Agent" to STVR_USER_AGENT)
        val lookup = findArchive(
            channel = channel,
            startsAtMs = startsAtMs,
            title = title,
            originalStartsAtMs = originalStartsAtMs,
            headers = headers,
        )
        val archiveId = lookup.archiveId ?: throw StreamResolveException(
            archiveLookupFailureMessage(
                listing = lookup.listing,
                channel = channel,
                expectedTime = lookup.expectedTime,
                expectedTitle = title,
                originalStart = lookup.originalStart,
            ),
        )

        val body = httpClient.get("$STVR_ARCHIVE_JSON_URL?id=$archiveId", headers)
        val hlsUrl = parser.parse(body).hlsUrl
            ?: throw StreamResolveException("STVR archive response does not contain an HLS source")

        return StreamSource(
            url = hlsUrl,
            userAgent = STVR_USER_AGENT,
        )
    }

    suspend fun isAvailable(
        channel: TvChannel,
        program: ProgramMetadata,
    ): Boolean {
        if (channel.archive?.provider != ArchiveProvider.STVR) return false
        val startsAtMs = program.startsAtMs ?: return false
        return findArchive(
            channel = channel,
            startsAtMs = startsAtMs,
            title = program.title,
            originalStartsAtMs = program.archiveOriginalStartsAtMs,
            headers = mapOf("User-Agent" to STVR_USER_AGENT),
        ).archiveId != null
    }

    private suspend fun findArchive(
        channel: TvChannel,
        startsAtMs: Long,
        title: String,
        originalStartsAtMs: Long?,
        headers: Map<String, String>,
    ): ArchiveLookup {
        val start = Instant.ofEpochMilli(startsAtMs).atZone(zoneId)
        val startTime = start.format(TIME_FORMAT)
        val listing = archiveListing(start, headers)
        val directArchiveId = findArchiveId(
            listing = listing,
            channel = channel,
            startTime = startTime,
            title = title,
            headers = headers,
        )
        val originalStart = originalStartsAtMs
            ?.takeIf { it != startsAtMs }
            ?.let { Instant.ofEpochMilli(it).atZone(zoneId) }
        val archiveId = directArchiveId ?: originalStart?.let { original ->
            val originalListing = archiveListing(original, headers)
            findArchiveId(
                listing = originalListing,
                channel = channel,
                startTime = original.format(TIME_FORMAT),
                title = title,
                headers = headers,
            )
        }
        return ArchiveLookup(
            archiveId = archiveId,
            listing = listing,
            expectedTime = startTime,
            originalStart = originalStart,
        )
    }

    private suspend fun archiveListing(
        start: ZonedDateTime,
        headers: Map<String, String>,
    ): String {
        val url = start.programListingUrl()
        return archiveListingMutex.withLock {
            val currentNowMs = nowMs()
            val cached = archiveListings[url]
            if (cached != null && cached.isValidFor(start, currentNowMs)) {
                return@withLock cached.listing
            }

            httpClient.get(url, headers).also { listing ->
                archiveListings[url] = ArchiveListingCacheEntry(
                    listing = listing,
                    fetchedAtMs = currentNowMs,
                )
            }
        }
    }

    private fun ArchiveListingCacheEntry.isValidFor(
        start: ZonedDateTime,
        currentNowMs: Long,
    ): Boolean {
        val today = Instant.ofEpochMilli(currentNowMs).atZone(zoneId).toLocalDate()
        if (start.toLocalDate() != today) return true
        return currentNowMs - fetchedAtMs < TODAY_ARCHIVE_LISTING_TTL_MS
    }

    private suspend fun findArchiveId(
        listing: String,
        channel: TvChannel,
        startTime: String,
        title: String,
        headers: Map<String, String>,
    ): String? {
        val expectedMinutes = startTime.minutesOfDay() ?: return null
        val expectedTitle = title.normalizedTitle()
        val nearbyCandidates = parseProgrammeItems(listing, channel)
            .mapNotNull { candidate ->
                val candidateMinutes = candidate.time.minutesOfDay() ?: return@mapNotNull null
                candidate to minuteDistance(expectedMinutes, candidateMinutes)
            }
            .filter { (_, distance) -> distance <= ARCHIVE_TIME_TOLERANCE_MINUTES }
            .toList()

        val exact = nearbyCandidates
            .filter { (candidate, _) -> candidate.title == expectedTitle }
            .sortedBy { (_, distance) -> distance }
        val partial = nearbyCandidates
            .filter { (candidate, _) ->
                candidate.title.contains(expectedTitle) || expectedTitle.contains(candidate.title)
            }
            .sortedBy { (_, distance) -> distance }
        val fallback = nearbyCandidates.singleOrNull()?.let(::listOf).orEmpty()
        val candidates = (exact + partial + fallback)
            .map { (candidate, _) -> candidate }
            .distinctBy { it.programUrl }

        for (candidate in candidates) {
            resolveArchiveId(candidate.programUrl, headers)?.let { return it }
        }
        return null
    }

    private fun parseProgrammeItems(
        listing: String,
        channel: TvChannel,
    ): List<ProgrammeItem> {
        val channelListing = listing.channelSection(channel)
        val starts = PROGRAMME_START_REGEX.findAll(channelListing).toList()
        return starts.mapIndexedNotNull { index, match ->
            val blockEnd = starts.getOrNull(index + 1)?.range?.first ?: channelListing.length
            val block = channelListing.substring(match.range.first, blockEnd)
            val programmeLink = PROGRAMME_LINK_REGEX.find(block)
                ?: return@mapIndexedNotNull null
            val programUrl = requireNotNull(programmeLink.groups["url"]).value.absoluteStvrUrl()
            val title = requireNotNull(programmeLink.groups["title"]).value.normalizedTitle()
            if (title.isBlank()) return@mapIndexedNotNull null

            ProgrammeItem(
                programUrl = programUrl,
                time = requireNotNull(match.groups["time"]).value,
                title = title,
            )
        }
    }

    private suspend fun resolveArchiveId(
        programUrl: String,
        headers: Map<String, String>,
    ): String? {
        val cached = archiveRedirectMutex.withLock {
            if (archiveRedirects.containsKey(programUrl)) {
                true to archiveRedirects[programUrl]
            } else {
                false to null
            }
        }
        if (cached.first) return cached.second

        val finalUrl = runCatching {
            httpClient.finalUrl(programUrl, headers)
        }.getOrNull() ?: return null
        val archiveId = ARCHIVE_FINAL_URL_REGEX.find(finalUrl)
            ?.groups
            ?.get("id")
            ?.value
        archiveRedirectMutex.withLock {
            archiveRedirects[programUrl] = archiveId
        }
        return archiveId
    }

    private fun String.absoluteStvrUrl(): String =
        if (startsWith("http://") || startsWith("https://")) {
            this
        } else {
            "$STVR_ORIGIN${if (startsWith('/')) this else "/$this"}"
        }

    private fun archiveLookupFailureMessage(
        listing: String,
        channel: TvChannel,
        expectedTime: String,
        expectedTitle: String,
        originalStart: ZonedDateTime? = null,
    ): String {
        val channelListing = listing.channelSection(channel)
        val programmeItems = parseProgrammeItems(listing, channel)
        val parsedCandidates = programmeItems.size
        return buildString {
            append("STVR archive item was not found")
            append("; expectedTime=").append(expectedTime)
            append("; expectedTitle=").append(expectedTitle.normalizedTitle())
            append("; programmeLinks=").append(programmeItems.size)
            append("; parsedCandidates=").append(parsedCandidates)
            append("; programmeUrls=").append(
                programmeItems.take(MAX_DIAGNOSTIC_IDS).joinToString(",") { it.programUrl },
            )
            append("; sectionChars=").append(channelListing.length)
            append("; listingChars=").append(listing.length)
            originalStart?.let {
                append("; originalDate=").append(it.toLocalDate())
                append("; originalTime=").append(it.format(TIME_FORMAT))
            }
        }
    }

    private fun ZonedDateTime.programListingUrl(): String =
        "$STVR_PROGRAM_URL?date=${toLocalDate()}"

    private fun String.channelSection(channel: TvChannel): String {
        val heading = channel.archiveHeading()
            ?: throw StreamResolveException("STVR archive is not configured for channel")
        val headingMatch = Regex(
            "<h(?<level>[1-6])[^>]*>\\s*${Regex.escape(heading)}\\s*</h[1-6]>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(this) ?: return this
        val headingLevel = requireNotNull(headingMatch.groups["level"]).value
        val nextChannelHeading = Regex(
            "<h$headingLevel[^>]*>.*?</h$headingLevel>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(this, headingMatch.range.last + 1)
        return substring(
            headingMatch.range.last + 1,
            nextChannelHeading?.range?.first ?: length,
        )
    }

    private fun TvChannel.archiveHeading(): String? {
        val archiveConfig = archive?.takeIf { it.provider == ArchiveProvider.STVR } ?: return null
        return when (archiveConfig.channelId) {
            "1" -> "Jednotka"
            "2" -> "Dvojka"
            "3" -> "24"
            "15" -> "Šport"
            else -> null
        }
    }

    private fun String.normalizedTitle(): String =
        replace(HTML_TAG_REGEX, " ")
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&nbsp;", " ", ignoreCase = true)
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun String.minutesOfDay(): Int? {
        val parts = split(':')
        if (parts.size != 2) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59) return null
        return hours * 60 + minutes
    }

    private fun minuteDistance(first: Int, second: Int): Int {
        val direct = abs(first - second)
        return minOf(direct, MINUTES_PER_DAY - direct)
    }

    private data class ArchiveListingCacheEntry(
        val listing: String,
        val fetchedAtMs: Long,
    )

    private data class ArchiveLookup(
        val archiveId: String?,
        val listing: String,
        val expectedTime: String,
        val originalStart: ZonedDateTime?,
    )

    private data class ProgrammeItem(
        val programUrl: String,
        val time: String,
        val title: String,
    )

    private companion object {
        const val STVR_PROGRAM_URL = "https://www.stvr.sk/televizia/program/"
        const val STVR_ARCHIVE_JSON_URL = "https://www.rtvs.sk/json/archive5f.json"
        const val ARCHIVE_TIME_TOLERANCE_MINUTES = 30
        const val TODAY_ARCHIVE_LISTING_TTL_MS = 5 * 60_000L
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_DIAGNOSTIC_IDS = 12
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val HTML_TAG_REGEX = Regex("<[^>]+>")
        val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]+")
        val WHITESPACE_REGEX = Regex("\\s+")
        const val STVR_ORIGIN = "https://www.stvr.sk"
        val PROGRAMME_START_REGEX = Regex(
            """<[^>]*class=["'][^"']*time--start[^"']*["'][^>]*>\s*(?<time>\d{2}:\d{2})""",
            RegexOption.IGNORE_CASE,
        )
        val PROGRAMME_LINK_REGEX = Regex(
            """<a\b[^>]*href=["'](?<url>(?:https?://[^"']+)?/televizia/program/[^/"']+/\d+)["'][^>]*>(?<title>.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val ARCHIVE_FINAL_URL_REGEX = Regex(
            """/televizia/archiv/[^/?#]+/(?<id>\d+)(?:[/?#]|$)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
