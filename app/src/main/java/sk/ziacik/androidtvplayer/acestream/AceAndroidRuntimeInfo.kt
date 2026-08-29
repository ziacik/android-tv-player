package sk.ziacik.androidtvplayer.acestream

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.StatFs
import java.io.File
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

internal object AceAndroidRuntimeInfo {
    fun write(
        context: Context,
        abi: String,
        root: File,
        cache: File,
        output: File,
    ) {
        val appContext = context.applicationContext
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val memoryClassMb = activityManager?.memoryClass ?: 64
        val storage = StatFs(cache.absolutePath)
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val locale = Locale.getDefault()

        val paths = JSONObject()
            .put("aceStreamHome", cache.absolutePath)
            .put("cacheDir", cache.absolutePath)
            .put("logsDir", cache.absolutePath)
            .put("androidDataDir", File(root, "android-data").absolutePath)
            .put("tempDir", File(root, "tmp").absolutePath)

        val memory = JSONObject()
            .put(
                "totalBytes",
                memoryInfo.totalMem.takeIf { it > 0L } ?: memoryClassMb * 1024L * 1024L,
            )
            .put("availableBytes", memoryInfo.availMem)
            .put("javaMaxBytes", Runtime.getRuntime().maxMemory())
            .put("memoryClassMb", memoryClassMb)
            .put("lowMemory", memoryInfo.lowMemory)

        val storageInfo = JSONObject()
            .put("cacheAvailableBytes", storage.availableBytes)
            .put("cacheTotalBytes", storage.totalBytes)
            .put("cacheBlockSizeBytes", storage.blockSizeLong)
            .put("cacheBlockCount", storage.blockCountLong)
            .put("cacheAvailableBlocks", storage.availableBlocksLong)

        val device = JSONObject()
            .put("deviceId", deviceId(appContext))
            .put("appId", appContext.packageName)
            .put("arch", abi)
            .put("deviceAbi", abi)
            .put("supportedAbis", Build.SUPPORTED_ABIS.joinToString(","))
            .put("manufacturer", Build.MANUFACTURER.orFallback("Android"))
            .put("model", Build.MODEL.orFallback("Android"))
            .put("deviceName", Build.DEVICE.orFallback("Android"))
            .put("productName", Build.PRODUCT.orFallback("Android"))
            .put("androidRelease", Build.VERSION.RELEASE.orFallback(""))
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("displayLanguage", locale.language)
            .put("locale", locale.toLanguageTag())
            .put("isAndroidTv", isAndroidTv(appContext))
            .put("hasBrowser", false)
            .put("hasWebView", false)

        val app = JSONObject()
            .put("packageName", appContext.packageName)
            .put("versionCode", versionCode(packageInfo))
            .put("versionName", packageInfo.versionName.orFallback(""))
            .put("aceCompatVersionCode", ACE_COMPAT_APP_VERSION_CODE)

        output.parentFile?.mkdirs()
        output.writeText(
            JSONObject()
                .put("paths", paths)
                .put("memory", memory)
                .put("storage", storageInfo)
                .put("device", device)
                .put("app", app)
                .toString() + "\n",
            Charsets.UTF_8,
        )
    }

    private fun deviceId(context: Context): String {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.getString(DEVICE_ID_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return UUID.randomUUID().toString().also { created ->
            preferences.edit().putString(DEVICE_ID_KEY, created).apply()
        }
    }

    private fun isAndroidTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (mode == Configuration.UI_MODE_TYPE_TELEVISION) return true
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    @Suppress("DEPRECATION")
    private fun versionCode(packageInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

    private fun String?.orFallback(fallback: String): String =
        this?.takeIf { it.isNotBlank() } ?: fallback

    private const val PREFERENCES_NAME = "aceserve_android_info"
    private const val DEVICE_ID_KEY = "device_id"
    private const val ACE_COMPAT_APP_VERSION_CODE = "302131302"
}
