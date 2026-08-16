package sk.ziacik.androidtvplayer.epg

import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

data class EpgProgramme(
    val title: String,
    val startsAtMs: Long,
    val endsAtMs: Long,
) {
    fun toProgramMetadata() = ProgramMetadata(
        title = title,
        startsAtMs = startsAtMs,
        endsAtMs = endsAtMs,
        internetAllowed = true,
    )
}
