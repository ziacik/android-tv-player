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
    private val resolve: suspend () -> StreamResolution,
    private val playerPort: PlayerPort,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val diagnostics: (message: String, cause: Throwable?) -> Unit = { _, _ -> },
) {
    private val mutableState = MutableStateFlow<PlayerUiState>(
        PlayerUiState.Resolving(initialChannel),
    )
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private var activeProgram: ProgramMetadata? = null
    private var resolveJob: Job? = null
    private var restrictedRetryJob: Job? = null

    init {
        playerPort.setListener(
            object : PlayerPort.Listener {
                override fun onReady(isPlaying: Boolean) {
                    updateReadyState(isPlaying)
                }

                override fun onPlayingChanged(isPlaying: Boolean) {
                    if (mutableState.value is PlayerUiState.Ready) {
                        updateReadyState(isPlaying)
                    }
                }

                override fun onError(message: String) {
                    diagnostics("Media3 playback failed: $message", null)
                    mutableState.value = PlayerUiState.Error(initialChannel, ERROR_MESSAGE)
                }
            },
        )
    }

    fun start() = retry()

    fun retry() {
        resolveJob?.cancel()
        restrictedRetryJob?.cancel()
        restrictedRetryJob = null
        resolveJob = scope.launch {
            mutableState.value = PlayerUiState.Resolving(initialChannel)
            try {
                applyResolution(resolve())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics("STVR resolve failed", error)
                mutableState.value = PlayerUiState.Error(initialChannel, ERROR_MESSAGE)
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
        resolveJob?.cancel()
        restrictedRetryJob?.cancel()
        playerPort.release()
    }

    private fun applyResolution(resolution: StreamResolution) {
        when (resolution) {
            is StreamResolution.Playable -> {
                activeProgram = resolution.program
                mutableState.value = PlayerUiState.Preparing(initialChannel)
                playerPort.load(resolution.source)
            }
            is StreamResolution.Unavailable -> {
                activeProgram = null
                mutableState.value = PlayerUiState.Unavailable(
                    channel = initialChannel,
                    program = resolution.program,
                )
                scheduleRestrictedRetry(resolution.program.endsAtMs)
            }
        }
    }

    private fun scheduleRestrictedRetry(endsAtMs: Long?) {
        restrictedRetryJob?.cancel()
        val untilEnd = endsAtMs?.minus(nowMs())
        val retryDelayMs = if (untilEnd != null && untilEnd > 0L) {
            untilEnd + RETRY_AFTER_END_PADDING_MS
        } else {
            RESTRICTED_RETRY_FALLBACK_MS
        }
        restrictedRetryJob = scope.launch {
            delay(retryDelayMs)
            retry()
        }
    }

    private fun updateReadyState(isPlaying: Boolean) {
        val program = activeProgram ?: return
        mutableState.value = PlayerUiState.Ready(
            channel = initialChannel,
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
