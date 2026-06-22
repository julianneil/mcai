package com.modai.mcai.client.recipe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JeiTrackingOverlay {
    private static final String JEI_RECIPES_GUI_CLASS = "mezz.jei.gui.recipes.RecipesGui";
    private static final String JEI_OUTPUT_ROLE_NAME = "OUTPUT";
    private static final int BUTTON_WIDTH = 44;
    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_GAP = 4;

    private JeiTrackingOverlay() {
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!isJeiRecipeScreen(event.getScreen())) {
            return;
        }

        List<TrackButton> buttons = visibleTrackButtons(event.getScreen());
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        if (buttons.isEmpty()) {
            renderStatus(guiGraphics, "MCAI: JEI recipe detected, no output found", 8, 8, 0xFFFF7777);
            return;
        }

        for (TrackButton button : buttons) {
            renderButton(guiGraphics, button, button.contains(mouseX, mouseY));
        }

        buttons.stream()
                .filter(button -> button.contains(mouseX, mouseY))
                .findFirst()
                .ifPresent(button -> guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, List.of(
                        Component.literal("Track recipe with MCAI").withStyle(ChatFormatting.YELLOW),
                        Component.literal("Target: ").withStyle(ChatFormatting.GRAY).append(button.stack().getHoverName())), mouseX, mouseY));
    }

    public static void onMouseButtonPressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !isJeiRecipeScreen(event.getScreen())) {
            return;
        }

        visibleTrackButtons(event.getScreen()).stream()
                .filter(button -> button.contains(event.getMouseX(), event.getMouseY()))
                .findFirst()
                .ifPresent(button -> {
                    track(button.stack());
                    event.setCanceled(true);
                });
    }

    private static boolean isJeiRecipeScreen(Screen screen) {
        Class<?> type = screen.getClass();
        while (type != null) {
            if (JEI_RECIPES_GUI_CLASS.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static List<TrackButton> visibleTrackButtons(Screen screen) {
        try {
            Object layouts = fieldValue(screen, "layouts");
            List<?> layoutsWithButtons = List.copyOf((List<?>) fieldValue(layouts, "recipeLayoutsWithButtons"));
            List<TrackButton> buttons = new ArrayList<>();
            for (Object layoutWithButtons : layoutsWithButtons) {
                Object recipeLayout = invoke(layoutWithButtons, "getRecipeLayout");
                Optional<ItemStack> output = outputStack(recipeLayout);
                if (output.isEmpty()) {
                    continue;
                }

                Object rect = invoke(recipeLayout, "getRectWithBorder");
                int x = ((Number) invoke(rect, "getX")).intValue() + ((Number) invoke(rect, "getWidth")).intValue() + BUTTON_GAP;
                int y = ((Number) invoke(rect, "getY")).intValue();
                buttons.add(new TrackButton(x, y, output.get()));
            }
            return buttons;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return List.of();
        }
    }

    private static Optional<ItemStack> outputStack(Object recipeLayout) throws ReflectiveOperationException {
        Object slotsView = invoke(recipeLayout, "getRecipeSlotsView");
        List<?> slotViews = (List<?>) invoke(slotsView, "getSlotViews");
        for (Object slotView : slotViews) {
            Object role = invoke(slotView, "getRole");
            if (!JEI_OUTPUT_ROLE_NAME.equals(roleName(role))) {
                continue;
            }

            Optional<?> displayedStack = (Optional<?>) invoke(slotView, "getDisplayedItemStack");
            if (displayedStack.isPresent() && displayedStack.get() instanceof ItemStack stack && !stack.isEmpty()) {
                return Optional.of(stack.copy());
            }

            Optional<ItemStack> firstStack = firstItemStack(slotView);
            if (firstStack.isPresent()) {
                return firstStack;
            }
        }
        return Optional.empty();
    }

    private static Optional<ItemStack> firstItemStack(Object slotView) throws ReflectiveOperationException {
        Object stream = invoke(slotView, "getItemStacks");
        Object optional = invoke(stream, "findFirst");
        if (optional instanceof Optional<?> first && first.isPresent() && first.get() instanceof ItemStack stack && !stack.isEmpty()) {
            return Optional.of(stack.copy());
        }
        return Optional.empty();
    }

    private static String roleName(Object role) {
        try {
            Object name = invoke(role, "name");
            return String.valueOf(name);
        } catch (ReflectiveOperationException error) {
            return String.valueOf(role);
        }
    }

    private static void renderButton(GuiGraphics guiGraphics, TrackButton button, boolean hovered) {
        int background = hovered ? 0xFFE2D68B : 0xFFC6C6C6;
        int border = hovered ? 0xFFFFF27A : 0xFF555555;
        guiGraphics.fill(button.x(), button.y(), button.x() + BUTTON_WIDTH, button.y() + BUTTON_HEIGHT, background);
        guiGraphics.renderOutline(button.x(), button.y(), BUTTON_WIDTH, BUTTON_HEIGHT, border);
        guiGraphics.drawString(Minecraft.getInstance().font, "Track", button.x() + 7, button.y() + 3, 0xFF202020, false);
    }

    private static void renderStatus(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.fill(x - 3, y - 3, x + Minecraft.getInstance().font.width(text) + 3, y + 11, 0xCC111111);
        guiGraphics.drawString(Minecraft.getInstance().font, text, x, y, color, false);
    }

    private static void track(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        RecipeTracker.TrackResult result = RecipeTracker.get().track(stack.getHoverName().getString());
        minecraft.player.displayClientMessage(Component.literal("MCAI: ").withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(result.message()), false);
    }

    private static Object fieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + fieldName);
    }

    private static Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, args.length);
        return method.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String methodName, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + methodName + " with " + parameterCount + " parameters");
    }

    private record TrackButton(int x, int y, ItemStack stack) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        }
    }
}
