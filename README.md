# mcai

AI Integration in Minecraft.

MCAI is a client-side NeoForge 1.21.1 mod that adds an in-game AI assistant backed by a local Ollama server.

## Features

- `/ai <message>` in-game chat command.
- `G` keybind for the MCAI chat GUI.
- Inventory, player, modpack, and recipe-aware prompt context.
- Recipe grounding from loaded client recipes.
- Recipe tracking with colored inventory highlights and a recipe tree panel.

## Development

Use JDK 21 and the included Gradle wrapper:

```powershell
.\gradlew.bat build
```
