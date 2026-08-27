package sk.ziacik.androidtvplayer.epg

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sk.ziacik.androidtvplayer.channel.EpgSourceId
import sk.ziacik.androidtvplayer.channel.TvChannel

@OptIn(ExperimentalCoroutinesApi::class)
class EpgRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `returns programme containing the supplied instant`() = runTest {
        val repository = repository(
            downloader = { gzip(xml("JEDNOTKA.cz", "180000 +0200", "190000 +0200", "Správy")) },
        )

        assertEquals("Správy", repository.currentProgram(TEST_CHANNEL, NOW_MS)?.title)
    }

    @Test
    fun `uses fresh cached feed without downloading again`() = runTest {
        val cacheFile = cacheFile()
        cacheFile.writeBytes(gzip(xml("JEDNOTKA.cz", "180000 +0200", "190000 +0200", "Cache")))
        cacheFile.setLastModified(NOW_MS)
        val repository = repository(cacheFile, downloader = { error("download must not run") })

        assertEquals("Cache", repository.currentProgram(TEST_CHANNEL, NOW_MS)?.title)
    }

    @Test
    fun `fresh disk cache keeps its original age after loading into memory`() = runTest {
        var clockMs = NOW_MS
        var downloadAttempts = 0
        val cacheFile = cacheFile()
        cacheFile.writeBytes(
            gzip(
                """<tv>
                    <programme channel="JEDNOTKA.cz" start="20260816180000 +0200" stop="20260816180600 +0200"><title>Prvá</title></programme>
                    <programme channel="JEDNOTKA.cz" start="20260816180600 +0200" stop="20260816190000 +0200"><title>Stará</title></programme>
                </tv>""",
            ),
        )
        cacheFile.setLastModified(clockMs - SIX_HOURS_MS + 60_000L)
        val repository = CachedXmltvEpgRepository(
            sources = listOf(
                XmltvEpgSource(EpgSourceId.SKYLINK, cacheFile) {
                    downloadAttempts += 1
                    gzip(xml("JEDNOTKA.cz", "180600 +0200", "190000 +0200", "Čerstvá"))
                },
            ),
            clockMs = { clockMs },
            parser = XmltvEpgParser(),
        )

        assertEquals("Prvá", repository.currentProgram(TEST_CHANNEL, clockMs)?.title)
        clockMs += 120_000L
        assertEquals("Čerstvá", repository.currentProgram(TEST_CHANNEL, clockMs)?.title)
        assertEquals(1, downloadAttempts)
    }

    @Test
    fun `reuses current programme until its interval ends`() = runTest {
        var clockMs = NOW_MS
        var downloads = 0
        val cacheFile = cacheFile()
        val repository = CachedXmltvEpgRepository(
            sources = listOf(
                XmltvEpgSource(EpgSourceId.SKYLINK, cacheFile) {
                    downloads += 1
                    gzip(
                        """<tv><programme channel="JEDNOTKA.cz" start="20260816180000 +0200" stop="20260817060000 +0200"><title>Dlhá relácia</title></programme></tv>""",
                    )
                },
            ),
            clockMs = { clockMs },
            parser = XmltvEpgParser(),
        )

        assertEquals("Dlhá relácia", repository.currentProgram(TEST_CHANNEL, clockMs)?.title)
        cacheFile.delete()
        clockMs += SIX_HOURS_MS + 1L
        assertEquals("Dlhá relácia", repository.currentProgram(TEST_CHANNEL, clockMs)?.title)
        assertEquals(1, downloads)
    }

    @Test
    fun `concurrent lookups share one source load`() = runTest {
        var downloads = 0
        val downloadStarted = CompletableDeferred<Unit>()
        val releaseDownload = CompletableDeferred<Unit>()
        val repository = repository(
            downloader = {
                downloads += 1
                downloadStarted.complete(Unit)
                releaseDownload.await()
                gzip(xml("JEDNOTKA.cz", "180000 +0200", "190000 +0200", "Správy"))
            },
        )

        val first = async { repository.currentProgram(TEST_CHANNEL, NOW_MS) }
        val second = async { repository.currentProgram(TEST_CHANNEL, NOW_MS) }
        downloadStarted.await()
        withContext(Dispatchers.IO) { delay(50L) }

        val downloadsBeforeRelease = downloads
        releaseDownload.complete(Unit)
        assertEquals("Správy", first.await()?.title)
        assertEquals("Správy", second.await()?.title)
        assertEquals(1, downloadsBeforeRelease)
    }

    @Test
    fun `resolved fallback programme skips the failed primary source until programme end`() = runTest {
        var clockMs = NOW_MS
        var openDownloads = 0
        val openCache = File(temporaryFolder.root, "open.xml")
        val skylinkCache = File(temporaryFolder.root, "skylink.xml")
        val repository = CachedXmltvEpgRepository(
            sources = listOf(
                XmltvEpgSource(EpgSourceId.OPEN_EPG, openCache) {
                    openDownloads += 1
                    xml("Markíza HD.sk", "160000 +0200", "170000 +0200", "Stará relácia").encodeToByteArray()
                },
                XmltvEpgSource(EpgSourceId.SKYLINK, skylinkCache) {
                    gzip(
                        """<tv><programme channel="336e46bf4276e77a716e494c6285d5db" start="20260816180000 +0200" stop="20260817060000 +0200"><title>Skylink</title></programme></tv>""",
                    )
                },
            ),
            clockMs = { clockMs },
            parser = XmltvEpgParser(),
        )

        assertEquals("Skylink", repository.currentProgram(MARKIZA_WITH_OPEN_EPG, clockMs)?.title)
        openCache.delete()
        skylinkCache.delete()
        clockMs += SIX_HOURS_MS + 1L
        assertEquals("Skylink", repository.currentProgram(MARKIZA_WITH_OPEN_EPG, clockMs)?.title)
        assertEquals(1, openDownloads)
    }

    @Test
    fun `uses last parsed cache when stale refresh fails`() = runTest {
        var downloadAttempts = 0
        val cacheFile = cacheFile()
        cacheFile.writeBytes(gzip(xml("JEDNOTKA.cz", "180000 +0200", "190000 +0200", "Cache")))
        cacheFile.setLastModified(NOW_MS - SIX_HOURS_MS - 1L)
        val repository = repository(
            cacheFile,
            downloader = {
                downloadAttempts += 1
                throw IOException("offline")
            },
        )

        assertEquals("Cache", repository.currentProgram(TEST_CHANNEL, NOW_MS)?.title)
        assertNull(repository.currentProgram(TEST_CHANNEL, 1_786_899_600_000L))
        assertEquals(2, downloadAttempts)
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

        assertEquals("Cache", repository.currentProgram(TEST_CHANNEL, NOW_MS)?.title)
        assertEquals(cachedBytes.toList(), cacheFile.readBytes().toList())
    }

    @Test
    fun `does not cache an empty downloaded feed`() = runTest {
        val cacheFile = cacheFile()
        val repository = repository(cacheFile, downloader = { byteArrayOf() })

        assertNull(repository.currentProgram(TEST_CHANNEL, NOW_MS))
        assertFalse(cacheFile.exists())
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

        assertEquals("Druhý", repository.currentProgram(TEST_CHANNEL, 1_786_899_600_000L)?.title)
    }

    @Test
    fun `prefers current programme from the first configured source`() = runTest {
        val repository = repository(
            sources = listOf(
                source(EpgSourceId.OPEN_EPG) {
                    xml("Markíza HD.sk", "180000 +0200", "190000 +0200", "Open EPG").encodeToByteArray()
                },
                source(EpgSourceId.SKYLINK) {
                    gzip(xml("336e46bf4276e77a716e494c6285d5db", "180000 +0200", "190000 +0200", "Skylink"))
                },
            ),
        )

        assertEquals("Open EPG", repository.currentProgram(MARKIZA_WITH_OPEN_EPG, NOW_MS)?.title)
    }

    @Test
    fun `falls back when the first source has no current programme`() = runTest {
        val repository = repository(
            sources = listOf(
                source(EpgSourceId.OPEN_EPG) {
                    xml("Markíza HD.sk", "160000 +0200", "170000 +0200", "Stará relácia").encodeToByteArray()
                },
                source(EpgSourceId.SKYLINK) {
                    gzip(xml("336e46bf4276e77a716e494c6285d5db", "180000 +0200", "190000 +0200", "Skylink"))
                },
            ),
        )

        assertEquals("Skylink", repository.currentProgram(MARKIZA_WITH_OPEN_EPG, NOW_MS)?.title)
    }

    @Test
    fun `falls back when the first source cannot be downloaded`() = runTest {
        val repository = repository(
            sources = listOf(
                source(EpgSourceId.OPEN_EPG) { throw IOException("offline") },
                source(EpgSourceId.SKYLINK) {
                    gzip(xml("336e46bf4276e77a716e494c6285d5db", "180000 +0200", "190000 +0200", "Skylink"))
                },
            ),
        )

        assertEquals("Skylink", repository.currentProgram(MARKIZA_WITH_OPEN_EPG, NOW_MS)?.title)
    }

    private fun repository(
        cacheFile: File = cacheFile(),
        downloader: suspend () -> ByteArray,
    ) = CachedXmltvEpgRepository(
        sources = listOf(
            XmltvEpgSource(EpgSourceId.SKYLINK, cacheFile, downloader),
        ),
        clockMs = { NOW_MS },
        parser = XmltvEpgParser(),
    )

    private fun repository(
        sources: List<XmltvEpgSource>,
    ) = CachedXmltvEpgRepository(
        sources = sources,
        clockMs = { NOW_MS },
        parser = XmltvEpgParser(),
    )

    private fun source(
        id: EpgSourceId,
        downloader: suspend () -> ByteArray,
    ) = XmltvEpgSource(
        id = id,
        cacheFile = File(temporaryFolder.root, "${id.name.lowercase()}.xml"),
        download = downloader,
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
        val TEST_CHANNEL = TvChannel.JEDNOTKA.copy(
            epgIds = mapOf(EpgSourceId.SKYLINK to "JEDNOTKA.cz"),
        )
        val MARKIZA_WITH_OPEN_EPG = TvChannel.MARKIZA.copy(
            epgIds = mapOf(
                EpgSourceId.OPEN_EPG to "Markíza HD.sk",
                EpgSourceId.SKYLINK to "336e46bf4276e77a716e494c6285d5db",
            ),
        )
    }
}
