package sk.ziacik.androidtvplayer.resolver

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import sk.ziacik.androidtvplayer.channel.TvChannel

private const val FREEVIEW_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "Chrome/143.0.0.0 Safari/537.36"

private fun playable(
    channel: TvChannel,
    url: String,
    headers: Map<String, String> = emptyMap(),
    manifest: StreamManifest = StreamManifest.HLS,
) =
    StreamResolution.Playable(
        ProgramMetadata(channel.displayName, null, null, true),
        StreamSource(url, FREEVIEW_USER_AGENT, headers, manifest),
    )

class JojResolver(private val http: FreeviewHttpClient) {
    suspend fun resolve(channel: TvChannel): StreamResolution = protect("JOJ") {
        val definition = requireNotNull(JOJ_CHANNELS[channel])
        val source = definition.id?.let { id ->
            try {
                sourceUrl(id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        } ?: definition.fallbackUrl
        playable(
            channel = channel,
            url = source,
            headers = JOJ_HEADERS,
            manifest = if (source.endsWith(".mpd")) StreamManifest.DASH else StreamManifest.HLS,
        )
    }

    private suspend fun sourceUrl(id: String): String {
        val payload = JSONObject().put(
            "data",
            JSONObject()
                .put("id", id)
                .put("documentType", "tvChannel")
                .put("sourceHistory", JSONArray())
                .put(
                    "capabilities",
                    JSONArray()
                        .put(JSONObject().put("codec", "h264").put("protocol", "dash").put("encryption", "none"))
                        .put(JSONObject().put("codec", "h264").put("protocol", "hls").put("encryption", "none")),
                ),
        )
        return JSONObject(http.postJson(SOURCE_URL, payload.toString(), JOJ_HEADERS))
            .getJSONObject("result")
            .getString("url")
    }

    private companion object {
        const val SOURCE_URL = "https://europe-west3-tivio-production.cloudfunctions.net/getSourceUrl"
        val JOJ_HEADERS = mapOf("User-Agent" to FREEVIEW_USER_AGENT, "Referer" to "https://www.joj.sk/")
        val JOJ_CHANNELS = mapOf(
            TvChannel.JOJ to JojChannel("LYyAwEjjqmj8kMY23Lqw", "https://live.cdn.joj.sk/live/joj.m3u8"),
            TvChannel.JOJ_PLUS to JojChannel("60K9GwR6CLApIHVyNYOj", "https://live.cdn.joj.sk/live/plus.m3u8"),
            TvChannel.JOJ_KRIMI to JojChannel("0D9v2CuujVAlLJJTyLWd", "https://live.cdn.joj.sk/live/wau.m3u8"),
            TvChannel.JOJ_SPORT to JojChannel(null, "https://live.cdn.joj.sk/live/andromeda/joj_sport-1080.m3u8"),
            TvChannel.JOJ_SPORT_2 to JojChannel(null, "https://live.cdn.joj.sk/live/joj_sport2.m3u8"),
            TvChannel.JOJ_FAMILY to JojChannel(null, "https://live.cdn.joj.sk/live/family.m3u8"),
            TvChannel.JOJKO to JojChannel(null, "https://live.cdn.joj.sk/live/jojko.m3u8"),
            TvChannel.JOJ_24 to JojChannel("7tl6We5FhLyCfZcmSG6F", "https://live.cdn.joj.sk/live/joj_news.m3u8"),
            TvChannel.JOJ_CINEMA to JojChannel(null, "https://live.cdn.joj.sk/live/cinema.m3u8"),
            TvChannel.CS_FILM to JojChannel(null, "https://live.cdn.joj.sk/live/cs_film.m3u8"),
            TvChannel.CS_HISTORY to JojChannel(null, "https://live.cdn.joj.sk/live/cs_history.m3u8"),
            TvChannel.CS_MYSTERY to JojChannel(null, "https://live.cdn.joj.sk/live/cs_mystery.m3u8"),
        )
    }

    private data class JojChannel(val id: String?, val fallbackUrl: String)
}

class CtResolver(private val http: FreeviewHttpClient) {
    suspend fun resolve(channel: TvChannel): StreamResolution = protect("ČT") {
        val id = requireNotNull(channel.providerValue)
        val url = "$API$id?canPlayDrm=false&streamType=hls&quality=web&maxQualityCount=5"
        val body = JSONObject(http.get(url, HEADERS))
        playable(channel, body.getJSONObject("streamUrls").getString("main"))
    }

    private companion object {
        const val API = "https://api.ceskatelevize.cz/video/v1/playlist-live/v1/stream-data/channel/"
        val HEADERS = mapOf("User-Agent" to FREEVIEW_USER_AGENT)
    }
}

class Ta3Resolver(private val http: FreeviewHttpClient) {
    suspend fun resolve(channel: TvChannel): StreamResolution = protect("TA3") {
        val script = http.get(SOURCE_URL, HEADERS)
        val source = SOURCE.findAll(script)
            .map { it.groupValues[1] }
            .firstOrNull { it.contains("1.smil") }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.replace("|", "%7C")
            ?: throw StreamResolveException("TA3 source is missing")
        playable(channel, source, HEADERS)
    }

    private companion object {
        const val SOURCE_URL = "https://embed.livebox.cz/ta3_v2/live-source.js"
        val HEADERS = mapOf("User-Agent" to FREEVIEW_USER_AGENT)
        val SOURCE = Regex("""\"src\"\s*:\s*\"([^\"]+)""")
    }
}

class NovaResolver(private val http: FreeviewHttpClient) {
    suspend fun resolve(channel: TvChannel): StreamResolution = protect("Nova") {
        val headers = mapOf(
            "User-Agent" to FREEVIEW_USER_AGENT,
            "Referer" to MEDIA_HOST,
            "Origin" to MEDIA_HOST,
            "X-Forwarded-For" to "37.48.1.1",
        )
        val body = http.get("$MEDIA_HOST/embed/nova-cinema-live?autoplay=1", headers)
        val source = SOURCES.find(body)?.groupValues?.get(1)?.replace("\\/", "/")
            ?: throw StreamResolveException("Nova source is missing")
        playable(channel, source, headers)
    }

    private companion object {
        const val MEDIA_HOST = "https://media.cms.nova.cz"
        val SOURCES = Regex("""\{\"sources\":\[\{\"src\":\"([^\"]+)\",\"type\":\"application""")
    }
}

class CnnPrimaNewsResolver(private val http: FreeviewHttpClient) {
    suspend fun resolve(channel: TvChannel): StreamResolution = protect("CNN Prima News") {
        val body = JSONObject(http.get(SOURCE_URL, HEADERS))
        val source = body.getJSONArray("streamInfos").getJSONObject(0).getString("url").replace("_lq", "")
        playable(channel, source, HEADERS)
    }

    private companion object {
        const val SOURCE_URL = "https://api.play-backend.iprima.cz/api/v1/products/id-p650443/play"
        val HEADERS = mapOf("User-Agent" to FREEVIEW_USER_AGENT)
    }
}

class DirectResolver {
    suspend fun resolve(channel: TvChannel): StreamResolution =
        playable(channel, requireNotNull(channel.providerValue))
}

private suspend fun protect(provider: String, block: suspend () -> StreamResolution): StreamResolution = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: StreamResolveException) {
    throw error
} catch (error: Exception) {
    throw StreamResolveException("$provider request failed", error)
}
