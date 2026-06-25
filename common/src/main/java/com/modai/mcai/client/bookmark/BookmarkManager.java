package com.modai.mcai.client.bookmark;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class BookmarkManager {
    private static final BookmarkManager INSTANCE = new BookmarkManager();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<BookmarkEntry> bookmarks = new ArrayList<>();
    private boolean loaded;

    private BookmarkManager() {
    }

    public static BookmarkManager get() {
        return INSTANCE;
    }

    public synchronized List<BookmarkEntry> getBookmarks() {
        ensureLoaded();
        return List.copyOf(bookmarks);
    }

    public synchronized Optional<BookmarkEntry> getBookmark(int index) {
        ensureLoaded();
        int zeroBased = index - 1;
        if (zeroBased < 0 || zeroBased >= bookmarks.size()) {
            return Optional.empty();
        }
        return Optional.of(bookmarks.get(zeroBased));
    }

    public synchronized BookmarkOpResult addBookmark(String label, String kind, String prompt, String note) {
        ensureLoaded();

        String trimmedLabel = label == null ? "" : label.trim();
        String trimmedKind = kind == null ? "note" : kind.trim();
        String trimmedPrompt = prompt == null ? "" : prompt.trim();
        String trimmedNote = note == null ? "" : note.trim();

        if (trimmedLabel.isEmpty()) {
            return BookmarkOpResult.failure(Component.literal("Bookmark label cannot be empty."));
        }
        if (trimmedPrompt.isEmpty()) {
            return BookmarkOpResult.failure(Component.literal("Bookmark text cannot be empty."));
        }

        bookmarks.add(new BookmarkEntry(trimmedLabel, trimmedKind, trimmedPrompt, trimmedNote, LocalDateTime.now().format(TIMESTAMP_FORMAT)));
        if (!save()) {
            bookmarks.remove(bookmarks.size() - 1);
            return BookmarkOpResult.failure(Component.literal("Could not save bookmark file.").withStyle(ChatFormatting.RED));
        }

        return BookmarkOpResult.success(Component.literal("Saved bookmark '").append(Component.literal(trimmedLabel).withStyle(ChatFormatting.AQUA)).append(Component.literal("'.")));
    }

    public synchronized BookmarkOpResult removeBookmark(int index) {
        ensureLoaded();

        int zeroBased = index - 1;
        if (zeroBased < 0 || zeroBased >= bookmarks.size()) {
            return BookmarkOpResult.failure(Component.literal("No bookmark at that index."));
        }

        BookmarkEntry removed = bookmarks.remove(zeroBased);
        if (!save()) {
            bookmarks.add(zeroBased, removed);
            return BookmarkOpResult.failure(Component.literal("Could not save bookmark file.").withStyle(ChatFormatting.RED));
        }

        return BookmarkOpResult.success(Component.literal("Removed bookmark '").append(Component.literal(removed.label()).withStyle(ChatFormatting.AQUA)).append(Component.literal("'.")));
    }

    public synchronized String formatBookmarks() {
        ensureLoaded();
        if (bookmarks.isEmpty()) {
            return "MCAI bookmarks: none saved.";
        }

        StringBuilder text = new StringBuilder();
        text.append("MCAI bookmarks:\n");
        for (int i = 0; i < bookmarks.size(); i++) {
            BookmarkEntry entry = bookmarks.get(i);
            text.append(i + 1).append(". [").append(entry.kind()).append("] ").append(entry.label());
            if (!entry.note().isBlank()) {
                text.append(" - ").append(entry.note());
            }
            text.append(" (").append(entry.createdAt()).append(")\n");
        }
        return text.toString().trim();
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        bookmarks.clear();

        Path file = storagePath();
        if (file == null || !Files.exists(file)) {
            return;
        }

        Properties properties = new Properties();
        try {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            int count = Integer.parseInt(properties.getProperty("count", "0"));
            for (int i = 0; i < count; i++) {
                String label = decode(properties.getProperty(key(i, "label"), ""));
                String kind = decode(properties.getProperty(key(i, "kind"), ""));
                String prompt = decode(properties.getProperty(key(i, "prompt"), ""));
                String note = decode(properties.getProperty(key(i, "note"), ""));
                String createdAt = decode(properties.getProperty(key(i, "createdAt"), ""));
                if (!label.isBlank() && !prompt.isBlank()) {
                    bookmarks.add(new BookmarkEntry(label, kind, prompt, note, createdAt));
                }
            }
        } catch (Exception ignored) {
            bookmarks.clear();
        }
    }

    private boolean save() {
        Path file = storagePath();
        if (file == null) {
            return false;
        }

        Properties properties = new Properties();
        properties.setProperty("count", Integer.toString(bookmarks.size()));
        for (int i = 0; i < bookmarks.size(); i++) {
            BookmarkEntry entry = bookmarks.get(i);
            properties.setProperty(key(i, "label"), encode(entry.label()));
            properties.setProperty(key(i, "kind"), encode(entry.kind()));
            properties.setProperty(key(i, "prompt"), encode(entry.prompt()));
            properties.setProperty(key(i, "note"), encode(entry.note()));
            properties.setProperty(key(i, "createdAt"), encode(entry.createdAt()));
        }

        try {
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "MCAI bookmarks");
            }
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private Path storagePath() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameDirectory == null) {
            return null;
        }
        return minecraft.gameDirectory.toPath().resolve("mcai-bookmarks.properties");
    }

    private String key(int index, String field) {
        return "bookmark." + index + "." + field;
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return value;
        }
    }

    public record BookmarkEntry(String label, String kind, String prompt, String note, String createdAt) {
    }

    public record BookmarkOpResult(boolean success, Component message) {
        public static BookmarkOpResult success(Component message) {
            return new BookmarkOpResult(true, message.copy().withStyle(ChatFormatting.GREEN));
        }

        public static BookmarkOpResult failure(Component message) {
            return new BookmarkOpResult(false, message.copy().withStyle(ChatFormatting.RED));
        }
    }
}
