package sk.ziacik.androidtvplayer.epg

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sk.ziacik.androidtvplayer.channel.TvChannel

class EpgRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `returns programme containing the supplied instant`() = runTest {
        val repository = repository(
            downloader = { gzip(xml("JEDNOTKA.cz", "180000 +0200", "190000 +0200", "Správy")) },
        )

        assertEquals("Správy", repository.currentProgram(TvChannel.JEDNOTKA, NOW_MS)?.title)
    }

    @Test
    fun `uses fresh cached feed without downloading again`() = runTest {
        val cacheFile = cacheFile()
        cacheFile.writeBytes(gzip(xml("JEDNOTKA.cz", "180000 +0200", "190000 +0200", "Cache")))
        cacheFile.setLastModified(NOW_MS)
        val repository = repository(cacheFile, downloader = { error("download must not run") })

        assertEquals("Cache", repository.currentProgram(TvChannel.JEDNOTKA, NOW_MS)?.title)
    }

    @Test
    fun `uses last parsed cache when stale refresh fails`() = runTest {
        val cacheFile = cacheFile()
        cacheFile.writeBytes(gzip(xml("JEDNOTKA.cz", "180000 +0200", "190000 +0200", "Cache")))
        cacheFile.setLastModified(NOW_MS - SIX_HOURS_MS - 1L)
        val repository = repository(cacheFile, downloader = { throw IOException("offline") })

        assertEquals("Cache", repository.currentProgram(TvChannel.JEDNOTKA, NOW_MS)?.title)
    }

    @Test
    fun `returns null for unmapped channel without downloading`() = runTest {
        var calls = 0
        val repository = repository(downloader = { calls += 1; byteArrayOf() })

        assertNull(repository.currentProgram(TvChannel.WATERBEAR, NOW_MS))
        assertEquals(0, calls)
    }

    @Test
    fun `keeps valid cache when downloaded feed is malformed`() = runTest {
        val cacheFile = cacheFile()
        val cachedBytes = gzip(xml("JEDNOTKA.cz", "180000 +0200", "190000 +0200", "Cache"))
        cacheFile.writeBytes(cachedBytes)
        cacheFile.setLastModified(NOW_MS - SIX_HOURS_MS - 1L)
        val repository = repository(
            cacheFile,
            downloader = { gzip("<tv><programme") },
        )

        assertEquals("Cache", repository.currentProgram(TvChannel.JEDNOTKA, NOW_MS)?.title)
        assertEquals(cachedBytes.toList(), cacheFile.readBytes().toList())
    }

    @Test
    fun `selects programme starting at an interval boundary`() = runTest {
        val repository = repository(
            downloader = {
                gzip(
                    """<tv>
                        <programme channel="JEDNOTKA.cz" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Prvý</title></programme>
                        <programme channel="JEDNOTKA.cz" start="20260816190000 +0200" stop="20260816200000 +0200"><title>Druhý</title></programme>
                    </tv>""",
                )
            },
        )

        assertEquals("Druhý", repository.currentProgram(TvChannel.JEDNOTKA, 1_786_899_600_000L)?.title)
    }

    private fun repository(
        cacheFile: File = cacheFile(),
        downloader: suspend () -> ByteArray,
    ) = CachedXmltvEpgRepository(
        cacheFile = cacheFile,
        download = downloader,
        clockMs = { NOW_MS },
        parser = XmltvEpgParser(),
    )

    private fun cacheFile() = File(temporaryFolder.root, "epg-cz.xml.gz")

    private fun xml(channel: String, start: String, stop: String, title: String) =
        """<tv><programme channel="$channel" start="20260816$start" stop="20260816$stop"><title>$title</title></programme></tv>"""

    private fun gzip(xml: String): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(xml.encodeToByteArray()) }
        output.toByteArray()
    }

    private companion object {
        const val NOW_MS = 1_786_896_300_000L
        const val SIX_HOURS_MS = 6L * 60L * 60L * 1_000L
    }
}
