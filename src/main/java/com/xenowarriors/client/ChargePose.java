package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.compat.DmzCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Powering up (DragonMineZ's Ki charge) for Mikiko and Rhalix: feet planted wide, fists clenched down at their
 * sides, shaking with effort and head thrown back to yell - with an aura of their own color (Mikiko orange-red
 * fire, Rhalix violet) rising around them and their hair lifting.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ChargePose {
    private static final Map<Integer, float[]> WEIGHT = new HashMap<>();   // id -> {weight, ticks charging}

    private ChargePose() {}

    public static boolean charging(Player p) {
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) != CharacterType.NONE && DmzCompat.charging(p);
    }

    /** How far into the pose (0..1), eased in and out. */
    public static float weight(Player p) {
        float[] w = WEIGHT.get(p.getId());
        return w == null ? 0F : w[0];
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            boolean on = charging(p);
            float[] w = WEIGHT.computeIfAbsent(p.getId(), k -> new float[2]);
            w[0] = Mth.clamp(w[0] + (on ? 0.2F : -0.15F), 0F, 1F);
            w[1] = on ? w[1] + 1 : 0;
            if (w[0] <= 0F && !on) {
                WEIGHT.remove(p.getId());
                continue;
            }
            if (on && !(p == mc.player && mc.options.getCameraType().isFirstPerson())) aura(mc, p, (int) w[1]);
        }
    }

    private static void aura(Minecraft mc, AbstractClientPlayer p, int t) {
        boolean mikiko = ClientCharacterData.get(p) == CharacterType.MIKIKO;
        var r = p.getRandom();
        Vector3f c1 = mikiko ? new Vector3f(1F, 0.35F, 0.1F) : new Vector3f(0.7F, 0.3F, 1F);
        Vector3f c2 = mikiko ? new Vector3f(1F, 0.75F, 0.2F) : new Vector3f(0.9F, 0.7F, 1F);
        float grow = Math.min(1F, t / 30F);
        int n = 3 + Math.round(grow * 4);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, rad = 0.45 + r.nextDouble() * (0.3 + grow * 0.4);
            mc.level.addParticle(new DustParticleOptions(r.nextBoolean() ? c1 : c2, 0.8F + grow * 0.6F),
                    p.getX() + Math.cos(a) * rad, p.getY() + r.nextDouble() * 0.4, p.getZ() + Math.sin(a) * rad, 0, 0.12 + grow * 0.1, 0);
        }
        if (mikiko && r.nextInt(3) == 0) {
            mc.level.addParticle(ParticleTypes.FLAME, p.getX() + (r.nextDouble() - 0.5) * 1.2, p.getY() + 0.1,
                    p.getZ() + (r.nextDouble() - 0.5) * 1.2, 0, 0.08, 0);
        }
        if (!mikiko && r.nextInt(3) == 0) {
            mc.level.addParticle(ParticleTypes.WITCH, p.getX() + (r.nextDouble() - 0.5) * 1.2, p.getY() + r.nextDouble() * 2,
                    p.getZ() + (r.nextDouble() - 0.5) * 1.2, 0, 0.05, 0);
        }
        // dust kicked up off the ground once it builds
        if (grow > 0.5F && p.onGround() && r.nextInt(2) == 0) {
            double a = r.nextDouble() * Math.PI * 2;
            mc.level.addParticle(ParticleTypes.CLOUD, p.getX() + Math.cos(a) * 1.2, p.getY() + 0.05, p.getZ() + Math.sin(a) * 1.2,
                    Math.cos(a) * 0.08, 0.01, Math.sin(a) * 0.08);
        }
    }

    /** Applied last in setupAnim, blended over whatever else was posing them. */
    public static void apply(HumanoidModel<?> m, Player p, float age) {
        float w = weight(p);
        if (w <= 0F) return;
        float e = w * w * (3 - 2 * w);
        float shake = Mth.sin(age * 2.7F) * 0.035F + Mth.sin(age * 4.1F) * 0.02F;
        float yell = 0.5F + 0.5F * Mth.sin(age * 0.18F);           // throws his head back now and then
        m.rightArm.xRot = Mth.lerp(e, m.rightArm.xRot, 0.25F + shake);
        m.rightArm.yRot = Mth.lerp(e, m.rightArm.yRot, 0F);
        m.rightArm.zRot = Mth.lerp(e, m.rightArm.zRot, 0.5F + shake);
        m.leftArm.xRot = Mth.lerp(e, m.leftArm.xRot, 0.25F - shake);
        m.leftArm.yRot = Mth.lerp(e, m.leftArm.yRot, 0F);
        m.leftArm.zRot = Mth.lerp(e, m.leftArm.zRot, -0.5F - shake);
        m.rightLeg.xRot = Mth.lerp(e, m.rightLeg.xRot, 0F);
        m.leftLeg.xRot = Mth.lerp(e, m.leftLeg.xRot, 0F);
        m.rightLeg.zRot = Mth.lerp(e, m.rightLeg.zRot, 0.22F);
        m.leftLeg.zRot = Mth.lerp(e, m.leftLeg.zRot, -0.22F);
        m.body.xRot = Mth.lerp(e, m.body.xRot, 0.12F + shake * 0.5F);
        m.head.xRot = Mth.lerp(e, m.head.xRot, -0.15F - 0.35F * yell);
        m.hat.copyFrom(m.head);
    }
}
