package com.modai.mcai.client.context;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModpackContextProvider {
    private static final int MAX_MODS = 80;
    private static final int MAX_SUMMARY_MODS = 20;
    private static final int MAX_NAMESPACES = 40;

    public String buildContext() {
        StringBuilder context = new StringBuilder();
        context.append("Loaded modpack context:\n");
        appendLoadedMods(context);
        appendRegistrySummary(context, "Item namespaces", BuiltInRegistries.ITEM);
        appendRegistrySummary(context, "Block namespaces", BuiltInRegistries.BLOCK);
        appendRegistrySummary(context, "Entity namespaces", BuiltInRegistries.ENTITY_TYPE);
        return context.toString().trim();
    }

    public String buildSummary() {
        StringBuilder context = new StringBuilder();
        context.append("Modpack summary:\n");
        List<IModInfo> mods = ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .toList();

        context.append("Loaded mods: ").append(mods.size()).append('\n');
        if (!mods.isEmpty()) {
            context.append("Key mods: ");
            int limit = Math.min(mods.size(), MAX_SUMMARY_MODS);
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    context.append(", ");
                }
                context.append(mods.get(i).getModId());
            }
            if (mods.size() > limit) {
                context.append(", ...");
            }
            context.append('\n');
        }

        appendRegistrySummaryCompact(context, "Item namespaces", BuiltInRegistries.ITEM);
        appendRegistrySummaryCompact(context, "Block namespaces", BuiltInRegistries.BLOCK);
        return context.toString().trim();
    }

    private void appendLoadedMods(StringBuilder context) {
        List<IModInfo> mods = ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .toList();

        context.append("Loaded mods: ").append(mods.size()).append('\n');
        int limit = Math.min(mods.size(), MAX_MODS);
        for (int i = 0; i < limit; i++) {
            IModInfo mod = mods.get(i);
            context.append("- ")
                    .append(mod.getModId())
                    .append(" = ")
                    .append(mod.getDisplayName())
                    .append(" ")
                    .append(mod.getVersion())
                    .append('\n');
        }

        if (mods.size() > limit) {
            context.append("- ... plus ").append(mods.size() - limit).append(" more mods\n");
        }
    }

    private <T> void appendRegistrySummary(StringBuilder context, String label, Registry<T> registry) {
        Map<String, Integer> namespaceCounts = new HashMap<>();
        for (ResourceLocation key : registry.keySet()) {
            namespaceCounts.merge(key.getNamespace(), 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(namespaceCounts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));

        context.append(label).append(":\n");
        int limit = Math.min(entries.size(), MAX_NAMESPACES);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            context.append("- ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append('\n');
        }

        if (entries.size() > limit) {
            context.append("- ... plus ").append(entries.size() - limit).append(" more namespaces\n");
        }
    }

    private <T> void appendRegistrySummaryCompact(StringBuilder context, String label, Registry<T> registry) {
        Map<String, Integer> namespaceCounts = new HashMap<>();
        for (ResourceLocation key : registry.keySet()) {
            namespaceCounts.merge(key.getNamespace(), 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(namespaceCounts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));

        context.append(label).append(": ");
        int limit = Math.min(entries.size(), 8);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                context.append(", ");
            }
            Map.Entry<String, Integer> entry = entries.get(i);
            context.append(entry.getKey()).append("=").append(entry.getValue());
        }
        if (entries.size() > limit) {
            context.append(", ...");
        }
        context.append('\n');
    }
}
