package sk.ziacik.androidtvplayer.epg

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sk.ziacik.androidtvplayer.channel.EpgSourceId
import sk.ziacik.androidtvplayer.channel.TvChannel

class EpgBatchLookupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `parser finds current programmes for multiple channels in one pass`() {
        val programmes = XmltvEpgParser().currentPrograms(
            xml = """<tv>
                <programme channel="JEDNOTKA.cz" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Správy</title></programme>
                <programme channel="MARKIZA" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Film</title></programme>
                <programme channel="MARKIZA" start="20260816190000 +0200" stop="20260816200000 +0200"><title>Neskôr</title></programme>
            </tv>""".byteInputStream(),
            channelIds = setOf("JEDNOTKA.cz", "MARKIZA"),
            nowMs = NOW_MS,
        )

        assertEquals("Správy", programmes["JEDNOTKA.cz"]?.title)
        assertEquals("Film", programmes["MARKIZA"]?.title)
    }

    @Test
    fun `repository resolves multiple channels with one batched lookup`() = runTest {
        var downloads = 0
        val repository = CachedXmltvEpgRepository(
            sources = listOf(
                XmltvEpgSource(
                    id = EpgSourceId.SKYLINK,
                    cacheFile = File(temporaryFolder.root, "epg.xml.gz"),
                    download = {
                        downloads += 1
                        gzip(
                            """<tv>
                                <programme channel="JEDNOTKA.cz" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Správy</title></programme>
                                <programme channel="MARKIZA" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Film</title></programme>
                            </tv>""",
                        )
                    },
                ),
            ),
            clockMs = { NOW_MS },
        )
        val channels = listOf(
            TvChannel.JEDNOTKA.copy(
                epgIds = mapOf(EpgSourceId.SKYLINK to "JEDNOTKA.cz"),
            ),
            TvChannel.MARKIZA.copy(
                epgIds = mapOf(EpgSourceId.SKYLINK to "MARKIZA"),
            ),
        )

        val programmes = repository.currentPrograms(channels, NOW_MS)

        assertEquals("Správy", programmes[TvChannel.JEDNOTKA.storageKey]?.title)
        assertEquals("Film", programmes[TvChannel.MARKIZA.storageKey]?.title)
        assertEquals(1, downloads)
    }

    private fun gzip(xml: String): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(xml.encodeToByteArray()) }
        output.toByteArray()
    }

    private companion object {
        const val NOW_MS = 1_786_896_300_000L
    }
}
