package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

/** With DragonMineZ: its Dragon Balls (Earth's and Namek's, every star) stack up to 64 instead of one per slot. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DmzBallStacks {
    private DmzBallStacks() {}

    public static final int STACK = 64;
    /** (Tests) balls made stackable. */
    public static int changed;

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent e) {
        if (ModList.get() == null || !ModList.get().isLoaded("dragonminez")) return;
        e.enqueueWork(() -> {
            for (var entry : ForgeRegistries.ITEMS.getEntries()) {
                var id = entry.getKey().location();
                if (!id.getNamespace().equals("dragonminez") || !id.getPath().matches("dball[1-7](_namek)?")) continue;
                Item it = entry.getValue();
                if (it.getMaxStackSize() >= STACK) continue;
                try { ObfuscationReflectionHelper.setPrivateValue(Item.class, it, STACK, "f_41370_"); changed++; }
                catch (Throwable t) { XenoWarriors.LOGGER.warn("Couldn't make " + id + " stack", t); }
            }
            XenoWarriors.LOGGER.info("XW: {} DragonMineZ Dragon Balls now stack to {}", changed, STACK);
        });
    }
}
