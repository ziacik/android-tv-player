package sk.ziacik.androidtvplayer.epg

import java.io.InputStream
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

class XmltvEpgParser {
    fun currentProgram(
        xml: InputStream,
        channelId: String,
        nowMs: Long,
    ): EpgProgramme? {
        val document = xml.readBytes().decodeToString()
        require(document.contains("<tv") && document.contains("</tv>")) {
            "XMLTV document is incomplete"
        }
        val channelAttribute = "channel=\"$channelId\""
        var searchFrom = 0
        while (true) {
            val channelAttributeIndex = document.indexOf(channelAttribute, searchFrom)
            if (channelAttributeIndex < 0) return null
            val programmeStart = document.lastIndexOf("<programme", channelAttributeIndex)
            val openingTagEnd = document.indexOf('>', programmeStart)
            val programmeEnd = document.indexOf("</programme>", channelAttributeIndex)
            if (programmeStart < 0 || openingTagEnd < 0 || programmeEnd < 0) return null
            val openingTag = document.substring(programmeStart, openingTagEnd + 1)
            val startsAtMs = openingTag.attributeValue("start").toEpochMillis()
            val endsAtMs = openingTag.attributeValue("stop").toEpochMillis()
            val endExclusive = programmeEnd + "</programme>".length
            if (startsAtMs != null && endsAtMs != null && startsAtMs <= nowMs && nowMs < endsAtMs) {
                return parse(
                    "<tv>${document.substring(programmeStart, endExclusive)}</tv>".byteInputStream(),
                    setOf(channelId),
                )[channelId]?.singleOrNull()
            }
            searchFrom = endExclusive
        }
    }

    fun parse(
        xml: InputStream,
        channelIds: Set<String>,
    ): Map<String, List<EpgProgramme>> {
        val programmes = mutableMapOf<String, MutableList<EpgProgramme>>()
        secureSaxParserFactory().newSAXParser().parse(
            xml,
            object : DefaultHandler() {
                private var candidate: Candidate? = null
                private var titleBuilder: StringBuilder? = null

                override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
                    InputSource(StringReader(""))

                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String,
                    attributes: Attributes,
                ) {
                    when (qName) {
                        "programme" -> {
                            val channelId = attributes.getValue("channel")
                            candidate = channelId
                                ?.takeIf(channelIds::contains)
                                ?.let {
                                    Candidate(
                                        channelId = it,
                                        startsAtMs = attributes.getValue("start").toEpochMillis(),
                                        endsAtMs = attributes.getValue("stop").toEpochMillis(),
                                    )
                                }
                        }

                        "title" -> candidate?.let { currentCandidate ->
                            if (currentCandidate.title == null) titleBuilder = StringBuilder()
                        }
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    titleBuilder?.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String) {
                    when (qName) {
                        "title" -> {
                            if (candidate?.title == null) {
                                candidate?.title = titleBuilder
                                    ?.toString()
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                            }
                            titleBuilder = null
                        }

                        "programme" -> {
                            val currentCandidate = candidate
                            currentCandidate?.toProgramme()?.let { programme ->
                                programmes.getOrPut(currentCandidate.channelId) { mutableListOf() } += programme
                            }
                            candidate = null
                            titleBuilder = null
                        }
                    }
                }
            },
        )
        return programmes.mapValues { (_, items) -> items.sortedBy(EpgProgramme::startsAtMs) }
    }

    private data class Candidate(
        val channelId: String,
        val startsAtMs: Long?,
        val endsAtMs: Long?,
        var title: String? = null,
    ) {
        fun toProgramme(): EpgProgramme? {
            val start = startsAtMs ?: return null
            val end = endsAtMs ?: return null
            val programmeTitle = title ?: return null
            return EpgProgramme(programmeTitle, start, end).takeIf { end > start }
        }
    }

    private fun String?.toEpochMillis(): Long? = this
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { value ->
            runCatching {
                OffsetDateTime.parse(value, XMLTV_TIMESTAMP).toInstant().toEpochMilli()
            }.getOrNull()
        }

    private fun String.attributeValue(name: String): String? {
        val prefix = "$name=\""
        val valueStart = indexOf(prefix).takeIf { it >= 0 }?.plus(prefix.length) ?: return null
        val valueEnd = indexOf('"', valueStart).takeIf { it >= valueStart } ?: return null
        return substring(valueStart, valueEnd)
    }

    private fun secureSaxParserFactory(): SAXParserFactory = SAXParserFactory.newInstance().apply {
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }

    private companion object {
        val XMLTV_TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z", Locale.ROOT)
    }
}
