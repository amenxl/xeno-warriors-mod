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
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) != CharacterType.NONE && (DmzCompat.charging(p) || UiTest.forceCharge);
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
            if (on) motionFx(mc, p, ClientCharacterData.get(p) == CharacterType.MIKIKO);
        }
    }

    private static void aura(Minecraft mc, AbstractClientPlayer p, int t) {
        boolean mikiko = ClientCharacterData.get(p) == CharacterType.MIKIKO;
        var r = p.getRandom();
        int rgb = ClientCharacterData.get(p).getColor();                  // everyone's aura in their own Karma color
        Vector3f k = new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
        Vector3f c1 = mikiko ? new Vector3f(1F, 0.35F, 0.1F) : k;
        Vector3f c2 = mikiko ? new Vector3f(1F, 0.75F, 0.2F) : new Vector3f(Math.min(1F, k.x + 0.3F), Math.min(1F, k.y + 0.3F), Math.min(1F, k.z + 0.3F));
        float grow = Math.min(1F, t / 30F);
        int n = 1 + Math.round(grow * 2);              // a few motes - the DbzAura shell carries the look
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, rad = 0.45 + r.nextDouble() * (0.3 + grow * 0.4);
            mc.level.addParticle(new DustParticleOptions(r.nextBoolean() ? c1 : c2, 0.8F + grow * 0.6F),
                    p.getX() + Math.cos(a) * rad, p.getY() + r.nextDouble() * 0.4, p.getZ() + Math.sin(a) * rad, 0, 0.12 + grow * 0.1, 0);
        }
        if (mikiko && r.nextInt(3) == 0) {
            mc.level.addParticle(ParticleTypes.FLAME, p.getX() + (r.nextDouble() - 0.5) * 1.2, p.getY() + 0.1,
                    p.getZ() + (r.nextDouble() - 0.5) * 1.2, 0, 0.08, 0);
        }
        if (ClientCharacterData.get(p) == CharacterType.RHALIX && r.nextInt(3) == 0) {
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
        double speed = Math.sqrt(Math.pow(p.getX() - p.xo, 2) + Math.pow(p.getZ() - p.zo, 2));
        boolean attacking = m.attackTime > 0F || com.xenowarriors.client.mikiko.MikikoAnimations.attacking(p);
        if (attacking) {
            // charging and attacking: the strike plays as normal, thrown with the whole body behind it
            m.body.xRot = Mth.lerp(e, m.body.xRot, m.body.xRot + 0.18F);
            m.head.xRot = Mth.lerp(e, m.head.xRot, m.head.xRot - 0.1F);
            m.hat.copyFrom(m.head);
            return;
        }
        if (speed > 0.08) {
            // charging and running: leaning hard into it, arms swept back, aura streaming behind
            float run = Mth.clamp((float) (speed - 0.08) / 0.15F, 0F, 1F) * e;
            float pump = Mth.sin(age * 0.9F) * 0.15F;
            m.body.xRot = Mth.lerp(run, m.body.xRot, 0.5F);
            m.head.xRot = Mth.lerp(run, m.head.xRot, -0.35F);
            m.rightArm.xRot = Mth.lerp(run, m.rightArm.xRot, 1.25F + pump);
            m.rightArm.zRot = Mth.lerp(run, m.rightArm.zRot, 0.25F);
            m.leftArm.xRot = Mth.lerp(run, m.leftArm.xRot, 1.25F - pump);
            m.leftArm.zRot = Mth.lerp(run, m.leftArm.zRot, -0.25F);
            m.rightArm.yRot = Mth.lerp(run, m.rightArm.yRot, 0F);
            m.leftArm.yRot = Mth.lerp(run, m.leftArm.yRot, 0F);
            // legs keep their running stride, pushed back under the lean
            m.rightLeg.z = Mth.lerp(run, m.rightLeg.z, 2.5F);
            m.leftLeg.z = Mth.lerp(run, m.leftLeg.z, 2.5F);
            m.hat.copyFrom(m.head);
            return;
        }
        float yell = 0.5F + 0.5F * Mth.sin(age * 0.18F);           // throws his head back now and then
        m.rightArm.xRot = Mth.lerp(e, m.rightArm.xRot, 0.25F + shake);
        m.rightArm.yRot = Mth.lerp(e, m.rightArm.yRot, 0F);
        m.rightArm.zRot = Mth.lerp(e, m.rightArm.zRot, 0.5F + shake);
        m.leftArm.xRot = Mth.lerp(e, m.leftArm.xRot, 0.25F - shake);
        m.leftArm.yRot = Mth.lerp(e, m.leftArm.yRot, 0F);
        m.leftArm.zRot = Mth.lerp(e, m.leftArm.zRot, -0.5F - shake);
        // legs: a low, wide horse stance - thighs forward, knees bent and trembling with the same effort as the
        // rest of him; the whole body sinks into it so hips and legs stay joined
        float legShake = Mth.sin(age * 3.3F + 1.1F) * 0.03F + Mth.sin(age * 5.2F) * 0.015F;
        float sink = 1.6F * e + shake * 6F * e;                     // px down, shuddering with the body
        m.rightLeg.xRot = Mth.lerp(e, m.rightLeg.xRot, -0.32F + legShake);
        m.leftLeg.xRot = Mth.lerp(e, m.leftLeg.xRot, -0.32F - legShake);
        m.rightLeg.yRot = Mth.lerp(e, m.rightLeg.yRot, 0.12F);
        m.leftLeg.yRot = Mth.lerp(e, m.leftLeg.yRot, -0.12F);
        m.rightLeg.zRot = Mth.lerp(e, m.rightLeg.zRot, 0.3F + legShake * 0.6F);
        m.leftLeg.zRot = Mth.lerp(e, m.leftLeg.zRot, -0.3F + legShake * 0.6F);
        LimbBend.extraKnees(p, (0.75F + legShake * 2F) * e, (0.75F - legShake * 2F) * e);
        m.rightLeg.y += sink * 0.5F;
        m.leftLeg.y += sink * 0.5F;
        m.body.y += sink;
        m.head.y += sink;
        m.rightArm.y += sink;
        m.leftArm.y += sink;
        m.body.xRot = Mth.lerp(e, m.body.xRot, 0.12F + shake * 0.5F);
        m.head.xRot = Mth.lerp(e, m.head.xRot, -0.15F - 0.35F * yell);
        m.hat.copyFrom(m.head);
    }

    /** A burst of aura where a charged strike lands, and a trail while charging on the run. */
    private static void motionFx(Minecraft mc, AbstractClientPlayer p, boolean mikiko) {
        var r = p.getRandom();
        Vector3f c = mikiko ? new Vector3f(1F, 0.45F, 0.1F) : new Vector3f(0.75F, 0.35F, 1F);
        double speed = Math.sqrt(Math.pow(p.getX() - p.xo, 2) + Math.pow(p.getZ() - p.zo, 2));
        if (speed > 0.08) {
            for (int i = 0; i < 3; i++) {
                mc.level.addParticle(new DustParticleOptions(c, 1.2F), p.xo + (r.nextDouble() - 0.5) * 0.6,
                        p.getY() + r.nextDouble() * 1.8, p.zo + (r.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            }
        }
        if (p.swinging && p.swingTime == 1) {
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            double fx = p.getX() - Mth.sin(yaw) * 1.3, fz = p.getZ() + Mth.cos(yaw) * 1.3;
            for (int i = 0; i < 14; i++) {
                mc.level.addParticle(new DustParticleOptions(c, 1.5F), fx, p.getY() + 1.3, fz,
                        (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.6);
            }
            mc.level.addParticle(mikiko ? ParticleTypes.FLAME : ParticleTypes.WITCH, fx, p.getY() + 1.3, fz, 0, 0.05, 0);
        }
    }
}
