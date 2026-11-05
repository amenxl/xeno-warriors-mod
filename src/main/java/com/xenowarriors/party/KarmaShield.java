package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.combat.Allies;
import com.xenowarriors.network.KarmaShieldPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Rhalix's Karma shield (cast when he revive-warps to a downed teammate): a purple dome of Karma around him and the
 * one he's reviving. Enemy arrows and other projectiles stop dead at it, enemies who come at it are thrown back
 * out, and nothing from outside can hit the two of them inside. It lasts while he revives them (up to 15 s) and
 * drops as soon as they're back up or he leaves it.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KarmaShield {
    private KarmaShield() {}

    public static final double R = 3.5;
    public static final int MAX_TICKS = 15 * 20;

    private static final class Shield {
        final LivingEntity owner, downed; final Vec3 center; int age;
        Shield(LivingEntity o, LivingEntity d) { owner = o; downed = d; center = d.position(); }
    }

    private static final List<Shield> ACTIVE = new ArrayList<>();
    /** Counters for tests: projectiles stopped, enemies thrown back, hits blocked. */
    public static int blockedProjectiles, pushed, blockedHits;

    public static void cast(LivingEntity owner, LivingEntity downed) {
        ACTIVE.removeIf(s -> { if (s.owner == owner) { off(s); return true; } return false; });
        Shield s = new Shield(owner, downed);
        ACTIVE.add(s);
        ServerLevel l = (ServerLevel) owner.level();
        l.playSound(null, s.center.x, s.center.y, s.center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.4F);
        l.playSound(null, s.center.x, s.center.y, s.center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.8F);
        ModNetwork.sendToTrackingEntityAndSelf(new KarmaShieldPacket(owner.getId(), s.center.x, s.center.y, s.center.z, (float) R, MAX_TICKS), owner);
        XenoWarriors.LOGGER.info("Karma shield up: {} around {} (r={})", owner.getName().getString(), ReviveCircle.name(downed), R);
    }

    private static void off(Shield s) {
        ModNetwork.sendToTrackingEntityAndSelf(new KarmaShieldPacket(s.owner.getId(), s.center.x, s.center.y, s.center.z, (float) R, 0), s.owner);
        if (s.owner.level() instanceof ServerLevel l) l.playSound(null, s.center.x, s.center.y, s.center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    public static boolean active(LivingEntity owner) {
        for (Shield s : ACTIVE) if (s.owner == owner) return true;
        return false;
    }

    private static boolean inside(Shield s, Entity e) { return e.position().distanceToSqr(s.center) < R * R; }

    /** An enemy of the shield's caster (not their side, not the one down). */
    private static boolean foe(Shield s, Entity e) {
        return e != null && e != s.owner && e != s.downed && !Allies.isAlly(s.owner, e)
                && !(s.owner instanceof com.xenowarriors.bot.PartyBotEntity b && PartyBots.friendly(b, e));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        ACTIVE.removeIf(s -> {
            s.age++;
            boolean revived = !PartyBots.isDown(s.downed);
            if (s.age > MAX_TICKS || !s.owner.isAlive() || s.owner.isRemoved() || s.downed.isRemoved() || s.owner.level() != s.downed.level() || revived   // (a downed bot that bled out and went home: nobody left to guard)
                    || s.owner.position().distanceToSqr(s.center) > (R + 1.5) * (R + 1.5)) {
                XenoWarriors.LOGGER.info("Karma shield down after {} ticks ({}), stopped {} projectiles, pushed {}", s.age, revived ? "revived" : "ended", blockedProjectiles, pushed);
                off(s);
                return true;
            }
            ServerLevel l = (ServerLevel) s.owner.level();
            AABB box = new AABB(s.center, s.center).inflate(R + 2);
            for (Projectile p : l.getEntitiesOfClass(Projectile.class, box, p -> p.isAlive() && foe(s, p.getOwner()))) {
                Vec3 next = p.position().add(p.getDeltaMovement());
                if (p.position().distanceToSqr(s.center) < (R + 0.8) * (R + 0.8) || next.distanceToSqr(s.center) < R * R) {
                    l.sendParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY(), p.getZ(), 10, 0.15, 0.15, 0.15, 0.2);
                    l.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY(), p.getZ(), 8, 0.1, 0.1, 0.1, 0.05);
                    l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.3F);
                    p.discard();
                    blockedProjectiles++;
                }
            }
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && (e instanceof Mob || e instanceof Player) && foe(s, e))) {
                Vec3 out = new Vec3(e.getX() - s.center.x, 0, e.getZ() - s.center.z);
                double d = out.length();
                if (d > R + 0.6) continue;
                out = d < 1.0E-3 ? new Vec3(1, 0, 0) : out.scale(1 / d);
                e.setDeltaMovement(out.x * 0.9, 0.35, out.z * 0.9);
                e.hurtMarked = true;
                if (s.age % 5 == 0) l.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
                pushed++;
            }
            if (s.age % 20 == 0) l.playSound(null, s.center.x, s.center.y, s.center.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.5F);
            return false;
        });
    }

    /** Shields are this world's (their entities go with it): none carry into the next save opened (singleplayer). */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) { ACTIVE.clear(); }

    /** Nothing from outside gets through to the two inside. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (ACTIVE.isEmpty() || event.getEntity().level().isClientSide) return;
        LivingEntity v = event.getEntity();
        Entity src = event.getSource().getEntity(), direct = event.getSource().getDirectEntity();
        for (Shield s : ACTIVE) {
            if ((v != s.owner && v != s.downed) || !inside(s, v) || !foe(s, src)) continue;
            if (src != null && inside(s, src) && direct == src) continue;           // (already inside the dome: it's thrown out next tick)
            event.setCanceled(true);
            blockedHits++;
            if (src instanceof LivingEntity a && direct == src) {
                Vec3 out = new Vec3(a.getX() - s.center.x, 0, a.getZ() - s.center.z);
                out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
                a.setDeltaMovement(out.x * 1.1, 0.4, out.z * 1.1);
                a.hurtMarked = true;
            }
            return;
        }
    }
}
