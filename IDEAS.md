# MCAI Ideas

This is the running list of follow-up work for MCAI.

## 1. Context and prompt controls

Add prompt presets and response-style controls so the assistant can be tuned for terse answers, normal answers, or more detailed guidance.

## 2. Better recipe answering

Improve `/ai` so it can answer crafting questions from loaded recipes more reliably, list alternates, and explain dependency chains clearly.

## 3. History tools

Add commands or GUI actions for clearing, retrying, and exporting chat history.

## 4. Item and block lookup

Add commands like `/mcai item`, `/mcai block`, and `/mcai mod` for quick registry and pack-aware lookups.

## 5. Quest integration

Read FTB Quests progress when available so the assistant can give progression advice that matches the pack.
Also surface next-step guidance so players can ask what to do next when they are stuck or confused.

## 6. Favorites and bookmarks

Let players pin useful questions, recipes, or items so they stay easy to reopen later.
Implemented as `/mcai bookmark add`, `/mcai bookmark current`, `/mcai bookmark item`, `/mcai bookmark recipe`, `/mcai bookmark list`, `/mcai bookmark open`, and `/mcai bookmark remove`.

## 7. Better context summaries

Compress inventory, player, and modpack context into more useful summaries before sending them to the model.
Implemented by using compact summary context for the normal profile and fuller detail for the rich profile.

## 8. Offline fallback

Let MCAI still provide local recipe and registry answers when Ollama is unavailable.

## 9. Chat modes

Add named modes like help, debug, and progression to change the system prompt quickly.

## 10. Safer sharing controls

Add a whitelist for what game state is allowed to be sent to the model.
