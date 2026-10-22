# AI Development Instructions

This project (Mochu AI, an on-device AI assistant for Android) is developed with several AI coding agents, including Codex, Claude Code, OpenCode and Pi Agent. They share their knowledge through the files in `.ai/`.

## Starting a session

Before making changes:

1. Read `.ai/PROJECT.md` for the architecture, decisions and known pitfalls.
2. Read `.ai/HANDOFF.md` for recent work, open issues and next steps.
3. Check `git status` and the relevant source files to confirm the documented state is still accurate.
4. Continue from the latest confirmed state instead of starting over.

## During development

- Follow the existing patterns. Match the surrounding code's naming, comment style and structure.
- Prefer changing existing code over rewriting it.
- Preserve unrelated changes the user has made in the working tree.
- Add unit tests in `app/src/test/java/com/nikunj/gemmabuilder/` for parsing and logic changes.
- Record important technical decisions, discoveries and failed approaches in `.ai/PROJECT.md`.
- Treat the source code and tests as the source of truth when the docs are out of date.

## Rules from the project owner

- Never print, commit or copy `keystore.properties`, keystore files or their passwords.
- Commit, push or publish a GitHub release only when the owner asks. They sometimes want to try a build on their phone first.
- Don't operate or install onto the owner's phone while they're using it.
- Don't change phone system settings without asking first.
- Don't use the owner's personal photos or files for testing.
- Clean up test items afterwards: chats, tasks, reminders and pushed files.
- `gh auth login` is done by the owner. Don't try to authenticate for them.
- Commit messages end with the attribution line the agent's environment asks for, if any.

## Ending a session

After a substantial task, or when asked to hand off:

1. Update `.ai/HANDOFF.md`.
2. Record what was finished and what's still unfinished.
3. List the important files changed.
4. Note any known bugs or blockers.
5. Record the commands you ran and their results.
6. Write the specific next step another agent should take.

Update `HANDOFF.md` after every substantial completed task, not only at the end, because a session can end suddenly.

Keep handoff notes short, accurate and useful. Don't claim something was tested unless the test actually ran. Don't overwrite earlier findings without checking whether they still apply.
