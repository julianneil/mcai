# MCAI Development Checkpoint

Date: Jun 21, 2026
Project: NeoForge 1.21.1 client-side Minecraft AI assistant using local Ollama.

## Current status

MCAI is working in-game with local Ollama and context-aware answers.

Implemented:

- Local Ollama integration through `http://127.0.0.1:11434`.
- Default model: `gemma4:latest`.
- `/ai <message>` command for in-game chat AI.
- `G` keybind opens the MCAI GUI.
- Polished GUI with:
  - Scrollable transcript.
  - Scrollbar indicator.
  - Clear button.
  - Empty-state message.
  - Model/context status line.
  - Cached wrapped text rendering.
- Inventory context:
  - Main hand.
  - Offhand.
  - Armor.
  - Hotbar.
  - Inventory item counts.
  - Item registry IDs.
- Player/location context:
  - Dimension.
  - Coordinates.
  - Biome.
  - Health, hunger, saturation, armor, XP.
  - Game mode.
  - Looking-at block/entity.
- Modpack context:
  - Loaded mod list.
  - Mod IDs, names, versions.
  - Item/block/entity namespace counts.
- Recipe grounding:
  - Searches loaded client recipes.
  - Matches recipe ID, output item, ingredients, registry IDs.
  - Injects relevant recipes into AI prompt.
- Recipe tree tracking/highlighting:
  - `/mcai track <recipe>` starts tracking a recipe tree.
  - `/mcai cleartrack` clears highlights.
  - Highlights matching items in inventories/chests/container screens.
  - Uses recursive ingredient recipe traversal.
  - Fixed container-local coordinate alignment.
  - Uses distinct colors for target, crafted intermediate, and base ingredients.
  - Shows hover tooltips explaining why highlighted items matter.
  - Shows the active tracked recipe tree in the MCAI GUI.
  - Can open optional JEI recipe/usage views when JEI is installed.
  - Adds an optional MCAI Track button overlay on JEI recipe pages.

## Current commands

AI chat:

```text
/ai <message>
```

Examples:

```text
/ai what can I do with my current inventory?
/ai where am I and what should I do next?
/ai how do I craft a furnace?
```

Recipe tracker:

```text
/mcai track crafting table
/mcai jei crafting table
/mcai jeiuses cobblestone
/mcai cleartrack
```

## Important config

Runtime config file:

```text
run/client/config/mcai-client.toml
```

Important values:

```toml
ollamaEndpoint = "http://127.0.0.1:11434"
ollamaModel = "gemma4:latest"
includeInventoryContext = true
includePlayerContext = true
includeModpackContext = true
includeRecipeContext = true
maxRecipeContextResults = 8
requestTimeoutSeconds = 120
```

If Ollama connection fails, verify:

```powershell
curl http://127.0.0.1:11434/api/tags
ollama run gemma4:latest "hello"
```

## Build notes

JDK used successfully:

```text
C:\Users\smkne\.jdks\temurin-21.0.11
```

Gradle build command used:

```powershell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-21.0.11"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build
```

`org.gradle.configuration-cache=false` is intentionally set because NeoForge IDE run configs had configuration-cache issues.

## Key files

Core:

```text
src/main/java/com/modai/mcai/Config.java
src/main/java/com/modai/mcai/mcai.java
src/main/java/com/modai/mcai/mcaiClient.java
```

AI chat:

```text
src/main/java/com/modai/mcai/client/OllamaClient.java
src/main/java/com/modai/mcai/client/AiChatManager.java
src/main/java/com/modai/mcai/client/command/AiCommand.java
src/main/java/com/modai/mcai/client/gui/AiChatScreen.java
src/main/java/com/modai/mcai/client/KeyBindings.java
```

Context providers:

```text
src/main/java/com/modai/mcai/client/context/InventoryContextProvider.java
src/main/java/com/modai/mcai/client/context/PlayerContextProvider.java
src/main/java/com/modai/mcai/client/context/ModpackContextProvider.java
src/main/java/com/modai/mcai/client/context/RecipeContextProvider.java
```

Recipe tracking/highlighting:

```text
src/main/java/com/modai/mcai/client/recipe/RecipeTracker.java
src/main/java/com/modai/mcai/client/recipe/RecipeHighlightRenderer.java
```

Translations:

```text
src/main/resources/assets/mcai/lang/en_us.json
```

## Known limitations

- Recipe tree traversal picks the first matching child recipe for intermediate ingredients.
- Recipe tree GUI panel is compact and currently shows only the first few tree rows.
- External mod wiki/docs are not implemented yet.
- JEI integration is initial/optional; EMI integration is not implemented yet.
- Modpack support is based on loaded mods/registries and does not yet detect a pack display name.

## Recommended next steps

1. Improve recipe tree browsing:
   - Add scrolling/expansion for large recipe trees.
   - Show item counts/required quantities where recipe data exposes them clearly.
   - Let users select among multiple matching child recipes.
2. External wiki/docs support:
   - Configurable trusted source URLs.
   - Local cache.
   - Lightweight search/RAG snippets injected into prompts.
3. Improve modpack awareness:
   - Detect pack name if possible.
   - Include detected pack name in context/prompt wording.
4. Mod recipe viewer integration:
   - Expand JEI integration beyond opening focused recipe/usage views.
   - Add EMI support for packs that prefer EMI.
   - NEI is older and mostly not used in 1.21.1 packs.
