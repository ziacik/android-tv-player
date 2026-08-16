package sk.ziacik.androidtvplayer.player

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamResolveException

class PlayerController(
    private val scope: CoroutineScope,
    private val initialChannel: TvChannel,
    private val resolve: suspend (TvChannel) -> StreamResolution,
    private val playerPort: PlayerPort,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val onChannelSelected: (TvChannel) -> Unit = {},
    private val diagnostics: (message: String, cause: Throwable?) -> Unit = { _, _ -> },
) {
    private val mutableState = MutableStateFlow<PlayerUiState>(
        PlayerUiState.Resolving(initialChannel),
    )
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private var currentChannel = initialChannel
    private var activeProgram: ProgramMetadata? = null
    private var activePlaybackChannel: TvChannel? = null
    private var activeLoadId: Long? = null
    private var resolveJob: Job? = null
    private var restrictedRetryJob: Job? = null
    private var resolveGeneration = 0L
    private var nextLoadId = 0L
    private var released = false

    init {
        playerPort.setListener(
            object : PlayerPort.Listener {
                override fun onReady(loadId: Long, isPlaying: Boolean) {
                    if (!acceptsPlaybackCallback(loadId)) return
                    updateReadyState(isPlaying)
                }

                override fun onPlayingChanged(loadId: Long, isPlaying: Boolean) {
                    if (
                        acceptsPlaybackCallback(loadId) &&
                        mutableState.value is PlayerUiState.Ready
                    ) {
                        updateReadyState(isPlaying)
                    }
                }

                override fun onError(loadId: Long, message: String) {
                    if (!acceptsPlaybackCallback(loadId)) return
                    diagnostics("Media3 playback failed: $message", null)
                    activeProgram = null
                    activePlaybackChannel = null
                    activeLoadId = null
                    mutableState.value = PlayerUiState.Error(
                        channel = currentChannel,
                        message = ERROR_MESSAGE,
                        reason = playbackFailureReason(message),
                    )
                }
            },
        )
    }

    fun start() = resolveCurrentChannel()

    fun retry() = resolveCurrentChannel()

    fun channelUp() = switchTo(currentChannel.next())

    fun channelDown() = switchTo(currentChannel.previous())

    private fun switchTo(channel: TvChannel) {
        if (released) return
        if (channel == currentChannel) return
        currentChannel = channel
        onChannelSelected(channel)
        activeProgram = null
        activePlaybackChannel = null
        activeLoadId = null
        playerPort.stop()
        resolveCurrentChannel()
    }

    private fun resolveCurrentChannel() {
        if (released) return
        resolveJob?.cancel()
        restrictedRetryJob?.cancel()
        restrictedRetryJob = null
        val channel = currentChannel
        val generation = ++resolveGeneration
        resolveJob = scope.launch {
            if (released || generation != resolveGeneration || channel != currentChannel) {
                return@launch
            }
            mutableState.value = PlayerUiState.Resolving(channel)
            try {
                val resolution = resolve(channel)
                if (
                    released ||
                    generation != resolveGeneration ||
                    channel != currentChannel
                ) {
                    return@launch
                }
                applyResolution(channel, resolution)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (
                    released ||
                    generation != resolveGeneration ||
                    channel != currentChannel
                ) {
                    return@launch
                }
                diagnostics("Stream resolve failed for ${channel.displayName}", error)
                mutableState.value = PlayerUiState.Error(
                    channel = channel,
                    message = ERROR_MESSAGE,
                    reason = resolverFailureReason(error),
                )
            }
        }
    }

    fun refreshPlaybackSnapshot() {
        if (released) return
        val current = mutableState.value
        if (current is PlayerUiState.Ready) {
            mutableState.value = current.copy(playback = playerPort.snapshot())
        }
    }

    fun seekBack() {
        if (released) return
        val snapshot = playerPort.snapshot()
        if (!snapshot.isSeekable) return
        playerPort.seekTo(
            (snapshot.currentPositionMs - SEEK_INCREMENT_MS).coerceAtLeast(0L),
        )
    }

    fun seekForward() {
        if (released) return
        val snapshot = playerPort.snapshot()
        if (!snapshot.isSeekable) return
        val requested = snapshot.currentPositionMs + SEEK_INCREMENT_MS
        val target = snapshot.durationMs?.let { duration ->
            requested.coerceIn(0L, duration.coerceAtLeast(0L))
        } ?: requested
        playerPort.seekTo(target)
    }

    fun togglePlayback() {
        if (released) return
        if (playerPort.snapshot().isPlaying) playerPort.pause() else playerPort.play()
    }

    fun goLive() {
        if (released) return
        playerPort.goLive()
    }

    fun release() {
        if (released) return
        released = true
        resolveGeneration += 1
        resolveJob?.cancel()
        resolveJob = null
        restrictedRetryJob?.cancel()
        restrictedRetryJob = null
        activeProgram = null
        activePlaybackChannel = null
        activeLoadId = null
        playerPort.release()
    }

    private fun applyResolution(channel: TvChannel, resolution: StreamResolution) {
        when (resolution) {
            is StreamResolution.Playable -> {
                activeProgram = resolution.program
                activePlaybackChannel = channel
                val loadId = ++nextLoadId
                activeLoadId = loadId
                mutableState.value = PlayerUiState.Preparing(channel)
                try {
                    playerPort.load(loadId, resolution.source)
                } catch (_: Exception) {
                    if (!acceptsPlaybackCallback(loadId)) return
                    activeProgram = null
                    activePlaybackChannel = null
                    activeLoadId = null
                    diagnostics("Media3 load failed", null)
                    mutableState.value = PlayerUiState.Error(
                        channel = channel,
                        message = ERROR_MESSAGE,
                        reason = "Prehrávač nedokázal pripraviť stream",
                    )
                }
            }
            is StreamResolution.Unavailable -> {
                activeProgram = null
                activePlaybackChannel = null
                activeLoadId = null
                mutableState.value = PlayerUiState.Unavailable(
                    channel = channel,
                    program = resolution.program,
                )
                scheduleRestrictedRetry(channel, resolution.program.endsAtMs)
            }
            is StreamResolution.RequiresCredentials -> {
                activeProgram = null
                activePlaybackChannel = null
                activeLoadId = null
                mutableState.value = PlayerUiState.CredentialsRequired(resolution.channel)
            }
        }
    }

    private fun scheduleRestrictedRetry(channel: TvChannel, endsAtMs: Long?) {
        restrictedRetryJob?.cancel()
        val untilEnd = endsAtMs?.minus(nowMs())
        val retryDelayMs = if (untilEnd != null && untilEnd > 0L) {
            untilEnd + RETRY_AFTER_END_PADDING_MS
        } else {
            RESTRICTED_RETRY_FALLBACK_MS
        }
        restrictedRetryJob = scope.launch {
            delay(retryDelayMs)
            if (!released && channel == currentChannel) retry()
        }
    }

    private fun acceptsPlaybackCallback(loadId: Long): Boolean =
        !released &&
            loadId == activeLoadId &&
            activePlaybackChannel == currentChannel

    private fun updateReadyState(isPlaying: Boolean) {
        val program = activeProgram ?: return
        mutableState.value = PlayerUiState.Ready(
            channel = currentChannel,
            program = program,
            playback = playerPort.snapshot().copy(isPlaying = isPlaying),
        )
    }

    private fun resolverFailureReason(error: Exception): String = when (error) {
        is StreamResolveException -> error.message ?: "Zdroj vysielania neodpovedal"
        is IOException -> "Sieťové pripojenie nie je dostupné"
        else -> "Zdroj vysielania neodpovedal"
    }

    private fun playbackFailureReason(message: String): String = when {
        message.startsWith("HTTP ") -> "Server stream odmietol ($message)"
        message == "ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED" ->
            "Stream používa nepovolené HTTP spojenie"
        message == "ERROR_CODE_IO_BAD_HTTP_STATUS" -> "Server stream odpovedal chybou"
        message == "ERROR_CODE_PARSING_MANIFEST_MALFORMED" ->
            "Zdroj neposlal platný HLS playlist"
        else -> "Prehrávač stream odmietol"
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
        const val RETRY_AFTER_END_PADDING_MS = 2_000L
        const val RESTRICTED_RETRY_FALLBACK_MS = 60_000L
        const val ERROR_MESSAGE = "Stream sa nepodarilo načítať"
    }
}
