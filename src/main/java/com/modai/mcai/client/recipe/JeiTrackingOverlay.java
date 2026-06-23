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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class JeiTrackingOverlay {
    private static final String JEI_RECIPES_GUI_CLASS = "mezz.jei.gui.recipes.RecipesGui";
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
            renderStatus(guiGraphics, "MCAI: JEI recipe detected, but no trackable output slot was found", 8, 8, 0xFFFF7777);
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
            List<?> layoutsWithButtons = visibleRecipeLayouts(screen);
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
        Optional<ItemStack> recipeResult = recipeResultStack(recipeLayout);
        if (recipeResult.isPresent()) {
            return recipeResult;
        }

        Object slotsView = invoke(recipeLayout, "getRecipeSlotsView");
        List<?> slotViews = (List<?>) invoke(slotsView, "getSlotViews");
        List<CandidateStack> candidates = new ArrayList<>();
        int order = 0;
        for (Object slotView : slotViews) {
            Object role = invoke(slotView, "getRole");
            Optional<ItemStack> stack = displayedStack(slotView);
            if (stack.isEmpty()) {
                stack = firstItemStack(slotView);
            }
            if (stack.isEmpty()) {
                continue;
            }

            String roleName = roleName(role);
            int priority = isOutputRole(roleName) ? 0 : 1;
            candidates.add(new CandidateStack(stack.get(), priority, order++));
        }

        Optional<ItemStack> bestSlotStack = candidates.stream()
                .min(Comparator.comparingInt(CandidateStack::priority).thenComparing(Comparator.comparingInt(CandidateStack::order).reversed()))
                .map(CandidateStack::stack);
        return bestSlotStack;
    }

    private static Optional<ItemStack> recipeResultStack(Object recipeLayout) {
        try {
            Object recipe = invoke(recipeLayout, "getRecipe");
            Optional<ItemStack> result = invokeRecipeResult(recipe);
            if (result.isPresent()) {
                return result;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to empty.
        }
        return Optional.empty();
    }

    private static Optional<ItemStack> invokeRecipeResult(Object recipe) throws ReflectiveOperationException {
        try {
            Object result = invoke(recipe, "getResultItem", Minecraft.getInstance().level.registryAccess());
            if (result instanceof ItemStack stack && !stack.isEmpty()) {
                return Optional.of(stack.copy());
            }
        } catch (ReflectiveOperationException | NullPointerException ignored) {
            // Try other signatures below.
        }

        try {
            Object result = invoke(recipe, "getResultItem");
            if (result instanceof ItemStack stack && !stack.isEmpty()) {
                return Optional.of(stack.copy());
            }
        } catch (ReflectiveOperationException ignored) {
            // No-op.
        }

        return Optional.empty();
    }

    private static Optional<ItemStack> displayedStack(Object slotView) throws ReflectiveOperationException {
        try {
            Object displayedStack = invoke(slotView, "getDisplayedItemStack");
            if (displayedStack instanceof Optional<?> optional && optional.isPresent() && optional.get() instanceof ItemStack stack && !stack.isEmpty()) {
                return Optional.of(stack.copy());
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to other accessors.
        }
        try {
            Object displayedStack = invoke(slotView, "getDisplayedStack");
            if (displayedStack instanceof Optional<?> optional && optional.isPresent() && optional.get() instanceof ItemStack stack && !stack.isEmpty()) {
                return Optional.of(stack.copy());
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to other accessors.
        }
        return Optional.empty();
    }

    private static Optional<ItemStack> firstItemStack(Object slotView) throws ReflectiveOperationException {
        try {
            Object stream = invoke(slotView, "getItemStacks");
            Object optional = invoke(stream, "findFirst");
            if (optional instanceof Optional<?> first && first.isPresent() && first.get() instanceof ItemStack stack && !stack.isEmpty()) {
                return Optional.of(stack.copy());
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to other accessors.
        }
        try {
            Object stacks = invoke(slotView, "getItemStacksWithFallback");
            Object optional = invoke(stacks, "findFirst");
            if (optional instanceof Optional<?> first && first.isPresent() && first.get() instanceof ItemStack stack && !stack.isEmpty()) {
                return Optional.of(stack.copy());
            }
        } catch (ReflectiveOperationException ignored) {
            // No-op.
        }
        return Optional.empty();
    }

    private static boolean isOutputRole(String roleName) {
        return switch (roleName) {
            case "OUTPUT", "OUTPUT_SLOT", "RESULT", "RESULT_SLOT" -> true;
            default -> false;
        };
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

    private static List<?> visibleRecipeLayouts(Screen screen) throws ReflectiveOperationException {
        try {
            Object logic = fieldValue(screen, "logic");
            Object area = recipeLayoutsArea(screen);
            int recipeLayoutsAreaHeight = ((Number) invoke(area, "getHeight")).intValue();
            Object menu = invoke(screen, "getParentContainerMenu");
            Object bookmarks = fieldValue(screen, "bookmarks");
            return List.copyOf((List<?>) invoke(logic, "getVisibleRecipeLayoutsWithButtons", recipeLayoutsAreaHeight, 4, menu, bookmarks, screen));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            Object layouts = fieldValue(screen, "layouts");
            return List.copyOf((List<?>) fieldValue(layouts, "recipeLayoutsWithButtons"));
        }
    }

    private static Object recipeLayoutsArea(Screen screen) throws ReflectiveOperationException {
        Object area = fieldValue(screen, "area");
        int x = ((Number) invoke(area, "getX")).intValue() + 6;
        int y = ((Number) invoke(area, "getY")).intValue() + ((Number) fieldValue(screen, "headerHeight")).intValue() + 2;
        int width = ((Number) invoke(area, "getWidth")).intValue() - 12;
        int height = ((Number) invoke(area, "getHeight")).intValue() - ((Number) fieldValue(screen, "headerHeight")).intValue() - 8;
        return new net.minecraft.client.renderer.Rect2i(x, y, width, height);
    }

    private record CandidateStack(ItemStack stack, int priority, int order) {
    }
}
