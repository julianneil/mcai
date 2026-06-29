package com.modai.mcai.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.modai.mcai.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class LmStudioClient {
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<String> chat(List<AiMessage> messages) {
        HttpRequest request = createJsonPost(chatCompletionsPath(), createChatRequestBody(messages));
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseHttpResponse);
    }

    private HttpRequest createJsonPost(String path, String body) {
        String endpoint = trimTrailingSlash(Config.LM_STUDIO_ENDPOINT.get()) + path;
        int timeoutSeconds = Config.REQUEST_TIMEOUT_SECONDS.get();

        return HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String createChatRequestBody(List<AiMessage> messages) {
        JsonObject root = new JsonObject();
        root.addProperty("model", Config.OLLAMA_MODEL.get());

        JsonArray jsonMessages = new JsonArray();
        for (AiMessage message : messages) {
            JsonObject jsonMessage = new JsonObject();
            jsonMessage.addProperty("role", message.role());
            jsonMessage.addProperty("content", message.content());
            jsonMessages.add(jsonMessage);
        }
        root.add("messages", jsonMessages);

        return GSON.toJson(root);
    }

    private String parseHttpResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LmStudioException("LM Studio returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return parseChatCompletionsResponse(response.body());
    }

    private String parseChatCompletionsResponse(String responseBody) {
        JsonObject root = GSON.fromJson(responseBody, JsonObject.class);
        if (root == null || !root.has("choices")) {
            throw new LmStudioException("LM Studio /v1/chat/completions response did not include choices.");
        }

        JsonElement choicesElement = root.get("choices");
        if (choicesElement == null || !choicesElement.isJsonArray() || choicesElement.getAsJsonArray().isEmpty()) {
            throw new LmStudioException("LM Studio /v1/chat/completions response included no choices.");
        }

        JsonObject firstChoice = choicesElement.getAsJsonArray().get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new LmStudioException("LM Studio /v1/chat/completions response did not include message content.");
        }

        return message.get("content").getAsString().trim();
    }

    private String chatCompletionsPath() {
        String endpoint = trimTrailingSlash(Config.LM_STUDIO_ENDPOINT.get()).toLowerCase(Locale.ROOT);
        if (endpoint.endsWith("/v1")) {
            return "/chat/completions";
        }
        return "/v1/chat/completions";
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static class LmStudioException extends RuntimeException {
        public LmStudioException(String message) {
            super(message);
        }
    }
}
