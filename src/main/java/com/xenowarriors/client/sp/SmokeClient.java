package com.xenowarriors.client.sp;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Max is always smoking: a thin wisp now and then from the lit end of his cigarette. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SmokeClient {
    private SmokeClient() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (ClientCharacterData.get(p) != CharacterType.MAX || ClientCharacterData.outfit(p) != 0 || p.isInvisible() || p.isInWater()) continue;   // (no cigarette in his disguise)
            if ((p.tickCount + p.getId()) % 9 != 0) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            double yaw = Math.toRadians(p.yHeadRot);
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3 at = p.position().add(0, p.getEyeHeight() - 0.32, 0).add(fwd.scale(0.36)).add(left.scale(0.2));
            mc.level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0.015, 0);
        }
    }
}
