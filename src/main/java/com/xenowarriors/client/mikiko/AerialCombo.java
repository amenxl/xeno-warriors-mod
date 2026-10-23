package com.xenowarriors.client.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.LockOn;
import com.xenowarriors.mikiko.Hekublade;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mikiko's aerial Hekublade combo (attacking in the air, from a jump or flying):
 * <ul>
 * <li>floaty - each swing holds him up and he drifts down slowly between them;</li>
 * <li>a big reach that catches someone standing on the ground below him;</li>
 * <li>the first hit launches them up into the air with him, the next ones keep them there, and the last one (if he
 *     isn't interrupted and they don't block) spikes them down into the ground;</li>
 * <li>locked on to something in the air out of reach: he dashes up to it (and if he can fly, takes off after it).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AerialCombo {
    public static final int HITS = 4, TIMEOUT = 30;
    private static final double REACH = 5.5, DASH_RANGE = 18;
    private static int step, lastHit = -1000, floatUntil = -1000;
    private static boolean broken;

    private AerialCombo() {}

    /** Mikiko or Rhalix off the ground, fighting with bare hands, the Hekublade (or for Rhalix, a sword). */
    public static boolean applies(LocalPlayer p) {
        CharacterType type = ClientCharacterData.getLocal();
        var held = p.getMainHandItem();
        boolean weapon = held.isEmpty() || Hekublade.isBlade(held)
                || (type == CharacterType.RHALIX && held.getItem() instanceof net.minecraft.world.item.SwordItem);
        return (type == CharacterType.MIKIKO || type == CharacterType.RHALIX) && weapon
                && !p.onGround() && !p.isInWater() && !p.isPassenger() && !p.isFallFlying();
    }

    /** Called as he starts an aerial swing; returns which hit of the combo this is (0-based). */
    public static int swing(LocalPlayer p) {
        int now = p.tickCount;
        if (now - lastHit > TIMEOUT || step >= HITS || broken) { step = 0; broken = false; }
        int hit = step++;
        lastHit = now;
        floatUntil = now + 16;
        LivingEntity lock = LockOn.target();
        // locked on to something up in the air and out of reach: dash up to it (taking off if he can fly)
        if (lock != null && lock.isAlive() && !lock.onGround() && p.distanceTo(lock) > 3.0 && p.distanceTo(lock) <= DASH_RANGE) {
            if (p.getAbilities().mayfly && !p.getAbilities().flying) {
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
            }
            Vec3 to = lock.position().add(0, lock.getBbHeight() * 0.4, 0).subtract(p.position());
            double d = to.length();
            Vec3 dash = to.normalize().scale(Math.min(1.6, (d - 1.8) * 0.45));
            p.setDeltaMovement(dash);
            Minecraft.getInstance().level.addParticle(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 0, 0, 0);
        } else {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x * 0.6, Math.max(v.y, 0.14), v.z * 0.6);       // the swing holds him up
        }
        LivingEntity target = target(p, lock);
        if (target != null) {
            // can fly but can't reach it: take off after it
            if (p.distanceTo(target) > 3.2 && p.getAbilities().mayfly && !p.getAbilities().flying) {
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
            }
            boolean direct = Minecraft.getInstance().hitResult instanceof EntityHitResult eh && eh.getEntity() == target;
            ModNetwork.sendToServer(new com.xenowarriors.network.AerialComboPacket(target.getId(), hit, hit == HITS - 1, !direct));
        }
        return hit;
    }

    /** Who the swing is for: the lock-on target in reach, else the crosshair, else anyone close in front - above or below. */
    private static LivingEntity target(LocalPlayer p, LivingEntity lock) {
        if (lock != null && lock.isAlive() && p.distanceTo(lock) <= REACH) return lock;
        if (Minecraft.getInstance().hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity l) return l;
        Vec3 look = p.getViewVector(1F);
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(REACH, REACH + 1, REACH),
                e -> e != p && e.isAlive() && e.isPickable() && !e.isSpectator())) {
            Vec3 to = e.position().subtract(p.position());
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            if (horiz > REACH || Math.abs(to.y) > REACH) continue;
            if (horiz > 0.8 && new Vec3(to.x, 0, to.z).normalize().dot(flat) < 0.35) continue;     // roughly in front
            double d = to.length();
            if (d < bestD && p.hasLineOfSight(e)) { bestD = d; best = e; }
        }
        return best;
    }

    /** Floaty: drifting down slowly through the combo; getting hit breaks it (no spike at the end). */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || Minecraft.getInstance().isPaused()) return;
        if (p.onGround()) { step = 0; return; }
        if (p.hurtTime > 0 && p.tickCount - lastHit < TIMEOUT) broken = true;
        if (p.tickCount < floatUntil && !p.getAbilities().flying) {
            Vec3 v = p.getDeltaMovement();
            if (v.y < 0) p.setDeltaMovement(v.x, Math.max(v.y * 0.55, -0.12), v.z);
        }
    }

    public static boolean broken() { return broken; }
}
