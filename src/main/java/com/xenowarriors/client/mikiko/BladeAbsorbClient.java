package com.xenowarriors.client.mikiko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A mob Mikiko defeats doesn't fall over: it is stretched into a streak of red light and pulled into the
 * Hekublade in his hand, spinning and shrinking, and the blade flashes as it's sealed inside.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BladeAbsorbClient {
    public static final float DURATION = 16F;
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1F, 0.16F, 0.1F), 0.9F);
    private static final DustParticleOptions ORANGE = new DustParticleOptions(new Vector3f(1F, 0.55F, 0.15F), 0.7F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1F, 0.95F, 0.9F), 0.6F);

    private static final class Absorb {
        final int killer;
        final long start;
        boolean sealed;

        Absorb(int killer, long start) {
            this.killer = killer;
            this.start = start;
        }
    }

    private static final Map<Integer, Absorb> ACTIVE = new HashMap<>();

    private BladeAbsorbClient() {}

    public static void start(int victim, int killer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ACTIVE.put(victim, new Absorb(killer, mc.level.getGameTime()));
        MikikoAnimations.onAbsorb(killer);
        Entity e = mc.level.getEntity(victim);
        if (e != null) {
            mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2F, 0.8F, false);
        }
    }

    public static boolean isAbsorbing(int id) {
        return ACTIVE.containsKey(id);
    }

    /** Where the Hekublade is: his right hand, held out in front. */
    static Vec3 bladePoint(Entity killer, float partial) {
        float yaw = Mth.lerp(partial, killer instanceof LivingEntity l ? l.yBodyRotO : killer.yRotO,
                killer instanceof LivingEntity l2 ? l2.yBodyRot : killer.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        return killer.getPosition(partial).add(0, 1.55, 0).add(fwd.scale(0.55)).add(right.scale(-0.38));
    }

    private static float progress(Absorb a, float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 1F : Mth.clamp((mc.level.getGameTime() - a.start + partial) / DURATION, 0F, 1F);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Map.Entry<Integer, Absorb>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Absorb a = en.getValue();
            long age = level.getGameTime() - a.start;
            Entity e = level.getEntity(en.getKey());
            Entity killer = level.getEntity(a.killer);
            if (age > 60 || (e == null && age > DURATION + 4)) {
                it.remove();
                continue;
            }
            if (e instanceof LivingEntity l) l.deathTime = 0;      // no falling over
            if (killer == null) continue;
            Vec3 blade = bladePoint(killer, 1F);
            float p = Math.min(1F, age / DURATION);
            if (e != null && p < 1F) {
                // red light streaming from the body into the blade
                Vec3 from = e.position().add(0, e.getBbHeight() * 0.5, 0);
                Vec3 to = blade.subtract(from);
                for (int i = 0; i < 4; i++) {
                    double t = level.random.nextDouble();
                    Vec3 at = from.add(to.scale(t)).add((level.random.nextDouble() - 0.5) * 0.3 * (1 - t),
                            (level.random.nextDouble() - 0.5) * 0.3 * (1 - t), (level.random.nextDouble() - 0.5) * 0.3 * (1 - t));
                    level.addParticle(i % 2 == 0 ? RED : ORANGE, at.x, at.y, at.z, 0, 0, 0);
                }
            }
            if (p >= 1F && !a.sealed) {
                a.sealed = true;
                // sealed: the blade flashes white-red
                for (int i = 0; i < 14; i++) {
                    double ang = i / 14.0 * Math.PI * 2;
                    level.addParticle(i % 3 == 0 ? WHITE : RED, blade.x, blade.y, blade.z,
                            Math.cos(ang) * 0.12, (level.random.nextDouble() - 0.3) * 0.1, Math.sin(ang) * 0.12);
                }
                level.addParticle(ParticleTypes.FLASH, blade.x, blade.y, blade.z, 0, 0, 0);
                level.playLocalSound(blade.x, blade.y, blade.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.6F, 0.7F, false);
                level.playLocalSound(blade.x, blade.y, blade.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.35F, 1.6F, false);
            }
        }
    }

    private static final Map<Integer, Boolean> PUSHED = new HashMap<>();

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        Absorb a = ACTIVE.get(e.getId());
        if (a == null) return;
        Minecraft mc = Minecraft.getInstance();
        Entity killer = mc.level == null ? null : mc.level.getEntity(a.killer);
        float partial = event.getPartialTick();
        float p = progress(a, partial);
        if (p >= 1F || killer == null) {
            event.setCanceled(true);
            return;
        }
        float ease = p * p * (3F - 2F * p);
        float pull = p * p;                               // starts slow, then gets yanked in
        Vec3 offset = bladePoint(killer, partial).subtract(e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0)).scale(pull);
        float scale = Math.max(0.02F, 1F - ease);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        float h = e.getBbHeight() * 0.5F;
        pose.translate(0, h, 0);
        pose.mulPose(Axis.YP.rotationDegrees(p * p * 900F));
        pose.scale(scale * (1F + 0.4F * ease), scale, scale * (1F + 0.4F * ease));
        pose.translate(0, -h, 0);
        PUSHED.put(e.getId(), true);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId()) != null) event.getPoseStack().popPose();
    }
}
