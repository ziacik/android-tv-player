package sk.ziacik.androidtvplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmltvEpgParserTest {
    @Test
    fun `parses mapped programmes with XMLTV time zones and ignores malformed entries`() {
        val programmes = XmltvEpgParser().parse(
            xml = """<tv>
                <programme channel="JEDNOTKA.cz" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Správy</title></programme>
                <programme channel="JEDNOTKA.cz" start="20260816190000 +0200" stop="20260816200000 +0200"><title>Film</title></programme>
                <programme channel="OTHER" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Ignorovať</title></programme>
                <programme channel="JEDNOTKA.cz" start="invalid" stop="20260816190000 +0200"><title>Pokazené</title></programme>
            </tv>""".byteInputStream(),
            channelIds = setOf("JEDNOTKA.cz"),
        )

        assertEquals(
            listOf("Správy", "Film"),
            programmes["JEDNOTKA.cz"]!!.map(EpgProgramme::title),
        )
        assertEquals(1_786_896_000_000L, programmes["JEDNOTKA.cz"]!!.first().startsAtMs)
        assertNull(programmes["OTHER"])
    }

    @Test
    fun `decodes XML title entities and ignores entries without a title`() {
        val programmes = XmltvEpgParser().parse(
            xml = """<tv>
                <programme channel="JEDNOTKA.cz" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Film &amp; seriál</title></programme>
                <programme channel="JEDNOTKA.cz" start="20260816190000 +0200" stop="20260816200000 +0200"><title> </title></programme>
            </tv>""".byteInputStream(),
            channelIds = setOf("JEDNOTKA.cz"),
        )

        assertEquals(listOf("Film & seriál"), programmes["JEDNOTKA.cz"]!!.map(EpgProgramme::title))
    }

    @Test
    fun `keeps the first nonblank title when XMLTV contains localized titles`() {
        val programmes = XmltvEpgParser().parse(
            xml = """<tv>
                <programme channel="JEDNOTKA.cz" start="20260816180000 +0200" stop="20260816190000 +0200">
                    <title lang="sk">Prvý názov</title><title lang="en"> </title>
                </programme>
            </tv>""".byteInputStream(),
            channelIds = setOf("JEDNOTKA.cz"),
        )

        assertEquals("Prvý názov", programmes["JEDNOTKA.cz"]!!.single().title)
    }

    @Test
    fun `parses XMLTV feed with an external doctype without loading it`() {
        val programmes = XmltvEpgParser().parse(
            xml = """<!DOCTYPE tv SYSTEM "https://example.invalid/xmltv.dtd">
                <tv><programme channel="JEDNOTKA.cz" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Správy</title></programme></tv>""".byteInputStream(),
            channelIds = setOf("JEDNOTKA.cz"),
        )

        assertEquals("Správy", programmes["JEDNOTKA.cz"]!!.single().title)
    }

    @Test
    fun `finds only the programme covering the requested instant`() {
        val programme = XmltvEpgParser().currentProgram(
            xml = """<tv>
                <programme channel="OTHER" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Ignorovať</title></programme>
                <programme channel="MARKIZA" start="20260816170000 +0200" stop="20260816180000 +0200"><title>Staršia</title></programme>
                <programme channel="MARKIZA" start="20260816180000 +0200" stop="20260816190000 +0200"><title>Aktuálna &amp; relácia</title></programme>
            </tv>""".byteInputStream(),
            channelId = "MARKIZA",
            nowMs = 1_786_896_300_000L,
        )

        assertEquals("Aktuálna & relácia", programme?.title)
    }
}
