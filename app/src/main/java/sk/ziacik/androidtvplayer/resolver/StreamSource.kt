package sk.ziacik.androidtvplayer.resolver

enum class StreamManifest {
    HLS,
    DASH,
}

data class StreamSource(
    val url: String,
    val userAgent: String,
    val headers: Map<String, String> = emptyMap(),
    val manifest: StreamManifest = StreamManifest.HLS,
)

class StreamResolveException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
