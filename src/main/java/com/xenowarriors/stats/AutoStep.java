package com.xenowarriors.stats;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Fast runners step up blocks without jumping: Speed above 100 steps up a whole block (instead of a slab's height),
 * above 200 two blocks, and so on - one more block every 100 levels. A setting turns it off (PlayerOptions.AUTO_STEP).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class AutoStep {
    private static final UUID ID = UUID.fromString("3b1d6c52-9e0a-4f7d-8c61-2a4e5b7d9f13");
    private static final float VANILLA = 0.6F;

    private AutoStep() {}

    /** How many blocks this Speed level steps up (0 = the normal half block). */
    public static int blocks(int speedLevel) { return speedLevel > 100 ? (speedLevel - 1) / 100 : 0; }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 7) return;
        apply(p);
    }

    /** Puts the step height in line with their Speed and the setting. */
    public static void apply(ServerPlayer p) {
        AttributeInstance a = p.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (a == null) return;
        int n = PlayerOptions.get(p, PlayerOptions.AUTO_STEP) ? blocks(Stats.level(p, Stat.SPEED)) : 0;
        double add = n > 0 ? n - VANILLA + 0.01 : 0;                             // (a hair over a block: full blocks clear cleanly)
        AttributeModifier cur = a.getModifier(ID);
        if (cur != null && Math.abs(cur.getAmount() - add) < 1e-4) return;
        if (cur != null) a.removeModifier(ID);
        if (add > 0) a.addTransientModifier(new AttributeModifier(ID, "Xeno Warriors auto step", add, AttributeModifier.Operation.ADDITION));
    }
}
