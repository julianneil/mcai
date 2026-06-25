package com.modai.mcai.client.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class JeiRecipeBridge {
    private static final String JEI_INTERNAL_CLASS = "mezz.jei.common.Internal";
    private static final String JEI_VANILLA_TYPES_CLASS = "mezz.jei.api.constants.VanillaTypes";
    private static final String JEI_RECIPE_ROLE_CLASS = "mezz.jei.api.recipe.RecipeIngredientRole";

    private JeiRecipeBridge() {
    }

    public static boolean isAvailable() {
        return runtime().isPresent();
    }

    public static OpenResult showRecipesFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return OpenResult.failure(Component.literal("No item selected for JEI lookup."));
        }

        Optional<Object> runtime = runtime();
        if (runtime.isEmpty()) {
            return OpenResult.failure(Component.literal("JEI is not loaded or is not ready yet."));
        }

        try {
            Object jeiRuntime = runtime.get();
            Object helpers = invoke(jeiRuntime, "getJeiHelpers");
            Object focusFactory = invoke(helpers, "getFocusFactory");
            Object recipesGui = invoke(jeiRuntime, "getRecipesGui");
            Object itemStackType = staticField(JEI_VANILLA_TYPES_CLASS, "ITEM_STACK");
            Object outputRole = enumConstant(JEI_RECIPE_ROLE_CLASS, "OUTPUT");
            Object focus = invoke(focusFactory, "createFocus", outputRole, itemStackType, stack.copy());

            Minecraft.getInstance().execute(() -> invokeUnchecked(recipesGui, "show", List.of(focus)));
            return OpenResult.success(Component.literal("Opened JEI recipes for ").append(stack.getHoverName()));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return OpenResult.failure(Component.literal("Could not open JEI recipes: " + errorText(error)));
        }
    }

    public static OpenResult showUsesFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return OpenResult.failure(Component.literal("No item selected for JEI lookup."));
        }

        Optional<Object> runtime = runtime();
        if (runtime.isEmpty()) {
            return OpenResult.failure(Component.literal("JEI is not loaded or is not ready yet."));
        }

        try {
            Object jeiRuntime = runtime.get();
            Object helpers = invoke(jeiRuntime, "getJeiHelpers");
            Object focusFactory = invoke(helpers, "getFocusFactory");
            Object recipesGui = invoke(jeiRuntime, "getRecipesGui");
            Object itemStackType = staticField(JEI_VANILLA_TYPES_CLASS, "ITEM_STACK");
            Object inputRole = enumConstant(JEI_RECIPE_ROLE_CLASS, "INPUT");
            Object focus = invoke(focusFactory, "createFocus", inputRole, itemStackType, stack.copy());

            Minecraft.getInstance().execute(() -> invokeUnchecked(recipesGui, "show", List.of(focus)));
            return OpenResult.success(Component.literal("Opened JEI uses for ").append(stack.getHoverName()));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return OpenResult.failure(Component.literal("Could not open JEI uses: " + errorText(error)));
        }
    }

    private static Optional<Object> runtime() {
        try {
            Class<?> internal = Class.forName(JEI_INTERNAL_CLASS);
            Method getRuntime = internal.getMethod("getJeiRuntime");
            return Optional.ofNullable(getRuntime.invoke(null));
        } catch (ReflectiveOperationException | LinkageError error) {
            return Optional.empty();
        }
    }

    private static Object staticField(String className, String fieldName) throws ReflectiveOperationException {
        Class<?> type = Class.forName(className);
        Field field = type.getField(fieldName);
        return field.get(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(String className, String constantName) throws ReflectiveOperationException {
        Class<? extends Enum> enumType = Class.forName(className).asSubclass(Enum.class);
        return Enum.valueOf(enumType, constantName);
    }

    private static Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, args.length);
        return method.invoke(target, args);
    }

    private static void invokeUnchecked(Object target, String methodName, Object... args) {
        try {
            invoke(target, methodName, args);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Method findMethod(Class<?> type, String methodName, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + methodName + " with " + parameterCount + " parameters");
    }

    private static String errorText(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    public record OpenResult(boolean success, Component message) {
        public static OpenResult success(Component message) {
            return new OpenResult(true, message);
        }

        public static OpenResult failure(Component message) {
            return new OpenResult(false, message);
        }
    }
}
