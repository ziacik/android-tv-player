package sk.ziacik.androidtvplayer.acestream

object AceRuntimePlatform {
    const val ABI_ARM64 = "arm64-v8a"
    const val ABI_ARM32 = "armeabi-v7a"

    fun selectAbi(
        supportedAbis: List<String>,
        is64Bit: Boolean,
    ): String? {
        val preferred = if (is64Bit) ABI_ARM64 else ABI_ARM32
        return preferred.takeIf(supportedAbis::contains)
    }

    fun isAceSource(url: String?): Boolean =
        url?.startsWith(ACESTREAM_SCHEME) == true

    private const val ACESTREAM_SCHEME = "acestream://"
}
