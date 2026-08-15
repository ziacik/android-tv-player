package sk.ziacik.androidtvplayer.resolver

data class StreamSource(
    val url: String,
    val userAgent: String,
)

class StreamResolveException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

