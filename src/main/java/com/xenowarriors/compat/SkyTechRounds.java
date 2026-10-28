package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Gov soldiers' Sky-tech rounds (TACZ guns): hit harder, slow you down and burst in Skylander-blue. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkyTechRounds {
    private static final DustParticleOptions SKY = new DustParticleOptions(new Vector3f(0.45F, 0.85F, 1F), 1.2F);

    private SkyTechRounds() {}

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!TaczGuns.isBullet(event.getSource().getDirectEntity())) return;
        if (!(event.getSource().getEntity() instanceof com.xenowarriors.gov.GovAgentEntity g)) return;
        // guards' rifles are tuned down against players so a squad doesn't drop you in a couple of seconds
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player) event.setAmount(event.getAmount() * 0.3F);
        if (!g.skyAmmo()) return;
        var v = event.getEntity();
        event.setAmount(event.getAmount() * 1.3F);
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true));
        if (v.level() instanceof ServerLevel l) l.sendParticles(SKY, v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(), 10, 0.25, 0.3, 0.25, 0);
    }
}
