package com.modai.mcai.client;

import com.modai.mcai.Config;
import com.modai.mcai.client.OllamaClient.AiMessage;
import com.modai.mcai.client.context.InventoryContextProvider;
import com.modai.mcai.client.context.ModpackContextProvider;
import com.modai.mcai.client.context.PlayerContextProvider;
import com.modai.mcai.client.context.QuestContextProvider;
import com.modai.mcai.client.context.RecipeContextProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;

public class AiChatManager {
    private static final int MAX_HISTORY_MESSAGES = 24;
    private static final AiChatManager INSTANCE = new AiChatManager();

    private final OllamaClient ollamaClient = new OllamaClient();
    private final InventoryContextProvider inventoryContextProvider = new InventoryContextProvider();
    private final PlayerContextProvider playerContextProvider = new PlayerContextProvider();
    private final ModpackContextProvider modpackContextProvider = new ModpackContextProvider();
    private final QuestContextProvider questContextProvider = new QuestContextProvider();
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

    public synchronized Optional<String> getLastUserMessage() {
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage message = history.get(i);
            if ("user".equals(message.role())) {
                return Optional.of(message.content());
            }
        }
        return Optional.empty();
    }

    public synchronized boolean canRetryLastResponse() {
        return !history.isEmpty() && "assistant".equals(history.get(history.size() - 1).role());
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

        sendChatRequest(requestMessages, onReply, onError, null);
    }

    public boolean retryLastResponse(Consumer<String> onReply, Consumer<Throwable> onError) {
        synchronized (this) {
            if (!canRetryLastResponse()) {
                return false;
            }

            AiMessage removedAssistant = history.remove(history.size() - 1);
            String userMessage = getLastUserMessage().orElse("");
            List<AiMessage> requestMessages = new ArrayList<>();
            requestMessages.add(new AiMessage("system", buildSystemPrompt(userMessage)));
            requestMessages.addAll(history);
            sendChatRequest(requestMessages, onReply, onError, () -> {
                synchronized (this) {
                    history.add(removedAssistant);
                    trimHistory();
                }
            });
            return true;
        }
    }

    public synchronized HistoryExportResult exportHistory() {
        List<AiMessage> snapshot = new ArrayList<>(history);
        if (snapshot.isEmpty()) {
            return HistoryExportResult.failure(Component.literal("No chat history to export."));
        }

        Path outputFile = Minecraft.getInstance().gameDirectory.toPath().resolve("mcai-history-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".txt");
        StringBuilder export = new StringBuilder();
        export.append("MCAI chat export\n");
        export.append("Model: ").append(Config.OLLAMA_MODEL.get()).append('\n');
        export.append("Tone: ").append(Config.RESPONSE_TONE.get()).append('\n');
        export.append("Context profile: ").append(Config.CONTEXT_PROFILE.get()).append('\n');
        export.append('\n');

        for (AiMessage message : snapshot) {
            export.append(message.role().toUpperCase(Locale.ROOT)).append(": ").append(message.content()).append('\n');
        }

        try {
            Files.writeString(outputFile, export.toString(), StandardCharsets.UTF_8);
            return HistoryExportResult.success(outputFile);
        } catch (Exception error) {
            return HistoryExportResult.failure(Component.literal("Could not export history: " + errorText(error)));
        }
    }

    public synchronized void clearHistory() {
        history.clear();
    }

    private String buildSystemPrompt(String userMessage) {
        StringBuilder prompt = new StringBuilder(Config.SYSTEM_PROMPT.get());
        appendToneInstruction(prompt);

        String contextProfile = Config.CONTEXT_PROFILE.get().toLowerCase(Locale.ROOT);
        if (Config.INCLUDE_PLAYER_CONTEXT.getAsBoolean() || Config.INCLUDE_INVENTORY_CONTEXT.getAsBoolean() || Config.INCLUDE_MODPACK_CONTEXT.getAsBoolean() || Config.INCLUDE_RECIPE_CONTEXT.getAsBoolean() || Config.INCLUDE_QUEST_CONTEXT.getAsBoolean()) {
            prompt.append("\n\nUse the following live game/modpack context when it is relevant. Do not invent items, positions, entities, loaded mods, or recipes that are not listed.");
        }

        if (Config.CONTEXT_PROFILE_RICH.equals(contextProfile)) {
            prompt.append("\n\nWhen the context is ambiguous, explain the uncertainty and give the most useful next check to do in-game.");
        }

        if (!Config.CONTEXT_PROFILE_MINIMAL.equals(contextProfile) && Config.INCLUDE_MODPACK_CONTEXT.getAsBoolean()) {
            prompt.append("\n\n").append(modpackContextProvider.buildContext());
        }

        if (!Config.CONTEXT_PROFILE_MINIMAL.equals(contextProfile) && Config.INCLUDE_PLAYER_CONTEXT.getAsBoolean()) {
            prompt.append("\n\n").append(playerContextProvider.buildContext());
        }

        if (!Config.CONTEXT_PROFILE_MINIMAL.equals(contextProfile) && Config.INCLUDE_INVENTORY_CONTEXT.getAsBoolean()) {
            prompt.append("\n\n").append(inventoryContextProvider.buildContext());
        }

        if (Config.INCLUDE_QUEST_CONTEXT.getAsBoolean()) {
            prompt.append("\n\nIf the quest context includes next progression suggestions, use them first when the user asks what to do next or how to continue the pack. If no clear next step exists, say so plainly and mention the closest relevant quest or missing dependency.");
            prompt.append("\n\n").append(questContextProvider.buildContext());
        }

        if (Config.INCLUDE_RECIPE_CONTEXT.getAsBoolean()) {
            prompt.append("\n\n").append(recipeContextProvider.buildContext(userMessage));
        }

        return prompt.toString();
    }

    private void appendToneInstruction(StringBuilder prompt) {
        String tone = Config.RESPONSE_TONE.get().toLowerCase(Locale.ROOT);
        switch (tone) {
            case Config.RESPONSE_TONE_TERSE -> prompt.append("\n\nAnswer briefly. Prefer concise, direct responses unless the user asks for detail.");
            case Config.RESPONSE_TONE_DETAILED -> prompt.append("\n\nAnswer with more detail than usual. Include short explanations and concrete next steps when useful.");
            default -> prompt.append("\n\nAnswer in a balanced style: concise by default, but explain enough to be useful.");
        }
    }

    private void trimHistory() {
        while (history.size() > MAX_HISTORY_MESSAGES) {
            history.remove(0);
        }
    }

    private void sendChatRequest(List<AiMessage> requestMessages, Consumer<String> onReply, Consumer<Throwable> onError, Runnable onFailureRestore) {
        ollamaClient.chat(requestMessages)
                .whenComplete((reply, throwable) -> Minecraft.getInstance().execute(() -> {
                    if (throwable != null) {
                        if (onFailureRestore != null) {
                            onFailureRestore.run();
                        }
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

    private Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable.getCause();
        return cause == null ? throwable : cause;
    }

    private String errorText(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    public record HistoryExportResult(boolean success, Component message, Path path) {
        public static HistoryExportResult success(Path path) {
            return new HistoryExportResult(true, Component.literal("Exported chat history to ").append(Component.literal(path.toString())), path);
        }

        public static HistoryExportResult failure(Component message) {
            return new HistoryExportResult(false, message.copy().withStyle(net.minecraft.ChatFormatting.RED), null);
        }
    }
}
