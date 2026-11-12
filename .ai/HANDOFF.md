# Development Handoff

## 2026-11-12: v2.4.2 release

- Prepared v2.4.2 (versionCode 38) with the repetition false-positive fix and Spanish image-request recognition described below. `README.md` and `app/build.gradle.kts` carry the new version.
- Gradle 8.13 `testReleaseUnitTest assembleRelease --console=plain -q` passed. The APK has package `com.nikunj.mochu`, version 2.4.2, and the expected signing certificate (`ad1dbbc3…`). The prior build of the repetition fix was tested on the S26; Spanish image generation has not been tested on a device.
- Next: publish the v2.4.2 commit and signed APK on GitHub, then verify the release. The Spanish prompt is not translated.

## 2026-11-12: Spanish image requests

- Added Spanish image-request recognition in `app/src/main/java/com/nikunj/gemmabuilder/engine/ImageGenerator.kt`, with positive and non-image cases in `app/src/test/java/com/nikunj/gemmabuilder/ImagePromptTest.kt`. The original Spanish description goes to the image model; no translation is performed, so image quality depends on the model's language support.
- Gradle 8.13 `testReleaseUnitTest assembleRelease --console=plain -q` passed. Not installed or tested on a device for this change.
- Next: try Spanish image prompts with the user's chosen image model; assess whether optional, reliable offline prompt translation is needed.

## 2026-11-12: Repetition false positive

- Fixed the shared repetition detector stopping valid code with recurring prefixes; it now requires consecutive identical blocks. Chat and scheduled tasks both use it.
- Changed `app/src/main/java/com/nikunj/gemmabuilder/data/Repetition.kt` and `app/src/test/java/com/nikunj/gemmabuilder/RepetitionTest.kt`; updated `.ai/PROJECT.md`.
- Compiled the detector and test with the installed Gradle 8.10.2 Kotlin compiler and ran JUnitCore: 6 tests passed. Full Android Gradle tests/build were not run: Gradle 8.13 is unavailable and downloading it failed due to DNS/network restrictions. No phone testing or installation.
- Gradle 8.13 `testReleaseUnitTest assembleRelease --console=plain -q` passed; the signed APK (certificate `ad1dbbc3…`) was installed on the S26. In a temporary chat, Qwen3.5-4B-3D-Coder generated the same series of `sun.shadow.camera` assignments and continued through several materials without a repetition warning. Stopped the still-running long response after verifying that behavior; the test chat and temporary phone files were removed.
- Next: the owner can try their original prompt on the installed build. The earlier test chat "Hello what's up" mentioned below is unrelated.

## Last Updated

Date: 2026-11-12
Agent: Claude Code (Claude Opus 5.5)

## Current State

- **v2.4.1 is released** (versionCode 37) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.4.1 (commit `0098c41`), with Mochu as the default digital assistant. The signature was verified (`ad1dbbc3…`). It's installed on the S26 Ultra and the onn tablet.
- **Default digital assistant (GitHub feature request), released in v2.4.1:**
  - **Manifest:** `MainActivity` handles `android.intent.action.ASSIST` and `VOICE_COMMAND`. That's enough for Android's assistant role: Samsung's Digital assistant app list on the S26 shows "Mochu AI" under Others, next to ChatGPT. No VoiceInteractionService is needed.
  - **`MainActivity.handleTaskIntent`:** sets `AppEvents.assist`, only on a fresh launch (`savedInstanceState == null`), so a rotation or restore doesn't start another voice chat.
  - **`AppShell`:** opens a new assistant chat, or reuses the open one if it's empty, then sets `AppEvents.listenNow`. `AssistantChatScreen` starts the mic. While a reply is running it only shows the app.
  - **Settings › `DigitalAssistantSetting()`:** shows whether Mochu holds `RoleManager.ROLE_ASSISTANT`, re-checked on resume. Its button opens `Settings.ACTION_VOICE_INPUT_SETTINGS`, falling back to `ACTION_MANAGE_DEFAULT_APPS_SETTINGS`. Apps can't request the assistant role themselves.
  - **Verified on the S26:**
    - Choosing Mochu set the role holder to `com.nikunj.mochu`.
    - `KEYCODE_ASSIST` opened Mochu, asked for the mic once (granted "Only this time"), and listened. Speech came through ("Hello what's up"), was sent, answered by LFM2.5 and read aloud.
    - The Settings row showed ✓, its button opened the role screen, and switching back to Google updated the row on resume.
    - The S26's assistant is back to Google.
    - 94 unit tests pass.
  - **Not yet checked:**
    - The physical side-button long-press. Samsung's Side key setting may need "Digital assistant" chosen; the test used `KEYCODE_ASSIST`.
    - Invoking from the lock screen. The activity doesn't show over the keyguard, so the user unlocks first.
  - **Left on the S26:** the test chat "Hello what's up". Deleting it from the drawer was blocked by the permission check, so the owner should delete it.
- **v2.4.0 is released** (versionCode 36) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.4.0 (commit `8ca8a93`), with LFM3 support, llama.cpp b12134 and the Combine devices version check. The signature was verified (`ad1dbbc3…`). It's installed on the S26 Ultra and the onn 8 Core tablet.
- **v2.3.0** (versionCode 35) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.3.0 (commit `d779c3f`), with tool calling in the local API and bold API details.
- **LFM3 support (GitHub issue #3), released in v2.4.0:**
  - **llama.cpp submodule** moved from `c061df1` (2026-10-01) to tag `b12134` (`040ce19`, 2026-11-05). That's after LFM3 support (#31252) and its fixes (tensor split, NVFP4, spec-dec), and before the 2026-11-10 FP8 KV-cache change.
  - **Build fixes:**
    - `llama_bridge.cpp`: `common_chat_templates_apply(...).prompt` is now a `common_chat_prompt` made of parts, so the bridge calls `.str()`. That's the same string as before.
    - `CMakeLists.txt`: the patched RPC copy now swaps only `ggml-rpc.cpp` inside upstream's own source list. Upstream added `log.cpp`, and listing files by hand broke the link. All four Mochu RPC patches still applied.
  - **Catalog:** a new LFM3 1.7B entry (`LiquidAI/LFM3-1.7B-GGUF` @ `cd0c74c`, `LFM3-1.7B-QAD-Q4_0.gguf`, 1,049,615,680 bytes, sha256 `43a7d09a…`). KV is 12,288 bytes per token: 6 full-attention layers × 4 KV heads × 128 head dim; the 5 sliding layers hold only 256 tokens.
  - **Tool-calling parser:** LFM3 writes the XML style `<tool_call><function=name><parameter=k>v</parameter></function></tool_call>`, and `xmlCalls` now reads it. Before this fix it scored 0/12, after it 12/12.
  - **Verified on the S26 Ultra:**
    - The catalog download passed its fingerprint check.
    - Chat, multi-turn, tool-result answers, streaming calls, and plain text with tools offered all worked.
    - LFM3 runs at roughly 50 tok/s on the CPU (chars ÷ 4 over the request time, including prompt time).
    - LFM2.5 still loads and works on the new llama.cpp.
    - 91 unit tests pass.
  - **The RPC protocol changed from 7.0.0 to 8.0.1.** Every device in a Combine group must run the new version. Release notes must say so.
  - **Version check for Combine devices** (`DeviceCluster.kt`, `CombineDevices.kt`):
    - `CLUSTER_PROTOCOL = 8` matches llama.cpp's `RPC_PROTO_MAJOR_VERSION`. `DeviceClusterTest.clusterProtocolMatchesLlamaCpp` reads `ggml-rpc.h` and fails if they differ, so a llama.cpp update that changes the protocol can't slip through.
    - **Protocol:** the worker's INFO reply adds the protocol as a 6th field. The main device sends its protocol after `INFO` and as a 6th field of `CONNECT`. Older Mochu sends none, which reads as 0.
    - **Main device:** a paired device on a different protocol gets a red message in its row ("… has an older Mochu … Update Mochu on … to the latest version"). It's left out of the memory total and skipped by `prepareForLoad`, and `pair()` refuses it.
    - **Worker:** it answers a mismatched `CONNECT` with `OLD`, writes `mismatch` to its status file, and the Share card shows the message.
    - The Share this device text now says both devices need the same Mochu version.
    - Tests: `parseWorkerInfo`, `clusterVersionProblem`, and the header check. 94 unit tests in total, all passing.
  - **Combine devices verified on the new llama.cpp** with the S26 (main) and the onn 8 Core tablet (6 GB, sharing):
    - LFM3 loaded split across both: the tablet showed "✓ Connected · SM-S948U1", and chat and a tool call worked.
    - The first load took 11 s including sending parts. Speed was about 20 tok/s, against about 50 on the S26 alone; expected for a small model.
    - With 2.3.0 on the tablet, the S26 row showed the "older Mochu" message, and a fresh load ran on the S26 alone.
    - With the old tablet as main and the S26 sharing, the S26's Share card showed "192.168.1.222 has an older Mochu…". Older Mochu sends no name with INFO, so it shows the IP.
    - **Afterwards:** both devices have the new build, the pairings were forgotten, Share, Create a cluster and the API server are off on both, and the S26 is back on LFM2.5. LFM3 is still downloaded on the S26.
  - Omni's audio input and LFM3-VL vision aren't wired up in Mochu. The Hugging Face search hides mmproj files.
  - Noticed: Gemma 4 E4B (LiteRT) is no longer downloaded on the S26. This session didn't delete it; the only delete was the Qwen3 1.7B test file.
- **v2.2.0** (versionCode 34) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.2.0, with Hugging Face search and download in the Models tab. The APK was replaced twice in place (parameter counts and GB progress, then search-result sizes), so the v2.2.0 tag points to the first commit.
- **v2.1.1 is released** (versionCode 33) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.1.1, with layer balance modes, send progress, import progress, and the stale-status fix.
- **v2.1.0** (versionCode 32) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.1.0, with Combine devices (experimental).
- **v2.0.3** (versionCode 31) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.0.3, with the wake lock while generating.
- **v2.0.2** was released (versionCode 30) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.0.2, with the opt-in server fallback and the hidden API key field (the APK was replaced once with the key-field change). v2.0.1 (29) added the Mochu notification icon, code downloads and code colors. v2.0.0 (28) is the Smart cards release.
- **The Athena tablet** wasn't connected, so it may still be on an older version.
- **The agent-memory files** (`AGENTS.md`, `CLAUDE.md`, `.ai/`) are committed in `c6c5c28`.
- **Server-model fixes** are committed in `8e75750`. The owner asked for them to go into the **existing v2.0.0 release** instead of a new one. The APK asset was replaced (same versionCode 28, signature verified) and the notes got a "Home-server models" section. The v2.0.0 git tag still points to `78eb98c`, so its source zip doesn't include these fixes.

## Recently Completed

- **v2.4.1 (2026-11-10):** Mochu as the default digital assistant. The side button, the assistant gesture, and Bluetooth/car voice buttons open a listening voice chat. Settings has a "Use Mochu as your digital assistant" row.
- **v2.4.0 (2026-11-10):**
  - LFM3 support: llama.cpp moved to b12134, and LFM3 1.7B was added to the catalog.
  - LFM3's XML tool-call format is read.
  - Combine devices checks versions (`CLUSTER_PROTOCOL`).
- **v2.3.0 (2026-11-10):** OpenAI-style tool calling in the local API, and bold Base URL, API key and model on the API card.
- **v2.2.0:** Hugging Face search and download in the Models tab.

Earlier:
- **v2.0.0 Smart cards:**
  - Product price with history chart and the store's typical price with the % saving.
  - Stocks and crypto, headlines, sports scores, two-link product comparison.
  - Recipe checklist, document summary with "ask more" chips, and places with hours and Open in Maps.
  - Cards also appear in scheduled tasks.
- **"Today at 11:05 p.m, …" scheduling:** "a.m."/"p.m." are read with or without dots, and messages can start with "today", "tonight" or "this morning/afternoon/evening".
- **Chats drawer:** it opens scrolled to the open chat.
- **Web reading:** comparisons read two pasted links from the same site. Sports, opening-hours and crypto questions trigger web search.
- **v1.9.7:**
  - Voice error 13 falls back and asks Android to download the language pack.
  - The chat box always says "Message Mochu…".
- **v1.9.6:**
  - Polite reminders ("could you please remind me…").
  - Reschedule from chat ("actually run this only today at 9am").
  - "Https://" links aren't doubled.
  - Tasks page order: Scheduled, Memory, Reminders.
  - Timers and the stopwatch live in their chat.
- **v1.9.5:**
  - Unload button and idle auto-unload (Settings, default 60 s).
  - Use scrolls to Loaded model.
  - Other chats can be deleted while a reply runs.
  - Q4_0 KV and Qwen2.5 Coder removed.

## Files Changed

Paths below are under `app/src/main/java/com/nikunj/gemmabuilder/` unless they start with `app/`.

**v2.4.1:**
- **`app/src/main/AndroidManifest.xml`:** `ASSIST` and `VOICE_COMMAND` intent filters on `MainActivity`.
- **`MainActivity.kt`:** `handleTaskIntent(intent, fresh)` sets `AppEvents.assist`.
- **`data/TaskRunner.kt`:** `AppEvents.assist` and `listenNow`.
- **`ui/AppShell.kt`:** opens or reuses a chat when invoked.
- **`ui/chat/AssistantChatScreen.kt`:** starts the mic on `listenNow`.
- **`ui/SettingsAndFiles.kt`:** `DigitalAssistantSetting()`.

**v2.4.0:**
- **`app/src/main/cpp/llama.cpp`:** the submodule moved to b12134.
- **`app/src/main/cpp/llama_bridge.cpp`:** `.prompt.str()`.
- **`app/src/main/cpp/CMakeLists.txt`:** the RPC source list.
- **`data/ModelCatalog.kt`:** the LFM3 1.7B entry.
- **`data/ToolCalling.kt`:** `xmlCalls`.
- **`data/DeviceCluster.kt`, `ui/CombineDevices.kt`:** the version check.
- **Tests:** `DeviceClusterTest.kt`, `ToolCallingTest.kt`.

**v2.3.0:**
- **`data/ToolCalling.kt`** (new).
- **`data/ApiServer.kt`:** tools in requests and `tool_calls` in replies.
- **`ui/SettingsAndFiles.kt`:** bold API details.
- **Tests:** `ToolCallingTest.kt` (new).

**v2.0.0 (older):**

- **`data/RichCards.kt`** (new): card detection, prompts, checks, `richBlocksFor()`.
- **`data/PriceTracking.kt`** (new): price extraction, the typical price, history storage (`recordValue`).
- **`ui/chat/RichBlocks.kt`, `ui/chat/RichCardTypes.kt`** (new): card and chart UI. `LocalAskAction` powers the chips.
- **`viewmodel/BuilderViewModel.kt`:**
  - Adds cards after replies.
  - `WebResearch.found`.
  - Pasted links are read even when they're on the same site.
- **`data/TaskRunner.kt`:** adds cards to scheduled task results.
- **`data/Tasks.kt`:** "today …" and "p.m" parsing, `Reschedule`, polite openings.
- **`data/web/WebTools.kt`:** `linksIn()` and more `chatNeedsWeb` triggers.
- **`ui/AppShell.kt`:** the drawer scrolls to the open chat.
- **`data/Speech.kt`:** card blocks are stripped from speech.
- **Tests:** `RichCardsTest.kt`, `PriceTrackingTest.kt`, `TasksTest.kt`.
- **`README.md`, `docs/assets/cards/`:** the Smart cards section and screenshots.

## Work in Progress

Nothing is in progress: everything is committed and released up to v2.4.1. The notes below are detailed records of released features, kept for reference.

**Tool calling in the local API** (2026-11-10, released in v2.3.0; from a GitHub feature request asking for "tool calling / function calling"):
- `/v1/chat/completions` now takes OpenAI `tools` (and the older `functions`), `tool_choice` (auto, none, required or a named function) and `tool`/`tool_calls` messages. It answers with `tool_calls` and `finish_reason: "tool_calls"`, streamed or not.
- **New `data/ToolCalling.kt`:**
  - The tools are listed in the system prompt, and the model is asked to write `<tool_call>{"name","arguments"}</tool_call>`.
  - `parseToolCalls` also accepts a reply that is only a JSON call (fenced or not). Only offered names count as calls.
  - `streamableLength` holds back a call while it streams.
- **`openAiMessagesToPrompt`** now writes the assistant's calls and "Result of <name>: …" turns. When the conversation ends with a result, it asks the model to continue. Plain conversations produce the same prompt as before.
- Tests: `ToolCallingTest.kt`. `gradle testReleaseUnitTest assembleRelease` passed, using Gradle 8.13 downloaded to the session scratchpad (AGP 8.12 needs 8.13; only 8.10.2 is installed at `C:\Users\Nikunj\Android\gradle-8.10.2`). The signature was verified (`ad1dbbc3…`).
- **Verified on the S26 Ultra** (SM-S948U1, wireless adb; pairing port from `adb mdns services`) with Gemma 4 E2B LiteRT, using curl from the PC:
  - A non-streamed call returned `get_weather {"city":"Paris"}`.
  - Sending the tool result back gave a correct text answer.
  - A streamed call returned `{"city":"Oslo","unit":"fahrenheit"}`.
  - A streamed reply with no call needed came through as normal text.
  - A request without tools was unchanged.
  - The API server was switched off afterwards.
- **Gemma 4 E4B (LiteRT)** sometimes writes its own native call format, `<|tool_call>call:get_weather{city:<|"|>Paris<|"|>}<tool_call|>`, and that text came back as the answer. `withGemmaCallsAsJson` now rewrites those blocks before parsing, and `streamableLength` holds them back while streaming. After the fix, 10 of 10 E4B requests (3 prompts × 3, plus 1 streamed) returned correct `tool_calls`. 16 unit tests pass.
- **GGUF, LFM2.5 1.2B:** it improvises formats, and the parser now reads them:
  - `<name>fn</name>` followed by JSON, sometimes with the arguments under "properties", plus stray `}}` or `</argument>`. Handled by `looseCalls`.
  - Its trained format, `<|tool_call_start|>[fn(city="Paris")]<|tool_call_end|>`. Handled by `withLfmCallsAsJson` and `pythonCalls`.
  - A made-up result after a call ("The weather is 22°C") is dropped.
  - Final runs: 15/16, then 13/16. The misses were `<name>get_weather</name>` followed by "I don't have real-time data", with no arguments, so they're correctly treated as text. It also never called for "Is it cold in Oslo?" These are limits of a 1.2B model, not parser bugs.
- **GGUF, Qwen3 1.7B Q8_0** (from the in-app Hugging Face search): 12/12 correct calls, plus correct result answers, streaming and plain text. Qwen is trained on the same `<tool_call>` format. The test download was deleted afterwards and LFM2.5 is selected again.
- **API card:** the Base URL, API key and model values are now bold (`Detail()` in `ApiServerSetting`). The key is saved in settings and stays the same until changed with "Change".
- Tests: 18 in `ToolCallingTest`, 90 in total, all passing. Small issue: `usage.completion_tokens` is 0 for a reply that is only a call.
- Not done: tools the model picks inside the app's own chat (web search and so on). That would be a separate, opt-in feature.

**Forget confirmation** (added to the v2.1.1 APK in place): Forget on a paired device now asks "Forget X?" first. Noticed while testing: the onn tablet had disappeared from the Pixel's paired list (cause unknown; nothing in the code removes pairings by itself). Re-pair it to use it.

**Layer balance, send progress, stale-status fix** (released in v2.1.1; verified on Pixel + Idea Tab):
- **Native:** `nativeMemBandwidth(threads)` (multi-thread memcpy, best of 3, GB/s) and `nativeBlockCount(path)`.
- **Speed reporting:** `memoryBandwidth(context)` is measured once per process. The worker measures at start and adds it to INFO: `OK <free> <model> <total> <bw>`. `DeviceCluster.askInfo()` returns `WorkerInfo`.
- **Balance modes:** `DeviceCluster.balanceMode` is `speed` (default), `memory`, `even` or `manual`, and `manualShares` holds per-device sliders ("local" for this device). `shares()` picks the weights.
  - The automatic modes go through `fitSharesToMemory`, which caps each device at 80% of its free MB and gives the rest to the others.
  - Speed falls back to memory if any bandwidth is unknown.
  - Tests in `DeviceClusterTest`.
  - UI: "Advanced: how layers are split" in `CombineDevices.kt`, with mode chips and, in Manual, a slider per device showing % and ≈ GB of the active GGUF model. It turns red if that's more than 90% of the device's free memory.
- **Send progress:** the relay counts bytes sent to each worker (`sentBytes`). `prepareForLoad` sets each device's expected bytes and layers (model size × share, layer count × share), and `watchSending` publishes `DeviceCluster.sending` every 0.5 s. `LoadingModelRow` shows "TB390FU · 7 of 13 layers sent · 1.5 / 2.8 GB" with a bar, plus "…can take several minutes…".
- **Stale status fix:** `ClusterWorkerService.readStatus` now ignores a "running" status file when the `:cluster` process isn't alive (after an app update or kill). Before, the screen showed sharing on and disabled Delete cache.
- **Results with LFM2.5-24B-A2B-UD-IQ3_XXS (8.3 GB):** the Pixel alone does about 0.8 tok/s (it doesn't fit in RAM and pages from storage). Pixel + Idea Tab: 5.2 and 4.9 tok/s. A fresh send of the Idea Tab's 2.8 GB / 13 layers took about 1 min 50 s.

**Import progress** (released in v2.1.1; verified on the Pixel):
- `copyModel` copies in 1 MB chunks and reports progress every 16 MB. `sizeForUri` reads the size.
- `BuilderUiState.importingName` / `importedBytes` / `importTotalBytes` / `importDoneName`.
- The Models page shows a progress bar ("Importing X… 56% · 4.7 / 8.3 GB") under a filled import button, then "✓ X imported and in use" for 60 s.
- Verified by importing LFM2.5-24B-A2B-UD-IQ3_XXS (8.3 GB, the owner's download) on the Pixel: about 25 s.

**Combine devices (released in v2.1.0; verified on Pixel 8 Pro + Idea Tab Pro + onn tablet):** a GGUF model split across devices with llama.cpp RPC.
- **Native** (`app/src/main/cpp/llama_bridge.cpp`, `-DGGML_RPC=ON` in `app/build.gradle.kts`):
  - `nativeSetCluster(endpoints, weights, localWeight)` sets the workers for the next load. `plan_cluster()` registers them through `ggml_backend_rpc_add_server` (loaded from `libggml-rpc.so`).
  - This device keeps the first layers by its share, and the workers get the rest via `tensor_split` / `n_gpu_layers`.
  - A tensor override keeps `output` / `token_embd` on this device's CPU, and `op_offload = false`.
  - `nativeServe(endpoint, cacheDir, threads)` runs `ggml_backend_rpc_start_server` (it blocks forever). `nativeClusterNote()` gives a label for the description.
- **`data/DeviceCluster.kt`:**
  - `ClusterWorkerService` runs in the `:cluster` process (special-use foreground service). It holds a wake lock and a `WIFI_MODE_FULL_LOW_LATENCY` Wi-Fi lock and advertises `_mochu-cluster._tcp` over NSD with free RAM.
  - llama.cpp RPC listens only on `127.0.0.1:50053`. A gate on `:50052` requires `MOCHU1 <4-digit code>` first (`... INFO` returns free MB), then pipes through. Stop kills the process. Status goes to `cluster_worker_status.json`.
  - `DeviceCluster` (main device): paired devices in prefs `clusterDevices`, the `clusterEnabled` switch, NSD discovery, and `pair()`. Per-worker relays on `127.0.0.1:<random>` send the code, then pipe. They must be IPv4: llama.cpp connects to 127.0.0.1 over IPv4 only and aborted when the relay was on `::1`.
  - `prepareForLoad()` checks each paired device (INFO), sets the cluster weighted by free MB, and holds the low-latency Wi-Fi lock. `fastCoreCount()` sets the threads.
- **`engine/LlamaCppEngine.kt`:** calls `prepareForLoad` before `nativeLoad`, uses fast-core threads with other devices, appends "· N devices (x of y layers here…)" to the description, and releases the Wi-Fi lock on close.
- **`ui/CombineDevices.kt`:** the Models page section "Combine devices (experimental)". It has a Share this device switch (shows the big 4-digit code and status), a Use other devices switch, a nearby list with a Pair dialog (4 digits), and the paired list with Forget.
- **`AndroidManifest.xml`:** the `.ClusterWorkerService` in process `:cluster` with a special-use subtype.
- **Verified in the app:** LFM2-8B-A1B Q4_K_M (imported on the Pixel) split across the Pixel and the Idea Tab (the Idea Tab held about 1.9 GB) at **7.8 and 8.9 tok/s**, with first-token time about 1 s on the second turn. The Pixel alone does 11.8 tok/s. The same split from the command line without the Wi-Fi lock gave 0.32 tok/s. Afterwards sharing was turned off and the test files and the Downloads copy were removed. The imported model, the pairing and a test chat ("write a short poem about the sea") remain on the Pixel.
- **Lost devices no longer crash the app:**
  - `app/src/main/cpp/CMakeLists.txt` builds the `ggml-rpc` target from a patched copy (`ggml-rpc-mochu.cpp` in the CMake build dir; the submodule is untouched). It fails the build loudly if upstream text changes.
  - In that copy, `RPC_STATUS_ASSERT` and the connect/handshake aborts only set `g_rpc_lost`. The dispatcher thread still starts, and requests after a loss finish at once.
  - It exports `ggml_backend_rpc_lost` / `ggml_backend_rpc_clear_lost`, which `llama_bridge.cpp` reads via `dlopen("libggml-rpc.so", RTLD_NOLOAD)` as `nativeClusterLost` / `nativeClusterClearLost`.
  - `LlamaCppEngine` checks paired devices before each turn (`DeviceCluster.activeStillThere`) and the lost flag after every token. Then it sets `lostDevice`, `DeviceCluster.onLost()` closes the relays (llama.cpp caches a connection per address, so new relay ports force a fresh connection), and it throws a friendly message.
  - The ViewModel keeps the partial reply with that note and unloads the engine (`dropEngineIfDeviceLost`), so the next message reloads with the devices still sharing, or locally.
  - `nativeLoad` now always passes an explicit device list: empty unless the cluster is used. Otherwise a registered dead RPC device was used by default and local loads failed.
- **Verified on the Pixel + Idea Tab:**
  - A drop during load gives "Model load failed: … across your devices" with no crash.
  - The next message loads on the Pixel alone (10.2 tok/s).
  - A drop mid-reply keeps the partial text plus the note, with no crash; "continue the story" then ran alone at 10.1 tok/s.
  - The split speed stays 9.1 tok/s.
- **Memory view:** the Models page "Memory for models" card shows this device plus each paired device's free RAM (asked over the gate every 5 s) as a colored bar and an approximate total (9.8 GB on Pixel + Idea Tab), and marks devices that aren't sharing. Several paired devices already work: each one that's sharing gets a share of the layers by free MB.
- **Auto-reconnect and continue** (`BuilderViewModel.recoverAndResend`):
  - When a device drops mid-reply (or before it), the Chat shows the text so far plus "X stopped responding. Waiting up to 10 seconds…" and polls with `DeviceCluster.waitForLost(10_000)`.
  - Then it reloads (across the devices that answer, else alone) and re-asks the same message with `LlamaCppEngine.continueWith` = the raw text written so far, which is injected with `nativeInject`, so the reply continues where it stopped.
  - If the device didn't come back, the reply ends with "X didn't come back, so this device finished the reply on its own."
  - One retry per message (`resendingAfterLostDevice`). Only for Chat messages without photos, documents or web research.
  - Verified: a 3 s drop reconnected across both and continued at 9.3 tok/s; a permanent drop continued alone with the note.
- **Worker shows who it's connected to; one main device only:** the main device's relay sends `MOCHU1 <code> CONNECT <model name>`. The gate refuses another IP (`BUSY`) while connections from the current one are open, and clears the peer when they all close. The worker card shows a big "✓ Connected · Pixel 8 Pro" and hides the code.
- **Main device:** the chat chip shows "N devices" instead of "llama.cpp CPU" when shared. The Models page shows "Model running on N devices" and marks each paired device "● Connected" (`DeviceCluster.connected` flow, cleared by `onUnloaded()` when the engine closes).
- **Local API server works with a combined model:** `ApiServer` uses `EngineHolder.loaded`. Verified from the PC: `/v1/chat/completions` answered in 6 s while the Idea Tab showed Connected. API calls don't get auto-reconnect: they error if a device drops.
- **Memory shown as free / total:** the gate's INFO reply is `OK <freeMb> <model> <totalMb>`; `DeviceCluster.askMemory()` returns (free, total), with total 0 for older workers. The Models card reads "7.3 GB free / 12 GB" per device plus a total ("≈ 11 GB free / 19 GB"), and the worker card "Ready · 3.6 GB free / 8 GB". The main switch is renamed "Create a cluster with multiple devices".
- **Loading message:** `DeviceCluster.loadingOn` (names, set in `prepareForLoad`, cleared by `loadFinished()` after `nativeLoad`) makes the chat's `LoadingModelRow` say "Loading <model> on N devices…" plus "Sending part of the model to X over Wi-Fi…". Verified on the Pixel.
- **Context in a cluster:** the KV cache follows the layers, so a bigger context is spread across devices too. The Models page RAM estimate under the context slider still assumes this device only (idea: make it cluster-aware).
- **Per-model cache on workers:** `DeviceCluster.prepareForLoad(context, modelName)`. The relay sends `MOCHU1 <code> CONNECT <device> <model>`. While a main device is connected, the worker's `watchCache` labels new files in `cluster_cache/` every 5 s in `cluster_cache_index.json` (`ClusterCache`). The Models page lists "Model parts kept here" with one "Delete cache" per model (disabled while sharing); unlabeled older files show as "Earlier models". Verified: LFM2-8B-A1B-Q4_K_M · 1.6 GB listed and deleted on the Idea Tab.
- **Suggested bigger MoE models** (sizes from Hugging Face, unsloth repos):
  - Pixel + Idea Tab (about 11 GB free): LFM2.5-24B-A2B UD-IQ3_XXS (8.3 GB) or Gemma 4 26B-A4B UD-IQ2_M (9.3 GB).
  - Adding the onn tablet allows LFM2.5-24B UD-Q3_K_XL (10.2 GB) or Gemma 4 26B UD-IQ3_XXS (10.6 GB).
  - Qwen3-30B-A3B UD-IQ2_M (10.1 GB) is tight.
- **Three devices verified:** the onn tablet (onn8Core, model number 36018340, 6 GB, 192.168.1.222, already adb-paired) was added. LFM2-8B-A1B was 9.2 tok/s on Pixel + onn and 8.4 tok/s on Pixel + Idea Tab + onn, with "Loading … on 3 devices", the chip "3 devices", and both tablets "✓ Connected".
- **Speed note:** LFM2-8B-A1B is about 10–12 tok/s on the Pixel alone and 8–9 split, so splitting only pays off for models that don't fit on one device.
- **Remaining gaps:**
  - A worker that silently disappears from Wi-Fi (no TCP reset) could leave a reply hanging until TCP times out.
  - The chat chip doesn't show the device count.
  - No manual add-by-IP.
  - Not yet tested with 3+ devices or a model bigger than one device.

**Wake lock while generating** (released in v2.0.3; verified on the S25):
- `data/WorkWakeLock.kt` (new):
  - A `PARTIAL_WAKE_LOCK` ("Mochu:generating") held by name ("reply", "task") and released when the last holder ends.
  - Each hold is 30 min, renewed every 10 min while work continues, with a hard 2-hour cap. Play's Android vitals flags apps that hold wake locks for long with the screen off, so a stuck generation must never hold one for hours.
- `ReplyKeepAliveService.started()` takes it right away and `finished()` releases it. The foreground service itself only starts after 1.5 s and only while the app is visible.
- `TaskRunnerService.run()` holds it from `backgroundRunning = true` until the `finally` block.
- `AndroidManifest.xml`: the `WAKE_LOCK` permission (a normal permission; no Play declaration needed).
- **Verified:** a 500-word story on the server model (Qwen3.6 35B) kept generating with the screen off (power key) for about 5 min. `dumpsys power` showed the lock throughout, then released. The "Mochu replied" notification arrived with the story.
- **Left over:** the test chat "write a 500 word story about a robot…" is still on the S25, because the phone was locked. Delete it next time.

**Opt-in server fallback** (released in v2.0.2; verified on the S25 with the owner's server):
- `engine/RemoteEngine.kt`: `remoteFallbackEnabled` / `setRemoteFallbackEnabled` (pref `remoteFallback`, off by default), and `remoteReachable()`, a GET of `/models` with a 2 s connect timeout where any HTTP reply counts.
- `viewmodel/BuilderViewModel.kt`:
  - `chooseEngineForTurn()` runs before loading in Chat and Code. With a server model active and the setting on, it checks the server. If unreachable, it calls `loadGemmaInternal(forceLocal = true)` for the phone model, and the Chat reply ends with a note. Once the server is reachable again, the phone engine is freed so the server is used.
  - The local branch of `loadGemmaInternal` now uses `savedLocalModelName`. `savedModelName` returns the server model name while a server model is active.
- `ui/ModelDownloads.kt`: a switch in the home-server settings: "If my server can't be reached, use the phone's model".
- The owner's server uses an nginx URL that works on 5G, which is why this is based on whether the server can be reached, not on Wi-Fi.
- **Reachability rule:** 502/503/504 count as unreachable. With Unsloth stopped, the owner's nginx URL (`https://chat.nikunjserver.duckdns.org/v1`) still answers, with 502.
- **Verified on the S25:**
  - Server up: the server answered, with no note.
  - Server stopped (nginx 502): Gemma 4 E2B on the phone answered, with the note, and the meter showed 8.2K.
  - Server back: the server answered again, and the meter showed 262K.
  - Afterwards the switch was turned back off (default), and the test chats were deleted.
- **API key field:** the home-server API key is hidden like a password. An eye button shows it, and while it's shown it can be selected and there's a copy button. This was added to the v2.0.2 release by replacing its APK (same versionCode 30).

**Notification icon, code downloads and code colors** (released in v2.0.1; verified on the S25):
- `res/drawable/ic_stat_mochu.xml`: a one-color 24dp Mochu (blob with eyes, a smile and the sparkle). Every `setSmallIcon` now uses it (`ApiServer`, `ModelDownloadService`, `ReplyInBackground`, `TaskRunner`). Before, they used Android's stock icons.
- `ui/chat/CodeTools.kt` (new):
  - `highlightCode()`: a one-pass regex highlighter for comments, strings, numbers and keywords, plus HTML tags and attributes for markup languages.
  - `codeFileType()` and `codeFileName()`: HTML files are named from `<title>`.
  - `tableToCsv()`, `saveToDownloads()` (MediaStore Downloads) and `DownloadButton`.
- `ui/chat/RichMarkdown.kt`:
  - Code blocks are colored with a light or dark palette chosen from the block's background, and get a Download button after Copy.
  - Tables get a header row with a Download-as-CSV button.
- Test: `CodeToolsTest`.
- `<!DOCTYPE` counts as a tag too.
- `refreshPredictedWindow` shows no context size for a server model before it connects. It used to show the phone model's 8.2K after an app restart.
- **Verified on the S25** with the server model `unsloth/Qwen3.6-35B-A3B-GGUF`:
  - HTML colors work (tags, attributes, strings, DOCTYPE).
  - Download saved `Hello-Mochu.html` to Downloads.
  - The status bar shows the Mochu icon for a running timer.
  - After a restart, the meter shows 262K.
  - Cleaned up afterwards: the test chat, the timer and the file.
- **adb pairing:** the S25's wireless debugging port changes. Pair with `adb pair <ip>:<pair-port> <code>` (the owner gives the code), then `adb connect <ip>:<port>`. If the phone is listed twice (IP and mDNS), use one `-t` transport id.

**Server model context size** (done, committed in `8e75750`, shipped in the v2.0.0 APK):
- The owner's Unsloth Studio server (192.168.1.213:8888) runs 131K context, but Mochu showed 32K. Unsloth's `/v1/models` has no context field and its `/props` is a stub, so Mochu fell back to 32,768.
- `engine/RemoteEngine.kt`:
  - `detectRemoteContext` now checks a context size the user typed first.
  - Then Unsloth Studio's `/api/inference/status` (`context_length`, needs the Bearer API key).
  - Then llama.cpp `/models` `meta.n_ctx` / `n_ctx_train`.
  - Models match loosely through `sameRemoteModel` (ignores an "org/" prefix and case).
  - `RemoteModelConfig.contextTokens` is saved as `remoteContextTokens`.
- `viewmodel/BuilderViewModel.kt`:
  - When the size is only a guess, the context meter is hidden. The owner asked for this: only show a size the server reported.
  - The 32K guess is still used for trimming history.
  - `recheckRemoteContext()` asks the server again after each reply, because Unsloth only loads a model on its first request.
- `ui/ModelDownloads.kt`: an optional "Context size in tokens" field in the home-server settings. The port hint now lists Unsloth Studio: 8888.
- `ui/AppShell.kt`: "Use this model" for a server model now leaves the Models tab and opens a new Chat, unless a reply is running.
- **Summarizing:** it no longer starts after 6 messages. `compactHistoryIfNeeded` now runs only when the unsummarized history exceeds the snapshot budget. `buildAssistantSnapshot` sends all messages since the summary, trimmed by characters. Server models have no open session, so every turn rebuilds the history, and each rebuild used to fold anything older than 6 messages into a summary.
- **Context used:** `RemoteEngine` sends `stream_options.include_usage` and keeps the server's `lastContextTokens`, which `refreshContextUsage` shows. A 400 response retries once without the option. The local estimate now counts all messages plus about 900 tokens of system prompt.
- Test: `WebToolsTest.serverModelNamesMatchLoosely`.
- **Verified on the S25 with the owner's Unsloth model** (`unsloth/LFM2.5-24B-A2B-GGUF`):
  - The meter shows 131K.
  - 5 exchanges ran with no summarizing.
  - Context used rose with the server's counts: 436 → 570 → 574 → 666 → 921.

## Known Issues

- **News card rows** are search results, often section pages rather than individual articles.
- **The place card's "Open now"** is worked out by the model from the page's hours and the current time. Usually right, but not guaranteed.
- **Card extraction** adds one extra model call, about 5–15 s, after the reply.
- **The voice error-13 fallback** couldn't be reproduced here, so it's untested on an affected phone.
- **Older scheduled tasks:** the owner's earlier tasks (Tesla news, Best Buy laptop, Summarize tech news, Amazon) were no longer on the Tasks page during 2.0 testing. It's not known whether they were deleted on purpose.
- **API `usage.completion_tokens` is 0** when a reply is only a tool call. It counts the text content, which is empty.
- **Digital assistant from the lock screen:** Mochu doesn't open over the keyguard, so the user has to unlock first.
- **Combine devices with an older Mochu** shows the other device's IP instead of its name, because older versions don't send a name with INFO.

## Verification

- **v2.4.1 build:** `gradle testReleaseUnitTest assembleRelease` (Gradle 8.13) passed with 94 unit tests, and the APK signature was verified (SHA-256 `ad1dbbc3…`). The S26 Ultra and onn tablet tests for 2.3.0–2.4.1 are listed under Current State and Work in Progress.
- **Not tested on a device:**
  - Holding the physical side button for the digital assistant; only `KEYCODE_ASSIST` was sent over adb.
  - The weekly llama.cpp update check (a proposed task, not built).
- **v2.0.0, manual tests on the S25 with Gemma 4 E2B:**
  - Price card plus history chart, and the typical-price line.
  - Stock, news, sports (re-checked after the placeholder fix), place (Open in Maps opens Google Maps), recipe, comparison and document cards, plus an "ask more" chip.
  - The "Today at 11:50 p.m" scheduling, and the drawer scroll.
- **Not tested on a device:** cards inside a scheduled task run.

## Next Steps

1. **GitHub issues:**
   - The owner replied on the LFM3 issue (#3), and on the tool-calling issue about local models.
   - Still to post: the digital-assistant issue reply, now that v2.4.1 is out.
   - If the tool-calling requester wants in-app tools, build the opt-in, read-only version (see PROJECT.md). Chat already runs web search, page reading and Memory by itself.
2. **Digital assistant:** hold the S26's physical side button (Samsung: Settings › Advanced features › Side button › Digital assistant) to confirm it opens Mochu. Delete the leftover test chat "Hello what's up" on the S26.
3. **Weekly llama.cpp update check:** a GitHub Action that bumps the submodule to the newest `bNNNNN` tag, builds, runs tests and opens a PR. It should flag new architectures and `RPC_PROTO` changes. It was proposed as a separate task and isn't built.
4. **API `usage` for tool-call replies:** count the raw reply, not the empty content.
5. **Price-drop alerts:** for example, "tell me if it goes under $60". Only notify when a tracked price drops below a target. It builds on `recordValue` history.
6. **Weather card:** temperature, high and low, rain chance and an hourly chart. Good for daily morning tasks.
7. **Test a scheduled task end to end** that produces a card (price or stock) and check the result chat.
8. **Better news cards:** read headlines from inside news pages instead of listing search results.

## Additional Context

- **Owner's test devices (from 2026-11-05; the S25 is gone):**
  - Samsung S26 Ultra (SM-S948U1): 11 GB, 192.168.1.47. It's the main test phone since 2026-11-10, with a PIN lock.
  - Pixel 8 Pro: 12 GB, Tensor G3, about 4–6 GB free.
  - Lenovo Idea Tab Pro Gen 2 (TB390FU): 8 GB, Snapdragon 8s Gen 4, about 3.3 GB free.
  - onn tablet 8.1 core 2026 (onn8Core, model number 36018340): 6 GB, 192.168.1.222, with a PIN lock. It runs v2.4.1.
  - Wireless adb: pair on the pairing port shown by `adb mdns services`, then connect on the connect port. In Git Bash, set `MSYS_NO_PATHCONV=1` so `/data/local/tmp` paths aren't rewritten.
- **Experiment: splitting a model across devices (llama.cpp RPC, as in DistAi) is NOT worth building.**
  - Setup: `llama-bench` and `ggml-rpc-server` built with `GGML_RPC=ON` (arm64, armv8.2-a+dotprod+i8mm). The Pixel was the main device; the Idea Tab ran the worker over home Wi-Fi.
  - Wi-Fi round trips between them: 88 ms on average, peaking at 255 ms.
  - Writing speed:
    - Qwen3-4B Q4_K_M: Pixel alone 10.0 tok/s; split 18/36 layers 1.7 tok/s.
    - Qwen3-8B Q4_K_M: Pixel alone 4.0 tok/s; split 16/36 layers 1.4 tok/s.
  - Splitting was 3–6× slower even for a model the Pixel could barely hold. The only upside is fitting models that can't load on one device, at about 1–1.5 tok/s.
  - Better ideas: a pool of devices that each run a full copy (faster for several users), and the home server for big models.
  - All test files were removed from both devices afterwards.
- **Why PrivateLM's cluster (orailnoor/cross-platform-llm-client 1.0.7-cluster) reaches 4–6 tok/s with Gemma 4 26B on 4 phones, using the same llama.cpp RPC.** Its `jni_wrapper.cpp` and `ClusterWorkerService.kt` show:
  1. It keeps `output.weight` / `token_embd.weight` on the main phone with a tensor override. Otherwise the last worker sends about 600 KB of word scores back over Wi-Fi every token. My test didn't do this.
  2. Workers hold `WIFI_MODE_FULL_LOW_LATENCY` plus a wake lock. Wi-Fi power save explains the 88 ms pings.
  3. Threads = the fast cores the process may use (2–6). Too many threads stall.
  4. It uses a mixture-of-experts model.
  5. The split is weighted by measured memory speed and free RAM, `op_offload = false`, workers are probed over TCP first (llama.cpp aborts if one is unreachable), the worker runs in its own `:cluster` process, there's an RPC cache dir, NSD discovery, and QR pairing.
  The owner wants this built eventually. The plan is in the session notes: main-phone changes in `llama_bridge.cpp` / `LlamaCppEngine`, plus a worker foreground service.

- **Promotion:** the owner has posted 2.0 on X and Reddit. Suggested next places: Show HN, IzzyOnDroid, XDA, AlternativeTo, Product Hunt.
- **Release notes** embed screenshots through raw.githubusercontent URLs pinned to a commit hash, so they keep working.

## Hugging Face search in the Models tab (released in v2.2.0)
- `data/HuggingFace.kt`: `searchHuggingFace` (api/models, filter=gguf, by downloads), `huggingFaceFiles` (tree/main recursive; skips mmproj and split `-0000N-of-0000M` files; `lfs.oid` = SHA-256), `isMixtureOfExperts`, `HfDownloads` registry in prefs `hfDownloads` (ids `hf:repo/path` → CatalogModel).
- `ui/HuggingFaceSearch.kt`: card under Import; repos (gated greyed), files with parameter count (from the name), size, fit label, MoE active parameters, Download with GB/percent progress and Cancel. Search results show the exact parameter count from GGUF metadata and an estimated Q4 size.
- ModelDownloadService/ViewModel accept `hf:` ids; finished file goes to `importedModelFile` (copy fallback because external→internal rename fails across file systems) and is used immediately.
- Tested on Pixel: search "LFM2 350M" and "unsloth Qwen3", downloaded LFM2.5-350M-Q4_0 (verified, imported, in use); test model deleted and Qwen3.6-35B restored.
