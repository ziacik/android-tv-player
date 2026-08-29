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

val aceGeneratedRoot = layout.buildDirectory.dir("generated/ace")
val acePrepareScript = rootProject.layout.projectDirectory.file("scripts/prepare-aceserve-runtime.sh")

val prepareAceServeRuntime by tasks.registering(Exec::class) {
    description = "Downloads the pinned Android AceServe runtime used by the experimental AceStream support."
    group = "build setup"
    inputs.file(acePrepareScript)
    outputs.dir(aceGeneratedRoot)
    commandLine(
        "bash",
        acePrepareScript.asFile.absolutePath,
        aceGeneratedRoot.get().asFile.absolutePath,
    )
}

tasks.named("preBuild") {
    dependsOn(copyChannelCatalog)
}

tasks.configureEach {
    if (
        name.startsWith("merge") &&
        (name.endsWith("Assets") || name.endsWith("JniLibFolders") || name.endsWith("NativeLibs"))
    ) {
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
