package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The first punch of Mikiko's fist combo knocks them back a little - just far enough that his skip-in on the second
 * hit still reaches them. (A new combo = 2 s since his last bare-handed hit, like the animations.)
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OpeningKnockback {
    private static final int COMBO_TIMEOUT = 40;
    private static final double STRENGTH = 0.55;
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();
    private static final List<Object[]> PENDING = new ArrayList<>();      // {target, player}

    private OpeningKnockback() {}

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer p) || !event.getSource().is(DamageTypes.PLAYER_ATTACK)) return;
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO || !p.getMainHandItem().isEmpty()) return;
        long now = p.level().getGameTime();
        Long last = LAST_HIT.put(p.getUUID(), now);
        if (last == null || now - last > COMBO_TIMEOUT) PENDING.add(new Object[]{event.getEntity(), p});
    }

    /** Applied after the hit's own knockback (which would otherwise halve it). */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        for (Object[] o : PENDING) {
            LivingEntity t = (LivingEntity) o[0];
            ServerPlayer p = (ServerPlayer) o[1];
            if (!t.isAlive()) continue;
            double dx = p.getX() - t.getX(), dz = p.getZ() - t.getZ();
            t.knockback(STRENGTH, dx, dz);
            t.hurtMarked = true;
        }
        PENDING.clear();
        if (LAST_HIT.size() > 256) LAST_HIT.clear();
    }
}
