# Development Handoff

## Last Updated

Date: 2026-10-22
Agent: Claude Code (Claude Opus 5.5)

## Current State

- **v2.0.0 is released** at https://github.com/nikunjsingh93/Mochu-Local-AI-Assistant/releases/tag/v2.0.0. It's on `main` at commit `78eb98c`, and installed on the owner's Samsung S25.
- **The Athena tablet** wasn't connected, so it may still be on an older version.
- **The agent-memory files** (`AGENTS.md`, `CLAUDE.md`, `.ai/`) are committed in `c6c5c28`. The working tree is clean.

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

None in the app code.

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
