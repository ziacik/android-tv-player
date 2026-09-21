package sk.ziacik.androidtvplayer.ui

import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

enum class MiniEpgArchiveState {
    NOT_APPLICABLE,
    LOADING,
    AVAILABLE,
    UNAVAILABLE,
}

enum class MiniEpgSelectionAction {
    SELECT_LIVE,
    PLAY_ARCHIVE,
    IGNORE,
}

data class MiniEpgProgrammeKey(
    val channelStorageKey: String,
    val startsAtMs: Long?,
    val title: String,
)

data class MiniEpgRow(
    val channel: TvChannel,
    val channelNumber: Int,
    val programmeTitle: String,
    val progress: Float?,
    val isSelected: Boolean,
    val isCurrent: Boolean,
    val archiveState: MiniEpgArchiveState = MiniEpgArchiveState.NOT_APPLICABLE,
)

internal fun buildMiniEpgRows(
    channels: List<TvChannel>,
    currentChannel: TvChannel,
    selectedChannel: TvChannel,
    programmes: Map<String, ProgramMetadata>,
    nowMs: Long,
    archiveStates: Map<MiniEpgProgrammeKey, MiniEpgArchiveState> = emptyMap(),
    visibleCount: Int = 5,
): List<MiniEpgRow> {
    if (channels.isEmpty()) return emptyList()

    val selectedIndex = channels.indexOfFirst { it.storageKey == selectedChannel.storageKey }
        .takeIf { it >= 0 }
        ?: 0
    val rowCount = visibleCount.coerceAtLeast(1).coerceAtMost(channels.size)
    val firstOffset = -(rowCount / 2)

    return (0 until rowCount).map { rowIndex ->
        val channelIndex = floorMod(selectedIndex + firstOffset + rowIndex, channels.size)
        val channel = channels[channelIndex]
        val programme = programmes[channel.storageKey]
        val archiveState = programme?.let { selectedProgramme ->
            val key = miniEpgProgrammeKey(channel, selectedProgramme)
            when {
                !isPastProgramme(selectedProgramme, nowMs) -> MiniEpgArchiveState.NOT_APPLICABLE
                channel.archive == null -> MiniEpgArchiveState.UNAVAILABLE
                else -> archiveStates[key] ?: MiniEpgArchiveState.LOADING
            }
        } ?: MiniEpgArchiveState.NOT_APPLICABLE
        MiniEpgRow(
            channel = channel,
            channelNumber = channelIndex + 1,
            programmeTitle = programme?.title.orEmpty(),
            progress = programmeProgress(programme, nowMs),
            isSelected = channel.storageKey == selectedChannel.storageKey,
            isCurrent = channel.storageKey == currentChannel.storageKey,
            archiveState = archiveState,
        )
    }
}

internal fun adjacentMiniEpgChannel(
    channels: List<TvChannel>,
    selectedChannel: TvChannel,
    direction: Int,
): TvChannel {
    if (channels.isEmpty()) return selectedChannel
    val currentIndex = channels.indexOfFirst { it.storageKey == selectedChannel.storageKey }
        .takeIf { it >= 0 }
        ?: 0
    return channels[floorMod(currentIndex + direction, channels.size)]
}

internal fun previousProgrammeLookupTime(programme: ProgramMetadata): Long? =
    programme.startsAtMs?.takeIf { it > Long.MIN_VALUE }?.minus(1L)

internal fun nextProgrammeLookupTime(programme: ProgramMetadata): Long? =
    programme.endsAtMs

internal fun isPastProgramme(programme: ProgramMetadata, nowMs: Long): Boolean =
    programme.endsAtMs?.let { it <= nowMs } == true

internal fun miniEpgArchiveState(
    channel: TvChannel,
    programme: ProgramMetadata,
    nowMs: Long,
    available: Boolean?,
): MiniEpgArchiveState = when {
    !isPastProgramme(programme, nowMs) -> MiniEpgArchiveState.NOT_APPLICABLE
    channel.archive == null -> MiniEpgArchiveState.UNAVAILABLE
    available == true -> MiniEpgArchiveState.AVAILABLE
    available == false -> MiniEpgArchiveState.UNAVAILABLE
    else -> MiniEpgArchiveState.LOADING
}

internal fun miniEpgSelectionAction(
    programme: ProgramMetadata?,
    nowMs: Long,
    archiveState: MiniEpgArchiveState,
): MiniEpgSelectionAction {
    if (programme == null || !isPastProgramme(programme, nowMs)) {
        return MiniEpgSelectionAction.SELECT_LIVE
    }
    return if (archiveState == MiniEpgArchiveState.AVAILABLE) {
        MiniEpgSelectionAction.PLAY_ARCHIVE
    } else {
        MiniEpgSelectionAction.IGNORE
    }
}

internal fun miniEpgProgrammeKey(
    channel: TvChannel,
    programme: ProgramMetadata,
): MiniEpgProgrammeKey = MiniEpgProgrammeKey(
    channelStorageKey = channel.storageKey,
    startsAtMs = programme.startsAtMs,
    title = programme.title,
)

private fun programmeProgress(programme: ProgramMetadata?, nowMs: Long): Float? {
    val start = programme?.startsAtMs ?: return null
    val end = programme.endsAtMs ?: return null
    if (end <= start) return null
    return ((nowMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
}

private fun floorMod(value: Int, modulus: Int): Int =
    ((value % modulus) + modulus) % modulus
