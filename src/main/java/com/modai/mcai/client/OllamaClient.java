package com.modai.mcai.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.modai.mcai.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OllamaClient {
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<String> chat(List<AiMessage> messages) {
        return sendChatRequest(messages)
                .handle((reply, throwable) -> {
                    if (throwable == null) {
                        return CompletableFuture.completedFuture(reply);
                    }

                    return sendGenerateRequest(messages);
                })
                .thenCompose(future -> future);
    }

    private CompletableFuture<String> sendChatRequest(List<AiMessage> messages) {
        HttpRequest request = createJsonPost("/api/chat", createChatRequestBody(messages));
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseHttpResponse(response, this::parseChatResponse));
    }

    private CompletableFuture<String> sendGenerateRequest(List<AiMessage> messages) {
        HttpRequest request = createJsonPost("/api/generate", createGenerateRequestBody(messages));
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseHttpResponse(response, this::parseGenerateResponse));
    }

    private HttpRequest createJsonPost(String path, String body) {
        String endpoint = trimTrailingSlash(Config.OLLAMA_ENDPOINT.get()) + path;
        int timeoutSeconds = Config.REQUEST_TIMEOUT_SECONDS.getAsInt();

        return HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String createChatRequestBody(List<AiMessage> messages) {
        JsonObject root = new JsonObject();
        root.addProperty("model", Config.OLLAMA_MODEL.get());
        root.addProperty("stream", false);

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

    private String createGenerateRequestBody(List<AiMessage> messages) {
        JsonObject root = new JsonObject();
        root.addProperty("model", Config.OLLAMA_MODEL.get());
        root.addProperty("stream", false);
        root.addProperty("prompt", createPrompt(messages));
        return GSON.toJson(root);
    }

    private String createPrompt(List<AiMessage> messages) {
        StringBuilder prompt = new StringBuilder();
        for (AiMessage message : messages) {
            String role = switch (message.role()) {
                case "system" -> "System";
                case "assistant" -> "Assistant";
                default -> "User";
            };
            prompt.append(role).append(": ").append(message.content()).append("\n\n");
        }
        prompt.append("Assistant:");
        return prompt.toString();
    }

    private String parseHttpResponse(HttpResponse<String> response, ResponseParser parser) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OllamaException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return parser.parse(response.body());
    }

    private String parseChatResponse(String responseBody) {
        JsonObject root = GSON.fromJson(responseBody, JsonObject.class);
        if (root == null || !root.has("message")) {
            throw new OllamaException("Ollama /api/chat response did not include a message.");
        }

        JsonObject message = root.getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new OllamaException("Ollama /api/chat response did not include message content.");
        }

        return message.get("content").getAsString().trim();
    }

    private String parseGenerateResponse(String responseBody) {
        JsonObject root = GSON.fromJson(responseBody, JsonObject.class);
        if (root == null || !root.has("response")) {
            throw new OllamaException("Ollama /api/generate response did not include response text.");
        }

        return root.get("response").getAsString().trim();
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static class OllamaException extends RuntimeException {
        public OllamaException(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    private interface ResponseParser {
        String parse(String responseBody);
    }
}
