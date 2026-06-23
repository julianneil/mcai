package com.modai.mcai.client;

import com.modai.mcai.Config;
import com.modai.mcai.client.context.InventoryContextProvider;
import com.modai.mcai.client.context.ModpackContextProvider;
import com.modai.mcai.client.context.PlayerContextProvider;
import com.modai.mcai.client.context.QuestContextProvider;
import com.modai.mcai.client.context.RecipeContextProvider;
import com.modai.mcai.client.lookup.PackLookupService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class OfflineFallbackResponder {
    private final PackLookupService lookupService = new PackLookupService();
    private final RecipeContextProvider recipeContextProvider = new RecipeContextProvider();
    private final QuestContextProvider questContextProvider = new QuestContextProvider();
    private final PlayerContextProvider playerContextProvider = new PlayerContextProvider();
    private final InventoryContextProvider inventoryContextProvider = new InventoryContextProvider();
    private final ModpackContextProvider modpackContextProvider = new ModpackContextProvider();

    public Optional<String> buildReply(String userMessage, Throwable error) {
        if (!Config.ENABLE_OFFLINE_FALLBACK.getAsBoolean()) {
            return Optional.empty();
        }

        LocalPlayer player = Minecraft.getInstance().player;
        Level level = Minecraft.getInstance().level;
        if (player == null || level == null) {
            return Optional.of("Ollama is unavailable, and no world is loaded yet. MCAI's offline fallback can answer local recipe, item, block, mod, and quest questions once a world is open.");
        }

        String message = userMessage == null ? "" : userMessage.trim();
        String lower = message.toLowerCase(Locale.ROOT);
        List<String> sections = new ArrayList<>();
        sections.add("Ollama is unavailable right now, so I'm using MCAI's local fallback.");

        boolean matched = false;

        if (looksLikeRecipeQuestion(lower)) {
            sections.add(recipeContextProvider.buildContext(message));
            matched = true;
        }

        if (looksLikeItemQuestion(lower)) {
            lookupService.describeItem(message).ifPresent(sections::add);
            matched = true;
        }

        if (looksLikeBlockQuestion(lower)) {
            lookupService.describeBlock(message).ifPresent(sections::add);
            matched = true;
        }

        if (looksLikeModQuestion(lower)) {
            lookupService.describeMod(message).ifPresent(sections::add);
            matched = true;
        }

        if (looksLikeQuestQuestion(lower)) {
            sections.add(questContextProvider.buildNextProgressionSummary());
            matched = true;
        }

        if (looksLikeStateQuestion(lower)) {
            sections.add(playerContextProvider.buildSummary());
            sections.add(inventoryContextProvider.buildSummary());
            sections.add(modpackContextProvider.buildSummary());
            matched = true;
        }

        if (!matched) {
            sections.add("I can still answer local recipe, item, block, mod, and quest questions while Ollama is offline.");
            sections.add("Try /mcai item <query>, /mcai block <query>, /mcai mod <query>, /mcai quests next, or /mcai track <recipe>.");
        }

        if (error != null) {
            sections.add("Last connection error: " + error.getClass().getSimpleName());
        }

        return Optional.of(String.join("\n\n", sections).trim());
    }

    private boolean looksLikeRecipeQuestion(String lower) {
        return containsAny(lower, "recipe", "recipes", "craft", "crafting", "make", "ingredients", "what do i need", "how do i craft", "how do i make");
    }

    private boolean looksLikeItemQuestion(String lower) {
        return containsAny(lower, "item", "items");
    }

    private boolean looksLikeBlockQuestion(String lower) {
        return containsAny(lower, "block", "blocks");
    }

    private boolean looksLikeModQuestion(String lower) {
        return containsAny(lower, "mod", "mods");
    }

    private boolean looksLikeQuestQuestion(String lower) {
        return containsAny(lower, "quest", "quests", "progress", "progression", "next step", "what should i do next", "where do i go next");
    }

    private boolean looksLikeStateQuestion(String lower) {
        return containsAny(lower, "inventory", "current inventory", "where am i", "player", "location", "status", "what can i do", "what should i do");
    }

    private boolean containsAny(String lower, String... needles) {
        for (String needle : needles) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
