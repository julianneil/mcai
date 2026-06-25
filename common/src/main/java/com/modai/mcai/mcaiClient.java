package com.modai.mcai;

import com.modai.mcai.client.KeyBindings;
import com.modai.mcai.client.command.AiCommand;
import com.modai.mcai.client.recipe.JeiTrackingOverlay;
import com.modai.mcai.client.recipe.RecipeHighlightRenderer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = mcai.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class mcaiClient {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        KeyBindings.onKeyInput(event);
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        AiCommand.register(event);
    }

    @SubscribeEvent
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        RecipeHighlightRenderer.onContainerForeground(event);
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        JeiTrackingOverlay.onScreenRenderPost(event);
    }

    @SubscribeEvent
    public static void onMouseButtonPressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        JeiTrackingOverlay.onMouseButtonPressedPre(event);
    }
}
