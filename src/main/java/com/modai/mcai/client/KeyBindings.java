package com.modai.mcai.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.modai.mcai.client.gui.AiChatScreen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String CATEGORY = "key.categories.mcai";

    public static final KeyMapping OPEN_AI_CHAT = new KeyMapping(
            "key.mcai.open_ai_chat",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_AI_CHAT);
    }

    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_AI_CHAT.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new AiChatScreen());
            }
        }
    }
}
