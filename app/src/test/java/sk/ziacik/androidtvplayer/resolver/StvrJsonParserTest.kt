package sk.ziacik.androidtvplayer.resolver

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class StvrJsonParserTest {
    private val zoneId = ZoneId.of("Europe/Bratislava")
    private val parser = StvrJsonParser(zoneId)

    @Test
    fun `parses program metadata and HLS source`() {
        val parsed = parser.parse(
            response(
                internet = "Y",
                series = "Ordinácia v Eifeli",
                subtitle = "Šance",
                timestart = "1786785969000",
                timestop = "1786791292000",
                source = "https://cdn.example/live.m3u8?auth=secret",
            ),
        )

        assertEquals("Ordinácia v Eifeli: Šance", parsed.program.title)
        assertEquals(1_786_785_969_000L, parsed.program.startsAtMs)
        assertEquals(1_786_791_292_000L, parsed.program.endsAtMs)
        assertEquals(true, parsed.program.internetAllowed)
        assertEquals("https://cdn.example/live.m3u8?auth=secret", parsed.hlsUrl)
    }

    @Test
    fun `uses ISO timestamp fallback and title fallback order`() {
        val parsed = parser.parse(
            """{"clip":{
              "title":"Fallback title",
              "titleorig":"Original title",
              "internet":"N",
              "dateTimeStart":"2026-08-15T11:25:00",
              "dateTimeStop":"2026-08-15T12:53:44",
              "sources":[]
            }}""",
        )

        assertEquals("Original title", parsed.program.title)
        assertEquals(false, parsed.program.internetAllowed)
        assertNull(parsed.hlsUrl)
        assertEquals(
            LocalDateTime.parse("2026-08-15T12:53:44")
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli(),
            parsed.program.endsAtMs,
        )
    }

    @Test
    fun `unknown internet flag stays unknown`() {
        val parsed = parser.parse(
            response(
                internet = "",
                source = "https://cdn.example/live.m3u8",
            ),
        )

        assertNull(parsed.program.internetAllowed)
    }

    @Test
    fun `missing HLS stays representable for unavailable programs`() {
        val parsed = parser.parse(response(internet = "N", source = null))

        assertNull(parsed.hlsUrl)
    }

    @Test
    fun `reports invalid JSON`() {
        val error = assertThrows(StreamResolveException::class.java) {
            parser.parse("not-json")
        }

        assertEquals("STVR returned invalid JSON", error.message)
    }

    private fun response(
        internet: String,
        series: String = "",
        subtitle: String = "",
        timestart: String = "",
        timestop: String = "",
        source: String?,
    ): String {
        val sourceJson = source?.let {
            """{"src":"$it","type":"application/x-mpegurl"}"""
        }.orEmpty()
        return """{"clip":{
          "internet":"$internet",
          "series":"$series",
          "subtitle":"$subtitle",
          "timestart":"$timestart",
          "timestop":"$timestop",
          "sources":[$sourceJson]
        }}"""
    }
}
