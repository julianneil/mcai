package com.modai.mcai.client.context;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class PlayerContextProvider {
    public String buildContext() {
        return buildDetailedContext();
    }

    public String buildSummary() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        if (player == null || level == null) {
            return "Player context unavailable: no world loaded.";
        }

        StringBuilder context = new StringBuilder();
        context.append("Player summary:\n");
        appendLocationSummary(context, player, level);
        appendStatusSummary(context, player, minecraft.gameMode);
        appendTargetSummary(context, minecraft, level);
        return context.toString().trim();
    }

    private String buildDetailedContext() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        if (player == null || level == null) {
            return "Player context unavailable: no world loaded.";
        }

        StringBuilder context = new StringBuilder();
        context.append("Current player status and location context:\n");
        appendLocation(context, player, level);
        appendStatus(context, player, minecraft.gameMode);
        appendTarget(context, minecraft, level);
        return context.toString().trim();
    }

    private void appendLocationSummary(StringBuilder context, LocalPlayer player, ClientLevel level) {
        BlockPos blockPos = player.blockPosition();
        Holder<Biome> biome = level.getBiome(blockPos);
        ResourceLocation biomeId = biome.unwrapKey()
                .map(ResourceKey::location)
                .orElse(ResourceLocation.parse("unknown:unknown"));

        context.append("Location: ")
                .append(level.dimension().location())
                .append(" @ x=").append(blockPos.getX())
                .append(", y=").append(blockPos.getY())
                .append(", z=").append(blockPos.getZ())
                .append(" in ").append(biomeId)
                .append('\n');
    }

    private void appendStatusSummary(StringBuilder context, LocalPlayer player, MultiPlayerGameMode gameMode) {
        GameType gameType = gameMode == null ? null : gameMode.getPlayerMode();
        context.append("Vitals: ")
                .append(formatOneDecimal(player.getHealth()))
                .append("/").append(formatOneDecimal(player.getMaxHealth()))
                .append(" hp, ")
                .append(player.getFoodData().getFoodLevel()).append("/20 hunger, ")
                .append(formatOneDecimal(player.getFoodData().getSaturationLevel())).append(" saturation, ")
                .append(player.getArmorValue()).append(" armor, ")
                .append("xp ").append(player.experienceLevel)
                .append(", ")
                .append(gameType == null ? "unknown mode" : gameType.getName())
                .append('\n');
    }

    private void appendTargetSummary(StringBuilder context, Minecraft minecraft, ClientLevel level) {
        HitResult hitResult = minecraft.hitResult;
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            context.append("Target: nothing\n");
            return;
        }

        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            context.append("Target: block ")
                    .append(state.getBlock().getName().getString())
                    .append(" (").append(blockId).append(")\n");
            return;
        }

        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            context.append("Target: entity ")
                    .append(entity.getDisplayName().getString())
                    .append(" (").append(entityId).append(")\n");
        }
    }

    private void appendLocation(StringBuilder context, LocalPlayer player, ClientLevel level) {
        BlockPos blockPos = player.blockPosition();
        Holder<Biome> biome = level.getBiome(blockPos);
        ResourceLocation biomeId = biome.unwrapKey()
                .map(ResourceKey::location)
                .orElse(ResourceLocation.parse("unknown:unknown"));

        context.append("Dimension: ").append(level.dimension().location()).append('\n');
        context.append("Position: x=").append(blockPos.getX())
                .append(", y=").append(blockPos.getY())
                .append(", z=").append(blockPos.getZ()).append('\n');
        context.append("Biome: ").append(biomeId).append('\n');
    }

    private void appendStatus(StringBuilder context, LocalPlayer player, MultiPlayerGameMode gameMode) {
        GameType gameType = gameMode == null ? null : gameMode.getPlayerMode();

        context.append("Health: ").append(formatOneDecimal(player.getHealth()))
                .append("/").append(formatOneDecimal(player.getMaxHealth())).append('\n');
        context.append("Food: ").append(player.getFoodData().getFoodLevel()).append("/20\n");
        context.append("Saturation: ").append(formatOneDecimal(player.getFoodData().getSaturationLevel())).append('\n');
        context.append("Armor value: ").append(player.getArmorValue()).append('\n');
        context.append("XP level: ").append(player.experienceLevel).append('\n');
        context.append("Game mode: ").append(gameType == null ? "unknown" : gameType.getName()).append('\n');
        context.append("On ground: ").append(player.onGround()).append('\n');
    }

    private void appendTarget(StringBuilder context, Minecraft minecraft, ClientLevel level) {
        HitResult hitResult = minecraft.hitResult;
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            context.append("Looking at: Nothing\n");
            return;
        }

        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            context.append("Looking at block: ")
                    .append(state.getBlock().getName().getString())
                    .append(" (").append(blockId).append(") at x=")
                    .append(pos.getX()).append(", y=").append(pos.getY()).append(", z=").append(pos.getZ())
                    .append('\n');
            return;
        }

        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            context.append("Looking at entity: ")
                    .append(entity.getDisplayName().getString())
                    .append(" (").append(entityId).append(")\n");
        }
    }

    private String formatOneDecimal(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
