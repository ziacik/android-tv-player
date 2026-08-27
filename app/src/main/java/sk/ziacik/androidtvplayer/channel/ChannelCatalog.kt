package sk.ziacik.androidtvplayer.channel

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ChannelCatalog(val channels: List<TvChannel>) {
    init {
        require(channels.isNotEmpty())
        require(channels.map(TvChannel::storageKey).distinct().size == channels.size)
    }

    fun fromStorageKey(key: String?): TvChannel = channels.firstOrNull { it.storageKey == key } ?: channels.first()
    fun fromChannelNumber(number: Int): TvChannel? = channels.getOrNull(number - 1)

    fun next(channel: TvChannel): TvChannel {
        val index = channels.indexOfFirst { it.storageKey == channel.storageKey }
        return channels[if (index >= 0) (index + 1) % channels.size else 0]
    }

    fun previous(channel: TvChannel): TvChannel {
        val index = channels.indexOfFirst { it.storageKey == channel.storageKey }
        return channels[if (index >= 0) (index - 1 + channels.size) % channels.size else channels.lastIndex]
    }

    fun numberOf(channel: TvChannel): Int =
        channels.indexOfFirst { it.storageKey == channel.storageKey } + 1
}

object ChannelCatalogJsonParser {
    fun parse(json: String): ChannelCatalog {
        val parsed = JSONObject(json).optJSONArray("channels") ?: error("channels is missing")
        val channels = buildList<TvChannel> {
            repeat(parsed.length()) { index ->
                val value = parsed.optJSONObject(index) ?: return@repeat
                val id = value.optString("id").trim()
                val name = value.optString("name").trim()
                val url = value.optString("url").trim()
                if (id.isBlank() || name.isBlank() || !(url.startsWith("http://") || url.startsWith("https://")) || this@buildList.any { it.storageKey == id }) return@repeat
                val epg = value.optJSONObject("epg")
                val epgIds = buildMap {
                    epg?.optString("skylink")?.takeIf { it.isNotBlank() }?.let { put(EpgSourceId.SKYLINK, it) }
                    epg?.optString("iptvOrg")?.takeIf { it.isNotBlank() }?.let { put(EpgSourceId.IPTV_ORG, it) }
                }
                add(TvChannel(id, displayName = name, provider = ChannelProvider.DIRECT, providerValue = url, epgIds = epgIds))
            }
        }
        return ChannelCatalog(channels)
    }
}

class ChannelCatalogRepository(
    private val seed: () -> String,
    private val cacheFile: File,
    private val download: suspend () -> String,
) {
    fun load(): ChannelCatalog = runCatching { ChannelCatalogJsonParser.parse(cacheFile.readText()) }
        .getOrElse { ChannelCatalogJsonParser.parse(seed()) }

    suspend fun refresh(): ChannelCatalog = withContext(Dispatchers.IO) {
        val json = try {
            download()
        } catch (error: CancellationException) {
            throw error
        }
        val catalog = ChannelCatalogJsonParser.parse(json)
        cacheFile.parentFile?.mkdirs()
        val temporary = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        temporary.writeText(json)
        Files.move(temporary.toPath(), cacheFile.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        catalog
    }
}

class OkHttpChannelCatalogDownloader(
    private val url: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun download(): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            response.body.string()
        }
    }
}
