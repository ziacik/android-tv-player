package sk.ziacik.androidtvplayer.channel

enum class TvChannel(
    val storageKey: String,
    val stvrId: String,
    val displayName: String,
) {
    JEDNOTKA("jednotka", "1", "JEDNOTKA"),
    DVOJKA("dvojka", "2", "DVOJKA");

    fun next(): TvChannel = entries[(ordinal + 1) % entries.size]

    fun previous(): TvChannel = entries[(ordinal - 1 + entries.size) % entries.size]

    companion object {
        fun fromStorageKey(key: String?): TvChannel =
            entries.firstOrNull { it.storageKey == key } ?: JEDNOTKA
    }
}
