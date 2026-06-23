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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
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
    private final OfflineFallbackResponder offlineFallbackResponder = new OfflineFallbackResponder();
    private final LinkedList<AiMessage> history = new LinkedList<>();

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
        return !history.isEmpty() && "assistant".equals(history.getLast().role());
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

        sendChatRequest(requestMessages, trimmedMessage, onReply, onError, null);
    }

    public boolean retryLastResponse(Consumer<String> onReply, Consumer<Throwable> onError) {
        synchronized (this) {
            if (!canRetryLastResponse()) {
                return false;
            }

            AiMessage removedAssistant = history.removeLast();
            String userMessage = getLastUserMessage().orElse("");
            List<AiMessage> requestMessages = new ArrayList<>();
            requestMessages.add(new AiMessage("system", buildSystemPrompt(userMessage)));
            requestMessages.addAll(history);
            sendChatRequest(requestMessages, userMessage, onReply, onError, () -> {
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
        appendModeInstruction(prompt);

        String contextProfile = Config.CONTEXT_PROFILE.get().toLowerCase(Locale.ROOT);
        if (hasAnyAllowedContext()) {
            prompt.append("\n\nUse the following live game/modpack context when it is relevant. Do not invent items, positions, entities, loaded mods, or recipes that are not listed.");
        }

        if (Config.CONTEXT_PROFILE_RICH.equals(contextProfile)) {
            prompt.append("\n\nWhen the context is ambiguous, explain the uncertainty and give the most useful next check to do in-game.");
        }

        if (!Config.CONTEXT_PROFILE_MINIMAL.equals(contextProfile) && Config.INCLUDE_MODPACK_CONTEXT.getAsBoolean() && isShareAllowed("modpack")) {
            prompt.append("\n\n").append(richContext(contextProfile) ? modpackContextProvider.buildContext() : modpackContextProvider.buildSummary());
        }

        if (!Config.CONTEXT_PROFILE_MINIMAL.equals(contextProfile) && Config.INCLUDE_PLAYER_CONTEXT.getAsBoolean() && isShareAllowed("player")) {
            prompt.append("\n\n").append(richContext(contextProfile) ? playerContextProvider.buildContext() : playerContextProvider.buildSummary());
        }

        if (!Config.CONTEXT_PROFILE_MINIMAL.equals(contextProfile) && Config.INCLUDE_INVENTORY_CONTEXT.getAsBoolean() && isShareAllowed("inventory")) {
            prompt.append("\n\n").append(richContext(contextProfile) ? inventoryContextProvider.buildContext() : inventoryContextProvider.buildSummary());
        }

        if (Config.INCLUDE_QUEST_CONTEXT.getAsBoolean() && isShareAllowed("quest")) {
            prompt.append("\n\nIf the quest context includes next progression suggestions, use them first when the user asks what to do next or how to continue the pack. If no clear next step exists, say so plainly and mention the closest relevant quest or missing dependency.");
            prompt.append("\n\n").append(questContextProvider.buildContext());
        }

        if (Config.INCLUDE_RECIPE_CONTEXT.getAsBoolean() && isShareAllowed("recipe")) {
            prompt.append("\n\n").append(recipeContextProvider.buildContext(userMessage));
        }

        return prompt.toString();
    }

    private boolean richContext(String contextProfile) {
        return Config.CONTEXT_PROFILE_RICH.equals(contextProfile);
    }

    private void appendModeInstruction(StringBuilder prompt) {
        String mode = Config.CHAT_MODE.get().toLowerCase(Locale.ROOT);
        switch (mode) {
            case Config.CHAT_MODE_HELP -> prompt.append("\n\nMode: help. Give direct, practical guidance and keep the answer easy to act on.");
            case Config.CHAT_MODE_DEBUG -> prompt.append("\n\nMode: debug. Be explicit about assumptions, missing data, and what was used from live context.");
            case Config.CHAT_MODE_PROGRESS -> prompt.append("\n\nMode: progression. Prioritize next steps, pack progression, and the most useful thing to do next.");
            default -> prompt.append("\n\nMode: default. Stay balanced unless the user asks for a different style.");
        }
    }

    private boolean hasAnyAllowedContext() {
        return isShareAllowed("player")
                || isShareAllowed("inventory")
                || isShareAllowed("modpack")
                || isShareAllowed("recipe")
                || isShareAllowed("quest");
    }

    private boolean isShareAllowed(String category) {
        String whitelist = Config.SHARE_WHITELIST.get();
        if (whitelist.isBlank()) {
            return false;
        }

        for (String entry : whitelist.toLowerCase(Locale.ROOT).split(",")) {
            String trimmed = entry.trim();
            if (trimmed.equals("all")) {
                return true;
            }
            if (trimmed.equals(category.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public synchronized String describeShareWhitelist() {
        return normalizeShareWhitelist(Config.SHARE_WHITELIST.get());
    }

    public synchronized String describeShareWhitelistCompact() {
        String whitelist = describeShareWhitelist();
        if (whitelist.isBlank()) {
            return "none";
        }
        if (whitelist.equals("all")) {
            return "all";
        }

        List<String> parts = new ArrayList<>();
        for (String category : whitelist.split(",")) {
            switch (category) {
                case "player" -> parts.add("P");
                case "inventory" -> parts.add("I");
                case "modpack" -> parts.add("M");
                case "recipe" -> parts.add("R");
                case "quest" -> parts.add("Q");
                default -> {
                    // Ignore.
                }
            }
        }
        return parts.isEmpty() ? "none" : String.join("/", parts);
    }

    public synchronized void setShareWhitelist(String whitelist) {
        Config.SHARE_WHITELIST.set(normalizeShareWhitelist(whitelist));
        Config.SHARE_WHITELIST.save();
    }

    public synchronized void setChatMode(String mode) {
        Config.CHAT_MODE.set(normalizeChatMode(mode));
        Config.CHAT_MODE.save();
    }

    public synchronized String describeChatMode() {
        return Config.CHAT_MODE.get();
    }

    private String normalizeChatMode(String mode) {
        String value = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (Config.CHAT_MODE_HELP.equals(value)
                || Config.CHAT_MODE_DEBUG.equals(value)
                || Config.CHAT_MODE_PROGRESS.equals(value)) {
            return value;
        }
        return Config.CHAT_MODE_DEFAULT;
    }

    private String normalizeShareWhitelist(String whitelist) {
        if (whitelist == null || whitelist.isBlank()) {
            return "";
        }

        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String token : whitelist.toLowerCase(Locale.ROOT).split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.equals("all")) {
                return "all";
            }
            switch (trimmed) {
                case "player", "inventory", "modpack", "recipe", "quest" -> values.add(trimmed);
                default -> {
                    // Ignore unknown categories.
                }
            }
        }
        return String.join(",", values);
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
            history.removeFirst();
        }
    }

    private void sendChatRequest(List<AiMessage> requestMessages, String userMessage, Consumer<String> onReply, Consumer<Throwable> onError, Runnable onFailureRestore) {
        ollamaClient.chat(requestMessages)
                .whenComplete((reply, throwable) -> Minecraft.getInstance().execute(() -> {
                    if (throwable != null) {
                        Optional<String> fallback = offlineFallbackResponder.buildReply(userMessage, unwrap(throwable));
                        if (fallback.isPresent()) {
                            synchronized (this) {
                                history.add(new AiMessage("assistant", fallback.get()));
                                trimHistory();
                            }
                            onReply.accept(fallback.get());
                            return;
                        }

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
