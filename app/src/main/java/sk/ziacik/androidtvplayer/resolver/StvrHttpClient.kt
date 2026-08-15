package sk.ziacik.androidtvplayer.resolver

interface StvrHttpClient {
    suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): String
}

