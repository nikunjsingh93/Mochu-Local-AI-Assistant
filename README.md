<p align="center">
  <img src="docs/assets/mochu.svg" alt="Mochu AI logo" width="96" />
</p>

<h1 align="center">Mochu AI</h1>

<p align="center">
  A cute, private AI assistant that runs entirely on your Android phone. Chat, search the web, ask about photos,
  set timers and scheduled tasks, talk with your voice, and build small web apps, all with on-device models.
</p>

## App Features

- **Chat** with on-device models (Gemma 4 E2B/E4B on the GPU, GGUF models like Qwen and Bonsai on the CPU), with visible thinking
- **Web search and an in-app Browser**: Mochu searches DuckDuckGo when a question needs current info and cites its sources
- **Photos**: ask about pictures and screenshots (Gemma 4 E2B/E4B)
- **Memory**: "remember that…" facts used in every chat, managed in Settings
- **Tasks**: timers, a stopwatch, reminders, and scheduled assistant tasks ("every day at 8am summarize tech news") with notifications
- **Voice**: talk with the mic; replies can be read aloud
- **Studio**: build and preview small web apps (HTML/CSS/JS) from prompts and export them as ZIP
- Everything runs on the phone; chats, files and models stay on the device

## Screenshots

<p align="center">
  <img src="docs/screenshots/1.jpg" alt="App screenshot 1" width="300" />
  <img src="docs/screenshots/3.jpg" alt="App screenshot 3" width="300" />
</p>

<p align="center">
  <img src="docs/screenshots/2.jpg" alt="App screenshot 2" width="300" />
  <img src="docs/screenshots/Screenshot_20260505_123027.jpg" alt="App screenshot 4" width="300" />
</p>

<p align="center">
  <img src="docs/screenshots/Screenshot_20260505-121538.png" alt="App screenshot 5" width="960" />
</p>

## Device Requirements

Minimum (app + basic usage):
- Android 12 or newer
- 6 GB RAM
- 4 GB free storage for app, workspace, and model files

Recommended (comfortable local model experience):
- Android 13 or newer
- 8 GB RAM or more
- Mid-range to flagship processor
- 4-6 GB free storage (depending on model size and project assets)

Notes:
- Larger `.litertlm` models need more RAM and storage.
- First model load can take longer; later runs are usually faster.

## Installing

1. Go to the GitHub **Releases** page: [latest release](https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/latest)
2. Download the latest APK from the most recent release.
3. Open the APK on your Android device and install it.
4. Launch **Mochu AI**.

Upgrading from v1.0.6 or earlier: v1.0.7 and later are signed with a new key, so they install as a separate app next to the old one. Export any projects you want to keep from the old app (Files → Save ZIP), then uninstall it.

## Using the App

1. Open Mochu AI and download a model from the **Models** tab (the app recommends one for your phone), or import a `.litertlm` / `.gguf` file.
2. Chat with Mochu, or switch to **Studio** to build a web app from a prompt.
3. Use the globe for web search, the photo button for pictures, the mic to talk, and the clock for tasks and reminders.

## Security and Privacy Notes

- Generated project files stay inside app-private storage by default.
- External web navigation is restricted in preview flow.
- Imported files/models are handled locally on-device.

## Development

### Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- Android WebView
- LiteRT-LM Android runtime (`.litertlm` models, CPU/GPU/NPU)
- llama.cpp via JNI (`.gguf` models, CPU)
- ML Kit (OCR / labeling helpers)

### Requirements

- Android Studio (recent stable)
- JDK 17+
- Android SDK matching project config
- Android NDK 28.2 and CMake 3.31.6 (install from Android Studio's SDK Manager)
- The llama.cpp submodule: clone with `git clone --recurse-submodules`, or run `git submodule update --init` in an existing checkout

### Project Structure

- `app/src/main/java/com/nikunj/gemmabuilder/` - main app source
- `app/src/main/res/` - Android resources
- `app/src/main/AndroidManifest.xml` - app manifest
- `app/build.gradle.kts` - app module build config

### Build Notes

Current app version:

- `versionName`: `1.2.0`
- `applicationId`: `com.nikunj.ondevicestudio` (kept from the app's original name so updates install over earlier versions)

LiteRT-LM dependency is pinned in app Gradle config. If your environment needs it, you can switch to `latest.release`.

### Signing Release Builds

Release builds are signed when `keystore.properties` exists at the project root (it is gitignored). Copy `keystore.properties.example`, point `storeFile` at your keystore, and fill in the passwords. Without it, `assembleRelease` produces an unsigned APK. Keep the keystore and its password backed up outside the repo: every future update must be signed with the same key.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
