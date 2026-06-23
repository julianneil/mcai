package com.modai.mcai.client.context;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InventoryContextProvider {
    private static final int MAX_SUMMARY_LINES = 80;
    private static final int MAX_COMPACT_LINES = 12;

    public String buildContext() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return "Inventory context unavailable: no player loaded.";
        }

        StringBuilder context = new StringBuilder();
        context.append("Current player inventory context:\n");
        appendStack(context, "Main hand", player.getMainHandItem());
        appendStack(context, "Off hand", player.getOffhandItem());
        appendArmor(context, player);
        appendHotbar(context, player);
        appendInventorySummary(context, player);
        return context.toString().trim();
    }

    public String buildSummary() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return "Inventory context unavailable: no player loaded.";
        }

        StringBuilder context = new StringBuilder();
        context.append("Inventory summary:\n");
        appendStack(context, "Main hand", player.getMainHandItem());
        appendStack(context, "Off hand", player.getOffhandItem());
        appendArmorCompact(context, player);
        appendHotbarCompact(context, player);
        appendInventoryTopItems(context, player);
        return context.toString().trim();
    }

    private void appendArmor(StringBuilder context, LocalPlayer player) {
        context.append("Armor:\n");
        appendStack(context, "- Head", player.getItemBySlot(EquipmentSlot.HEAD));
        appendStack(context, "- Chest", player.getItemBySlot(EquipmentSlot.CHEST));
        appendStack(context, "- Legs", player.getItemBySlot(EquipmentSlot.LEGS));
        appendStack(context, "- Feet", player.getItemBySlot(EquipmentSlot.FEET));
    }

    private void appendHotbar(StringBuilder context, LocalPlayer player) {
        context.append("Hotbar:\n");
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            appendStack(context, "- Slot " + (slot + 1), stack);
        }
    }

    private void appendArmorCompact(StringBuilder context, LocalPlayer player) {
        context.append("Armor: ");
        context.append(stackSummary(player.getItemBySlot(EquipmentSlot.HEAD)));
        context.append(", ");
        context.append(stackSummary(player.getItemBySlot(EquipmentSlot.CHEST)));
        context.append(", ");
        context.append(stackSummary(player.getItemBySlot(EquipmentSlot.LEGS)));
        context.append(", ");
        context.append(stackSummary(player.getItemBySlot(EquipmentSlot.FEET)));
        context.append('\n');
    }

    private void appendHotbarCompact(StringBuilder context, LocalPlayer player) {
        List<String> entries = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            entries.add((slot + 1) + ":" + stackSummary(stack));
        }

        context.append("Hotbar: ");
        if (entries.isEmpty()) {
            context.append("empty\n");
            return;
        }
        context.append(String.join(", ", entries)).append('\n');
    }

    private void appendInventoryTopItems(StringBuilder context, LocalPlayer player) {
        Map<String, Integer> itemCounts = new LinkedHashMap<>();
        int totalItems = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                continue;
            }

            String key = stackName(stack);
            itemCounts.merge(key, stack.getCount(), Integer::sum);
            totalItems += stack.getCount();
        }

        context.append("Inventory: ").append(itemCounts.size()).append(" item types, ").append(totalItems).append(" total items\n");
        if (itemCounts.isEmpty()) {
            context.append("- Empty\n");
            return;
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(itemCounts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));

        int limit = Math.min(entries.size(), MAX_COMPACT_LINES);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            context.append("- ").append(entry.getValue()).append("x ").append(entry.getKey()).append('\n');
        }

        if (entries.size() > limit) {
            context.append("- ... plus ").append(entries.size() - limit).append(" more item types\n");
        }
    }

    private void appendInventorySummary(StringBuilder context, LocalPlayer player) {
        Map<String, Integer> itemCounts = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                continue;
            }

            String key = stackName(stack);
            itemCounts.merge(key, stack.getCount(), Integer::sum);
        }

        context.append("Inventory item counts:\n");
        if (itemCounts.isEmpty()) {
            context.append("- Empty\n");
            return;
        }

        List<String> lines = new ArrayList<>();
        itemCounts.forEach((name, count) -> lines.add("- " + count + "x " + name));

        int limit = Math.min(lines.size(), MAX_SUMMARY_LINES);
        for (int i = 0; i < limit; i++) {
            context.append(lines.get(i)).append('\n');
        }

        if (lines.size() > limit) {
            context.append("- ... plus ").append(lines.size() - limit).append(" more item types\n");
        }
    }

    private void appendStack(StringBuilder context, String label, ItemStack stack) {
        if (stack.isEmpty()) {
            context.append(label).append(": Empty\n");
            return;
        }

        context.append(label).append(": ")
                .append(stack.getCount()).append("x ")
                .append(stackName(stack))
                .append('\n');
    }

    private String stackName(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String displayName = stack.getHoverName().getString();
        return displayName + " (" + id + ")";
    }

    private String stackSummary(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return stack.getCount() + "x " + stack.getHoverName().getString();
    }
}
