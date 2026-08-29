package sk.ziacik.androidtvplayer.acestream

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AceEngineController(
    context: Context,
) {
    private val context = context.applicationContext
    private val startupMutex = Mutex()
    private val processLock = Any()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var lastLogLine: String? = null

    suspend fun ensureReady() {
        startupMutex.withLock {
            if (withContext(Dispatchers.IO) { isHealthy() }) return

            withContext(Dispatchers.IO) {
                val current = currentProcess()
                if (current == null || !current.isAlive) {
                    prepareAndStart()
                }
            }

            val deadline = System.nanoTime() +
                TimeUnit.MILLISECONDS.toNanos(AceStartupPolicy.STARTUP_TIMEOUT_MILLIS)
            while (System.nanoTime() < deadline) {
                val current = currentProcess()
                    ?: throw IOException("AceServe process disappeared during startup")
                if (!current.isAlive) {
                    val detail = lastLogLine?.let { ": $it" }.orEmpty()
                    throw IOException("AceServe exited during startup$detail")
                }
                if (withContext(Dispatchers.IO) { isHealthy() }) {
                    Log.i(TAG, "AceServe is ready on 127.0.0.1 ports ${AceStartupPolicy.REQUIRED_PORTS.joinToString()}")
                    return
                }
                delay(250)
            }

            stop()
            val detail = lastLogLine?.let { ": $it" }.orEmpty()
            throw IOException(
                "AceServe did not become ready within ${AceStartupPolicy.STARTUP_TIMEOUT_MILLIS / 1000} seconds$detail",
            )
        }
    }

    fun stop() {
        val current = synchronized(processLock) {
            process.also { process = null }
        } ?: return

        current.destroy()
        Thread({
            runCatching {
                if (!current.waitFor(3, TimeUnit.SECONDS)) {
                    current.destroyForcibly()
                }
            }
        }, "aceserve-stop").apply {
            isDaemon = true
            start()
        }
    }

    private fun prepareAndStart() {
        val abi = AceRuntimePlatform.selectAbi(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            is64Bit = android.os.Process.is64Bit(),
        ) ?: throw IOException(
            "Embedded AceServe supports arm64-v8a and armeabi-v7a; device ABIs: " +
                Build.SUPPORTED_ABIS.joinToString(", "),
        )

        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE).takeIf { it > 0L } ?: 4096L
        if (pageSize > 4096L) {
            throw IOException(
                "Embedded AceServe requires a 4 KB Android page size; device page size is $pageSize bytes",
            )
        }

        val root = File(context.filesDir, "aceserve/$abi")
        prepareRuntime(root, abi)
        val cache = File(context.cacheDir, "aceserve").apply {
            if (!exists() && !mkdirs()) throw IOException("Cannot create $this")
        }
        File(root, "android-data").mkdirs()
        File(root, "tmp").mkdirs()

        val runtimeInfo = File(root, "android-runtime.json")
        AceAndroidRuntimeInfo.write(context, abi, root, cache, runtimeInfo)

        val runner = File(context.applicationInfo.nativeLibraryDir, "libacepython.so")
        if (!runner.isFile) throw IOException("Missing embedded AceServe runner: $runner")
        val script = File(root, "main_android.py")
        if (!script.isFile) throw IOException("Missing embedded AceServe launcher: $script")

        val command = AceRuntimeCommand.build(runner.absolutePath, script.absolutePath)
        val builder = ProcessBuilder(command)
            .directory(root)
            .redirectErrorStream(true)
        val environment = builder.environment()
        environment["ACE_ROOT"] = root.absolutePath
        environment["ACE_CACHE_DIR"] = cache.absolutePath
        environment["ACE_ANDROID_INFO"] = runtimeInfo.absolutePath
        environment["ACESTREAM_HOME"] = root.absolutePath
        environment["ANDROID_ROOT"] = "/system"
        environment["ANDROID_DATA"] = File(root, "android-data").absolutePath
        environment["PYTHONHOME"] = File(root, "python").absolutePath
        environment["PYTHONPATH"] = AceRuntimeCommand.pythonPath(root.absolutePath)
        environment["LD_LIBRARY_PATH"] = AceRuntimeCommand.libraryPath(
            root.absolutePath,
            context.applicationInfo.nativeLibraryDir,
        )
        environment["FROZENLIST_NO_EXTENSIONS"] = "1"
        environment["MULTIDICT_NO_EXTENSIONS"] = "1"
        environment["YARL_NO_EXTENSIONS"] = "1"
        environment["TEMP"] = File(root, "tmp").absolutePath
        environment["PATH"] = File(root, "python/bin").absolutePath + ":/system/bin"

        Log.i(TAG, "Starting embedded AceServe abi=$abi")
        val started = builder.start()
        synchronized(processLock) {
            process = started
        }
        startLogThread(started)
    }

    private fun prepareRuntime(root: File, abi: String) {
        val marker = File(root, ".prepared-ace-$abi-v1")
        if (!marker.isFile) {
            deleteRecursively(root)
            if (!root.mkdirs() && !root.isDirectory) throw IOException("Cannot create $root")

            val zipName = if (abi == AceRuntimePlatform.ABI_ARM32) {
                "ace-armeabi-v7a.zip"
            } else {
                "ace-arm64-v8a.zip"
            }
            val cachedZip = File(context.cacheDir, zipName)
            context.assets.open("aceserve/$abi/$zipName").use { input ->
                FileOutputStream(cachedZip).buffered().use { output -> input.copyTo(output) }
            }
            unzip(cachedZip, root)
            cachedZip.delete()
            if (!marker.createNewFile()) throw IOException("Cannot create $marker")
        }

        context.assets.open("aceserve/main_android.py").use { input ->
            FileOutputStream(File(root, "main_android.py")).buffered().use { output ->
                input.copyTo(output)
            }
        }
        deletePythonBridge(root)
    }

    private fun isHealthy(): Boolean = AceStartupPolicy.REQUIRED_PORTS.all(::isPortOpen)

    private fun isPortOpen(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(LOCAL_HOST, port), TCP_PROBE_TIMEOUT_MILLIS)
        }
        true
    }.getOrDefault(false)

    private fun startLogThread(current: Process) {
        Thread({
            try {
                BufferedReader(InputStreamReader(current.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        lastLogLine = line
                        Log.i(TAG, line.orEmpty())
                    }
                }
            } catch (error: IOException) {
                if (current.isAlive) Log.w(TAG, "AceServe log reader failed", error)
            }
        }, "aceserve-log").apply {
            isDaemon = true
            start()
        }
    }

    private fun currentProcess(): Process? = synchronized(processLock) { process }

    private fun unzip(zip: File, destination: File) {
        val destinationPath = destination.canonicalPath + File.separator
        ZipInputStream(FileInputStream(zip).buffered()).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val entry = input.nextEntry ?: break
                val target = File(destination, entry.name)
                val targetPath = target.canonicalPath
                if (!targetPath.startsWith(destinationPath)) {
                    throw IOException("Unsafe AceServe zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    if (!target.exists() && !target.mkdirs()) throw IOException("Cannot create $target")
                } else {
                    target.parentFile?.let { parent ->
                        if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create $parent")
                    }
                    FileOutputStream(target).buffered().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0) output.write(buffer, 0, read)
                        }
                    }
                }
                input.closeEntry()
            }
        }
    }

    private fun deletePythonBridge(root: File) {
        File(root, "app_bridge.py").delete()
        File(root, "__pycache__").listFiles()
            ?.filter { it.name.startsWith("app_bridge.") }
            ?.forEach(File::delete)
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        file.listFiles()?.forEach(::deleteRecursively)
        if (!file.delete()) Log.w(TAG, "Could not delete $file")
    }

    private companion object {
        const val TAG = "AceEngine"
        const val LOCAL_HOST = "127.0.0.1"
        const val TCP_PROBE_TIMEOUT_MILLIS = 250
    }
}
