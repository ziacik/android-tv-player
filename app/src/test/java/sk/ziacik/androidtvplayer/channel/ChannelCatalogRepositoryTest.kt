package sk.ziacik.androidtvplayer.channel

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChannelCatalogRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing bundled catalog and cache returns no initial catalog`() {
        val repository = ChannelCatalogRepository(
            seed = { null },
            cacheFile = File(temporaryFolder.root, "channels.json"),
            download = { error("download must not run") },
        )

        assertNull(repository.loadOrNull())
    }

    @Test
    fun `refresh returns downloaded catalog and updates cache`() = runTest {
        val cacheFile = File(temporaryFolder.root, "channels.json")
        val downloaded = """
            {
              "channels": [
                { "id": "new", "name": "NEW", "url": "https://example.com/new.m3u8" }
              ]
            }
        """.trimIndent()
        val repository = ChannelCatalogRepository(
            seed = { catalog(version = 0, id = "seed") },
            cacheFile = cacheFile,
            download = { downloaded },
        )

        val refreshed = repository.refresh()

        assertEquals(listOf("new"), refreshed.channels.map(TvChannel::storageKey))
        assertEquals(downloaded, cacheFile.readText())
    }

    @Test
    fun `newer bundled catalog replaces an older cached catalog`() {
        val cacheFile = File(temporaryFolder.root, "channels.json")
        cacheFile.writeText(catalog(version = 1, id = "cached"))
        val repository = ChannelCatalogRepository(
            seed = { catalog(version = 2, id = "bundled") },
            cacheFile = cacheFile,
            download = { error("download must not run") },
        )

        assertEquals(listOf("bundled"), repository.load().channels.map(TvChannel::storageKey))
        assertEquals(
            listOf("bundled"),
            ChannelCatalogJsonParser.parse(cacheFile.readText()).channels.map(TvChannel::storageKey),
        )
    }

    @Test
    fun `refresh does not downgrade the active catalog`() = runTest {
        val cacheFile = File(temporaryFolder.root, "channels.json")
        val repository = ChannelCatalogRepository(
            seed = { catalog(version = 2, id = "bundled") },
            cacheFile = cacheFile,
            download = { catalog(version = 1, id = "remote") },
        )

        assertEquals(listOf("bundled"), repository.refresh().channels.map(TvChannel::storageKey))
        assertEquals(
            listOf("bundled"),
            ChannelCatalogJsonParser.parse(cacheFile.readText()).channels.map(TvChannel::storageKey),
        )
    }

    private fun catalog(version: Int, id: String) =
        """{"version":$version,"channels":[{"id":"$id","name":"${id.uppercase()}","url":"https://example.com/$id.m3u8"}]}"""
}
