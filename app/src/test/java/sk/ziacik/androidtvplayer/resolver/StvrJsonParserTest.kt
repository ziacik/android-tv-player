package sk.ziacik.androidtvplayer.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StvrJsonParserTest {
    private val validJson = """
        {"clip":{"sources":[
          {"src":"https://cdn.example/live.mpd","type":"application/dash+xml"},
          {"src":"https://cdn.example/live.m3u8?auth=secret","type":"application/x-mpegurl"}
        ]}}
    """.trimIndent()

    @Test
    fun `selects HLS source`() {
        assertEquals(
            "https://cdn.example/live.m3u8?auth=secret",
            StvrJsonParser().parseHlsUrl(validJson),
        )
    }

    @Test
    fun `rejects response without HLS`() {
        assertThrows(StreamResolveException::class.java) {
            StvrJsonParser().parseHlsUrl("""{"clip":{"sources":[]}}""")
        }
    }

    @Test
    fun `reports invalid JSON`() {
        val error = assertThrows(StreamResolveException::class.java) {
            StvrJsonParser().parseHlsUrl("not-json")
        }

        assertEquals("STVR returned invalid JSON", error.message)
    }
}
