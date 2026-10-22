# Development Handoff

## Last Updated

Date: 2026-10-22
Agent: Claude Code (Claude Opus 5.5)

## Current State

- **v2.0.3 is released** (versionCode 31) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.0.3, with the wake lock while generating.
- **v2.0.2** was released (versionCode 30) at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.0.2, with the opt-in server fallback and the hidden API key field (the APK was replaced once with the key-field change). v2.0.1 (29) added the Mochu notification icon, code downloads and code colors. v2.0.0 (28) is the Smart cards release.
- **The Athena tablet** wasn't connected, so it may still be on an older version.
- **The agent-memory files** (`AGENTS.md`, `CLAUDE.md`, `.ai/`) are committed in `c6c5c28`.
- **Server-model fixes** are committed in `8e75750`. The owner asked for them to go into the **existing v2.0.0 release** instead of a new one. The APK asset was replaced (same versionCode 28, signature verified) and the notes got a "Home-server models" section. The v2.0.0 git tag still points to `78eb98c`, so its source zip doesn't include these fixes.

## Recently Completed

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

These are the files changed in the last release, v2.0.0:

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

## Verification

- **Unit tests:** `gradle testReleaseUnitTest assembleRelease` passed for the v2.0.0 build, and the APK signature was verified (SHA-256 `ad1dbbc3…`).
- **Manual tests on the S25 with Gemma 4 E2B:**
  - Price card plus history chart, and the typical-price line.
  - Stock, news, sports (re-checked after the placeholder fix), place (Open in Maps opens Google Maps), recipe, comparison and document cards, plus an "ask more" chip.
  - The "Today at 11:50 p.m" scheduling, and the drawer scroll.
- **Not tested on a device:** cards inside a scheduled task run.

## Next Steps

1. **Price-drop alerts:** for example, "tell me if it goes under $60". Only notify when a tracked price drops below a target. It builds on `recordValue` history.
2. **Weather card:** temperature, high and low, rain chance and an hourly chart. Good for daily morning tasks.
3. **Test a scheduled task end to end** that produces a card (price or stock) and check the result chat.
4. **Better news cards:** read headlines from inside news pages instead of listing search results.

## Additional Context

- **Promotion:** the owner has posted 2.0 on X and Reddit. Suggested next places: Show HN, IzzyOnDroid, XDA, AlternativeTo, Product Hunt.
- **Release notes** embed screenshots through raw.githubusercontent URLs pinned to a commit hash, so they keep working.
