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
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

fun interface EpgRepository {
    suspend fun currentProgram(channel: TvChannel, nowMs: Long): ProgramMetadata?
}

class CachedXmltvEpgRepository(
    private val cacheFile: File,
    private val download: suspend () -> ByteArray,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val parser: XmltvEpgParser = XmltvEpgParser(),
) : EpgRepository {
    private var programmes: Map<String, List<EpgProgramme>>? = null
    private var loadedAtMs: Long? = null

    override suspend fun currentProgram(channel: TvChannel, nowMs: Long): ProgramMetadata? {
        val channelId = channel.epgId ?: return null
        return withContext(Dispatchers.IO) {
            val programme = loadProgrammes()
                ?.get(channelId)
                ?.firstOrNull { it.startsAtMs <= nowMs && nowMs < it.endsAtMs }
            programme?.toProgramMetadata()
        }
    }

    private suspend fun loadProgrammes(): Map<String, List<EpgProgramme>>? {
        val now = clockMs()
        programmes
            ?.takeIf { loadedAtMs?.let { now - it < CACHE_FRESHNESS_MS } == true }
            ?.let { return it }

        if (cacheFile.isFile && now - cacheFile.lastModified() < CACHE_FRESHNESS_MS) {
            parseCache()?.let { return remember(it, now) }
        }

        val downloaded = try {
            download()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (downloaded != null) {
            try {
                val parsed = parseGzip(downloaded)
                writeCacheAtomically(downloaded)
                return remember(parsed, now)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the last complete cache if the downloaded feed is unusable.
            }
        }
        return parseCache()?.let { remember(it, now) }
    }

    private fun parseCache(): Map<String, List<EpgProgramme>>? = runCatching {
        parseGzip(cacheFile.readBytes())
    }.getOrNull()

    private fun parseGzip(bytes: ByteArray): Map<String, List<EpgProgramme>> =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { stream ->
            parser.parse(stream, TvChannel.entries.mapNotNull(TvChannel::epgId).toSet())
        }

    private fun remember(
        parsed: Map<String, List<EpgProgramme>>,
        now: Long,
    ): Map<String, List<EpgProgramme>> {
        programmes = parsed
        loadedAtMs = now
        return parsed
    }

    private fun writeCacheAtomically(bytes: ByteArray) {
        cacheFile.parentFile?.mkdirs()
        val temporaryFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        temporaryFile.writeBytes(bytes)
        Files.move(
            temporaryFile.toPath(),
            cacheFile.toPath(),
            ATOMIC_MOVE,
            REPLACE_EXISTING,
        )
    }

    private companion object {
        const val CACHE_FRESHNESS_MS = 6L * 60L * 60L * 1_000L
    }
}

class OkHttpEpgDownloader(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun download(): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(EPG_URL).build())
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

    private companion object {
        const val EPG_URL = "https://iptv-epg.org/files/epg-cz.xml.gz"
    }
}
