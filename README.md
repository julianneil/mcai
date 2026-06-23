# mcai

AI Integration in Minecraft.

MCAI is a client-side NeoForge 1.21.1 mod that adds an in-game AI assistant backed by a local Ollama server. It can answer questions from chat or an in-game GUI, include game context in prompts, look up loaded recipes, and track recipe trees with inventory highlights.

## Features

- `/ai <message>` in-game chat command.
- `/mcai tone terse|balanced|detailed` response-style control.
- `/mcai mode default|help|debug|progression` chat-mode control.
- `/mcai share`, `/mcai share set <csv>`, `/mcai share allow <category>`, `/mcai share deny <category>`, and `/mcai share reset` sharing controls.
- `/mcai history`, `/mcai history clear`, `/mcai history retry`, and `/mcai history export`.
- `/mcai bookmark add`, `/mcai bookmark current`, `/mcai bookmark item`, `/mcai bookmark recipe`, `/mcai bookmark list`, `/mcai bookmark open`, and `/mcai bookmark remove`.
- `G` keybind for the MCAI chat GUI.
- Inventory, player, modpack, and recipe-aware prompt context.
- Compact summaries for inventory, player, and modpack context when using the normal context profile.
- FTB Quests context and progression suggestions when the quest mod is installed.
- Recipe grounding from loaded client recipes.
- Recipe tracking with colored inventory highlights and a branch-style recipe visual.
- Quick lookups with `/mcai item`, `/mcai block`, and `/mcai mod`.
- Quest summary and next-step guidance with `/mcai quests` and `/mcai quests next`.
- Offline fallback answers for local recipe, lookup, and quest questions when Ollama is unavailable.
- Local-only AI inference through Ollama by default.

## Requirements

- Minecraft `1.21.1`.
- NeoForge for Minecraft `1.21.1`.
- Java/JDK `21`.
- A local Ollama install.
- An Ollama chat model, for example `gemma4:latest` or another model configured in `mcai-client.toml`.

## Install Ollama

1. Download and install Ollama from [ollama.com](https://ollama.com/).
2. Start Ollama. On Windows, Ollama usually runs in the background after installation.
3. Verify the local API is available:

```powershell
curl http://127.0.0.1:11434/api/tags
```

4. Pull the model you want MCAI to use. The current default config expects:

```powershell
ollama pull gemma4:latest
```

If you prefer another model, pull it instead, then update `ollamaModel` in the MCAI config.

5. Test the model directly:

```powershell
ollama run gemma4:latest "hello"
```

## Install the mod

1. Install Minecraft `1.21.1` with NeoForge.
2. Build or download the MCAI mod `.jar`.
3. Put the MCAI `.jar` into your Minecraft instance `mods` folder.
4. Launch the game once to generate the client config file.
5. Open the generated config file:

```text
config/mcai-client.toml
```

For this development workspace, the run-client config is here:

```text
run/client/config/mcai-client.toml
```

## Configure MCAI

Important config values:

```toml
ollamaEndpoint = "http://127.0.0.1:11434"
ollamaModel = "gemma4:latest"
includeInventoryContext = true
includePlayerContext = true
includeModpackContext = true
includeRecipeContext = true
includeQuestContext = true
enableOfflineFallback = true
maxRecipeContextResults = 8
recipeBranchMaxDepth = 4
recipeBranchMaxChildren = 3
chatMode = "default"
shareWhitelist = "player,inventory,modpack,recipe,quest"
requestTimeoutSeconds = 120
```

Notes:

- `ollamaEndpoint` should point to your local Ollama server.
- `ollamaModel` must match a model installed with `ollama pull`.
- Context toggles control what game information MCAI includes in prompts.
- `includeQuestContext` enables soft FTB Quests integration when the mod is present.
- `enableOfflineFallback` lets MCAI answer local recipe, registry, and quest questions even if Ollama is down.
- `recipeBranchMaxDepth` and `recipeBranchMaxChildren` control how much of the recipe branch visual is shown in the GUI.
- `chatMode` changes the prompt style to default, help, debug, or progression.
- `shareWhitelist` limits which live game-state categories MCAI is allowed to send to the model.
- Increase `requestTimeoutSeconds` if your model is slow on your hardware.

## In-game commands

Ask the AI a question:

```text
/ai <message>
```

Examples:

```text
/ai what can I do with my current inventory?
/ai where am I and what should I do next?
/ai how do I craft a furnace?
/ai what recipes use cobblestone?
```

Track a recipe tree:

```text
/mcai track <recipe>
```

Examples:

```text
/mcai track crafting table
/mcai track furnace
/mcai track iron pickaxe
```

Change the assistant tone:

```text
/mcai tone terse
/mcai tone balanced
/mcai tone detailed
```

Change the chat mode:

```text
/mcai mode default
/mcai mode help
/mcai mode debug
/mcai mode progression
```

Control what MCAI can share:

```text
/mcai share
/mcai share set player,inventory,recipe
/mcai share allow quest
/mcai share deny modpack
/mcai share reset
```

Inspect chat history:

```text
/mcai history
/mcai history clear
/mcai history retry
/mcai history export
```

Manage bookmarks:

```text
/mcai bookmark add "base goals" remember to craft the miner and storage drawer
/mcai bookmark current "last question"
/mcai bookmark item "copper" copper ingot
/mcai bookmark recipe "furnace"
/mcai bookmark list
/mcai bookmark open 1
/mcai bookmark remove 1
```

Look up registry and mod data:

```text
/mcai item iron pickaxe
/mcai block furnace
/mcai mod ftb quests
```

Open a tracked recipe in JEI, if JEI is installed:

```text
/mcai jei <recipe>
```

Open usages for a tracked item in JEI, if JEI is installed:

```text
/mcai jeiuses <item>
```

Examples:

```text
/mcai jei furnace
/mcai jeiuses cobblestone
```

Clear active recipe highlights:

```text
/mcai cleartrack
```

Check quest status and next steps:

```text
/mcai quests
/mcai quests next
```

## GUI and keybinds

- Press `G` to open the MCAI chat GUI.
- The GUI shows conversation history, model/context status, and active recipe tracking status.
- When a recipe is tracked, the GUI displays a branch-style recipe visual.
- The GUI also reflects the current tone and context toggles.
- The GUI status line shows the current chat mode and share whitelist summary.
- If JEI is installed, JEI recipe pages show an MCAI `Track` button for the visible recipe output.
- Clicking a tracked recipe node opens that recipe in JEI when JEI is installed.
- If JEI is not installed or not ready, the click falls back cleanly and shows a message instead of erroring.
- Inventory/container screens highlight tracked recipe items by role:
  - Target output.
  - Crafted intermediate ingredient.
  - Base ingredient.
- Hover a highlighted slot to see why the item is highlighted.
- When FTB Quests is installed, MCAI can use quest context to explain likely next progression steps.
- Normal context mode sends shorter summaries for player, inventory, and modpack state; rich mode sends fuller detail.

## Troubleshooting

### MCAI says Ollama is unavailable

Check that Ollama is running:

```powershell
curl http://127.0.0.1:11434/api/tags
```

If this fails, restart Ollama and try again.

If you want MCAI to keep working without Ollama, leave `enableOfflineFallback = true` and use the local commands for items, blocks, mods, quests, and tracked recipes.

### Model not found

Pull the configured model:

```powershell
ollama pull gemma4:latest
```

Or change `ollamaModel` in `config/mcai-client.toml` to a model that appears in:

```powershell
ollama list
```

### Replies take too long

- Use a smaller/faster model.
- Increase `requestTimeoutSeconds` in `mcai-client.toml`.
- Close other GPU/CPU-heavy applications.

### Recipe tracking cannot find an item

- Make sure you are in a loaded world.
- Try the item name, registry-style name, or a simpler query.
- Some modded recipes may have multiple variants; MCAI currently picks the best matching loaded recipe.

## Development

Use JDK 21 and the included Gradle wrapper.

Build:

```powershell
.\gradlew.bat build
```

Run a development client:

```powershell
.\gradlew.bat runClient
```

If Gradle cannot find Java 21, set `JAVA_HOME` first:

```powershell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-21.0.11"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build
```
