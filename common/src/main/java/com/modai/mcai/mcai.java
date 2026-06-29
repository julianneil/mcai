package com.modai.mcai;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(mcai.MODID)
public class mcai {
    public static final String MODID = "mcai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public mcai() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        LOGGER.info("MCAI loaded");
    }
}
