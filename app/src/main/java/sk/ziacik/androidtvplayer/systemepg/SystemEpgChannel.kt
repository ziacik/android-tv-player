package sk.ziacik.androidtvplayer.systemepg

import sk.ziacik.androidtvplayer.channel.TvChannel

data class SystemEpgChannel(
    val storageKey: String,
    val displayName: String,
    val displayNumber: String,
) {
    companion object {
        fun from(channel: TvChannel, position: Int) = SystemEpgChannel(
            storageKey = channel.storageKey,
            displayName = channel.displayName,
            displayNumber = position.toString(),
        )
    }
}
