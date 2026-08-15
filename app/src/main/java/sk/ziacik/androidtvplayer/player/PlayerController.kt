package sk.ziacik.androidtvplayer.player

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
    private var resolveJob: Job? = null
    private var restrictedRetryJob: Job? = null
    private var resolveGeneration = 0L

    init {
        playerPort.setListener(
            object : PlayerPort.Listener {
                override fun onReady(isPlaying: Boolean) {
                    if (activePlaybackChannel != currentChannel) return
                    updateReadyState(isPlaying)
                }

                override fun onPlayingChanged(isPlaying: Boolean) {
                    if (
                        activePlaybackChannel == currentChannel &&
                        mutableState.value is PlayerUiState.Ready
                    ) {
                        updateReadyState(isPlaying)
                    }
                }

                override fun onError(message: String) {
                    if (activePlaybackChannel != currentChannel) return
                    diagnostics("Media3 playback failed: $message", null)
                    activePlaybackChannel = null
                    mutableState.value = PlayerUiState.Error(currentChannel, ERROR_MESSAGE)
                }
            },
        )
    }

    fun start() = resolveCurrentChannel()

    fun retry() = resolveCurrentChannel()

    fun channelUp() = switchTo(currentChannel.next())

    fun channelDown() = switchTo(currentChannel.previous())

    private fun switchTo(channel: TvChannel) {
        if (channel == currentChannel) return
        currentChannel = channel
        onChannelSelected(channel)
        activeProgram = null
        activePlaybackChannel = null
        playerPort.stop()
        resolveCurrentChannel()
    }

    private fun resolveCurrentChannel() {
        resolveJob?.cancel()
        restrictedRetryJob?.cancel()
        restrictedRetryJob = null
        val channel = currentChannel
        val generation = ++resolveGeneration
        resolveJob = scope.launch {
            mutableState.value = PlayerUiState.Resolving(channel)
            try {
                val resolution = resolve(channel)
                if (generation != resolveGeneration || channel != currentChannel) return@launch
                applyResolution(channel, resolution)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation != resolveGeneration || channel != currentChannel) return@launch
                diagnostics("STVR resolve failed", error)
                mutableState.value = PlayerUiState.Error(channel, ERROR_MESSAGE)
            }
        }
    }

    fun refreshPlaybackSnapshot() {
        val current = mutableState.value
        if (current is PlayerUiState.Ready) {
            mutableState.value = current.copy(playback = playerPort.snapshot())
        }
    }

    fun seekBack() {
        val snapshot = playerPort.snapshot()
        if (!snapshot.isSeekable) return
        playerPort.seekTo(
            (snapshot.currentPositionMs - SEEK_INCREMENT_MS).coerceAtLeast(0L),
        )
    }

    fun seekForward() {
        val snapshot = playerPort.snapshot()
        if (!snapshot.isSeekable) return
        val requested = snapshot.currentPositionMs + SEEK_INCREMENT_MS
        val target = snapshot.durationMs?.let { duration ->
            requested.coerceIn(0L, duration.coerceAtLeast(0L))
        } ?: requested
        playerPort.seekTo(target)
    }

    fun togglePlayback() {
        if (playerPort.snapshot().isPlaying) playerPort.pause() else playerPort.play()
    }

    fun goLive() {
        playerPort.goLive()
    }

    fun release() {
        resolveGeneration += 1
        resolveJob?.cancel()
        restrictedRetryJob?.cancel()
        playerPort.release()
    }

    private fun applyResolution(channel: TvChannel, resolution: StreamResolution) {
        when (resolution) {
            is StreamResolution.Playable -> {
                activeProgram = resolution.program
                activePlaybackChannel = channel
                mutableState.value = PlayerUiState.Preparing(channel)
                playerPort.load(resolution.source)
            }
            is StreamResolution.Unavailable -> {
                activeProgram = null
                activePlaybackChannel = null
                mutableState.value = PlayerUiState.Unavailable(
                    channel = channel,
                    program = resolution.program,
                )
                scheduleRestrictedRetry(channel, resolution.program.endsAtMs)
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
            if (channel == currentChannel) retry()
        }
    }

    private fun updateReadyState(isPlaying: Boolean) {
        val program = activeProgram ?: return
        mutableState.value = PlayerUiState.Ready(
            channel = currentChannel,
            program = program,
            playback = playerPort.snapshot().copy(isPlaying = isPlaying),
        )
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
        const val RETRY_AFTER_END_PADDING_MS = 2_000L
        const val RESTRICTED_RETRY_FALLBACK_MS = 60_000L
        const val ERROR_MESSAGE = "Stream sa nepodarilo načítať"
    }
}
