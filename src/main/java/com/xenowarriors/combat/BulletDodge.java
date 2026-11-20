package com.xenowarriors.combat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.compat.GunLevel;
import com.xenowarriors.compat.TaczGuns;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Blocking against gunfire. Someone blocking (a shield up, Epic Fight's guard, DragonMineZ's guard) whose gun level is
 * higher than the shooter's can weave out of the way of a bullet (a bob-and-weave, drawn by DodgeClient), and from gun
 * level {@value #REFLECT_LEVEL} sometimes knocks it straight back at whoever fired it. The bigger the gap in gun
 * level, the better the odds - but never every shot: a single shot gets through at least one time in ten, and a gun
 * firing fast (3+ bullets at them in under a second) gets through at least one time in three, so a shooter always has a
 * chance. Bots too: a bot under fire whose gun level beats the shooter's puts its guard up for a couple of seconds
 * (the first bullet still lands), and bots shooting count with their own gun level. Shooters without a gun level
 * (soldiers, mobs) count as level {@value #NPC_LEVEL}.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BulletDodge {
    private BulletDodge() {}

    public static final int REFLECT_LEVEL = 300, NPC_LEVEL = 25, RAPID = 3, RAPID_TICKS = 18;
    /** The most of a burst that can be avoided: single shots, and rapid fire. */
    public static final float CAP = 0.9F, CAP_RAPID = 0.65F;
    /** (Tests) dodged, reflected, let through. */
    public static int dodged, reflected, through;
    /** (Tests) treat these as blocking. */
    public static final java.util.Set<UUID> FORCE_GUARD = new java.util.HashSet<>();
    /** (Tests) the next roll (null: random). */
    public static Float forceRoll;

    /** Players' guards the server can't see itself (Epic Fight's guard), from their client (GuardStatePacket). */
    private static final Map<UUID, Boolean> CLIENT_GUARD = new HashMap<>();
    /** A bot's guard against gunfire: until when. */
    private static final Map<UUID, Long> BOT_GUARD = new HashMap<>();
    /** Bullets at each target lately (game times), for telling rapid fire. */
    private static final Map<UUID, ArrayDeque<Long>> SHOTS = new HashMap<>();
    /** A bullet already avoided by someone: it can't hit them on its way past. */
    private static final Map<Integer, Integer> PASSED = new HashMap<>();
    /** Which way they last weaved (they alternate). */
    private static final Map<UUID, Integer> SIDE = new HashMap<>();

    public static void clientGuard(ServerPlayer p, boolean on) { if (on) CLIENT_GUARD.put(p.getUUID(), true); else CLIENT_GUARD.remove(p.getUUID()); }

    /** Blocking right now. */
    public static boolean blocking(LivingEntity e) {
        if (FORCE_GUARD.contains(e.getUUID())) return true;
        if (e.isBlocking() || e.isUsingItem() && e.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem) return true;
        if (e instanceof PartyBotEntity b) {
            Long until = BOT_GUARD.get(b.getUUID());
            return until != null && until >= b.level().getGameTime();
        }
        if (e instanceof Player p) {
            if (Boolean.TRUE.equals(CLIENT_GUARD.get(p.getUUID()))) return true;
            return com.xenowarriors.client.compat.DmzCompat.blocking(p);      // (reflection only: safe on a server)
        }
        return false;
    }

    /** Gun level of a defender or shooter (players, bots); others count as {@link #NPC_LEVEL}. */
    public static int level(Entity e) {
        if (e instanceof PartyBotEntity b) return GunLevel.level(b);
        if (e instanceof Player p && !(p instanceof net.minecraftforge.common.util.FakePlayer)) return GunLevel.level(p);
        if (e instanceof net.minecraftforge.common.util.FakePlayer fp) {
            PartyBotEntity b = com.xenowarriors.bot.PartyBots.botOf(fp);
            if (b != null) return GunLevel.level(b);
        }
        return NPC_LEVEL;
    }

    /** {dodge, reflect} chances for this defender's gun level against this shooter's, capped for single or rapid fire. */
    public static float[] chances(int def, int shot, boolean rapid) {
        int gap = def - shot;
        if (gap <= 0) return new float[]{0F, 0F};
        float dodge = Mth.clamp(0.25F + gap * 0.003F, 0.25F, 0.75F);
        float reflect = def >= REFLECT_LEVEL ? Mth.clamp(0.15F + (def - REFLECT_LEVEL) * 0.0006F, 0.15F, 0.4F) : 0F;
        float cap = rapid ? CAP_RAPID : CAP;
        if (dodge + reflect > cap) {                                            // (the share that gets through stays)
            float k = cap / (dodge + reflect);
            dodge *= k; reflect *= k;
        }
        return new float[]{dodge, reflect};
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide || !TaczGuns.isBullet(e.getSource().getDirectEntity())) return;
        if (!(t instanceof Player || t instanceof PartyBotEntity) || t.isSpectator()) return;
        Entity bullet = e.getSource().getDirectEntity();
        Integer passed = PASSED.get(bullet.getId());
        if (passed != null && passed == t.getId()) { e.setCanceled(true); return; }     // (already avoided: it flies on past)
        Entity shooter = e.getSource().getEntity();
        long now = t.level().getGameTime();
        // how fast it's coming
        ArrayDeque<Long> q = SHOTS.computeIfAbsent(t.getUUID(), k -> new ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && q.peekFirst() < now - RAPID_TICKS) q.pollFirst();
        boolean rapid = q.size() >= RAPID;
        int def = level(t), shot = level(shooter);
        // a bot under fire from a weaker shooter puts its guard up (this bullet still lands)
        if (t instanceof PartyBotEntity b && def > shot && !b.downed() && !blocking(b)) {
            BOT_GUARD.put(b.getUUID(), now + 50);
            through++;
            return;
        }
        if (t instanceof PartyBotEntity b && def > shot && !b.downed()) BOT_GUARD.put(b.getUUID(), now + 50);   // (keeps it up while the shooting goes on)
        if (!blocking(t) || def <= shot) return;
        if (t instanceof PartyBotEntity b && b.downed() || t instanceof Player p && com.xenowarriors.party.Duels.isDowned(p)) return;
        // only what comes from in front of them
        Vec3 from = bullet.getDeltaMovement().lengthSqr() > 1.0E-4 ? bullet.getDeltaMovement().scale(-1).normalize()
                : bullet.position().subtract(t.position()).normalize();
        Vec3 look = t.getViewVector(1F);
        if (new Vec3(from.x, 0, from.z).normalize().dot(new Vec3(look.x, 0, look.z).normalize()) < -0.2) { through++; return; }
        float[] c = chances(def, shot, rapid);
        float roll = forceRoll != null ? forceRoll : t.getRandom().nextFloat();
        int kind;
        if (roll < c[1]) kind = 1;
        else if (roll < c[0] + c[1]) kind = 0;
        else { through++; return; }
        e.setCanceled(true);
        PASSED.put(bullet.getId(), t.getId());
        if (PASSED.size() > 512) PASSED.clear();
        int side = -SIDE.getOrDefault(t.getUUID(), -1);
        SIDE.put(t.getUUID(), side);
        com.xenowarriors.network.ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.DodgeAnimPacket(t.getId(), side, kind), t);
        ServerLevel l = (ServerLevel) t.level();
        if (kind == 0) {
            dodged++;
            l.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8F, 1.5F);
            l.sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY() + 1.0, t.getZ(), 3, 0.25, 0.3, 0.25, 0.01);
        } else {
            reflected++;
            reflect(l, t, bullet, shooter, e.getAmount());
        }
    }

    /** Knocked back at the shooter: a streak of sparks back to them, and they take the shot. */
    private static void reflect(ServerLevel l, LivingEntity t, Entity bullet, Entity shooter, float amount) {
        bullet.discard();
        Vec3 a = t.getEyePosition().add(t.getViewVector(1F).scale(0.6));
        l.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 2.0F);
        l.sendParticles(ParticleTypes.CRIT, a.x, a.y, a.z, 8, 0.1, 0.1, 0.1, 0.4);
        if (!(shooter instanceof LivingEntity s) || !s.isAlive() || s.level() != l || s.distanceToSqr(t) > 64 * 64) return;
        if (Allies.isAlly(t, s)) return;
        Vec3 b = s.getBoundingBox().getCenter();
        Vec3 d = b.subtract(a);
        int n = (int) Math.min(48, d.length() * 2);
        for (int i = 1; i <= n; i++) { Vec3 p = a.add(d.scale(i / (double) n)); l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0); }
        if (!s.hasLineOfSight(t)) return;
        s.invulnerableTime = 0;
        s.hurt(l.damageSources().thrown(t, t), Math.max(1F, amount));
    }

    /** (Tests) a bot's guard against gunfire is up. */
    public static boolean botGuardForTest(PartyBotEntity b) { Long u = BOT_GUARD.get(b.getUUID()); return u != null && u >= b.level().getGameTime(); }
}
