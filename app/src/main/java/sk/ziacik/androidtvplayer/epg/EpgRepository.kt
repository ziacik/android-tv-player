package sk.ziacik.androidtvplayer.epg

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import sk.ziacik.androidtvplayer.channel.EpgSourceId
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

fun interface EpgRepository {
    suspend fun currentProgram(channel: TvChannel, nowMs: Long): ProgramMetadata?
}

data class XmltvEpgSource(
    val id: EpgSourceId,
    val cacheFile: File,
    val download: suspend () -> ByteArray,
)

class CachedXmltvEpgRepository(
    private val sources: List<XmltvEpgSource>,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val parser: XmltvEpgParser = XmltvEpgParser(),
    private val diagnostics: (String, Throwable?) -> Unit = { _, _ -> },
) : EpgRepository {
    private val cachedFeeds = mutableMapOf<EpgSourceId, CachedFeed>()
    private val cachedProgrammes = mutableMapOf<ProgrammeKey, EpgProgramme>()
    private val lookupMutex = Mutex()

    override suspend fun currentProgram(channel: TvChannel, nowMs: Long): ProgramMetadata? {
        return withContext(Dispatchers.IO) {
            lookupMutex.withLock {
                val programmeKey = ProgrammeKey(channel.storageKey, channel.epgIds)
                cachedProgrammes[programmeKey]
                    ?.takeIf { it.startsAtMs <= nowMs && nowMs < it.endsAtMs }
                    ?.let { return@withLock it.toProgramMetadata() }
                cachedProgrammes.remove(programmeKey)
                for (source in sources) {
                    val channelId = channel.epgIds[source.id] ?: continue
                    val programme = try {
                        loadCurrentProgram(source, channelId, nowMs)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        diagnostics("EPG feed failed for ${source.id}", error)
                        null
                    }
                    if (programme != null) {
                        cachedProgrammes[programmeKey] = programme
                        return@withLock programme.toProgramMetadata()
                    }
                }
                null
            }
        }
    }

    private suspend fun loadCurrentProgram(
        source: XmltvEpgSource,
        channelId: String,
        nowMs: Long,
    ): EpgProgramme? {
        val now = clockMs()
        cachedFeeds[source.id]
            ?.takeIf { now - it.loadedAtMs < CACHE_FRESHNESS_MS }
            ?.let { cachedFeed ->
                val indexedFeed = ensureChannelIndexed(source, cachedFeed, channelId)
                return indexedFeed.currentProgram(channelId, nowMs)
            }

        if (source.cacheFile.isFile && now - source.cacheFile.lastModified() < CACHE_FRESHNESS_MS) {
            val cachedBytes = source.cacheFile.readBytes()
            val cachedFeed = remember(
                source = source,
                bytes = cachedBytes,
                loadedAtMs = source.cacheFile.lastModified(),
                requiredChannelIds = setOf(channelId),
            )
            return cachedFeed.currentProgram(channelId, nowMs)
        }

        val downloaded = try {
            source.download()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            diagnostics("EPG download failed for ${source.id}", error)
            null
        }
        if (downloaded != null) {
            try {
                val downloadedFeed = buildCachedFeed(
                    source = source,
                    bytes = downloaded,
                    loadedAtMs = now,
                    requiredChannelIds = setOf(channelId),
                )
                writeCacheAtomically(source, downloaded)
                cachedFeeds[source.id] = downloadedFeed
                return downloadedFeed.currentProgram(channelId, nowMs)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics("EPG feed failed for ${source.id}", error)
            }
        }
        return parseCachedProgram(source, channelId, nowMs, source.cacheFile.lastModified())
    }

    private fun ensureChannelIndexed(
        source: XmltvEpgSource,
        cachedFeed: CachedFeed,
        channelId: String,
    ): CachedFeed {
        if (channelId in cachedFeed.indexedChannelIds) return cachedFeed
        return buildCachedFeed(
            source = source,
            bytes = cachedFeed.bytes,
            loadedAtMs = cachedFeed.loadedAtMs,
            requiredChannelIds = cachedFeed.indexedChannelIds + channelId,
        ).also { cachedFeeds[source.id] = it }
    }

    private fun parseCachedProgram(
        source: XmltvEpgSource,
        channelId: String,
        nowMs: Long,
        loadedAtMs: Long,
    ): EpgProgramme? = runCatching {
        val cachedBytes = source.cacheFile.readBytes()
        remember(
            source = source,
            bytes = cachedBytes,
            loadedAtMs = loadedAtMs,
            requiredChannelIds = setOf(channelId),
        ).currentProgram(channelId, nowMs)
    }.getOrNull()

    private fun remember(
        source: XmltvEpgSource,
        bytes: ByteArray,
        loadedAtMs: Long,
        requiredChannelIds: Set<String>,
    ): CachedFeed = buildCachedFeed(
        source = source,
        bytes = bytes,
        loadedAtMs = loadedAtMs,
        requiredChannelIds = requiredChannelIds,
    ).also { cachedFeeds[source.id] = it }

    private fun buildCachedFeed(
        source: XmltvEpgSource,
        bytes: ByteArray,
        loadedAtMs: Long,
        requiredChannelIds: Set<String>,
    ): CachedFeed {
        val channelIds = configuredChannelIds(source.id) + requiredChannelIds
        val programmes = bytes.openXmlStream().use { stream ->
            parser.parse(stream, channelIds)
        }
        return CachedFeed(
            bytes = bytes,
            loadedAtMs = loadedAtMs,
            indexedChannelIds = channelIds,
            programmes = programmes,
        )
    }

    private fun configuredChannelIds(sourceId: EpgSourceId): Set<String> =
        TvChannel.entries.mapNotNull { channel -> channel.epgIds[sourceId] }.toSet()

    private fun CachedFeed.currentProgram(channelId: String, nowMs: Long): EpgProgramme? =
        programmes[channelId]?.firstOrNull { programme ->
            programme.startsAtMs <= nowMs && nowMs < programme.endsAtMs
        }

    private fun writeCacheAtomically(source: XmltvEpgSource, bytes: ByteArray) {
        source.cacheFile.parentFile?.mkdirs()
        val temporaryFile = File(source.cacheFile.parentFile, "${source.cacheFile.name}.tmp")
        temporaryFile.writeBytes(bytes)
        Files.move(
            temporaryFile.toPath(),
            source.cacheFile.toPath(),
            ATOMIC_MOVE,
            REPLACE_EXISTING,
        )
    }

    private fun ByteArray.openXmlStream() =
        ByteArrayInputStream(this).let { byteStream ->
            if (isGzip()) GZIPInputStream(byteStream) else byteStream
        }

    private fun ByteArray.isGzip(): Boolean =
        size >= 2 && this[0] == GZIP_MAGIC_FIRST_BYTE && this[1] == GZIP_MAGIC_SECOND_BYTE

    private data class CachedFeed(
        val bytes: ByteArray,
        val loadedAtMs: Long,
        val indexedChannelIds: Set<String>,
        val programmes: Map<String, List<EpgProgramme>>,
    )

    private data class ProgrammeKey(
        val channelStorageKey: String,
        val epgIds: Map<EpgSourceId, String>,
    )

    private companion object {
        const val CACHE_FRESHNESS_MS = 6L * 60L * 60L * 1_000L
        const val GZIP_MAGIC_FIRST_BYTE: Byte = 0x1f
        const val GZIP_MAGIC_SECOND_BYTE: Byte = 0x8b.toByte()
    }
}

class OkHttpEpgDownloader(
    private val url: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun download(): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        val completed = AtomicBoolean(false)
        continuation.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) call.cancel()
        }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (completed.compareAndSet(false, true)) continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                            it.body.bytes()
                        }
                        if (completed.compareAndSet(false, true)) {
                            continuation.resumeWith(Result.success(bytes))
                        }
                    } catch (error: Exception) {
                        if (completed.compareAndSet(false, true)) {
                            continuation.resumeWith(Result.failure(error))
                        }
                    }
                }
            },
        )
    }

}
