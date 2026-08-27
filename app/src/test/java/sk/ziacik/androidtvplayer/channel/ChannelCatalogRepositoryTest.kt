package sk.ziacik.androidtvplayer.channel

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChannelCatalogRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
            seed = { error("seed must not be read") },
            cacheFile = cacheFile,
            download = { downloaded },
        )

        val refreshed = repository.refresh()

        assertEquals(listOf("new"), refreshed.channels.map(TvChannel::storageKey))
        assertEquals(downloaded, cacheFile.readText())
    }
}
