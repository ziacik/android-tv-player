import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "sk.ziacik.androidtvplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "sk.ziacik.androidtvplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets.named("main") {
        assets.srcDir(layout.buildDirectory.dir("generated/assets/channels").get().asFile)
        assets.srcDir(layout.buildDirectory.dir("generated/ace/assets").get().asFile)
        jniLibs.srcDir(layout.buildDirectory.dir("generated/ace/jniLibs").get().asFile)
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val copyChannelCatalog by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.file("channels.json"))
    into(layout.buildDirectory.dir("generated/assets/channels"))
}

val aceUpstreamCommit = "19cbe60d0533c734ac3f50c7ccfdefe22422b4de"
val aceUpstreamBase =
    "https://raw.githubusercontent.com/jopsis/StreamVault-IPTV-Plugin-HaP/$aceUpstreamCommit/app/src/main"
val aceGeneratedRoot = layout.buildDirectory.dir("generated/ace")
val aceRuntimeFiles = listOf(
    "assets/aceserve/arm64-v8a/ace-arm64-v8a.zip",
    "assets/aceserve/armeabi-v7a/ace-armeabi-v7a.zip",
    "assets/aceserve/main_android.py",
    "jniLibs/arm64-v8a/libacepython.so",
    "jniLibs/armeabi-v7a/libacepython.so",
)

val prepareAceServeRuntime by tasks.registering {
    description = "Downloads the pinned Android AceServe runtime used by the experimental AceStream support."
    group = "build setup"
    outputs.files(
        aceRuntimeFiles.map { relativePath ->
            aceGeneratedRoot.map { root -> root.file(relativePath).asFile }
        },
    )

    doLast {
        val root = aceGeneratedRoot.get().asFile
        aceRuntimeFiles.forEach { relativePath ->
            val destination = root.resolve(relativePath)
            if (destination.isFile && destination.length() > 0L) return@forEach

            destination.parentFile?.mkdirs()
            val partial = destination.resolveSibling("${destination.name}.part")
            partial.delete()
            val connection = URI("$aceUpstreamBase/$relativePath").toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 120_000
            }
            connection.getInputStream().use { input ->
                partial.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            check(partial.length() > 0L) { "Downloaded empty AceServe runtime file: $relativePath" }
            check(partial.renameTo(destination)) {
                "Cannot move downloaded AceServe runtime file into place: $relativePath"
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(copyChannelCatalog)
}

tasks.configureEach {
    if (name.startsWith("merge") && (name.endsWith("Assets") || name.endsWith("NativeLibs"))) {
        dependsOn(prepareAceServeRuntime)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
