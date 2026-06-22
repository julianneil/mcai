# mcai

AI Integration in Minecraft.

MCAI is a client-side NeoForge 1.21.1 mod that adds an in-game AI assistant backed by a local Ollama server. It can answer questions from chat or an in-game GUI, include game context in prompts, look up loaded recipes, and track recipe trees with inventory highlights.

## Features

- `/ai <message>` in-game chat command.
- `G` keybind for the MCAI chat GUI.
- Inventory, player, modpack, and recipe-aware prompt context.
- Recipe grounding from loaded client recipes.
- Recipe tracking with colored inventory highlights and a recipe tree panel.
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
maxRecipeContextResults = 8
requestTimeoutSeconds = 120
```

Notes:

- `ollamaEndpoint` should point to your local Ollama server.
- `ollamaModel` must match a model installed with `ollama pull`.
- Context toggles control what game information MCAI includes in prompts.
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

Clear active recipe highlights:

```text
/mcai cleartrack
```

## GUI and keybinds

- Press `G` to open the MCAI chat GUI.
- The GUI shows conversation history, model/context status, and active recipe tracking status.
- When a recipe is tracked, the GUI displays a compact recipe tree panel.
- Inventory/container screens highlight tracked recipe items by role:
  - Target output.
  - Crafted intermediate ingredient.
  - Base ingredient.
- Hover a highlighted slot to see why the item is highlighted.

## Troubleshooting

### MCAI says Ollama is unavailable

Check that Ollama is running:

```powershell
curl http://127.0.0.1:11434/api/tags
```

If this fails, restart Ollama and try again.

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
