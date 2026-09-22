package sk.ziacik.androidtvplayer.epg

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sk.ziacik.androidtvplayer.channel.EpgSourceId
import sk.ziacik.androidtvplayer.channel.TvChannel

class ArchiveRepeatEpgRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `does not inspect secondary EPG to infer a repeat`() = runTest {
        var secondaryDownloads = 0
        val repository = CachedXmltvEpgRepository(
            sources = listOf(
                XmltvEpgSource(
                    id = EpgSourceId.OPEN_EPG,
                    cacheFile = File(temporaryFolder.root, "open.xml"),
                    download = {
                        """<tv>
                            <programme channel="Jednotka HD.sk" start="20260909104500 +0200" stop="20260909111500 +0200"><title>Duel</title></programme>
                        </tv>""".trimIndent().encodeToByteArray()
                    },
                ),
                XmltvEpgSource(
                    id = EpgSourceId.SKYLINK,
                    cacheFile = File(temporaryFolder.root, "skylink.xml"),
                    download = {
                        secondaryDownloads += 1
                        """<tv>
                            <programme channel="jednotka-skylink" start="20260908174500 +0200" stop="20260908181500 +0200"><title>Duel (74)</title></programme>
                            <programme channel="jednotka-skylink" start="20260909104500 +0200" stop="20260909111500 +0200"><title>Duel (74)</title></programme>
                        </tv>""".trimIndent().encodeToByteArray()
                    },
                ),
            ),
            clockMs = { MORNING_DUEL_MS },
            parser = XmltvEpgParser(),
        )

        val programme = repository.currentProgram(TEST_CHANNEL, MORNING_DUEL_MS)

        assertEquals("Duel", programme?.title)
        assertEquals(0, secondaryDownloads)
    }

    private companion object {
        const val MORNING_DUEL_MS = 1_788_943_500_000L
        val TEST_CHANNEL = TvChannel.JEDNOTKA.copy(
            epgIds = mapOf(
                EpgSourceId.OPEN_EPG to "Jednotka HD.sk",
                EpgSourceId.SKYLINK to "jednotka-skylink",
            ),
        )
    }
}
