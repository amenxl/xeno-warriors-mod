package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Recommended (not required) companion mods: a one-time tip naming the ones that aren't installed. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecommendedMods {
    private static final String[][] MODS = {
            {"tacz", "Timeless and Classics Zero", "Government soldiers with guns"},
            {"lodestone", "Lodestone", "better skill effects"},
            {"refurbished_furniture", "MrCrayfish's Furniture Mod: Refurbished", "furnished houses"},
    };

    private RecommendedMods() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getPersistentData().getBoolean("xw_rec_tip")) return;
        StringBuilder missing = new StringBuilder();
        for (String[] m : MODS) if (!ModList.get().isLoaded(m[0])) missing.append("\n - ").append(m[1]).append(" (").append(m[2]).append(")");
        p.getPersistentData().putBoolean("xw_rec_tip", true);
        if (missing.length() == 0) return;
        p.sendSystemMessage(Component.literal("Xeno Warriors works on its own, but these mods are recommended:" + missing).withStyle(ChatFormatting.GRAY));
    }
}
