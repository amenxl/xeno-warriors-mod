package com.xenowarriors.skill.rhalix;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rhalix's bare-handed five-hit combo hits harder as it goes, and his kicks knock enemies back. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RhalixCombat {
    private static final float[] DAMAGE = {3F, 3.5F, 4.5F, 5F, 7F};
    private static final Map<UUID, int[]> COMBO = new HashMap<>();   // {step, last hit tick}

    private RhalixCombat() {}

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer p) || event.getSource().getEntity() != p) return;
        if (ModCapabilities.getType(p) != CharacterType.RHALIX || !p.getMainHandItem().isEmpty()) return;
        if (!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) || event.getAmount() < 0.85F) return;
        int[] c = COMBO.computeIfAbsent(p.getUUID(), k -> new int[]{0, -1000});
        if (p.tickCount - c[1] > 30) c[0] = 0;
        int step = c[0];
        c[0] = (step + 1) % DAMAGE.length;
        c[1] = p.tickCount;
        event.setAmount(DAMAGE[step] * Math.max(0.25F, event.getAmount()));
        LivingEntity target = event.getEntity();
        Vec3 dir = target.position().subtract(p.position()).normalize();
        if (step >= 2) target.push(dir.x * (step == 4 ? 0.9 : 0.45), step == 4 ? 0.4 : 0.2, dir.z * (step == 4 ? 0.9 : 0.45));
    }
}
