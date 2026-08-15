package sk.ziacik.androidtvplayer.resolver

data class ProgramMetadata(
    val title: String,
    val startsAtMs: Long?,
    val endsAtMs: Long?,
    val internetAllowed: Boolean?,
)

sealed interface StreamResolution {
    data class Playable(
        val program: ProgramMetadata,
        val source: StreamSource,
    ) : StreamResolution

    data class Unavailable(
        val program: ProgramMetadata,
    ) : StreamResolution
}

internal data class ParsedStvrClip(
    val program: ProgramMetadata,
    val hlsUrl: String?,
)
