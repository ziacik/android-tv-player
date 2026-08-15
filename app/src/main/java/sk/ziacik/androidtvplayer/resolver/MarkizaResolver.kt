package sk.ziacik.androidtvplayer.resolver

import kotlinx.coroutines.CancellationException
import sk.ziacik.androidtvplayer.channel.TvChannel

const val MARKIZA_LOGIN_URL = "https://www.markiza.sk/prihlasenie"
const val MARKIZA_LIVE_URL = "https://www.markiza.sk/live/1-markiza"
const val MARKIZA_ORIGIN = "https://media.cms.markiza.sk/"
const val MARKIZA_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "Chrome/133.0.0.0 Safari/537.36"

data class MarkizaCredentials(val email: String, val password: String)

data class MarkizaHttpResponse(val code: Int, val body: String)

interface MarkizaHttpClient {
    suspend fun get(url: String, headers: Map<String, String>): MarkizaHttpResponse

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): MarkizaHttpResponse
}

class MarkizaResolver(
    private val httpClient: MarkizaHttpClient,
    private val credentials: () -> MarkizaCredentials?,
) {
    suspend fun resolve(channel: TvChannel): StreamResolution {
        val liveUrl = requireNotNull(channel.providerValue)
        val account = credentials()?.takeIf {
            it.email.isNotBlank() && it.password.isNotBlank()
        } ?: return StreamResolution.RequiresCredentials(channel)
        val headers = mapOf("User-Agent" to MARKIZA_USER_AGENT)

        return try {
            val loginPage = httpClient.get(MARKIZA_LOGIN_URL, headers)
            requireSuccess(loginPage, "Markíza login page")
            val login = httpClient.postForm(
                url = MARKIZA_LOGIN_URL,
                form = mapOf(
                    "email" to account.email,
                    "password" to account.password,
                    "_do" to loginToken(loginPage.body),
                ),
                headers = headers + ("Referer" to MARKIZA_LOGIN_URL),
            )
            if (login.code != 302) return StreamResolution.RequiresCredentials(channel)

            val livePage = httpClient.get(
                liveUrl,
                headers + ("Referer" to MARKIZA_LOGIN_URL),
            )
            requireSuccess(livePage, "Markíza live page")
            val embedPage = httpClient.get(
                embedUrl(livePage.body),
                headers + ("Referer" to liveUrl),
            )
            requireSuccess(embedPage, "Markíza embed page")

            StreamResolution.Playable(
                program = ProgramMetadata(channel.displayName, null, null, true),
                source = StreamSource(
                    url = hlsUrl(embedPage.body),
                    userAgent = MARKIZA_USER_AGENT,
                    headers = mapOf(
                        "User-Agent" to MARKIZA_USER_AGENT,
                        "Referer" to MARKIZA_ORIGIN,
                        "Origin" to MARKIZA_ORIGIN,
                    ),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            throw error
        } catch (error: Exception) {
            throw StreamResolveException("Markíza request failed", error)
        }
    }

    private fun requireSuccess(response: MarkizaHttpResponse, page: String) {
        if (response.code !in 200..299) throw StreamResolveException("$page request failed")
    }

    private fun loginToken(body: String): String =
        LOGIN_TOKEN.find(body)?.groupValues?.get(1)
            ?: throw StreamResolveException("Markíza login form token is missing")

    private fun embedUrl(body: String): String =
        EMBED_URL.find(body)?.groupValues?.get(1)
            ?: throw StreamResolveException("Markíza player embed is missing")

    private fun hlsUrl(body: String): String =
        HLS_URL.find(body)?.groupValues?.get(1)?.replace("\\/", "/")
            ?: throw StreamResolveException("Markíza HLS source is missing")

    private companion object {
        val LOGIN_TOKEN = Regex("""<input[^>]+name="_do"[^>]+value="([^"]+)"""")
        val EMBED_URL = Regex("""<iframe[^>]+data-src="([^"]+)""", RegexOption.DOT_MATCHES_ALL)
        val HLS_URL = Regex(
            """[\"]source[\"]\s*:\s*\{\s*[\"]sources[\"]\s*:\s*\[\s*\{\s*[\"]src[\"]\s*:\s*[\"]([^\"]+)[\"]\s*,\s*[\"]type[\"]\s*:\s*[\"]application/x-mpegurl[\"]""",
        )
    }
}
