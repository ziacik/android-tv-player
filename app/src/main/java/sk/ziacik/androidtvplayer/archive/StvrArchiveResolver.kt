package sk.ziacik.androidtvplayer.archive

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
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
				expectedTime = lookup.expectedTime,
				expectedTitle = title,
				originalStart = lookup.originalStart,
			),
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
		val listing = archiveListing(start, channel, headers)
		val directArchiveId = findArchiveId(
			listing = listing,
			startTime = startTime,
			title = title,
		)

		val originalStart = originalStartsAtMs
			?.takeIf { it != startsAtMs }
			?.let { Instant.ofEpochMilli(it).atZone(zoneId) }
		val archiveId = directArchiveId ?: originalStart?.let { original ->
			findArchiveId(
				listing = archiveListing(original, channel, headers),
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
		channel: TvChannel,
		headers: Map<String, String>,
	): String {
		val url = archiveApiUrl(channel, start)
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

	private fun archiveApiUrl(
		channel: TvChannel,
		start: ZonedDateTime,
	): String {
		channel.archive
			?.takeIf { it.provider == ArchiveProvider.STVR }
			?: throw StreamResolveException("STVR archive is not configured for channel")
		return buildString {
			append(STVR_ARCHIVE_API_URL)
			append("?e=1&archive=1")
			append("&d=").append(start.toLocalDate())
			append("&p=1&l=100&o=desc")
		}
	}

	private fun findArchiveId(
		listing: String,
		startTime: String,
		title: String,
	): String? {
		val expectedMinutes = startTime.minutesOfDay() ?: return null
		val expectedTitle = title.normalizedTitle()
		val nearby = parseArchiveItems(listing)
			.map { item -> item to minuteDistance(expectedMinutes, item.airMinutes) }
			.filter { (_, distance) -> distance <= ARCHIVE_TIME_TOLERANCE_MINUTES }

		val exact = nearby
			.filter { (item, _) -> item.title == expectedTitle }
			.minByOrNull { (_, distance) -> distance }
		if (exact != null) return exact.first.id

		return nearby
			.filter { (item, _) ->
				item.title.contains(expectedTitle) || expectedTitle.contains(item.title)
			}
			.minByOrNull { (_, distance) -> distance }
			?.first
			?.id
	}

	private fun parseArchiveItems(listing: String): List<ArchiveItem> {
		return try {
			val programs = JSONObject(listing).optJSONArray("program") ?: return emptyList()
			(0 until programs.length()).mapNotNull { index ->
				val item = programs.optJSONObject(index) ?: return@mapNotNull null
				val id = item.optLong("ID", -1L).takeIf { it > 0L }?.toString()
					?: return@mapNotNull null
				val title = item.optString("name").normalizedTitle()
				if (title.isBlank()) return@mapNotNull null
				val air = item.optString("air")
				val airMinutes = runCatching {
					LocalDateTime.parse(air, ARCHIVE_AIR_FORMAT).let {
						it.hour * 60 + it.minute
					}
				}.getOrNull() ?: return@mapNotNull null
				ArchiveItem(
					id = id,
					title = title,
					airMinutes = airMinutes,
				)
			}
		} catch (error: Exception) {
			throw StreamResolveException("STVR archive API returned invalid JSON", error)
		}
	}

	private fun archiveLookupFailureMessage(
		listing: String,
		expectedTime: String,
		expectedTitle: String,
		originalStart: ZonedDateTime?,
	): String {
		val archiveItems = parseArchiveItems(listing)
		return buildString {
			append("STVR archive item was not found")
			append("; expectedTime=").append(expectedTime)
			append("; expectedTitle=").append(expectedTitle.normalizedTitle())
			append("; archiveItems=").append(archiveItems.size)
			append("; archiveIds=").append(
				archiveItems.take(MAX_DIAGNOSTIC_IDS).joinToString(",") { it.id },
			)
			originalStart?.let {
				append("; originalDate=").append(it.toLocalDate())
				append("; originalTime=").append(it.format(TIME_FORMAT))
			}
		}
	}

	private fun String.normalizedTitle(): String =
		lowercase(Locale.ROOT)
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
		val title: String,
		val airMinutes: Int,
	)

	private companion object {
		const val STVR_ARCHIVE_API_URL = "https://www.stvr.sk/json/tv/archiv"
		const val STVR_ARCHIVE_STREAM_JSON_URL = "https://www.rtvs.sk/json/archive5f.json"
		const val ARCHIVE_TIME_TOLERANCE_MINUTES = 30
		const val TODAY_ARCHIVE_LISTING_TTL_MS = 5 * 60_000L
		const val MINUTES_PER_DAY = 24 * 60
		const val MAX_DIAGNOSTIC_IDS = 12
		val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
		val ARCHIVE_AIR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]+")
		val WHITESPACE_REGEX = Regex("\\s+")
	}
}
