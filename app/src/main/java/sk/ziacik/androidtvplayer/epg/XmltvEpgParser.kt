package sk.ziacik.androidtvplayer.epg

import java.io.InputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

class XmltvEpgParser {
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

                        "title" -> if (candidate?.title == null) titleBuilder = StringBuilder()
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

    private fun secureSaxParserFactory(): SAXParserFactory = SAXParserFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }

    private companion object {
        val XMLTV_TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z", Locale.ROOT)
    }
}
