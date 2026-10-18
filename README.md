<p align="center">
  <img src="docs/assets/icon_no_bg.png" alt="OnDevice Studio logo" width="72" />
</p>

<h1 align="center">OnDevice Studio</h1>

<p align="center">
  OnDevice Studio is an Android app that helps you build and preview web apps (HTML/CSS/JS) directly on your device using local AI workflows.
  Import a compatible .litertlm model, generate with prompts, iterate fast in live preview, and export your project when ready.
</p>

## App Features

- Build simple web apps from prompts directly on Android
- Live preview your generated app instantly
- Edit and iterate with chat-style prompts
- Import model files and run generation on-device
- Import project files and export your work as ZIP
- Keep your generated files inside app-local storage by default

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

1. Go to the GitHub **Releases** page: [OnDevice Studio v1.0.7](https://github.com/nikunjsingh93/ondevice-studio/releases/tag/v1.0.7)
2. Download the latest APK from the most recent release.
3. Open the APK on your Android device and install it.
4. Launch **OnDevice Studio**.

Upgrading from v1.0.6 or earlier: v1.0.7 is signed with a new key, so it installs as a separate app next to the old one. Export any projects you want to keep from the old app (Files → Save ZIP), then uninstall it.

## Using the App

1. Open OnDevice Studio on your device.
2. Import a compatible `.litertlm` or `.gguf` model from the menu.
Model setup: This repository does **not** include model files, so download one first and then import it in-app.
Recommended model family: `litert-community/gemma-4-E2B-it.litertlm`
Model source: [Hugging Face - litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/tree/main)
GGUF models (the format used by apps like PocketPal) run on the CPU through llama.cpp. A 4-bit quant (`Q4_K_M` or `Q4_0`) of a 1–4B instruct model is a good fit for phones.
3. Enter a prompt (example: `Build a calculator app with a clean UI`).
4. Generate and review the live preview.
5. Refine with follow-up prompts.
6. Export files when you are ready.

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

- `versionName`: `1.0.7`
- `applicationId`: `com.nikunj.ondevicestudio`

LiteRT-LM dependency is pinned in app Gradle config. If your environment needs it, you can switch to `latest.release`.

### Signing Release Builds

Release builds are signed when `keystore.properties` exists at the project root (it is gitignored). Copy `keystore.properties.example`, point `storeFile` at your keystore, and fill in the passwords. Without it, `assembleRelease` produces an unsigned APK. Keep the keystore and its password backed up outside the repo: every future update must be signed with the same key.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
