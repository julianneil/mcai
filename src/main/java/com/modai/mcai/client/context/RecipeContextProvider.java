package com.modai.mcai.client.context;

import com.modai.mcai.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RecipeContextProvider {
    public String buildContext(String userMessage) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return "Recipe context unavailable: no world loaded.";
        }

        List<String> queryTerms = extractQueryTerms(userMessage);
        if (queryTerms.isEmpty()) {
            return "Recipe context unavailable: no useful recipe search terms found.";
        }

        List<RecipeMatch> matches = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) {
                continue;
            }

            int score = scoreRecipe(holder, recipe, result, queryTerms);
            if (score > 0) {
                matches.add(new RecipeMatch(holder, recipe, result, score));
            }
        }

        if (matches.isEmpty()) {
            return "Relevant loaded recipe context: no matching recipes found for the latest question.";
        }

        matches.sort(Comparator.comparingInt(RecipeMatch::score).reversed()
                .thenComparing(match -> match.holder().id().toString()));

        StringBuilder context = new StringBuilder();
        context.append("Relevant loaded recipe context from this modpack:\n");
        int limit = Math.min(matches.size(), Config.MAX_RECIPE_CONTEXT_RESULTS.getAsInt());
        for (int i = 0; i < limit; i++) {
            appendRecipe(context, matches.get(i));
        }

        if (matches.size() > limit) {
            context.append("- ... plus ").append(matches.size() - limit).append(" more matching recipes\n");
        }

        return context.toString().trim();
    }

    private void appendRecipe(StringBuilder context, RecipeMatch match) {
        RecipeHolder<?> holder = match.holder();
        Recipe<?> recipe = match.recipe();
        ItemStack result = match.result();
        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());

        context.append("- Recipe ").append(holder.id()).append('\n');
        context.append("  Type: ").append(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())).append('\n');
        context.append("  Output: ").append(result.getCount()).append("x ")
                .append(result.getHoverName().getString())
                .append(" (").append(resultId).append(")\n");
        context.append("  Ingredients: ").append(ingredientSummary(recipe)).append('\n');
    }

    private String ingredientSummary(Recipe<?> recipe) {
        List<String> ingredientLines = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length == 0) {
                continue;
            }

            List<String> options = new ArrayList<>();
            for (int i = 0; i < Math.min(stacks.length, 4); i++) {
                ItemStack stack = stacks[i];
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                options.add(stack.getHoverName().getString() + " (" + itemId + ")");
            }

            if (stacks.length > 4) {
                options.add("... " + (stacks.length - 4) + " more options");
            }
            ingredientLines.add(String.join(" OR ", options));
        }

        if (ingredientLines.isEmpty()) {
            return "No listed ingredients";
        }
        return String.join("; ", ingredientLines);
    }

    private int scoreRecipe(RecipeHolder<?> holder, Recipe<?> recipe, ItemStack result, List<String> queryTerms) {
        String recipeId = holder.id().toString().toLowerCase(Locale.ROOT);
        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        String resultText = (result.getHoverName().getString() + " " + resultId).toLowerCase(Locale.ROOT);
        String ingredientText = ingredientSearchText(recipe);

        int score = 0;
        for (String term : queryTerms) {
            if (resultText.contains(term)) {
                score += 5;
            }
            if (recipeId.contains(term)) {
                score += 3;
            }
            if (ingredientText.contains(term)) {
                score += 1;
            }
        }
        return score;
    }

    private String ingredientSearchText(Recipe<?> recipe) {
        StringBuilder text = new StringBuilder();
        for (Ingredient ingredient : recipe.getIngredients()) {
            for (ItemStack stack : ingredient.getItems()) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                text.append(stack.getHoverName().getString()).append(' ').append(itemId).append(' ');
            }
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private List<String> extractQueryTerms(String userMessage) {
        Set<String> ignored = Set.of("what", "with", "from", "into", "make", "craft", "recipe", "recipes", "using", "need", "does", "this", "that", "have", "current", "inventory", "how", "can", "get", "for", "the", "and");
        Set<String> uniqueTerms = new HashSet<>();
        String[] rawTerms = userMessage.toLowerCase(Locale.ROOT).split("[^a-z0-9_:]+");

        for (String rawTerm : rawTerms) {
            String term = rawTerm.trim();
            if (term.length() < 3 || ignored.contains(term)) {
                continue;
            }
            uniqueTerms.add(term);
        }

        return uniqueTerms.stream().sorted().toList();
    }

    private record RecipeMatch(RecipeHolder<?> holder, Recipe<?> recipe, ItemStack result, int score) {
    }
}
