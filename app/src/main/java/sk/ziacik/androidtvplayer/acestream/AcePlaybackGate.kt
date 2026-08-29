package sk.ziacik.androidtvplayer.acestream

class AcePlaybackGate(
    private val ensureReady: suspend () -> Unit,
) {
    suspend fun prepare(source: String?) {
        if (AceRuntimePlatform.isAceSource(source)) {
            ensureReady()
        }
    }
}
