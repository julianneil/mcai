# MCAI Work Log

This file records the main work completed today and the remaining follow-up items.

## Done Today

- Added `IDEAS.md` as a running backlog of feature ideas.
- Implemented quest progression guidance from FTB Quests.
- Added a `next progression` path so MCAI can answer what to do next when a player is stuck.
- Added `/mcai quests` and `/mcai quests next`.
- Updated the prompt so quest next-step suggestions are preferred when relevant.
- Added bookmarks for questions, recipes, items, and current conversations.
- Added `/mcai bookmark add`, `/mcai bookmark current`, `/mcai bookmark item`, `/mcai bookmark recipe`, `/mcai bookmark list`, `/mcai bookmark open`, and `/mcai bookmark remove`.
- Added compact context summaries for normal mode and fuller context for rich mode.
- Added offline fallback answers when Ollama is unavailable.
- Added `enableOfflineFallback` config.
- Added named chat modes:
  - `default`
  - `help`
  - `debug`
  - `progression`
- Added `/mcai mode`.
- Added share whitelist controls so live context can be limited before sending to the model.
- Added `/mcai share`, `/mcai share set`, `/mcai share allow`, `/mcai share deny`, and `/mcai share reset`.
- Added branch-style recipe visualization in the MCAI GUI.
- Added config controls for recipe branch depth and child limits.
- Made tracked recipe nodes clickable.
- Added JEI opening from tracked recipe nodes when JEI is installed.
- Kept JEI clicks safe when JEI is missing or not ready.
- Added AI backend compatibility selection so MCAI can use either Ollama or LM Studio local servers.
- Tightened recipe branch visual layout behavior so narrow client widths keep the branch panel in-bounds and readable.
- Updated `README.md` to document the new commands, config values, and behavior.
- Built and verified the project with `.\gradlew.bat build`.

## Current State

- MCAI now covers:
  - chat assistance
  - recipe grounding and recipe tracking
  - JEI recipe access
  - quest progression guidance
  - item, block, and mod lookup
  - chat history tools
  - bookmarks
  - offline fallback
  - prompt mode and tone controls
  - share whitelist controls
- The mod has been tested successfully in the SB4 modpack on NeoForge 21.1.233.
- The working tree has active changes but no new commit was requested for the latest work.

## Future Work

### Polish

- Tighten the branch visual layout so large recipes stay readable.
- Add clearer hover labels for recipe branch nodes when JEI is unavailable.
- Review command help text so the new `/mcai` surface is easier to discover.
- Add more targeted summaries for quest, inventory, and modpack state if needed.
- Improve offline fallback wording so it is more direct and less repetitive.

### Feature Follow-up

- Continue refining recipe answering for more modded packs.
- Add exact `modid.itemid` tracking support so `/mcai track` can resolve the intended item directly and avoid false matches like AE2 dyed tracking when the user means controller.
- Add favorites/bookmarks UI affordances if command-only use is not enough.
- Add safer sharing presets instead of only a manual whitelist.
- Add more chat modes if the current set is not enough.
- Consider better local answer coverage when Ollama is unavailable.

### Pack Integration

- Keep verifying JEI behavior against real modpacks.
- Keep checking FTB Quests compatibility against different quest setups.
- If another modpack or mod needs integration help, verify the path before release.
