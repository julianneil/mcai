package com.modai.mcai;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(mcai.MODID)
public class mcai {
    public static final String MODID = "mcai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public mcai(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        LOGGER.info("MCAI loaded");
    }
}
