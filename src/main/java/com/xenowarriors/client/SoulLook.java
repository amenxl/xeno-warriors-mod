package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.mikiko.HekubladeRealm;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Inside the Hekublade everyone except Mikiko is only a soul: drawn see-through and pale blue (see
 * LivingEntityRendererSoulMixin), with soul wisps drifting up off them.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoulLook {
    public static final float R = 0.55F, G = 0.85F, B = 1.0F, A = 0.5F;

    private SoulLook() {}

    public static boolean isSoul(Entity e) {
        if (!(e instanceof LivingEntity) || e.level().dimension() != HekubladeRealm.KEY) return false;
        return !(e instanceof Player p && ClientCharacterData.get(p) == CharacterType.MIKIKO);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused() || mc.level.dimension() != HekubladeRealm.KEY) return;
        if (mc.level.getGameTime() % 4 != 0) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!isSoul(e) || e.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 48 * 48) continue;
            if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            var r = e.level().random;
            if (r.nextInt(3) == 0) {
                mc.level.addParticle(ParticleTypes.SOUL, e.getX() + (r.nextDouble() - 0.5) * e.getBbWidth(),
                        e.getY() + r.nextDouble() * e.getBbHeight(), e.getZ() + (r.nextDouble() - 0.5) * e.getBbWidth(), 0, 0.03, 0);
            }
        }
    }
}
