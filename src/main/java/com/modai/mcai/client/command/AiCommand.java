package com.modai.mcai.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.modai.mcai.Config;
import com.modai.mcai.client.AiChatManager.HistoryExportResult;
import com.modai.mcai.client.AiChatManager;
import com.modai.mcai.client.bookmark.BookmarkManager;
import com.modai.mcai.client.bookmark.BookmarkManager.BookmarkEntry;
import com.modai.mcai.client.bookmark.BookmarkManager.BookmarkOpResult;
import com.modai.mcai.client.context.QuestContextProvider;
import com.modai.mcai.client.recipe.JeiRecipeBridge;
import com.modai.mcai.client.recipe.JeiRecipeBridge.OpenResult;
import com.modai.mcai.client.recipe.RecipeTracker;
import com.modai.mcai.client.recipe.RecipeTracker.TrackResult;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class AiCommand {
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(literal("ai")
                .then(argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            ask(StringArgumentType.getString(context, "message"));
                            return 1;
                        })));

        event.getDispatcher().register(literal("mcai")
                .then(literal("track")
                        .then(argument("recipe", StringArgumentType.greedyString())
                                .executes(context -> {
                                    trackRecipe(StringArgumentType.getString(context, "recipe"));
                                    return 1;
                                })))
                .then(literal("jei")
                        .then(argument("recipe", StringArgumentType.greedyString())
                                .executes(context -> {
                                    openJeiRecipe(StringArgumentType.getString(context, "recipe"));
                                    return 1;
                                })))
                .then(literal("jeiuses")
                        .then(argument("item", StringArgumentType.greedyString())
                                .executes(context -> {
                                    openJeiUses(StringArgumentType.getString(context, "item"));
                                    return 1;
                                })))
                .then(literal("cleartrack")
                        .executes(context -> {
                            clearRecipeTrack();
                            return 1;
                        }))
                .then(literal("bookmark")
                        .executes(context -> {
                            showBookmarkHelp();
                            return 1;
                        })
                        .then(literal("add")
                                .then(argument("label", StringArgumentType.string())
                                        .then(argument("text", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    addBookmark(StringArgumentType.getString(context, "label"), StringArgumentType.getString(context, "text"));
                                                    return 1;
                                                }))))
                        .then(literal("current")
                                .then(argument("label", StringArgumentType.string())
                                        .executes(context -> {
                                            bookmarkCurrentConversation(StringArgumentType.getString(context, "label"));
                                            return 1;
                                        })))
                        .then(literal("item")
                                .then(argument("label", StringArgumentType.string())
                                        .then(argument("query", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    bookmarkItem(StringArgumentType.getString(context, "label"), StringArgumentType.getString(context, "query"));
                                                    return 1;
                                                }))))
                        .then(literal("recipe")
                                .then(argument("label", StringArgumentType.string())
                                        .executes(context -> {
                                            bookmarkTrackedRecipe(StringArgumentType.getString(context, "label"));
                                            return 1;
                                        })
                                        .then(argument("query", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    bookmarkRecipeQuery(StringArgumentType.getString(context, "label"), StringArgumentType.getString(context, "query"));
                                                    return 1;
                                                }))))
                        .then(literal("list")
                                .executes(context -> {
                                    listBookmarks();
                                    return 1;
                                }))
                        .then(literal("open")
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            openBookmark(IntegerArgumentType.getInteger(context, "index"));
                                            return 1;
                                        })))
                        .then(literal("remove")
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            removeBookmark(IntegerArgumentType.getInteger(context, "index"));
                                            return 1;
                                        }))))
                .then(literal("history")
                        .executes(context -> {
                            showHistoryHelp();
                            return 1;
                        })
                        .then(literal("clear")
                                .executes(context -> {
                                    clearChatHistory();
                                    return 1;
                                }))
                        .then(literal("retry")
                                .executes(context -> {
                                    retryChat();
                                    return 1;
                                }))
                        .then(literal("export")
                                .executes(context -> {
                                    exportChatHistory();
                                    return 1;
                                })))
                .then(literal("tone")
                        .executes(context -> {
                            showTone();
                            return 1;
                        })
                        .then(literal(Config.RESPONSE_TONE_TERSE)
                                .executes(context -> {
                                    setTone(Config.RESPONSE_TONE_TERSE);
                                    return 1;
                                }))
                        .then(literal(Config.RESPONSE_TONE_BALANCED)
                                .executes(context -> {
                                    setTone(Config.RESPONSE_TONE_BALANCED);
                                    return 1;
                                }))
                        .then(literal(Config.RESPONSE_TONE_DETAILED)
                                .executes(context -> {
                                    setTone(Config.RESPONSE_TONE_DETAILED);
                                    return 1;
                                })))
                .then(literal("item")
                        .executes(context -> {
                            showLookupHelp("item");
                            return 1;
                        })
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(context -> {
                                    lookupItem(StringArgumentType.getString(context, "query"));
                                    return 1;
                                })))
                .then(literal("block")
                        .executes(context -> {
                            showLookupHelp("block");
                            return 1;
                        })
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(context -> {
                                    lookupBlock(StringArgumentType.getString(context, "query"));
                                    return 1;
                                })))
                .then(literal("mod")
                        .executes(context -> {
                            showLookupHelp("mod");
                            return 1;
                        })
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(context -> {
                                    lookupMod(StringArgumentType.getString(context, "query"));
                                    return 1;
                                })))
                .then(literal("quests")
                        .executes(context -> {
                            showQuestSummary();
                            return 1;
                        })
                        .then(literal("next")
                                .executes(context -> {
                                    showQuestNextSteps();
                                    return 1;
                                }))));
    }

    private static void ask(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        minecraft.player.displayClientMessage(Component.literal("You: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE)), false);
        minecraft.player.displayClientMessage(Component.literal("MCAI is thinking...").withStyle(ChatFormatting.YELLOW), false);

        AiChatManager.get().ask(message,
                reply -> minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(reply).withStyle(ChatFormatting.WHITE)), false),
                error -> minecraft.player.displayClientMessage(Component.literal("MCAI error: ").withStyle(ChatFormatting.RED)
                        .append(Component.literal(errorText(error)).withStyle(ChatFormatting.WHITE)), false));
    }

    private static void trackRecipe(String recipeQuery) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        TrackResult result = RecipeTracker.get().track(recipeQuery);
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(result.message()), false);
    }

    private static void openJeiRecipe(String recipeQuery) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        TrackResult trackResult = RecipeTracker.get().track(recipeQuery);
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(trackResult.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(trackResult.message()), false);
        if (!trackResult.success()) {
            return;
        }

        OpenResult jeiResult = JeiRecipeBridge.showRecipesFor(RecipeTracker.get().targetStack());
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(jeiResult.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(jeiResult.message()), false);
    }

    private static void openJeiUses(String itemQuery) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        TrackResult trackResult = RecipeTracker.get().track(itemQuery);
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(trackResult.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(trackResult.message()), false);
        if (!trackResult.success()) {
            return;
        }

        OpenResult jeiResult = JeiRecipeBridge.showUsesFor(RecipeTracker.get().targetStack());
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(jeiResult.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(jeiResult.message()), false);
    }

    private static void clearRecipeTrack() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        RecipeTracker.get().clear();
        minecraft.player.displayClientMessage(Component.literal("MCAI: Cleared recipe highlights.").withStyle(ChatFormatting.GREEN), false);
    }

    private static void showBookmarkHelp() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        minecraft.player.displayClientMessage(Component.literal("MCAI bookmarks: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("add / current / item / recipe / list / open / remove").withStyle(ChatFormatting.AQUA)), false);
    }

    private static void addBookmark(String label, String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        BookmarkOpResult result = BookmarkManager.get().addBookmark(label, "note", text, preview(text, 80));
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(result.message()), false);
    }

    private static void bookmarkCurrentConversation(String label) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        String prompt = AiChatManager.get().getLastUserMessage().orElse("");
        if (prompt.isBlank()) {
            minecraft.player.displayClientMessage(Component.literal("MCAI: No previous user question to bookmark.").withStyle(ChatFormatting.RED), false);
            return;
        }

        String note = AiChatManager.get().getHistory().stream()
                .filter(message -> "assistant".equals(message.role()))
                .reduce((first, second) -> second)
                .map(message -> message.content())
                .orElse("");
        BookmarkOpResult result = BookmarkManager.get().addBookmark(label, "question", prompt, note);
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(result.message()), false);
    }

    private static void bookmarkItem(String label, String query) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Item item = findBestItem(query);
        if (item == null) {
            minecraft.player.displayClientMessage(Component.literal("MCAI: No item found for ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(query).withStyle(ChatFormatting.WHITE)), false);
            return;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String note = item.getDescription().getString() + " (" + id + ")";
        BookmarkOpResult result = BookmarkManager.get().addBookmark(label, "item", query, note);
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(result.message()), false);
    }

    private static void bookmarkTrackedRecipe(String label) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        RecipeTracker tracker = RecipeTracker.get();
        if (!tracker.isTracking() || tracker.targetStack().isEmpty()) {
            minecraft.player.displayClientMessage(Component.literal("MCAI: No tracked recipe to bookmark.").withStyle(ChatFormatting.RED), false);
            return;
        }

        String prompt = tracker.targetName().getString();
        String note = "Tracked recipe for " + prompt;
        BookmarkOpResult result = BookmarkManager.get().addBookmark(label, "recipe", prompt, note);
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(result.message()), false);
    }

    private static void bookmarkRecipeQuery(String label, String query) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            minecraft.player.displayClientMessage(Component.literal("MCAI: Recipe bookmark query cannot be empty.").withStyle(ChatFormatting.RED), false);
            return;
        }

        BookmarkOpResult result = BookmarkManager.get().addBookmark(label, "recipe", trimmed, "Recipe query");
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(result.message()), false);
    }

    private static void listBookmarks() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        minecraft.player.displayClientMessage(Component.literal(BookmarkManager.get().formatBookmarks()).withStyle(ChatFormatting.AQUA), false);
    }

    private static void openBookmark(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        BookmarkEntry entry = BookmarkManager.get().getBookmark(index).orElse(null);
        if (entry == null) {
            minecraft.player.displayClientMessage(Component.literal("MCAI: No bookmark at index ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(Integer.toString(index)).withStyle(ChatFormatting.WHITE)), false);
            return;
        }

        minecraft.player.displayClientMessage(Component.literal("MCAI bookmark: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(entry.label()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" [" + entry.kind() + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
        if (!entry.note().isBlank()) {
            minecraft.player.displayClientMessage(Component.literal(entry.note()).withStyle(ChatFormatting.GRAY), false);
        }

        AiChatManager.get().ask(entry.prompt(),
                reply -> minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(reply).withStyle(ChatFormatting.WHITE)), false),
                error -> minecraft.player.displayClientMessage(Component.literal("MCAI error: ").withStyle(ChatFormatting.RED)
                        .append(Component.literal(errorText(error)).withStyle(ChatFormatting.WHITE)), false));
    }

    private static void removeBookmark(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        BookmarkOpResult result = BookmarkManager.get().removeBookmark(index);
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(result.message()), false);
    }

    private static void showHistoryHelp() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        minecraft.player.displayClientMessage(Component.literal("MCAI history: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("clear / retry / export").withStyle(ChatFormatting.AQUA)), false);
    }

    private static void clearChatHistory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        AiChatManager.get().clearHistory();
        minecraft.player.displayClientMessage(Component.literal("MCAI: Cleared chat history.").withStyle(ChatFormatting.GREEN), false);
    }

    private static void retryChat() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        boolean started = AiChatManager.get().retryLastResponse(
                reply -> minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(reply).withStyle(ChatFormatting.WHITE)), false),
                error -> minecraft.player.displayClientMessage(Component.literal("MCAI error: ").withStyle(ChatFormatting.RED)
                        .append(Component.literal(errorText(error)).withStyle(ChatFormatting.WHITE)), false));

        if (!started) {
            minecraft.player.displayClientMessage(Component.literal("MCAI: No assistant reply to retry yet.").withStyle(ChatFormatting.RED), false);
        } else {
            minecraft.player.displayClientMessage(Component.literal("MCAI is retrying the last reply...").withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static void exportChatHistory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        HistoryExportResult result = AiChatManager.get().exportHistory();
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(result.message()), false);
    }

    private static void showQuestSummary() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        QuestContextProvider provider = new QuestContextProvider();
        minecraft.player.displayClientMessage(Component.literal(provider.buildSummary()).withStyle(ChatFormatting.AQUA), false);
    }

    private static void showQuestNextSteps() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        QuestContextProvider provider = new QuestContextProvider();
        minecraft.player.displayClientMessage(Component.literal(provider.buildNextProgressionSummary()).withStyle(ChatFormatting.AQUA), false);
    }

    private static void showLookupHelp(String type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        minecraft.player.displayClientMessage(Component.literal("MCAI ").append(Component.literal(type).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" lookup: use a name, registry id, or mod id").withStyle(ChatFormatting.GRAY)), false);
    }

    private static void lookupItem(String query) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Item item = findBestItem(query);
        if (item == null) {
            minecraft.player.displayClientMessage(Component.literal("MCAI: No item found for ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(query).withStyle(ChatFormatting.WHITE)), false);
            return;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        minecraft.player.displayClientMessage(Component.literal("Item: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(item.getDescription().getString()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (" + id + ")").withStyle(ChatFormatting.DARK_GRAY)), false);
    }

    private static void lookupBlock(String query) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Block block = findBestBlock(query);
        if (block == null) {
            minecraft.player.displayClientMessage(Component.literal("MCAI: No block found for ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(query).withStyle(ChatFormatting.WHITE)), false);
            return;
        }

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        minecraft.player.displayClientMessage(Component.literal("Block: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(block.getName().getString()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (" + id + ")").withStyle(ChatFormatting.DARK_GRAY)), false);
    }

    private static void lookupMod(String query) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        IModInfo mod = findBestMod(query);
        if (mod == null) {
            minecraft.player.displayClientMessage(Component.literal("MCAI: No mod found for ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(query).withStyle(ChatFormatting.WHITE)), false);
            return;
        }

        minecraft.player.displayClientMessage(Component.literal("Mod: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(mod.getDisplayName()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (" + mod.getModId() + " " + mod.getVersion() + ")").withStyle(ChatFormatting.DARK_GRAY)), false);
    }

    private static Item findBestItem(String query) {
        String needle = query.toLowerCase();
        Item best = null;
        int bestScore = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String haystack = (item.getDescription().getString() + " " + id).toLowerCase();
            int score = scoreLookup(haystack, needle, id);
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return best;
    }

    private static Block findBestBlock(String query) {
        String needle = query.toLowerCase();
        Block best = null;
        int bestScore = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            String haystack = (block.getName().getString() + " " + id).toLowerCase();
            int score = scoreLookup(haystack, needle, id);
            if (score > bestScore) {
                bestScore = score;
                best = block;
            }
        }
        return best;
    }

    private static IModInfo findBestMod(String query) {
        String needle = query.toLowerCase();
        IModInfo best = null;
        int bestScore = 0;
        for (IModInfo mod : ModList.get().getMods()) {
            String haystack = (mod.getDisplayName() + " " + mod.getModId() + " " + mod.getVersion()).toLowerCase();
            int score = scoreLookup(haystack, needle, ResourceLocation.tryParse(mod.getModId()));
            if (score > bestScore) {
                bestScore = score;
                best = mod;
            }
        }
        return best;
    }

    private static int scoreLookup(String haystack, String needle, ResourceLocation id) {
        if (needle.isBlank()) {
            return 0;
        }

        int score = 0;
        if (haystack.contains(needle)) {
            score += 10;
        }
        if (id != null) {
            if (id.toString().equals(needle)) {
                score += 20;
            }
            if (id.getPath().equals(needle)) {
                score += 15;
            }
            if (id.getNamespace().equals(needle)) {
                score += 12;
            }
        }
        return score;
    }

    private static void showTone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        minecraft.player.displayClientMessage(Component.literal("MCAI tone: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Config.RESPONSE_TONE.get()).withStyle(ChatFormatting.AQUA)), false);
        minecraft.player.displayClientMessage(Component.literal("Use /mcai tone terse|balanced|detailed").withStyle(ChatFormatting.DARK_GRAY), false);
    }

    private static void setTone(String tone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Config.RESPONSE_TONE.set(tone);
        Config.RESPONSE_TONE.save();

        minecraft.player.displayClientMessage(Component.literal("MCAI tone set to ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(tone).withStyle(ChatFormatting.AQUA)), false);
    }

    private static String errorText(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private static String preview(String text, int limit) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, limit - 3)) + "...";
    }
}
