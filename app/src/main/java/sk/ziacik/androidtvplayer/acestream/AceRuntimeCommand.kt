package sk.ziacik.androidtvplayer.acestream

object AceRuntimeCommand {
    fun build(
        runner: String,
        script: String,
    ): List<String> = listOf(
        runner,
        script,
        "--bind-all",
        "--live-cache-type",
        "memory",
        "--live-mem-cache-size",
        "104857600",
        "--disable-sentry",
        "--log-stdout",
        "--disable-upnp",
    )

    fun pythonPath(root: String): String = listOf(
        "$root/python/lib/stdlib",
        "$root/python/lib/modules",
        "$root/data",
        "$root/modules.zip",
        "$root/eggs-unpacked",
        "$root/lib",
    ).joinToString(":")

    fun libraryPath(
        root: String,
        nativeLibraryDir: String,
    ): String = listOf(
        "$root/python/lib",
        "$root/lib",
        "$root/acestreamengine",
        nativeLibraryDir,
        "/system/lib64",
        "/system/lib",
    ).joinToString(":")
}
