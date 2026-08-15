package sk.ziacik.androidtvplayer.resolver

data class StreamSource(
    val url: String,
    val userAgent: String,
    val headers: Map<String, String> = emptyMap(),
)

class StreamResolveException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
