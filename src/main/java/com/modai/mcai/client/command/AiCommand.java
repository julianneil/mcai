package com.modai.mcai.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.modai.mcai.client.AiChatManager;
import com.modai.mcai.client.recipe.JeiRecipeBridge;
import com.modai.mcai.client.recipe.JeiRecipeBridge.OpenResult;
import com.modai.mcai.client.recipe.RecipeTracker;
import com.modai.mcai.client.recipe.RecipeTracker.TrackResult;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
                        })));
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

    private static String errorText(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }
}
