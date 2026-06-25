package com.modai.mcai.client.recipe;

import com.modai.mcai.client.recipe.RecipeTracker.HighlightInfo;
import com.modai.mcai.client.recipe.RecipeTracker.HighlightRole;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.event.ContainerScreenEvent;

import java.util.List;
import java.util.Optional;

public class RecipeHighlightRenderer {
    private static final int TARGET_HIGHLIGHT_COLOR = 0x8040DFFF;
    private static final int TARGET_BORDER_COLOR = 0xFF8EFFFF;
    private static final int INTERMEDIATE_HIGHLIGHT_COLOR = 0x80FFD21F;
    private static final int INTERMEDIATE_BORDER_COLOR = 0xFFFFF27A;
    private static final int BASE_HIGHLIGHT_COLOR = 0x8055E66D;
    private static final int BASE_BORDER_COLOR = 0xFFA8FFB3;

    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        RecipeTracker tracker = RecipeTracker.get();
        if (!tracker.isTracking()) {
            return;
        }

        AbstractContainerScreen<?> screen = event.getContainerScreen();
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Slot hoveredHighlightedSlot = null;
        HighlightInfo hoveredHighlight = null;

        for (Slot slot : screen.getMenu().slots) {
            Optional<HighlightInfo> highlightInfo = tracker.highlightInfo(slot.getItem());
            if (highlightInfo.isEmpty()) {
                continue;
            }

            HighlightInfo info = highlightInfo.get();
            renderSlotHighlight(guiGraphics, slot.x, slot.y, info.role());
            if (isMouseOverSlot(slot, event.getMouseX(), event.getMouseY())) {
                hoveredHighlightedSlot = slot;
                hoveredHighlight = info;
            }
        }

        Component label = Component.literal("MCAI tracking: ")
                .append(tracker.targetName().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (" + tracker.highlightedItemCount() + " items)"));
        guiGraphics.drawString(Minecraft.getInstance().font, label, 0, -12, 0xFFFFF27A, true);

        if (hoveredHighlightedSlot != null) {
            renderHighlightTooltip(guiGraphics, screen, hoveredHighlightedSlot, hoveredHighlight, event.getMouseX(), event.getMouseY());
        }
    }

    private static void renderSlotHighlight(GuiGraphics guiGraphics, int x, int y, HighlightRole role) {
        int highlightColor = switch (role) {
            case TARGET -> TARGET_HIGHLIGHT_COLOR;
            case INTERMEDIATE -> INTERMEDIATE_HIGHLIGHT_COLOR;
            case BASE -> BASE_HIGHLIGHT_COLOR;
        };
        int borderColor = switch (role) {
            case TARGET -> TARGET_BORDER_COLOR;
            case INTERMEDIATE -> INTERMEDIATE_BORDER_COLOR;
            case BASE -> BASE_BORDER_COLOR;
        };

        guiGraphics.fill(x, y, x + 16, y + 16, highlightColor);
        guiGraphics.fill(x, y, x + 16, y + 1, borderColor);
        guiGraphics.fill(x, y + 15, x + 16, y + 16, borderColor);
        guiGraphics.fill(x, y, x + 1, y + 16, borderColor);
        guiGraphics.fill(x + 15, y, x + 16, y + 16, borderColor);
    }

    private static boolean isMouseOverSlot(Slot slot, int mouseX, int mouseY) {
        return mouseX >= slot.x && mouseX < slot.x + 16 && mouseY >= slot.y && mouseY < slot.y + 16;
    }

    private static void renderHighlightTooltip(GuiGraphics guiGraphics, AbstractContainerScreen<?> screen, Slot slot, HighlightInfo info, int mouseX, int mouseY) {
        List<Component> tooltip = List.of(
                slot.getItem().getHoverName(),
                Component.literal("MCAI: ").withStyle(ChatFormatting.GRAY)
                        .append(roleLabel(info.role()).withStyle(info.role().color())),
                info.reason().copy().withStyle(ChatFormatting.DARK_GRAY));
        guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
    }

    private static MutableComponent roleLabel(HighlightRole role) {
        return switch (role) {
            case TARGET -> Component.literal("Target output");
            case INTERMEDIATE -> Component.literal("Crafted ingredient");
            case BASE -> Component.literal("Base ingredient");
        };
    }
}
