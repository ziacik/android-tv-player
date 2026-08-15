package sk.ziacik.androidtvplayer.resolver

import org.json.JSONObject

class StvrJsonParser {
    fun parseHlsUrl(body: String): String = try {
        val sources = JSONObject(body)
            .getJSONObject("clip")
            .getJSONArray("sources")

        (0 until sources.length())
            .asSequence()
            .map(sources::getJSONObject)
            .firstOrNull { source ->
                source.optString("type") == "application/x-mpegurl"
            }
            ?.getString("src")
            ?.takeIf(String::isNotBlank)
            ?: throw StreamResolveException(
                "STVR response does not contain an HLS source",
            )
    } catch (error: StreamResolveException) {
        throw error
    } catch (error: Exception) {
        throw StreamResolveException("STVR returned invalid JSON", error)
    }
}
