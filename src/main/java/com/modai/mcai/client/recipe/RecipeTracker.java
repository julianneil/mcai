package com.modai.mcai.client.recipe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public class RecipeTracker {
    private static final RecipeTracker INSTANCE = new RecipeTracker();
    private static final int MAX_TREE_DEPTH = 5;
    private static final int MAX_TRACKED_ITEMS = 128;

    private final Map<Item, HighlightInfo> highlightedItems = new HashMap<>();
    private Component targetName = Component.empty();
    private ItemStack targetStack = ItemStack.EMPTY;
    private Optional<RecipeTreeNode> recipeTree = Optional.empty();

    private RecipeTracker() {
    }

    public static RecipeTracker get() {
        return INSTANCE;
    }

    public boolean isTracking() {
        return !highlightedItems.isEmpty();
    }

    public Component targetName() {
        return targetName;
    }

    public Optional<RecipeTreeNode> recipeTree() {
        return recipeTree;
    }

    public ItemStack targetStack() {
        return targetStack.copy();
    }

    public Optional<HighlightInfo> highlightInfo(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(highlightedItems.get(stack.getItem()));
    }

    public int highlightedItemCount() {
        return highlightedItems.size();
    }

    public void clear() {
        highlightedItems.clear();
        targetName = Component.empty();
        targetStack = ItemStack.EMPTY;
        recipeTree = Optional.empty();
    }

    public TrackResult track(String query) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return TrackResult.failure(Component.literal("No world loaded."));
        }

        Optional<RecipeHolder<?>> recipe = findBestRecipe(level, query);
        if (recipe.isEmpty()) {
            clear();
            return TrackResult.failure(Component.literal("No loaded recipe found for: " + query));
        }

        highlightedItems.clear();
        RecipeHolder<?> holder = recipe.get();
        ItemStack result = holder.value().getResultItem(level.registryAccess());
        targetName = result.getHoverName();
        targetStack = result.copy();
        recipeTree = buildRecipeTree(level, holder, 0, new HashSet<>(), targetName);

        return TrackResult.success(Component.literal("Tracking recipe tree for ").withStyle(ChatFormatting.GREEN)
                .append(targetName.copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (" + highlightedItems.size() + " items highlighted)").withStyle(ChatFormatting.GREEN)));
    }

    private Optional<RecipeHolder<?>> findBestRecipe(ClientLevel level, String query) {
        List<String> terms = extractTerms(query);
        if (terms.isEmpty()) {
            return Optional.empty();
        }

        return level.getRecipeManager().getRecipes().stream()
                .filter(holder -> !holder.value().getResultItem(level.registryAccess()).isEmpty())
                .map(holder -> new RecipeScore(holder, scoreRecipe(level, holder, terms)))
                .filter(score -> score.score() > 0)
                .max(Comparator.comparingInt(RecipeScore::score))
                .map(RecipeScore::holder);
    }

    private int scoreRecipe(ClientLevel level, RecipeHolder<?> holder, List<String> terms) {
        ItemStack result = holder.value().getResultItem(level.registryAccess());
        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        String haystack = (holder.id() + " " + result.getHoverName().getString() + " " + resultId).toLowerCase(Locale.ROOT);

        int score = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                score += 5;
            }
            if (resultId.getPath().equals(term) || result.getHoverName().getString().equalsIgnoreCase(term)) {
                score += 10;
            }
        }
        return score;
    }

    private Optional<RecipeTreeNode> buildRecipeTree(ClientLevel level, RecipeHolder<?> rootRecipe, int depth, Set<Item> visitedOutputs, Component parentName) {
        if (depth > MAX_TREE_DEPTH || highlightedItems.size() >= MAX_TRACKED_ITEMS) {
            return Optional.empty();
        }

        Recipe<?> recipe = rootRecipe.value();
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return Optional.empty();
        }

        HighlightRole resultRole = depth == 0 ? HighlightRole.TARGET : HighlightRole.INTERMEDIATE;
        Component resultReason = depth == 0
                ? Component.literal("Target recipe output")
                : Component.literal("Crafted ingredient for ").append(parentName.copy());
        addHighlight(result.getItem(), resultRole, resultReason);

        boolean alreadyVisited = !visitedOutputs.add(result.getItem());
        if (alreadyVisited) {
            return Optional.of(new RecipeTreeNode(result.copy(), result.getHoverName(), resultRole, Component.literal("Already shown earlier in this recipe tree"), List.of()));
        }

        Component resultName = result.getHoverName();
        List<RecipeTreeNode> children = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            for (ItemStack option : relevantIngredientOptions(ingredient)) {
                if (option.isEmpty()) {
                    continue;
                }

                Optional<RecipeHolder<?>> childRecipe = findRecipeForItem(level, option.getItem());
                HighlightRole ingredientRole = childRecipe.isPresent() ? HighlightRole.INTERMEDIATE : HighlightRole.BASE;
                Component ingredientReason = childRecipe.isPresent()
                        ? Component.literal("Crafted ingredient for ").append(resultName.copy())
                        : Component.literal("Base ingredient for ").append(resultName.copy());
                addHighlight(option.getItem(), ingredientRole, ingredientReason);

                RecipeTreeNode childNode = childRecipe
                        .flatMap(child -> buildRecipeTree(level, child, depth + 1, visitedOutputs, resultName))
                        .orElseGet(() -> new RecipeTreeNode(option.copy(), option.getHoverName(), ingredientRole, ingredientReason, List.of()));
                children.add(childNode);

                if (highlightedItems.size() >= MAX_TRACKED_ITEMS) {
                    return Optional.of(new RecipeTreeNode(result.copy(), resultName, resultRole, resultReason, List.copyOf(children)));
                }
            }
        }

        return Optional.of(new RecipeTreeNode(result.copy(), resultName, resultRole, resultReason, List.copyOf(children)));
    }

    private void addHighlight(Item item, HighlightRole role, Component reason) {
        highlightedItems.merge(item, new HighlightInfo(role, reason), (existing, incoming) -> {
            if (incoming.role().priority() < existing.role().priority()) {
                return incoming;
            }
            return existing;
        });
    }

    private List<ItemStack> relevantIngredientOptions(Ingredient ingredient) {
        ItemStack[] options = ingredient.getItems();
        return Arrays.stream(options)
                .limit(4)
                .toList();
    }

    private Optional<RecipeHolder<?>> findRecipeForItem(ClientLevel level, Item item) {
        Queue<RecipeHolder<?>> candidates = new ArrayDeque<>(level.getRecipeManager().getRecipes());
        while (!candidates.isEmpty()) {
            RecipeHolder<?> candidate = candidates.poll();
            ItemStack result = candidate.value().getResultItem(level.registryAccess());
            if (!result.isEmpty() && result.is(item)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private List<String> extractTerms(String query) {
        Set<String> ignored = Set.of("track", "recipe", "tree", "for", "the", "and", "make", "craft", "how", "to", "a", "an");
        Set<String> uniqueTerms = new HashSet<>();
        for (String rawTerm : query.toLowerCase(Locale.ROOT).split("[^a-z0-9_:]+")) {
            String term = rawTerm.trim();
            if (term.length() < 2 || ignored.contains(term)) {
                continue;
            }
            uniqueTerms.add(term);
        }
        return uniqueTerms.stream().sorted().toList();
    }

    public enum HighlightRole {
        TARGET(0, ChatFormatting.AQUA),
        INTERMEDIATE(1, ChatFormatting.GOLD),
        BASE(2, ChatFormatting.GREEN);

        private final int priority;
        private final ChatFormatting color;

        HighlightRole(int priority, ChatFormatting color) {
            this.priority = priority;
            this.color = color;
        }

        public ChatFormatting color() {
            return color;
        }

        private int priority() {
            return priority;
        }
    }

    public record HighlightInfo(HighlightRole role, Component reason) {
    }

    public record RecipeTreeNode(ItemStack stack, Component name, HighlightRole role, Component reason, List<RecipeTreeNode> children) {
    }

    public record TrackResult(boolean success, Component message) {
        public static TrackResult success(Component message) {
            return new TrackResult(true, message);
        }

        public static TrackResult failure(Component message) {
            return new TrackResult(false, message.copy().withStyle(ChatFormatting.RED));
        }
    }

    private record RecipeScore(RecipeHolder<?> holder, int score) {
    }
}
