plugins {
    id("com.android.library")
}

// On-device image generation: stable-diffusion.cpp with its own (patched) ggml, built into
// libmochu_sd.so. It is a separate module because llama.cpp in :app ships a different ggml, and one
// CMake build can't hold both.
android {
    namespace = "com.nikunj.mochu.imagegen"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 31
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=OFF"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
}
