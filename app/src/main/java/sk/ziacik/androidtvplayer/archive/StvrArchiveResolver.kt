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
        val url = start.archiveListingUrl()
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

    private fun findArchiveId(
        listing: String,
        channel: TvChannel,
        startTime: String,
        title: String,
    ): String? {
        val expectedMinutes = startTime.minutesOfDay() ?: return null
        val expectedTitle = title.normalizedTitle()
        val nearbyCandidates = ARCHIVE_ITEM_REGEX.findAll(listing.channelSection(channel))
            .mapNotNull { match ->
                val candidate = ArchiveItem(
                    id = requireNotNull(match.groups["id"]).value,
                    time = requireNotNull(match.groups["time"]).value.trim(),
                    title = requireNotNull(match.groups["title"]).value.normalizedTitle(),
                )
                val candidateMinutes = candidate.time.minutesOfDay() ?: return@mapNotNull null
                candidate to minuteDistance(expectedMinutes, candidateMinutes)
            }
            .filter { (_, distance) -> distance <= ARCHIVE_TIME_TOLERANCE_MINUTES }
            .toList()

        return nearbyCandidates
            .filter { (candidate, _) -> candidate.title == expectedTitle }
            .minByOrNull { (_, distance) -> distance }
            ?.first
            ?.id
            ?: nearbyCandidates
                .filter { (candidate, _) ->
                    candidate.title.contains(expectedTitle) || expectedTitle.contains(candidate.title)
                }
                .minByOrNull { (_, distance) -> distance }
                ?.first
                ?.id
            ?: nearbyCandidates.singleOrNull()?.first?.id
    }

    private fun archiveLookupFailureMessage(
        listing: String,
        channel: TvChannel,
        expectedTime: String,
        expectedTitle: String,
        originalStart: ZonedDateTime? = null,
    ): String {
        val channelListing = listing.channelSection(channel)
        val archiveIds = ARCHIVE_LINK_ID_REGEX.findAll(channelListing)
            .map { match -> requireNotNull(match.groups["id"]).value }
            .distinct()
            .toList()
        val parsedCandidates = ARCHIVE_ITEM_REGEX.findAll(channelListing).count()
        return buildString {
            append("STVR archive item was not found")
            append("; expectedTime=").append(expectedTime)
            append("; expectedTitle=").append(expectedTitle.normalizedTitle())
            append("; archiveLinks=").append(archiveIds.size)
            append("; parsedCandidates=").append(parsedCandidates)
            append("; archiveIds=").append(archiveIds.take(MAX_DIAGNOSTIC_IDS).joinToString(","))
            append("; sectionChars=").append(channelListing.length)
            append("; listingChars=").append(listing.length)
            originalStart?.let {
                append("; originalDate=").append(it.toLocalDate())
                append("; originalTime=").append(it.format(TIME_FORMAT))
            }
        }
    }

    private fun ZonedDateTime.archiveListingUrl(): String =
        "$STVR_ARCHIVE_URL?date=${toLocalDate()}&ord=dt"

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
            "3" -> ":24"
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

    private data class ArchiveItem(
        val id: String,
        val time: String,
        val title: String,
    )

    private companion object {
        const val STVR_ARCHIVE_URL = "https://www.stvr.sk/televizia/archiv"
        const val STVR_ARCHIVE_JSON_URL = "https://www.rtvs.sk/json/archive5f.json"
        const val ARCHIVE_TIME_TOLERANCE_MINUTES = 30
        const val TODAY_ARCHIVE_LISTING_TTL_MS = 5 * 60_000L
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_DIAGNOSTIC_IDS = 12
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val HTML_TAG_REGEX = Regex("<[^>]+>")
        val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]+")
        val WHITESPACE_REGEX = Regex("\\s+")
        val ARCHIVE_LINK_ID_REGEX = Regex(
            """/televizia/archiv/[^/"']+/(?<id>\d+)""",
            RegexOption.IGNORE_CASE,
        )
        val ARCHIVE_ITEM_REGEX = Regex(
            """<div\b[^>]*class=["'][^"']*time--start[^"']*["'][^>]*>\s*(?<time>\d{2}:\d{2}).*?<a\b[^>]*href=["'][^"']*/televizia/archiv/[^/"']+/(?<id>\d+)["'][^>]*>.*?<a\b[^>]*title=["'](?<title>[^"']+)["'][^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    }
}
