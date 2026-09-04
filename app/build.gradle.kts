plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val releaseKeystoreFile = System.getenv("KANALIK_KEYSTORE_FILE")
val releaseKeystorePassword = System.getenv("KANALIK_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("KANALIK_KEY_ALIAS")
val releaseKeyPassword = System.getenv("KANALIK_KEY_PASSWORD")
val releaseVersionCode = System.getenv("KANALIK_VERSION_CODE")?.toIntOrNull()
val releaseVersionName = System.getenv("KANALIK_VERSION_NAME")

android {
    namespace = "sk.ziacik.androidtvplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "sk.ziacik.androidtvplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode ?: 2
        versionName = releaseVersionName ?: "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseKeystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = requireNotNull(releaseKeystorePassword) {
                    "KANALIK_KEYSTORE_PASSWORD is required when KANALIK_KEYSTORE_FILE is set"
                }
                keyAlias = requireNotNull(releaseKeyAlias) {
                    "KANALIK_KEY_ALIAS is required when KANALIK_KEYSTORE_FILE is set"
                }
                keyPassword = requireNotNull(releaseKeyPassword) {
                    "KANALIK_KEY_PASSWORD is required when KANALIK_KEYSTORE_FILE is set"
                }
            }
        }

        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    sourceSets.named("debug") {
        assets.srcDir(layout.buildDirectory.dir("generated/assets/channels").get().asFile)
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

tasks.named("preBuild") {
    dependsOn(copyChannelCatalog)
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
