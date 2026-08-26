package sk.ziacik.androidtvplayer.resolver

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import sk.ziacik.androidtvplayer.channel.TvChannel

class SweetTvResolver(private val http: FreeviewHttpClient) {
    suspend fun resolve(channel: TvChannel): StreamResolution = try {
        val channelId = requireNotNull(channel.providerValue).toInt()
        val request = JSONObject()
            .put("without_auth", true)
            .put("channel_id", channelId)
            .put("accept_scheme", JSONArray().put(SCHEME))
            .put("multistream", true)
        val response = JSONObject(http.postJson(API_URL, request.toString(), HEADERS))
        val source = response.optString("url")
        if (
            response.optString("result") != "OK" ||
            response.optString("scheme") != SCHEME ||
            !source.startsWith("https://")
        ) {
            throw StreamResolveException("SWEET.TV did not return an HLS stream")
        }

        StreamResolution.Playable(
            program = ProgramMetadata(channel.displayName, null, null, true),
            source = StreamSource(
                url = source,
                userAgent = USER_AGENT,
                headers = HEADERS,
                manifest = StreamManifest.HLS,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: StreamResolveException) {
        throw error
    } catch (error: Exception) {
        throw StreamResolveException("SWEET.TV request failed", error)
    }

    private companion object {
        const val API_URL = "https://api.sweet.tv/TvService/OpenStream.json"
        const val SCHEME = "HTTP_HLS"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "Chrome/143.0.0.0 Safari/537.36"
        val HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept-Language" to "sk-SK,sk;q=0.9,en;q=0.8",
            "Origin" to "https://sweet.tv",
            "Referer" to "https://sweet.tv/",
            "x-device" to "1;22;0;2;3.7.1",
        )
    }
}
