# Development Handoff

## Last Updated

Date: 2026-10-22
Agent: Claude Code (Claude Opus 5.5)

## Current State

- **v2.0.0 is released** at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.0.0. It's on `main` at commit `78eb98c`, and installed on the owner's Samsung S25.
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
