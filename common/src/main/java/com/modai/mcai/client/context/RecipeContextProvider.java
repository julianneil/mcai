package com.modai.mcai.client.context;

import com.modai.mcai.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        Map<String, String> modNamesById = loadedModNames();
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) {
                continue;
            }

            int score = scoreRecipe(recipe, result, queryTerms, modNamesById);
            if (score > 0) {
                ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
                String modName = modNamesById.getOrDefault(resultId.getNamespace(), resultId.getNamespace());
                matches.add(new RecipeMatch(recipe, result, score, modName));
            }
        }

        if (matches.isEmpty()) {
            return "Relevant loaded recipe context: no matching recipes found for the latest question.";
        }

        matches.sort(Comparator.comparingInt(RecipeMatch::score).reversed()
                .thenComparing(match -> match.recipe().getId().toString()));

        List<RecipeGroup> groups = groupMatches(matches);

        StringBuilder context = new StringBuilder();
        context.append("Relevant loaded recipe context from this modpack:\n");
        appendMatchedModSummary(context, groups);

        int limit = Math.min(groups.size(), Config.MAX_RECIPE_CONTEXT_RESULTS.get());
        for (int i = 0; i < limit; i++) {
            appendGroup(context, groups.get(i));
        }

        if (groups.size() > limit) {
            context.append("- ... plus ").append(groups.size() - limit).append(" more matching recipe groups\n");
        }

        return context.toString().trim();
    }

    private void appendMatchedModSummary(StringBuilder context, List<RecipeGroup> groups) {
        Map<String, Integer> modCounts = new LinkedHashMap<>();
        for (RecipeGroup group : groups) {
            modCounts.merge(group.modName(), 1, Integer::sum);
        }

        if (modCounts.isEmpty()) {
            return;
        }

        context.append("Matched mod sources:\n");
        int shown = 0;
        for (Map.Entry<String, Integer> entry : modCounts.entrySet()) {
            context.append("- ")
                    .append(entry.getKey())
                    .append(" (")
                    .append(entry.getValue())
                    .append(")\n");
            shown++;
            if (shown >= 6) {
                break;
            }
        }
    }

    private void appendGroup(StringBuilder context, RecipeGroup group) {
        RecipeMatch primary = group.primary();
        Recipe<?> recipe = primary.recipe();
        ItemStack result = primary.result();
        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());

        context.append("- Output: ").append(result.getHoverName().getString())
                .append(" (").append(resultId).append(")\n");
        context.append("  Mod: ").append(group.modName())
                .append(" | Namespace: ").append(resultId.getNamespace()).append('\n');
        context.append("  Best recipe: ").append(recipe.getId()).append('\n');
        context.append("  Type: ").append(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())).append('\n');
        context.append("  Ingredients: ").append(ingredientSummary(recipe)).append('\n');

        if (group.variants().size() > 1) {
            context.append("  Other variants: ");
            int shown = 0;
            for (int i = 1; i < group.variants().size() && shown < 3; i++) {
                RecipeMatch variant = group.variants().get(i);
                context.append(variant.recipe().getId()).append(" (")
                        .append(BuiltInRegistries.RECIPE_TYPE.getKey(variant.recipe().getType()))
                        .append(")");
                shown++;
                if (shown < Math.min(3, group.variants().size() - 1)) {
                    context.append("; ");
                }
            }
            context.append('\n');
        }
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

    private int scoreRecipe(Recipe<?> recipe, ItemStack result, List<String> queryTerms, Map<String, String> modNamesById) {
        String recipeId = recipe.getId().toString().toLowerCase(Locale.ROOT);
        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        String modName = modNamesById.getOrDefault(resultId.getNamespace(), resultId.getNamespace());
        String resultText = (result.getHoverName().getString() + " " + resultId + " " + modName).toLowerCase(Locale.ROOT);
        String ingredientText = ingredientSearchText(recipe);

        int score = 0;
        for (String term : queryTerms) {
            if (resultText.contains(term)) {
                score += 5;
            }
            if (recipeId.contains(term)) {
                score += 3;
            }
            if (modName.toLowerCase(Locale.ROOT).contains(term) || resultId.getNamespace().contains(term)) {
                score += 4;
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

    private Map<String, String> loadedModNames() {
        Map<String, String> modNames = new LinkedHashMap<>();
        for (IModInfo mod : ModList.get().getMods()) {
            modNames.put(mod.getModId(), mod.getDisplayName());
        }
        return modNames;
    }

    private List<RecipeGroup> groupMatches(List<RecipeMatch> matches) {
        Map<ResourceLocation, List<RecipeMatch>> grouped = new LinkedHashMap<>();
        for (RecipeMatch match : matches) {
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(match.result().getItem());
            grouped.computeIfAbsent(outputId, ignored -> new ArrayList<>()).add(match);
        }

        List<RecipeGroup> groups = new ArrayList<>();
        for (List<RecipeMatch> variants : grouped.values()) {
            variants.sort(Comparator.comparingInt(RecipeMatch::score).reversed()
                    .thenComparing(match -> match.recipe().getId().toString()));
            RecipeMatch primary = variants.get(0);
            groups.add(new RecipeGroup(primary.modName(), primary, List.copyOf(variants)));
        }

        groups.sort(Comparator.comparingInt((RecipeGroup group) -> group.primary().score()).reversed()
                .thenComparing(group -> group.primary().recipe().getId().toString()));
        return groups;
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

    private record RecipeMatch(Recipe<?> recipe, ItemStack result, int score, String modName) {
    }

    private record RecipeGroup(String modName, RecipeMatch primary, List<RecipeMatch> variants) {
    }
}
