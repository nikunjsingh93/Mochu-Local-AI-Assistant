# Project Knowledge

## Overview

**Mochu AI** is a private AI assistant for Android that runs models entirely on the phone. It has no account, no cloud and works offline. It's open source (MIT) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant, and the APKs are published as GitHub releases.

Main features:
- **Chat** with on-device models, with visible thinking.
- **Documents and photos:** ask about attached files and pictures.
- **Image creation** on the phone.
- **Web search** and an in-app **Browser**.
- **Memory:** facts the user asks Mochu to remember.
- **Scheduled assistant tasks**, reminders, timers and a stopwatch.
- **Voice** input and read-aloud replies.
- **Code:** build web, React and Python apps and export them as APKs.
- **Local API server** (OpenAI-compatible) for other devices on the same Wi-Fi.
- **Home-server models** from Ollama, LM Studio and similar.
- **Smart cards** under answers (new in 2.0).

## Technology Stack

- **App:** Kotlin and Jetpack Compose (Material 3) in a single app module. compileSdk/targetSdk 36, minSdk 31 (Android 12+).
- **Package:** `com.nikunj.mochu`. The Kotlin source package is still `com.nikunj.gemmabuilder`, from the project's original name.
- **LiteRT models** (`.litertlm`, e.g. Gemma 4 E2B/E4B) run on the GPU through `com.google.ai.edge.litertlm:litertlm-android` (pinned version in `app/build.gradle.kts`).
- **GGUF models** run on the CPU through llama.cpp. It's a git submodule at `app/src/main/cpp/llama.cpp`, with the JNI bridge in `app/src/main/cpp/llama_bridge.cpp`. NDK 28.2.13676358.
- **Image generation** uses stable-diffusion.cpp, a submodule in the `imagegen/` module.
- **`webshell/`** is the module used for APK export of Code projects.
- **Unit tests:** JUnit 4 in `app/src/test/java/com/nikunj/gemmabuilder/`.

## Project Structure

Paths are under `app/src/main/java/com/nikunj/gemmabuilder/`.

- **`viewmodel/BuilderViewModel.kt`:** the central ViewModel. It holds the chat list (`conversations`), `BuilderUiState`, model loading, `generate()` and `generateAssistantReply`, web research (`runWebResearch`), and task commands (`applyTaskCommand`).
- **`model/BuilderModels.kt`:** `BuilderUiState`, the `BuilderEngine` interface and data types.
- **`engine/`:**
  - `LiteRtGemmaEngine.kt` (GPU) and `LlamaCppEngine.kt` (CPU; the KV cache type is passed as an ordinal: 0 = F16, 1 = Q8_0).
  - `RemoteEngine.kt` for home-server models.
  - `ImageGenerator.kt`.
- **`data/`:**
  - `ModelCatalog.kt`: the downloadable models and `modelCapabilities()`.
  - `ProjectStorage.kt`: SharedPreferences settings, imported models in `filesDir/models/imported/`, and the auto-unload setting.
  - `Tasks.kt`: `ScheduledTask`, the stopwatch, and the chat command parser `taskCommandFrom()`.
  - `TaskRunner.kt`: AlarmManager alarms (`TaskAlarms`), notifications (`TaskNotifications`), and `TaskRunnerService`, which runs scheduled assistant tasks.
  - `web/WebTools.kt`: search (DuckDuckGo with a Bing fallback), page fetching, `WebPolicy`, `chatNeedsWeb()` and `linksIn()`. `web/WebViewReader.kt` reads JS-heavy pages.
  - `PriceTracking.kt`: product price extraction and price history in `filesDir/price_history/`.
  - `RichCards.kt`: card detection (`cardKindFor`), extraction prompts and validation, and `richBlocksFor()`.
  - `Speech.kt`: text-to-speech and `VoiceInput` speech recognition.
  - `ChatDocuments.kt`, `Memory.kt`, `ApiServer.kt`, `ApkExport.kt`, and others.
- **`ui/`:**
  - `AppShell.kt`: navigation, the chats drawer and screen wiring.
  - `ModelDownloads.kt`: the Models page.
  - `TasksScreen.kt`: Scheduled tasks, Memory and Reminders.
  - `SettingsAndFiles.kt`.
  - `chat/`: `AssistantChatScreen.kt`, `RichMarkdown.kt` (markdown renderer), `RichBlocks.kt` and `RichCardTypes.kt` (card and chart UI), and `ChatClocks.kt` (in-chat timers and stopwatch).
- **`docs/assets/`:** the icon and the card screenshots used in the README and release notes.

## Architecture

- **One engine at a time:** the ViewModel holds a single `engine: BuilderEngine`. `DemoBuilderEngine` means no model is loaded. `EngineHolder` (in `TaskRunner.kt`) shares the loaded engine with scheduled tasks and the API server.
- **Chat flow (`generateAssistantReply`):**
  1. Memory commands, image prompts and task commands are handled by the app, not the model.
  2. Then web research runs if it's needed.
  3. Then the model streams its reply.
  4. Then cards are added (`richBlocksFor`), then the Sources list.
- **Web research:** links pasted in a message are read directly, with no search. Otherwise it searches and reads up to `MAX_WEB_PAGES`, one page per site for search results. Pages are fetched directly first and fall back to a WebView reader.
- **Cards:**
  - A card is a fenced ```` ```card ```` / ```` ```chart ```` JSON block inside the reply text.
  - `RichMarkdown` renders those blocks as native UI. If the JSON can't be read, it falls back to showing the code.
  - The model only extracts JSON from text it was given, and the app checks every number against the source (`priceOnPage` / `numberOnPage`).
  - News cards are built from search results without the model.
  - `speakableText()` strips card blocks before text-to-speech and notifications.
- **Scheduled tasks:**
  - `TaskAlarms.schedule` saves the task (in `tasks.json`) and sets an exact alarm.
  - `TaskRunnerService` runs it, saves the result as a "⏰ <task>" chat and posts a notification.
  - Tasks store the `chatId` they were created from. Rescheduling ("actually make it 9am") and notification taps use it.
- **Chat commands** in `taskCommandFrom()` (in `Tasks.kt`): timers, reminders, the stopwatch, scheduled tasks and `Reschedule`. Polite openings like "could you please" are stripped first. A reminder that asks for writing ("…and add a mindful note") becomes an assistant task.
- **Idle auto-unload:** the model unloads after `autoUnloadSeconds` without use (default 60; set in Settings). The Models page has an Unload button.

## Development Commands

- **Build and test:**
  ```
  gradle testReleaseUnitTest assembleRelease --console=plain -q
  ```
  - Set `ANDROID_HOME` to the Android SDK (on the owner's machine, `C:\Users\Nikunj\Android\Sdk`).
  - There's no Gradle wrapper on that machine. Previous sessions used a downloaded Gradle 8.13.
- **APK output:** `app/build/outputs/apk/release/app-release.apk`. It's signed only when `keystore.properties` exists.
- **Verify the signature:**
  ```
  apksigner verify --print-certs app-release.apk
  ```
  The expected certificate SHA-256 starts with `ad1dbbc3`.
- **Install on a device:**
  ```
  adb -t <transport_id> install -r <apk>
  ```
  The owner's test phones are a Samsung S25 (SM-S938U1) and an Athena tablet (9425X), both connected over wireless adb.
- **Release flow** (only when the owner asks):
  1. Bump `versionCode`/`versionName` in `app/build.gradle.kts` and in the README's Build Notes.
  2. Build and verify the signature.
  3. Commit and push to `main`.
  4. Create the release:
     ```
     gh release create vX.Y.Z Mochu.AI.vX.Y.Z.apk --target main --title ... --notes-file ...
     ```
  5. Install on the phones.

## Important Decisions

- **Cards are text blocks, not a separate message type,** so they're saved with chats, copy as text, and fall back gracefully.
- **Card numbers must appear in the source.** Small models invent prices and scores, so unverified values are dropped rather than shown.
- **Price history is only Mochu's own checks.** Points within 10 minutes replace each other. A store's "typical/list price" is read from labeled text on the page and shown as a reference, not as a history point.
- **Task, timer, reminder and memory commands are parsed by the app.** Models often claim they set a reminder when they didn't, so these never go to the model.
- **The Models page has a fixed order:** "Loaded model" first, then LiteRT, then GGUF, then Image generation. Using a model scrolls up to Loaded model.
- **KV cache options are F16 and Q8_0 only.** Q4_0 was removed because it hurt answer quality.
- **Qwen2.5 Coder was removed** from the catalog.
- **Imported models** each get their own file in `models/imported/`. Several can coexist.
- **Timers and the stopwatch** live in the chat that started them (`ChatClocks`), not on the Tasks page.
- **Voice** uses on-device recognition first. On errors 12/13 (language pack missing), it asks Android to download the pack and falls back to the standard recognizer.
- **The chat box placeholder** is always "Message Mochu…". Web search state shows only on the globe button.

## Known Issues and Pitfalls

- **The in-app Browser blocks non-http redirects** (`WebPolicy`). Buttons that open other apps, like Maps, must use `context.startActivity(Intent)` directly. `LocalUriHandler` goes through the in-app browser.
- **DuckDuckGo sometimes asks for a bot check.** Bing is the fallback, and scheduled tasks retry once after a minute.
- **Amazon and other stores sometimes show bot checks.** Then no price card is shown, only the text answer.
- **News cards** list search results, which are often section pages ("AI News | Reuters") rather than single articles.
- **Small models copy example values** from JSON prompts ("next game, or null"). `RichCards.kt` filters known example values, so keep prompt examples realistic.
- **A starting service** that doesn't call `startForeground` in time crashes (`ForegroundServiceDidNotStartInTimeException`). `ReplyKeepAliveService` uses a delayed start for this reason.
- **`mergeDexRelease` occasionally fails** with a transient error. Rerunning the build fixes it.
- **Shell edits with `node -e` or heredocs break on apostrophes and backslashes.** Write a script file, or use an editor tool, for multi-line Kotlin edits.
- **adb transport ids change** when the adb server restarts. Look them up again with `adb devices -l`.
- **When testing on the phone,** a "Stop" button gap can end a wait loop early, while a card is still being made. Wait for several quiet checks in a row.

## Conventions

- Comments explain *why* in plain words. User-facing strings are short, friendly sentences.
- New behavior gets a unit test when it's parsing or logic (`TasksTest`, `RichCardsTest`, `PriceTrackingTest`, `WebToolsTest`).
- Release notes and README use plain language, headings and emoji bullets, with screenshots in `docs/assets/`.
