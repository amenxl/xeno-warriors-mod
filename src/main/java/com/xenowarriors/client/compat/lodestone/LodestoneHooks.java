package com.xenowarriors.client.compat.lodestone;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lodestone ambient effects each tick: karma auras while charging, the SR demons' dark aura, Logan's Redirect ring, blast trails. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LodestoneHooks {
    private static Boolean on;

    private LodestoneHooks() {}

    public static boolean on() {
        if (on == null) on = net.minecraftforge.fml.ModList.get().isLoaded("lodestone");
        return on;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        int t = (int) mc.level.getGameTime();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.distanceToSqr(mc.player) > 64 * 64) continue;
            if (e instanceof Player p) {
                float charge = com.xenowarriors.client.ChargePose.weight((net.minecraft.client.player.AbstractClientPlayer) p);
                if (charge > 0.05F && t % 2 == 0) {
                    var type = com.xenowarriors.client.ClientCharacterData.get((net.minecraft.client.player.AbstractClientPlayer) p);
                    LodestoneFx.aura(mc.level, p.getX(), p.getY(), p.getZ(), type.getColor(), charge);
                }
            } else if (e instanceof com.xenowarriors.sr.SrMemberEntity sr) {
                if (sr.demon() && t % 3 == 0) LodestoneFx.aura(mc.level, sr.getX(), sr.getY(), sr.getZ(), 0x8A0D1A, 0.8F);
                if (sr.redirecting()) LodestoneFx.spinRing(mc.level, sr.getX(), sr.getY(), sr.getZ(), 0xFFD940, t);
            } else if (e instanceof com.xenowarriors.npc.KiBlastEntity || e instanceof com.xenowarriors.corrupted.SoulBoltEntity) {
                LodestoneFx.trail(mc.level, e.position().add(0, e.getBbHeight() / 2, 0), e instanceof com.xenowarriors.corrupted.SoulBoltEntity ? 0x7A20C8 : 0xFFE070);
            }
        }
    }
}
