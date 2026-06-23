package com.modai.mcai;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final String CONTEXT_PROFILE_MINIMAL = "minimal";
    public static final String CONTEXT_PROFILE_NORMAL = "normal";
    public static final String CONTEXT_PROFILE_RICH = "rich";
    public static final String RESPONSE_TONE_TERSE = "terse";
    public static final String RESPONSE_TONE_BALANCED = "balanced";
    public static final String RESPONSE_TONE_DETAILED = "detailed";
    public static final String CHAT_MODE_DEFAULT = "default";
    public static final String CHAT_MODE_HELP = "help";
    public static final String CHAT_MODE_DEBUG = "debug";
    public static final String CHAT_MODE_PROGRESS = "progression";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> CONTEXT_PROFILE = BUILDER
            .comment("Prompt context preset. minimal includes only recipe context, normal uses compact summaries from the individual toggles, and rich adds the fuller context payload plus stronger guidance.")
            .define("contextProfile", CONTEXT_PROFILE_NORMAL, value -> value instanceof String string && isValidContextProfile(string));

    public static final ModConfigSpec.ConfigValue<String> RESPONSE_TONE = BUILDER
            .comment("How MCAI should answer. terse keeps replies short, balanced is the default, and detailed encourages longer explanations.")
            .define("responseTone", RESPONSE_TONE_BALANCED, value -> value instanceof String string && isValidResponseTone(string));

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

    public static final ModConfigSpec.BooleanValue INCLUDE_QUEST_CONTEXT = BUILDER
            .comment("Whether MCAI should include FTB Quests context in AI prompts when the mod is installed.")
            .define("includeQuestContext", true);

    public static final ModConfigSpec.BooleanValue ENABLE_OFFLINE_FALLBACK = BUILDER
            .comment("Whether MCAI should answer with local recipe, lookup, and quest summaries when Ollama is unavailable.")
            .define("enableOfflineFallback", true);

    public static final ModConfigSpec.ConfigValue<String> CHAT_MODE = BUILDER
            .comment("Named prompt mode. default is balanced, help prioritizes direct guidance, debug emphasizes explicit reasoning, and progression prioritizes next steps.")
            .define("chatMode", CHAT_MODE_DEFAULT, value -> value instanceof String string && isValidChatMode(string));

    public static final ModConfigSpec.ConfigValue<String> SHARE_WHITELIST = BUILDER
            .comment("Comma-separated whitelist of live game state MCAI may send to the model. Supported values: player, inventory, modpack, recipe, quest.")
            .define("shareWhitelist", "player,inventory,modpack,recipe,quest");

    public static final ModConfigSpec.IntValue MAX_RECIPE_CONTEXT_RESULTS = BUILDER
            .comment("Maximum number of matching recipes to include in AI prompts.")
            .defineInRange("maxRecipeContextResults", 8, 1, 25);

    public static final ModConfigSpec.IntValue RECIPE_BRANCH_MAX_DEPTH = BUILDER
            .comment("Maximum recipe branch depth shown in the MCAI GUI tracked-recipe visual.")
            .defineInRange("recipeBranchMaxDepth", 4, 2, 8);

    public static final ModConfigSpec.IntValue RECIPE_BRANCH_MAX_CHILDREN = BUILDER
            .comment("Maximum children shown per recipe branch node in the MCAI GUI tracked-recipe visual.")
            .defineInRange("recipeBranchMaxChildren", 3, 1, 8);

    public static final ModConfigSpec.ConfigValue<String> OLLAMA_MODEL = BUILDER
            .comment("Ollama model name to use for AI chat.")
            .define("ollamaModel", "gemma4:latest");

    public static final ModConfigSpec.ConfigValue<String> SYSTEM_PROMPT = BUILDER
            .comment("System prompt sent to the assistant before the conversation.")
            .define("systemPrompt", "You are MCAI, a helpful in-game Minecraft assistant for modded Minecraft. Use the provided inventory, player, modpack, recipe, and quest context to give concise, practical advice about recipes, progression, and gameplay. If you are unsure, say so.");

    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS = BUILDER
            .comment("Maximum number of seconds to wait for Ollama to respond.")
            .defineInRange("requestTimeoutSeconds", 120, 5, 600);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean isValidContextProfile(String value) {
        return CONTEXT_PROFILE_MINIMAL.equals(value)
                || CONTEXT_PROFILE_NORMAL.equals(value)
                || CONTEXT_PROFILE_RICH.equals(value);
    }

    private static boolean isValidResponseTone(String value) {
        return RESPONSE_TONE_TERSE.equals(value)
                || RESPONSE_TONE_BALANCED.equals(value)
                || RESPONSE_TONE_DETAILED.equals(value);
    }

    private static boolean isValidChatMode(String value) {
        return CHAT_MODE_DEFAULT.equals(value)
                || CHAT_MODE_HELP.equals(value)
                || CHAT_MODE_DEBUG.equals(value)
                || CHAT_MODE_PROGRESS.equals(value);
    }
}
