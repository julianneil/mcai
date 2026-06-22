package com.modai.mcai;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> OLLAMA_ENDPOINT = BUILDER
            .comment("Base URL for the local Ollama server.")
            .define("ollamaEndpoint", "http://127.0.0.1:11434");

    public static final ModConfigSpec.BooleanValue INCLUDE_INVENTORY_CONTEXT = BUILDER
            .comment("Whether MCAI should include your current inventory in AI prompts.")
            .define("includeInventoryContext", true);

    public static final ModConfigSpec.BooleanValue INCLUDE_PLAYER_CONTEXT = BUILDER
            .comment("Whether MCAI should include your current player status and location in AI prompts.")
            .define("includePlayerContext", true);

    public static final ModConfigSpec.BooleanValue INCLUDE_MODPACK_CONTEXT = BUILDER
            .comment("Whether MCAI should include a compact index of loaded mods and registry namespaces in AI prompts.")
            .define("includeModpackContext", true);

    public static final ModConfigSpec.BooleanValue INCLUDE_RECIPE_CONTEXT = BUILDER
            .comment("Whether MCAI should include matching loaded recipes in AI prompts.")
            .define("includeRecipeContext", true);

    public static final ModConfigSpec.IntValue MAX_RECIPE_CONTEXT_RESULTS = BUILDER
            .comment("Maximum number of matching recipes to include in AI prompts.")
            .defineInRange("maxRecipeContextResults", 8, 1, 25);

    public static final ModConfigSpec.ConfigValue<String> OLLAMA_MODEL = BUILDER
            .comment("Ollama model name to use for AI chat.")
            .define("ollamaModel", "gemma4:latest");

    public static final ModConfigSpec.ConfigValue<String> SYSTEM_PROMPT = BUILDER
            .comment("System prompt sent to the assistant before the conversation.")
            .define("systemPrompt", "You are MCAI, a helpful in-game Minecraft assistant for modded Minecraft. Use the provided inventory, player, modpack, and recipe context to give concise, practical advice about recipes, progression, and gameplay. If you are unsure, say so.");

    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS = BUILDER
            .comment("Maximum number of seconds to wait for Ollama to respond.")
            .defineInRange("requestTimeoutSeconds", 120, 5, 600);

    static final ModConfigSpec SPEC = BUILDER.build();
}
