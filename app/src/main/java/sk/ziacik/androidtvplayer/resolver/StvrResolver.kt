package sk.ziacik.androidtvplayer.resolver

import kotlinx.coroutines.CancellationException
import sk.ziacik.androidtvplayer.channel.TvChannel

const val STVR_LANDING_URL = "https://www.rtvs.sk/televizia/tv"
private const val STVR_LIVE_BASE_URL = "https://www.rtvs.sk/json/live5f.json"
const val STVR_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "Chrome/77.0.3865.90 Safari/537.36"

internal fun stvrLiveUrl(channel: TvChannel): String =
    "$STVR_LIVE_BASE_URL?c=${requireNotNull(channel.stvrId)}&ad=1&b=chrome&p=win&v=77&f=0&d=1"

class StvrResolver(
    private val httpClient: StvrHttpClient,
    private val parser: StvrJsonParser = StvrJsonParser(),
) {
    suspend fun resolve(channel: TvChannel): StreamResolution {
        val headers = mapOf("User-Agent" to STVR_USER_AGENT)

        return try {
            httpClient.get(STVR_LANDING_URL, headers)
            val body = httpClient.get(stvrLiveUrl(channel), headers)
            val parsed = parser.parse(body)
            if (parsed.program.internetAllowed == false) {
                return StreamResolution.Unavailable(parsed.program)
            }
            val hlsUrl = parsed.hlsUrl
                ?: throw StreamResolveException(
                    "STVR response does not contain an HLS source",
                )
            StreamResolution.Playable(
                program = parsed.program,
                source = StreamSource(
                    url = hlsUrl,
                    userAgent = STVR_USER_AGENT,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            throw error
        } catch (error: Exception) {
            throw StreamResolveException("STVR request failed", error)
        }
    }
}
