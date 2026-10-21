package com.xenowarriors;

import com.mojang.logging.LogUtils;
import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.entity.ModEntities;
import com.xenowarriors.network.ModNetwork;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class XenoWarriors implements ModInitializer {
    public static final String MOD_ID = "xenowarriors";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        ModEntities.init();
        ModNetwork.registerServerReceivers();
        CharacterManager.registerEvents();
        LOGGER.info("Xeno Warriors Recarnet loaded");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
