package sk.ziacik.androidtvplayer.epg

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
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
    fun `repository parses a fresh XMLTV feed once and reuses the schedule for other channels`() = runTest {
        val parser = CountingParser()
        val repository = CachedXmltvEpgRepository(
            sources = listOf(
                XmltvEpgSource(
                    id = EpgSourceId.SKYLINK,
                    cacheFile = File(temporaryFolder.root, "epg.xml.gz"),
                    download = {
                        gzip(
                            """<tv>
                                <programme channel="7a55634018be710a62bbf1750443a199" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Správy</title></programme>
                                <programme channel="336e46bf4276e77a716e494c6285d5db" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Film</title></programme>
                            </tv>""",
                        )
                    },
                ),
            ),
            clockMs = { NOW_MS },
            parser = parser,
        )

        assertEquals("Správy", repository.currentProgram(TvChannel.JEDNOTKA, NOW_MS)?.title)
        assertEquals("Film", repository.currentProgram(TvChannel.MARKIZA, NOW_MS)?.title)
        assertEquals(1, parser.scheduleParseCalls)
        assertEquals(0, parser.singleProgrammeParseCalls)
    }

    private class CountingParser : XmltvEpgParser() {
        var scheduleParseCalls = 0
        var singleProgrammeParseCalls = 0

        override fun parse(
            xml: InputStream,
            channelIds: Set<String>,
        ): Map<String, List<EpgProgramme>> {
            scheduleParseCalls += 1
            return super.parse(xml, channelIds)
        }

        override fun currentProgram(
            xml: InputStream,
            channelId: String,
            nowMs: Long,
        ): EpgProgramme? {
            singleProgrammeParseCalls += 1
            return super.currentProgram(xml, channelId, nowMs)
        }
    }

    private fun gzip(xml: String): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(xml.encodeToByteArray()) }
        output.toByteArray()
    }

    private companion object {
        const val NOW_MS = 1_786_896_300_000L
    }
}
