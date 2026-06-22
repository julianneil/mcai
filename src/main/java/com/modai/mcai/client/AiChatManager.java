package com.modai.mcai.client;

import com.modai.mcai.Config;
import com.modai.mcai.client.OllamaClient.AiMessage;
import com.modai.mcai.client.context.InventoryContextProvider;
import com.modai.mcai.client.context.ModpackContextProvider;
import com.modai.mcai.client.context.PlayerContextProvider;
import com.modai.mcai.client.context.RecipeContextProvider;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class AiChatManager {
    private static final int MAX_HISTORY_MESSAGES = 24;
    private static final AiChatManager INSTANCE = new AiChatManager();

    private final OllamaClient ollamaClient = new OllamaClient();
    private final InventoryContextProvider inventoryContextProvider = new InventoryContextProvider();
    private final PlayerContextProvider playerContextProvider = new PlayerContextProvider();
    private final ModpackContextProvider modpackContextProvider = new ModpackContextProvider();
    private final RecipeContextProvider recipeContextProvider = new RecipeContextProvider();
    private final List<AiMessage> history = new ArrayList<>();

    private AiChatManager() {
    }

    public static AiChatManager get() {
        return INSTANCE;
    }

    public synchronized List<AiMessage> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public void ask(String userMessage, Consumer<String> onReply, Consumer<Throwable> onError) {
        String trimmedMessage = userMessage.trim();
        if (trimmedMessage.isEmpty()) {
            return;
        }

        List<AiMessage> requestMessages;
        synchronized (this) {
            history.add(new AiMessage("user", trimmedMessage));
            trimHistory();

            requestMessages = new ArrayList<>();
            requestMessages.add(new AiMessage("system", buildSystemPrompt(trimmedMessage)));
            requestMessages.addAll(history);
        }

        ollamaClient.chat(requestMessages)
                .whenComplete((reply, throwable) -> Minecraft.getInstance().execute(() -> {
                    if (throwable != null) {
                        onError.accept(unwrap(throwable));
                        return;
                    }

                    synchronized (this) {
                        history.add(new AiMessage("assistant", reply));
                        trimHistory();
                    }
                    onReply.accept(reply);
                }));
    }

    public synchronized void clearHistory() {
        history.clear();
    }

    private String buildSystemPrompt(String userMessage) {
        StringBuilder prompt = new StringBuilder(Config.SYSTEM_PROMPT.get());
        if (Config.INCLUDE_PLAYER_CONTEXT.getAsBoolean() || Config.INCLUDE_INVENTORY_CONTEXT.getAsBoolean() || Config.INCLUDE_MODPACK_CONTEXT.getAsBoolean() || Config.INCLUDE_RECIPE_CONTEXT.getAsBoolean()) {
            prompt.append("\n\nUse the following live game/modpack context when it is relevant. Do not invent items, positions, entities, loaded mods, or recipes that are not listed.");
        }

        if (Config.INCLUDE_MODPACK_CONTEXT.getAsBoolean()) {
            prompt.append("\n\n").append(modpackContextProvider.buildContext());
        }

        if (Config.INCLUDE_PLAYER_CONTEXT.getAsBoolean()) {
            prompt.append("\n\n").append(playerContextProvider.buildContext());
        }

        if (Config.INCLUDE_INVENTORY_CONTEXT.getAsBoolean()) {
            prompt.append("\n\n").append(inventoryContextProvider.buildContext());
        }

        if (Config.INCLUDE_RECIPE_CONTEXT.getAsBoolean()) {
            prompt.append("\n\n").append(recipeContextProvider.buildContext(userMessage));
        }

        return prompt.toString();
    }

    private void trimHistory() {
        while (history.size() > MAX_HISTORY_MESSAGES) {
            history.remove(0);
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable.getCause();
        return cause == null ? throwable : cause;
    }
}
