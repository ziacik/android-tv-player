package sk.ziacik.androidtvplayer.ui

import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

data class MiniEpgRow(
    val channel: TvChannel,
    val channelNumber: Int,
    val programmeTitle: String,
    val progress: Float?,
    val isSelected: Boolean,
    val isCurrent: Boolean,
)

internal fun buildMiniEpgRows(
    channels: List<TvChannel>,
    currentChannel: TvChannel,
    selectedChannel: TvChannel,
    programmes: Map<String, ProgramMetadata>,
    nowMs: Long,
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
        MiniEpgRow(
            channel = channel,
            channelNumber = channelIndex + 1,
            programmeTitle = programme?.title.orEmpty(),
            progress = programmeProgress(programme, nowMs),
            isSelected = channel.storageKey == selectedChannel.storageKey,
            isCurrent = channel.storageKey == currentChannel.storageKey,
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

private fun programmeProgress(programme: ProgramMetadata?, nowMs: Long): Float? {
    val start = programme?.startsAtMs ?: return null
    val end = programme.endsAtMs ?: return null
    if (end <= start) return null
    return ((nowMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
}

private fun floorMod(value: Int, modulus: Int): Int =
    ((value % modulus) + modulus) % modulus
