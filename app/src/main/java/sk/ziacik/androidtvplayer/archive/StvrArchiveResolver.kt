package sk.ziacik.androidtvplayer.archive

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
	private val scheduleMutex = Mutex()
	private val schedules = mutableMapOf<String, ScheduleCacheEntry>()

	suspend fun resolve(
		channel: TvChannel,
		startsAtMs: Long,
	): StreamSource {
		val headers = mapOf("User-Agent" to STVR_USER_AGENT)
		val lookup = findArchive(
			channel = channel,
			startsAtMs = startsAtMs,
			headers = headers,
		)
		val archiveId = lookup.archiveId ?: throw StreamResolveException(
			archiveLookupFailureMessage(lookup),
		)

		val body = httpClient.get("$STVR_ARCHIVE_STREAM_JSON_URL?id=$archiveId", headers)
		val hlsUrl = parser.parse(body).hlsUrl
			?: throw StreamResolveException("STVR archive response does not contain a playable HLS source")

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
			headers = mapOf("User-Agent" to STVR_USER_AGENT),
		).archiveId != null
	}

	private suspend fun findArchive(
		channel: TvChannel,
		startsAtMs: Long,
		headers: Map<String, String>,
	): ArchiveLookup {
		val start = Instant.ofEpochMilli(startsAtMs).atZone(zoneId)
		val schedule = programSchedule(start, headers)
		val section = schedule.channelSection(channel)
		val expectedTime = start.format(TIME_FORMAT)
		val match = section.findExplicitArchiveAt(expectedTime)
		return ArchiveLookup(
			archiveId = match.archiveId,
			expectedTime = expectedTime,
			timeMatches = match.timeMatches,
			archiveIds = match.archiveIds,
			channelSectionChars = section.length,
		)
	}

	private suspend fun programSchedule(
		start: ZonedDateTime,
		headers: Map<String, String>,
	): String {
		val url = "$STVR_PROGRAM_URL?date=${start.toLocalDate()}"
		return scheduleMutex.withLock {
			val currentNowMs = nowMs()
			val cached = schedules[url]
			if (cached != null && cached.isValidFor(start, currentNowMs)) {
				return@withLock cached.html
			}

			httpClient.get(url, headers).also { html ->
				schedules[url] = ScheduleCacheEntry(
					html = html,
					fetchedAtMs = currentNowMs,
				)
			}
		}
	}

	private fun ScheduleCacheEntry.isValidFor(
		start: ZonedDateTime,
		currentNowMs: Long,
	): Boolean {
		val today = Instant.ofEpochMilli(currentNowMs).atZone(zoneId).toLocalDate()
		if (start.toLocalDate() != today) return true
		return currentNowMs - fetchedAtMs < TODAY_SCHEDULE_TTL_MS
	}

	private fun String.channelSection(channel: TvChannel): String {
		val heading = channel.archiveHeading()
			?: throw StreamResolveException("STVR archive is not configured for channel")
		val headingMatch = Regex(
			"<h(?<level>[1-6])[^>]*>\\s*${Regex.escape(heading)}\\s*</h[1-6]>",
			setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
		).find(this) ?: return ""
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

	private fun String.findExplicitArchiveAt(expectedTime: String): ExplicitArchiveMatch {
		val allTimes = PROGRAM_TIME_REGEX.findAll(this).toList()
		val matchingTimes = allTimes.filter { match ->
			match.groups["time"]?.value == expectedTime
		}
		if (matchingTimes.size != 1) {
			return ExplicitArchiveMatch(
				archiveId = null,
				timeMatches = matchingTimes.size,
				archiveIds = emptyList(),
			)
		}

		val timeMatch = matchingTimes.single()
		val nextTime = allTimes.firstOrNull { it.range.first > timeMatch.range.first }
		val programmeHtml = substring(
			timeMatch.range.last + 1,
			nextTime?.range?.first ?: length,
		)
		val archiveIds = ARCHIVE_LINK_REGEX.findAll(programmeHtml)
			.map { match -> requireNotNull(match.groups["id"]).value }
			.distinct()
			.toList()

		return ExplicitArchiveMatch(
			archiveId = archiveIds.singleOrNull(),
			timeMatches = 1,
			archiveIds = archiveIds,
		)
	}

	private fun archiveLookupFailureMessage(lookup: ArchiveLookup): String =
		buildString {
			append("STVR programme does not contain an explicit archive link")
			append("; expectedTime=").append(lookup.expectedTime)
			append("; exactTimeMatches=").append(lookup.timeMatches)
			append("; archiveIds=").append(lookup.archiveIds.joinToString(","))
			append("; channelSectionChars=").append(lookup.channelSectionChars)
		}

	private data class ScheduleCacheEntry(
		val html: String,
		val fetchedAtMs: Long,
	)

	private data class ExplicitArchiveMatch(
		val archiveId: String?,
		val timeMatches: Int,
		val archiveIds: List<String>,
	)

	private data class ArchiveLookup(
		val archiveId: String?,
		val expectedTime: String,
		val timeMatches: Int,
		val archiveIds: List<String>,
		val channelSectionChars: Int,
	)

	private companion object {
		const val STVR_PROGRAM_URL = "https://www.stvr.sk/televizia/program/"
		const val STVR_ARCHIVE_STREAM_JSON_URL = "https://www.rtvs.sk/json/archive5f.json"
		const val TODAY_SCHEDULE_TTL_MS = 5 * 60_000L
		val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
		val PROGRAM_TIME_REGEX = Regex(
			""">\s*(?<time>(?:[01]\d|2[0-3]):[0-5]\d)\s*<""",
		)
		val ARCHIVE_LINK_REGEX = Regex(
			"""href\s*=\s*["'](?:https?://www\.stvr\.sk)?/televizia/archiv/\d+/(?<id>\d+)(?:[^\d]|$)""",
			RegexOption.IGNORE_CASE,
		)
	}
}
