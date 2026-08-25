package sk.ziacik.androidtvplayer.resolver

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel

class SweetTvResolverTest {
    @Test
    fun `requests anonymous HLS stream and returns playable source`() = runTest {
        val http = RecordingHttpClient(
            response =
                """{"result":"OK","scheme":"HTTP_HLS","url":"https://cdn.example/live.m3u8","update_interval":300}""",
        )

        val result = SweetTvResolver(http).resolve(TvChannel.SVET_NARUBY) as StreamResolution.Playable

        assertEquals("https://api.sweet.tv/TvService/OpenStream.json", http.requestUrl)
        val request = JSONObject(http.requestBody)
        assertTrue(request.getBoolean("without_auth"))
        assertEquals(3257, request.getInt("channel_id"))
        assertEquals(listOf("HTTP_HLS"), request.getJSONArray("accept_scheme").let { array ->
            List(array.length()) { index -> array.getString(index) }
        })
        assertTrue(request.getBoolean("multistream"))
        assertFalse(http.requestHeaders.keys.any { it.equals("Authorization", ignoreCase = true) })
        assertEquals("https://sweet.tv", http.requestHeaders["Origin"])
        assertEquals("https://sweet.tv/", http.requestHeaders["Referer"])
        assertEquals("https://cdn.example/live.m3u8", result.source.url)
        assertEquals(StreamManifest.HLS, result.source.manifest)
        assertEquals(http.requestHeaders, result.source.headers)
    }

    @Test
    fun `rejects a response that is not an anonymous HLS stream`() = runTest {
        val http = RecordingHttpClient(
            response = """{"result":"OK","scheme":"HTTP_DASH","url":"https://cdn.example/live.mpd"}""",
        )

        val error = try {
            SweetTvResolver(http).resolve(TvChannel.SVET_NARUBY)
            null
        } catch (error: StreamResolveException) {
            error
        }

        assertEquals("SWEET.TV did not return an HLS stream", error?.message)
    }

    @Test
    fun `wraps malformed API response without exposing its contents`() = runTest {
        val http = RecordingHttpClient(response = "not-json-with-private-data")

        val error = try {
            SweetTvResolver(http).resolve(TvChannel.SVET_NARUBY)
            null
        } catch (error: StreamResolveException) {
            error
        }

        assertEquals("SWEET.TV request failed", error?.message)
        assertFalse(error.toString().contains("private-data"))
    }

    private class RecordingHttpClient(private val response: String) : FreeviewHttpClient {
        var requestUrl = ""
        var requestBody = ""
        var requestHeaders = emptyMap<String, String>()

        override suspend fun get(url: String, headers: Map<String, String>): String = error("unused")

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
        ): String {
            requestUrl = url
            requestBody = body
            requestHeaders = headers
            return response
        }
    }
}
