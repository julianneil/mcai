package com.modai.mcai.client.lookup;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.Locale;
import java.util.Optional;

public class PackLookupService {
    public Optional<String> describeItem(String query) {
        Item item = findBestItem(query);
        if (item == null) {
            return Optional.empty();
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return Optional.of("Item: " + item.getDescription().getString() + " (" + id + ")");
    }

    public Optional<String> describeBlock(String query) {
        Block block = findBestBlock(query);
        if (block == null) {
            return Optional.empty();
        }

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return Optional.of("Block: " + block.getName().getString() + " (" + id + ")");
    }

    public Optional<String> describeMod(String query) {
        IModInfo mod = findBestMod(query);
        if (mod == null) {
            return Optional.empty();
        }

        return Optional.of("Mod: " + mod.getDisplayName() + " (" + mod.getModId() + " " + mod.getVersion() + ")");
    }

    private Item findBestItem(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        Item best = null;
        int bestScore = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String haystack = (item.getDescription().getString() + " " + id).toLowerCase(Locale.ROOT);
            int score = scoreLookup(haystack, needle, id);
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return best;
    }

    private Block findBestBlock(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        Block best = null;
        int bestScore = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            String haystack = (block.getName().getString() + " " + id).toLowerCase(Locale.ROOT);
            int score = scoreLookup(haystack, needle, id);
            if (score > bestScore) {
                bestScore = score;
                best = block;
            }
        }
        return best;
    }

    private IModInfo findBestMod(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        IModInfo best = null;
        int bestScore = 0;
        for (IModInfo mod : ModList.get().getMods()) {
            String haystack = (mod.getDisplayName() + " " + mod.getModId() + " " + mod.getVersion()).toLowerCase(Locale.ROOT);
            int score = scoreLookup(haystack, needle, ResourceLocation.tryParse(mod.getModId()));
            if (score > bestScore) {
                bestScore = score;
                best = mod;
            }
        }
        return best;
    }

    private int scoreLookup(String haystack, String needle, ResourceLocation id) {
        if (needle.isBlank()) {
            return 0;
        }

        int score = 0;
        if (haystack.contains(needle)) {
            score += 10;
        }
        if (id != null) {
            if (id.toString().equals(needle)) {
                score += 20;
            }
            if (id.getPath().equals(needle)) {
                score += 15;
            }
            if (id.getNamespace().equals(needle)) {
                score += 12;
            }
        }
        return score;
    }
}
