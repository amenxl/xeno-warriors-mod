package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Rhalix doesn't need sleep the way people do: going without it never brings phantoms after him. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RhalixNoPhantoms {
    private RhalixNoPhantoms() {}

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 200 != 0) return;
        if (ModCapabilities.getType(p) == CharacterType.RHALIX) p.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
    }
}
