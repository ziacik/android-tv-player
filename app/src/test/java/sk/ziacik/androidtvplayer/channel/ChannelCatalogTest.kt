package sk.ziacik.androidtvplayer.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelCatalogTest {
    @Test
    fun `parses HTTP and HTTPS direct channels in file order`() {
        val catalog = ChannelCatalogJsonParser.parse(
            """
            {
              "version": 1,
              "channels": [
                { "id": "first", "name": "FIRST", "url": "https://example.com/first.m3u8" },
                { "id": "second", "name": "SECOND", "url": "http://example.com/second.m3u8" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("first", "second"), catalog.channels.map(TvChannel::storageKey))
        assertEquals(ChannelProvider.DIRECT, catalog.channels[0].provider)
        assertEquals("http://example.com/second.m3u8", catalog.channels[1].providerValue)
    }

    @Test
    fun `parses AceStream direct channels`() {
        val catalog = ChannelCatalogJsonParser.parse(
            """{"channels":[{"id":"ace","name":"ACE","url":"acestream://94c2fd8fb9bc8f2fc71a2cbe9d4b866f227a0209"}]}""",
        )

        assertEquals(ChannelProvider.DIRECT, catalog.channels.single().provider)
        assertEquals(
            "acestream://94c2fd8fb9bc8f2fc71a2cbe9d4b866f227a0209",
            catalog.channels.single().providerValue,
        )
    }

    @Test
    fun `parses Open EPG channel identifier`() {
        val catalog = ChannelCatalogJsonParser.parse(
            """{"channels":[{"id":"markiza","name":"MARKÍZA","url":"https://example.com/live.m3u8","epg":{"openEpg":"Markíza HD.sk","skylink":"skylink-id","iptvOrg":"obsolete"}}]}""",
        )

        assertEquals("Markíza HD.sk", catalog.channels.single().epgIds[EpgSourceId.OPEN_EPG])
        assertEquals("skylink-id", catalog.channels.single().epgIds[EpgSourceId.SKYLINK])
        assertEquals(2, catalog.channels.single().epgIds.size)
    }

    @Test
    fun `skips malformed duplicate and unsupported scheme entries`() {
        val catalog = ChannelCatalogJsonParser.parse(
            """
            {
              "channels": [
                { "id": "valid", "name": "VALID", "url": "https://example.com/live.m3u8" },
                { "id": "valid", "name": "DUPLICATE", "url": "https://example.com/duplicate.m3u8" },
                { "id": "ftp", "name": "FTP", "url": "ftp://example.com/live.m3u8" },
                { "id": "missing-url", "name": "MISSING" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("valid"), catalog.channels.map(TvChannel::storageKey))
    }

    @Test
    fun `looks up one based positions storage keys and wraparound`() {
        val first = direct("first")
        val second = direct("second")
        val catalog = ChannelCatalog(listOf(first, second))

        assertEquals(first, catalog.fromChannelNumber(1))
        assertEquals(second, catalog.fromChannelNumber(2))
        assertNull(catalog.fromChannelNumber(0))
        assertNull(catalog.fromChannelNumber(3))
        assertEquals(second, catalog.fromStorageKey("second"))
        assertEquals(first, catalog.fromStorageKey("unknown"))
        assertEquals(second, catalog.next(first))
        assertEquals(first, catalog.next(second))
        assertEquals(second, catalog.previous(first))
        assertEquals(2, catalog.numberOf(second))
    }

    private fun direct(id: String) = TvChannel(
        storageKey = id,
        displayName = id.uppercase(),
        provider = ChannelProvider.DIRECT,
        providerValue = "https://example.com/$id.m3u8",
    )
}
