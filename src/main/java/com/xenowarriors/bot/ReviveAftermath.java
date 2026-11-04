package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.party.Duels;
import com.xenowarriors.party.Parties;
import com.xenowarriors.party.ReviveCircle;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Going down and getting back up, for players and bot teammates alike:
 * <ul>
 *   <li>Enemies leave someone who's down alone and go for whoever's reviving them (or another teammate nearby).</li>
 *   <li>Revived: 10 seconds of invincibility (glowing, with a golden shimmer), and a shockwave that throws the enemies
 *       around them back and stuns them for 5 seconds (bosses half that). It never touches friends.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ReviveAftermath {
    public static final int INVINCIBLE = 10 * 20, STUN = 5 * 20;
    public static final double RADIUS = 6.0;
    private static final Map<UUID, Long> INVULN = new HashMap<>(), STUNNED = new HashMap<>();
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1F, 0.85F, 0.3F), 1F),
            STAR = new DustParticleOptions(new Vector3f(1F, 0.95F, 0.3F), 0.8F), STAR_W = new DustParticleOptions(new Vector3f(1F, 1F, 1F), 0.8F);

    /** (For the tests) what the last shockwave did. */
    public static String lastBurst = "";

    private ReviveAftermath() {}

    // ---------------------------------------------------------------- who's on whose side

    /** On this (player or bot) teammate's side: party players, their bots, pets and souls. */
    public static boolean allied(LivingEntity who, Entity e) {
        if (e == null || who == null) return false;
        if (e == who) return true;
        if (who instanceof PartyBotEntity b) return PartyBots.friendly(b, e);
        if (!(who instanceof Player p)) return false;
        PartyBotEntity ob = PartyBots.botOf(e);
        if (ob != null) return PartyBots.friendly(ob, p);
        if (e instanceof Player q) return Parties.sameParty(p.getUUID(), q.getUUID());
        if (e instanceof com.xenowarriors.soul.SoulEntity s) return s.ownerId() != null && (s.ownerId().equals(p.getUUID()) || Parties.sameParty(s.ownerId(), p.getUUID()));
        UUID pet = Parties.petOwner(e);
        return pet != null && (pet.equals(p.getUUID()) || Parties.sameParty(pet, p.getUUID()));
    }

    // ---------------------------------------------------------------- enemies leave the downed alone

    /** Someone a mob shouldn't keep after: a downed player or bot. */
    public static boolean down(LivingEntity e) {
        return e instanceof PartyBotEntity b ? b.downed() : e instanceof ServerPlayer p && Duels.isDowned(p);
    }

    /** Who a mob goes for instead of someone who's down: whoever's reviving them, else another teammate close by, else nobody. */
    public static LivingEntity redirect(Mob mob, LivingEntity downed) {
        LivingEntity helper = downed instanceof ServerPlayer p ? ReviveCircle.helperFor(p) : downed instanceof PartyBotEntity b ? ReviveCircle.helperFor(b) : null;
        if (ok(mob, helper)) return helper;
        // a teammate on the way to revive them
        List<LivingEntity> team = teammates(downed);
        for (LivingEntity t : team) if (t instanceof PartyBotEntity b && b.reviving == downed && ok(mob, b)) return b;
        LivingEntity best = null; double bd = 16 * 16;
        for (LivingEntity t : team) {
            if (!ok(mob, t)) continue;
            double d = t.distanceToSqr(mob);
            if (d < bd) { bd = d; best = t; }
        }
        return best;
    }

    private static boolean ok(Mob mob, LivingEntity t) {
        if (t == null || !t.isAlive() || t.level() != mob.level() || down(t) || t == mob) return false;
        if (t instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return mob.canAttack(t) && t.distanceToSqr(mob) < 32 * 32;
    }

    /** The downed one's party: players and bots. */
    private static List<LivingEntity> teammates(LivingEntity downed) {
        List<LivingEntity> out = new ArrayList<>();
        ServerPlayer owner = downed instanceof ServerPlayer p ? p : downed instanceof PartyBotEntity b ? b.owner() : null;
        if (owner == null) return out;
        List<ServerPlayer> members = Parties.inParty(owner) ? Parties.members(owner) : List.of(owner);
        for (ServerPlayer m : members) {
            if (m != downed) out.add(m);
            for (PartyBotEntity b : PartyBots.bots(m)) if (b != downed) out.add(b);
        }
        return out;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob) || event.getNewTarget() == null || !down(event.getNewTarget())) return;
        event.setNewTarget(redirect(mob, event.getNewTarget()));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 5 != 0) return;
        List<LivingEntity> downed = new ArrayList<>();
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) if (Duels.isDowned(p)) downed.add(p);
        for (var set : PartyBots.LIVE.values()) for (PartyBotEntity b : set) if (b.downed() && b.isAlive()) downed.add(b);
        for (LivingEntity d : downed)
            for (Mob m : d.level().getEntitiesOfClass(Mob.class, d.getBoundingBox().inflate(32), m -> m.getTarget() == d))
                m.setTarget(redirect(m, d));
        if (event.getServer().getTickCount() % 200 == 0) {
            long now = event.getServer().overworld().getGameTime();
            INVULN.values().removeIf(t -> t < now);
            STUNNED.values().removeIf(t -> t < now);
        }
    }

    // ---------------------------------------------------------------- back up: invincible, and a shockwave

    /** Someone (a player or a bot) was just revived. */
    public static void revived(LivingEntity who) {
        if (!(who.level() instanceof ServerLevel l)) return;
        long now = l.getGameTime();
        INVULN.put(who.getUUID(), now + INVINCIBLE);
        who.addEffect(new MobEffectInstance(MobEffects.GLOWING, INVINCIBLE, 0, false, false));
        who.clearFire();
        if (who instanceof ServerPlayer p) p.displayClientMessage(Component.literal("Back up! Invincible for 10 seconds").withStyle(ChatFormatting.GOLD), true);
        // the shockwave
        int n = 0, stunned = 0;
        StringBuilder sb = new StringBuilder();
        for (Mob m : l.getEntitiesOfClass(Mob.class, who.getBoundingBox().inflate(RADIUS), m -> m.isAlive() && m.distanceTo(who) <= RADIUS)) {
            if (allied(who, m)) continue;
            boolean hostile = m instanceof Enemy || (m.getTarget() != null && (m.getTarget() == who || allied(who, m.getTarget())));
            if (!hostile) continue;
            n++;
            m.hurt(who instanceof Player p ? l.damageSources().playerAttack(p) : l.damageSources().mobAttack(who), 4F);
            Vec3 away = new Vec3(m.getX() - who.getX(), 0, m.getZ() - who.getZ());
            away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
            double kb = com.xenowarriors.combat.Stagger.boss(m) ? 0.6 : 1.6;
            m.setDeltaMovement(away.x * kb, 0.55, away.z * kb);
            m.hurtMarked = true;
            stun(m, com.xenowarriors.combat.Stagger.boss(m) ? STUN / 2 : STUN);
            stunned++;
            sb.append(m.getType().builtInRegistryHolder().key().location().getPath()).append(' ');
        }
        lastBurst = "hit=" + n + " stunned=" + stunned + " [" + sb.toString().trim() + "]";
        // what it looks like: a ring of air bursting out, a boom and a chime
        for (int i = 0; i < 36; i++) {
            double a = i / 36.0 * Math.PI * 2;
            for (double r : new double[]{1.5, 3.5, 5.5})
                l.sendParticles(ParticleTypes.CLOUD, who.getX() + Math.cos(a) * r, who.getY() + 0.3, who.getZ() + Math.sin(a) * r, 1, 0, 0.05, 0, 0.02);
            others(l, who, GOLD, who.getX() + Math.cos(a) * 1.2, who.getY() + 1, who.getZ() + Math.sin(a) * 1.2, 1, 0, 0.3, 0);
        }
        l.sendParticles(ParticleTypes.EXPLOSION, who.getX(), who.getY() + 0.5, who.getZ(), 1, 0, 0, 0, 0);
        l.playSound(null, who.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.5F);
        l.playSound(null, who.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    public static boolean invulnerable(Entity e) {
        Long t = INVULN.get(e.getUUID());
        return t != null && e.level().getGameTime() < t;
    }

    /** Ticks of invincibility left (for the tests and the HUD). */
    public static int invulnerableTicks(Entity e) {
        Long t = INVULN.get(e.getUUID());
        return t == null ? 0 : (int) Math.max(0, t - e.level().getGameTime());
    }

    // ---------------------------------------------------------------- stun

    /** Stunned: rooted to the spot, can't attack (in melee or with anything it shoots), dazed with stars over its head. */
    public static void stun(LivingEntity e, int ticks) {
        STUNNED.merge(e.getUUID(), e.level().getGameTime() + ticks, Math::max);
        com.xenowarriors.anim.Poses.play(e, com.xenowarriors.anim.Poses.STUNNED, ticks);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
        if (e instanceof Mob m) {
            m.getNavigation().stop();
            for (var f : STUN_FLAGS) m.goalSelector.disableControlFlag(f);      // (no goal that moves, looks or jumps can run: no walking up to hit, no drawing a bow)
            DAZED.add(m.getUUID());
        }
    }

    private static final net.minecraft.world.entity.ai.goal.Goal.Flag[] STUN_FLAGS = {net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE,
            net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK, net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP};
    private static final java.util.Set<UUID> DAZED = new java.util.HashSet<>();

    public static boolean stunned(Entity e) {
        Long t = STUNNED.get(e.getUUID());
        return t != null && e.level().getGameTime() < t;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        if (invulnerable(e) && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) { event.setCanceled(true); return; }
        if (event.getSource().getEntity() instanceof LivingEntity att && stunned(att)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel l)) return;
        if (!stunned(e) && e instanceof Mob m0 && DAZED.remove(e.getUUID())) {       // it's over: back to normal
            for (var f : STUN_FLAGS) m0.goalSelector.enableControlFlag(f);
            com.xenowarriors.anim.Poses.clearState(e);
        }
        if (stunned(e)) {
            if (e instanceof Mob m) {
                m.getNavigation().stop();
                m.setZza(0F); m.setXxa(0F); m.setJumping(false);
                if (m instanceof Creeper c) c.setSwellDir(-1);
                if (m.isUsingItem()) m.stopUsingItem();
            }
            Vec3 v = e.getDeltaMovement();
            if (e.onGround()) e.setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
            if (e.tickCount % 2 == 0) {
                double top = e.getY() + e.getBbHeight() + 0.25;
                for (int s = 0; s < 3; s++) {
                    double a = e.tickCount * 0.35 + s * Math.PI * 2 / 3, r = e.getBbWidth() * 0.6 + 0.2;
                    l.sendParticles(s == 0 ? STAR_W : STAR, e.getX() + Math.cos(a) * r, top, e.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
        }
        if (e.tickCount % 4 == 0 && invulnerable(e))
            others(l, e, GOLD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 2, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5);
    }

    /** Particles for everyone nearby except the one they're on (round a player's own head they'd fill the screen). */
    private static void others(ServerLevel l, LivingEntity on, net.minecraft.core.particles.ParticleOptions o, double x, double y, double z, int n, double dx, double dy, double dz) {
        for (ServerPlayer p : l.players()) if (p != on && p.distanceToSqr(x, y, z) < 64 * 64) l.sendParticles(p, o, false, x, y, z, n, dx, dy, dz, 0);
    }
}
