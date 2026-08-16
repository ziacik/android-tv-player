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

    override suspend fun currentProgram(channel: TvChannel, nowMs: Long): ProgramMetadata? {
        return withContext(Dispatchers.IO) {
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
                if (programme != null) return@withContext programme.toProgramMetadata()
            }
            null
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
            ?.let { return parseCurrentProgram(it.bytes, source, channelId, nowMs) }

        if (source.cacheFile.isFile && now - source.cacheFile.lastModified() < CACHE_FRESHNESS_MS) {
            val cachedBytes = source.cacheFile.readBytes()
            return parseCurrentProgram(cachedBytes, source, channelId, nowMs)
                .also { remember(source, cachedBytes, now) }
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
                val programme = parseCurrentProgram(downloaded, source, channelId, nowMs)
                writeCacheAtomically(source, downloaded)
                remember(source, downloaded, now)
                return programme
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics("EPG feed failed for ${source.id}", error)
            }
        }
        return parseCachedProgram(source, channelId, nowMs, now)
    }

    private fun parseCachedProgram(
        source: XmltvEpgSource,
        channelId: String,
        nowMs: Long,
        loadedAtMs: Long,
    ): EpgProgramme? = runCatching {
        val cachedBytes = source.cacheFile.readBytes()
        parseCurrentProgram(cachedBytes, source, channelId, nowMs)
            .also { remember(source, cachedBytes, loadedAtMs) }
    }.getOrNull()

    private fun parseCurrentProgram(
        bytes: ByteArray,
        source: XmltvEpgSource,
        channelId: String,
        nowMs: Long,
    ): EpgProgramme? = bytes.openXmlStream().use { stream ->
        parser.currentProgram(stream, channelId, nowMs)
    }

    private fun remember(
        source: XmltvEpgSource,
        bytes: ByteArray,
        now: Long,
    ) {
        cachedFeeds[source.id] = CachedFeed(bytes, now)
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
