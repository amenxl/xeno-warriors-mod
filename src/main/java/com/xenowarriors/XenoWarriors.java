package com.xenowarriors;

import com.mojang.logging.LogUtils;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.entity.ModEntities;
import com.xenowarriors.item.ModItems;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(XenoWarriors.MOD_ID)
public class XenoWarriors {
    public static final String MOD_ID = "xenowarriors";
    public static final Logger LOGGER = LogUtils.getLogger();

    public XenoWarriors() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ModCapabilities::register);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        com.xenowarriors.fox.ModMenus.MENUS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(ModItems::addToTabs);
        ModNetwork.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
