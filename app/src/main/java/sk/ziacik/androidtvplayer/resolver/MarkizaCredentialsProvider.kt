package sk.ziacik.androidtvplayer.resolver

object FreeviewMarkizaCredentials {
    val default = MarkizaCredentials(
        email = "c3761381@urhen.com",
        password = "heslo123",
    )
}

class MarkizaCredentialsProvider(
    private val localCredentials: () -> MarkizaCredentials?,
) {
    fun load(): MarkizaCredentials = localCredentials() ?: FreeviewMarkizaCredentials.default
}
