import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing reads keystore.properties at the project root (gitignored; see
// keystore.properties.example). Without it, release builds are produced unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.nikunj.gemmabuilder"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    defaultConfig {
        // Changed from com.nikunj.gemmabuilder in 1.0.7: the original signing key was lost, so this
        // ships as a new app that installs next to 1.0.6 instead of failing to update it.
        applicationId = "com.nikunj.mochu"
        minSdk = 31
        // Google Play requires API 36 for new apps and updates from August 31, 2026.
        targetSdk = 36
        versionCode = 22
        versionName = "1.9.2"

        ndk {
            // On-device models only run on 64-bit Arm phones; this also drops the x86/armv7 copies
            // of every native library from the APK.
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                // llama.cpp is always built optimized; a debug build is far too slow to be usable.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_BUILD_TESTS=OFF"
                arguments += "-DLLAMA_BUILD_TOOLS=OFF"
                arguments += "-DLLAMA_BUILD_EXAMPLES=OFF"
                arguments += "-DLLAMA_BUILD_SERVER=OFF"
                arguments += "-DLLAMA_BUILD_APP=OFF"
                arguments += "-DLLAMA_OPENSSL=OFF"
                arguments += "-DLLAMA_CURL=OFF"
                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=ON"
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":imagegen"))
    implementation("com.android.tools.build:apksig:8.12.1")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // HTML parsing for DuckDuckGo results and readable page text.
    implementation("org.jsoup:jsoup:1.23.2")

    // LiteRT-LM Android runtime for .litertlm Gemma models.
    // If Android Studio warns about this version, change it to latest.release.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in local unit tests.
    testImplementation("org.json:json:20240303")
}

// "Export as Android app" fills in the :webshell APK, which ships inside this app's assets.
val webShellAssets = layout.buildDirectory.dir("generated/webShellAssets")
val copyWebShell = tasks.register<Copy>("copyWebShell") {
    dependsOn(":webshell:assembleRelease")
    from(project(":webshell").layout.buildDirectory.file("outputs/apk/release/webshell-release-unsigned.apk"))
    into(webShellAssets.map { it.dir("shell") })
    rename { "webshell.apk" }
}
android.sourceSets.getByName("main").assets.srcDir(webShellAssets)
tasks.named("preBuild") { dependsOn(copyWebShell) }
