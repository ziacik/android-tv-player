package sk.ziacik.androidtvplayer.player

import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.ziacik.androidtvplayer.channel.ChannelProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.epg.EpgRepository
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamResolveException

class PlayerController(
    private val scope: CoroutineScope,
    private val initialChannel: TvChannel,
    private val resolve: suspend (TvChannel) -> StreamResolution,
    private val playerPort: PlayerPort,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val epgRepository: EpgRepository = EpgRepository { _, _ -> null },
    private val onChannelSelected: (TvChannel) -> Unit = {},
    private val diagnostics: (message: String, cause: Throwable?) -> Unit = { _, _ -> },
) {
    private val mutableState = MutableStateFlow<PlayerUiState>(
        PlayerUiState.Resolving(initialChannel),
    )
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()
    private val mutableStreamHost = MutableStateFlow<String?>(null)
    val streamHost: StateFlow<String?> = mutableStreamHost.asStateFlow()

    private var currentChannel = initialChannel
    private var activeProgram: ProgramMetadata? = null
    private var activePlaybackChannel: TvChannel? = null
    private var activeLoadId: Long? = null
    private var resolveJob: Job? = null
    private var retryJob: Job? = null
    private var retryAttempt = 0
    private var epgJob: Job? = null
    private var epgLoadId: Long? = null
    private var refreshedProgrammeEndMs: Long? = null
    private var resolveGeneration = 0L
    private var nextLoadId = 0L
    private var started = false
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
                    if (message == ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        diagnostics("Media3 playback fell behind the live window", null)
                        playerPort.recoverFromBehindLiveWindow()
                        return
                    }
                    val program = activeProgram
                    cancelEpgLookup()
                    diagnostics("Media3 playback failed: $message", null)
                    activeProgram = null
                    activePlaybackChannel = null
                    activeLoadId = null
                    if (currentChannel.provider == ChannelProvider.SWEET_TV) {
                        resolveCurrentChannel()
                        return
                    }
                    val nextRetryAtMs = scheduleOrdinaryRetry(currentChannel)
                    mutableState.value = PlayerUiState.Error(
                        channel = currentChannel,
                        message = ERROR_MESSAGE,
                        reason = playbackFailureReason(message),
                        nextRetryAtMs = nextRetryAtMs,
                        program = program,
                    )
                }
            },
        )
    }

    fun start() {
        if (released || started) return
        started = true
        resolveCurrentChannel()
    }

    fun stop() {
        if (released || !started) return
        started = false
        resolveGeneration += 1
        resolveJob?.cancel()
        resolveJob = null
        retryJob?.cancel()
        retryJob = null
        cancelEpgLookup()
        activeProgram = null
        activePlaybackChannel = null
        activeLoadId = null
        mutableStreamHost.value = null
        playerPort.stop()
    }

    fun retry() {
        retryAttempt = 0
        if (!started) return
        resolveCurrentChannel()
    }

    fun channelUp() = switchTo(currentChannel.next())

    fun channelDown() = switchTo(currentChannel.previous())

    fun selectChannel(channel: TvChannel) = switchTo(channel)

    private fun switchTo(channel: TvChannel) {
        if (released) return
        if (channel.storageKey == currentChannel.storageKey) return
        currentChannel = channel
        onChannelSelected(channel)
        activeProgram = null
        activePlaybackChannel = null
        activeLoadId = null
        mutableStreamHost.value = null
        cancelEpgLookup()
        playerPort.pause()
        resolveCurrentChannel()
    }

    private fun resolveCurrentChannel() {
        if (released) return
        if (!started) {
            mutableState.value = PlayerUiState.Resolving(currentChannel)
            return
        }
        resolveJob?.cancel()
        retryJob?.cancel()
        retryJob = null
        cancelEpgLookup()
        currentChannel = TvChannel.entries
            .firstOrNull { it.storageKey == currentChannel.storageKey }
            ?: currentChannel
        val channel = currentChannel
        val generation = ++resolveGeneration
        resolveJob = scope.launch {
            if (
                released ||
                !started ||
                generation != resolveGeneration ||
                channel != currentChannel
            ) {
                return@launch
            }
            mutableState.value = PlayerUiState.Resolving(channel)
            try {
                val resolution = resolve(channel)
                if (
                    released ||
                    !started ||
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
                    !started ||
                    generation != resolveGeneration ||
                    channel != currentChannel
                ) {
                    return@launch
                }
                diagnostics("Stream resolve failed for ${channel.displayName}", error)
                val nextRetryAtMs = scheduleOrdinaryRetry(channel)
                mutableState.value = PlayerUiState.Error(
                    channel = channel,
                    message = ERROR_MESSAGE,
                    reason = resolverFailureReason(error),
                    nextRetryAtMs = nextRetryAtMs,
                )
            }
        }
    }

    fun refreshPlaybackSnapshot() {
        if (released) return
        val current = mutableState.value
        if (current is PlayerUiState.Ready) {
            mutableState.value = current.copy(playback = playerPort.snapshot())
            activeProgram?.endsAtMs
                ?.takeIf { nowMs() >= it && refreshedProgrammeEndMs != it }
                ?.let { endedAtMs ->
                    refreshedProgrammeEndMs = endedAtMs
                    if (current.channel.provider == ChannelProvider.STVR) {
                        refreshStvrProgramme()
                    } else {
                        epgLoadId = null
                        requestEpgProgrammeIfNeeded(current.program, force = true)
                    }
                }
        }
    }

    private fun refreshStvrProgramme() {
        activeProgram = null
        activePlaybackChannel = null
        activeLoadId = null
        playerPort.stop()
        resolveCurrentChannel()
    }

    fun seekBack(): Long? {
        if (released) return null
        val snapshot = playerPort.snapshot()
        if (!snapshot.isSeekable) return null
        val target = (snapshot.currentPositionMs - SEEK_INCREMENT_MS).coerceAtLeast(0L)
        playerPort.seekTo(target)
        return seekClockTime(snapshot, target)
    }

    fun seekForward(): Long? {
        if (released) return null
        val snapshot = playerPort.snapshot()
        if (!snapshot.isSeekable) return null
        val requested = snapshot.currentPositionMs + SEEK_INCREMENT_MS
        val target = snapshot.durationMs?.let { duration ->
            requested.coerceIn(0L, duration.coerceAtLeast(0L))
        } ?: requested
        playerPort.seekTo(target)
        return seekClockTime(snapshot, target)
    }

    private fun seekClockTime(snapshot: PlaybackSnapshot, targetPositionMs: Long): Long? {
        val playbackOffsetMs = snapshot.liveOffsetMs
            ?: snapshot.durationMs
                ?.minus(snapshot.currentPositionMs)
                ?.coerceAtLeast(0L)
            ?: return null
        val currentPlaybackClockMs = nowMs() - playbackOffsetMs
        return currentPlaybackClockMs + (targetPositionMs - snapshot.currentPositionMs)
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
        started = false
        resolveGeneration += 1
        resolveJob?.cancel()
        resolveJob = null
        retryJob?.cancel()
        retryJob = null
        cancelEpgLookup()
        activeProgram = null
        activePlaybackChannel = null
        activeLoadId = null
        mutableStreamHost.value = null
        playerPort.release()
    }

    private fun applyResolution(channel: TvChannel, resolution: StreamResolution) {
        when (resolution) {
            is StreamResolution.Playable -> {
                val program = resolution.program.withEpgLookupPending(channel)
                mutableStreamHost.value = streamHost(resolution.source.url)
                activeProgram = program
                activePlaybackChannel = channel
                val loadId = ++nextLoadId
                activeLoadId = loadId
                mutableState.value = PlayerUiState.Preparing(channel, program)
                try {
                    requestEpgProgrammeIfNeeded(program)
                    playerPort.load(loadId, resolution.source)
                } catch (_: Exception) {
                    if (!acceptsPlaybackCallback(loadId)) return
                    activeProgram = null
                    activePlaybackChannel = null
                    activeLoadId = null
                    diagnostics("Media3 load failed", null)
                    val nextRetryAtMs = scheduleOrdinaryRetry(channel)
                    mutableState.value = PlayerUiState.Error(
                        channel = channel,
                        message = ERROR_MESSAGE,
                        reason = "Prehrávač nedokázal pripraviť stream",
                        nextRetryAtMs = nextRetryAtMs,
                        program = resolution.program,
                    )
                }
            }
            is StreamResolution.Unavailable -> {
                mutableStreamHost.value = null
                activeProgram = null
                activePlaybackChannel = null
                activeLoadId = null
                val nextRetryAtMs = scheduleUnavailableRetry(channel, resolution.program.endsAtMs)
                mutableState.value = PlayerUiState.Unavailable(
                    channel = channel,
                    program = resolution.program,
                    nextRetryAtMs = nextRetryAtMs,
                )
            }
            is StreamResolution.RequiresCredentials -> {
                mutableStreamHost.value = null
                activeProgram = null
                activePlaybackChannel = null
                activeLoadId = null
                mutableState.value = PlayerUiState.CredentialsRequired(resolution.channel)
            }
        }
    }

    private fun scheduleUnavailableRetry(channel: TvChannel, endsAtMs: Long?): Long {
        val untilEnd = endsAtMs?.minus(nowMs())
        val retryDelayMs = if (untilEnd != null && untilEnd > 0L) {
            untilEnd + RETRY_AFTER_END_PADDING_MS
        } else {
            nextRetryDelayMs()
        }
        return scheduleRetry(channel, retryDelayMs)
    }

    private fun scheduleOrdinaryRetry(channel: TvChannel): Long =
        scheduleRetry(channel, nextRetryDelayMs())

    private fun scheduleRetry(channel: TvChannel, retryDelayMs: Long): Long {
        retryJob?.cancel()
        val nextRetryAtMs = nowMs() + retryDelayMs
        retryJob = scope.launch {
            delay(retryDelayMs)
            if (!released && started && channel == currentChannel) resolveCurrentChannel()
        }
        return nextRetryAtMs
    }

    private fun nextRetryDelayMs(): Long {
        val delayMs = RETRY_DELAYS_MS[retryAttempt.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
        retryAttempt += 1
        return delayMs
    }

    private fun acceptsPlaybackCallback(loadId: Long): Boolean =
        !released &&
            started &&
            loadId == activeLoadId &&
            activePlaybackChannel == currentChannel

    private fun updateReadyState(isPlaying: Boolean) {
        val program = activeProgram ?: return
        retryAttempt = 0
        mutableState.value = PlayerUiState.Ready(
            channel = currentChannel,
            program = program,
            playback = playerPort.snapshot().copy(isPlaying = isPlaying),
        )
        requestEpgProgrammeIfNeeded(program)
    }

    private fun requestEpgProgrammeIfNeeded(
        program: ProgramMetadata,
        force: Boolean = false,
    ) {
        if (!force && program.hasProgrammeInterval()) {
            return
        }
        val channel = activePlaybackChannel ?: return
        val loadId = activeLoadId ?: return
        if (epgLoadId == loadId) return
        epgLoadId = loadId
        epgJob = scope.launch {
            val epgProgramme = try {
                epgRepository.currentProgram(channel, nowMs())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics("EPG lookup failed for ${channel.displayName}", error)
                null
            }
            if (!acceptsPlaybackCallback(loadId)) return@launch
            val resolvedProgram = epgProgramme
                ?.takeIf { it.title.isNotBlank() }
                ?.copy(isEpgLookupPending = false)
                ?: program.copy(isEpgLookupPending = false)
            activeProgram = resolvedProgram
            when (val current = mutableState.value) {
                is PlayerUiState.Ready -> if (current.channel == channel) {
                    mutableState.value = current.copy(program = resolvedProgram)
                }
                is PlayerUiState.Preparing -> if (current.channel == channel) {
                    mutableState.value = current.copy(program = resolvedProgram)
                }
                else -> Unit
            }
        }
    }

    private fun cancelEpgLookup() {
        epgJob?.cancel()
        epgJob = null
        epgLoadId = null
        refreshedProgrammeEndMs = null
    }

    private fun ProgramMetadata.withEpgLookupPending(channel: TvChannel): ProgramMetadata =
        if (
            !hasProgrammeInterval() &&
            (title.isBlank() || title == channel.displayName)
        ) {
            copy(
                title = channel.displayName,
                isEpgLookupPending = true,
            )
        } else {
            this
        }

    private fun ProgramMetadata.hasProgrammeInterval(): Boolean =
        startsAtMs != null && endsAtMs != null && endsAtMs > startsAtMs

    private fun streamHost(url: String): String? =
        runCatching { URI(url).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

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
        const val ERROR_CODE_BEHIND_LIVE_WINDOW = "ERROR_CODE_BEHIND_LIVE_WINDOW"
        const val SEEK_INCREMENT_MS = 10_000L
        const val RETRY_AFTER_END_PADDING_MS = 2_000L
        val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L)
        const val ERROR_MESSAGE = "Stream sa nepodarilo načítať"
    }
}
