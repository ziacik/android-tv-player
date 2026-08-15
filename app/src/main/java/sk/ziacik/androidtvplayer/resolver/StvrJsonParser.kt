package sk.ziacik.androidtvplayer.resolver

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.json.JSONObject

class StvrJsonParser(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    internal fun parse(body: String): ParsedStvrClip = try {
        val clip = JSONObject(body).getJSONObject("clip")
        ParsedStvrClip(
            program = ProgramMetadata(
                title = clip.programTitle(),
                startsAtMs = clip.timestampMs("timestart", "dateTimeStart"),
                endsAtMs = clip.timestampMs("timestop", "dateTimeStop"),
                internetAllowed = clip.internetAllowed(),
            ),
            hlsUrl = clip.hlsUrl(),
        )
    } catch (error: StreamResolveException) {
        throw error
    } catch (error: Exception) {
        throw StreamResolveException("STVR returned invalid JSON", error)
    }

    private fun JSONObject.programTitle(): String {
        val series = optString("series").trim()
        val subtitle = optString("subtitle").trim()
        return when {
            series.isNotEmpty() && subtitle.isNotEmpty() -> "$series: $subtitle"
            series.isNotEmpty() -> series
            optString("titleorig").isNotBlank() -> optString("titleorig").trim()
            optString("title").isNotBlank() -> optString("title").trim()
            else -> "Aktuálny program"
        }
    }

    private fun JSONObject.timestampMs(
        epochKey: String,
        isoKey: String,
    ): Long? = optString(epochKey)
        .toLongOrNull()
        ?.takeIf { it > 0L }
        ?: optString(isoKey)
            .takeIf(String::isNotBlank)
            ?.let { value ->
                runCatching {
                    LocalDateTime.parse(value)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()
            }

    private fun JSONObject.internetAllowed(): Boolean? = when (
        optString("internet").trim().uppercase(Locale.ROOT)
    ) {
        "Y" -> true
        "N" -> false
        else -> null
    }

    private fun JSONObject.hlsUrl(): String? {
        val sources = optJSONArray("sources") ?: return null
        return (0 until sources.length())
            .asSequence()
            .map(sources::getJSONObject)
            .firstOrNull { source ->
                source.optString("type") == "application/x-mpegurl"
            }
            ?.optString("src")
            ?.takeIf(String::isNotBlank)
    }
}
