package sk.ziacik.androidtvplayer.archive

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import sk.ziacik.androidtvplayer.channel.ArchiveProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.STVR_USER_AGENT
import sk.ziacik.androidtvplayer.resolver.StvrHttpClient
import sk.ziacik.androidtvplayer.resolver.StvrJsonParser
import sk.ziacik.androidtvplayer.resolver.StreamResolveException
import sk.ziacik.androidtvplayer.resolver.StreamSource

class StvrArchiveResolver(
    private val httpClient: StvrHttpClient,
    private val parser: StvrJsonParser = StvrJsonParser(),
    private val zoneId: ZoneId = ZoneId.of("Europe/Bratislava"),
) {
    suspend fun resolve(
        channel: TvChannel,
        startsAtMs: Long,
        title: String,
    ): StreamSource {
        val start = Instant.ofEpochMilli(startsAtMs).atZone(zoneId)
        val listingUrl = "$STVR_ARCHIVE_URL?date=${start.toLocalDate()}&ord=dt"
        val headers = mapOf("User-Agent" to STVR_USER_AGENT)
        val listing = httpClient.get(listingUrl, headers)
        val archiveId = findArchiveId(
            listing = listing,
            channel = channel,
            startTime = start.format(TIME_FORMAT),
            title = title,
        ) ?: throw StreamResolveException("STVR archive item was not found")

        val body = httpClient.get("$STVR_ARCHIVE_JSON_URL?id=$archiveId", headers)
        val hlsUrl = parser.parse(body).hlsUrl
            ?: throw StreamResolveException("STVR archive response does not contain an HLS source")

        return StreamSource(
            url = hlsUrl,
            userAgent = STVR_USER_AGENT,
        )
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
                    id = match.groups["id"]!!.value,
                    time = match.groups["time"]!!.value.trim(),
                    title = match.groups["title"]!!.value.normalizedTitle(),
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

    private data class ArchiveItem(
        val id: String,
        val time: String,
        val title: String,
    )

    private companion object {
        const val STVR_ARCHIVE_URL = "https://www.stvr.sk/televizia/archiv"
        const val STVR_ARCHIVE_JSON_URL = "https://www.rtvs.sk/json/archive5f.json"
        const val ARCHIVE_TIME_TOLERANCE_MINUTES = 30
        const val MINUTES_PER_DAY = 24 * 60
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val HTML_TAG_REGEX = Regex("<[^>]+>")
        val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]+")
        val WHITESPACE_REGEX = Regex("\\s+")
        val ARCHIVE_ITEM_REGEX = Regex(
            """<div\s+class=["']media["'][^>]*>.*?<a\s+href=["'][^"']*/televizia/archiv/[^/"']+/(?<id>\d+)["'][^>]*>.*?<div\s+class=["']program\s+time--start["'][^>]*>\s*(?<time>\d{2}:\d{2}).*?<a\s+class=["']link["'][^>]*title=["'](?<title>[^"']+)["']""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    }
}
