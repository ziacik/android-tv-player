package sk.ziacik.androidtvplayer.systemepg

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import sk.ziacik.androidtvplayer.channel.EpgSourceId
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.epg.EpgProgramme
import sk.ziacik.androidtvplayer.epg.OkHttpEpgDownloader
import sk.ziacik.androidtvplayer.epg.XmltvEpgParser

class SystemEpgScheduleLoader(private val filesDir: File) {
    suspend fun load(channels: List<TvChannel>): Map<String, List<EpgProgramme>> {
        val remaining = channels.associateBy { it.storageKey }.toMutableMap()
        val result = mutableMapOf<String, List<EpgProgramme>>()
        sources.forEach { source ->
            val ids = remaining.values.mapNotNull { it.epgIds[source.id] }.toSet()
            if (ids.isEmpty()) return@forEach
            val parsed = runCatching { source.download().openXmlStream().use { XmltvEpgParser().parse(it, ids) } }.getOrDefault(emptyMap())
            remaining.values.toList().forEach { channel ->
                channel.epgIds[source.id]?.let(parsed::get)?.takeIf(List<EpgProgramme>::isNotEmpty)?.let {
                    result[channel.storageKey] = it
                    remaining.remove(channel.storageKey)
                }
            }
        }
        return result
    }

    private val sources = listOf(
        Source(EpgSourceId.OPEN_EPG, OkHttpEpgDownloader(OPEN_EPG_URL)::download),
        Source(EpgSourceId.SKYLINK, OkHttpEpgDownloader(SKYLINK_EPG_URL)::download),
    )

    private data class Source(val id: EpgSourceId, val download: suspend () -> ByteArray)

    private fun ByteArray.openXmlStream() = ByteArrayInputStream(this).let { stream ->
        if (size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()) GZIPInputStream(stream) else stream
    }

    private companion object {
        const val OPEN_EPG_URL = "https://www.open-epg.com/generate/jnapkTB7Wq.xml.gz"
        const val SKYLINK_EPG_URL = "https://raw.githubusercontent.com/370network/skylink-xmltv/refs/heads/main/a3b_a1.xml"
    }
}
