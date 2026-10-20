plugins {
    id("com.android.application")
}

// The app shell Mochu Code's "Export as Android app" fills in: a WebView that shows assets/www.
// It is built unsigned and shipped inside :app; on the phone Mochu adds the project's files, renames
// the package and label (the long placeholders below), and signs the result.
android {
    namespace = "com.mochu.shell"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mochu.app.p0000000000000000000000000000000000000000000"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
