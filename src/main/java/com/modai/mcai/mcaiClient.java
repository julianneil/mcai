package com.modai.mcai;

import com.modai.mcai.client.KeyBindings;
import com.modai.mcai.client.command.AiCommand;
import com.modai.mcai.client.recipe.JeiTrackingOverlay;
import com.modai.mcai.client.recipe.RecipeHighlightRenderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = mcai.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = mcai.MODID, value = Dist.CLIENT)
public class mcaiClient {
    public mcaiClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyBindings.register(event);
    }

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
