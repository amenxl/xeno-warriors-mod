package com.xenowarriors.gov;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * Who's in which healing pod. Getting in: you stand inside, eyes closed (the client shows the sleeping face while the
 * pod is occupied and closed), green light and bubbles heal you over time and wash bad effects away. Once you're
 * healed (at least 3 seconds, at most 12) the glass doors swing open, your eyes open, and a moment later you step out.
 * Getting hit (or sneaking) wakes you early. One occupant at a time, and a 5 second cooldown after each use.
 * Party bots under 35% health (out of combat) and the mod's human NPCs (Gov agents, Sky Rebellion members, humans)
 * walk to a free pod within 24 blocks and use it too.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HealingPods {
    public static int SLEEP_MIN = 60, SLEEP_MAX = 240, WAKE = 25, COOLDOWN = 100, CLOSE_AFTER = 20;
    public static final float LOW = 0.35F;
    /** Party bots go sooner: under this share of their health (out of a fight) they head for a free pod nearby. */
    public static final float BOT_LOW = 0.8F;
    public static final int SEEK_RADIUS = 24;
    private static final UUID STILL = UUID.fromString("9e3c5a10-2b4f-4c1e-8d7a-6f5e4d3c2b11");

    private static final class Session {
        final GlobalPos at; final UUID who; final long start; final int fixed; final Runnable done;
        long wakeAt = -1;
        Session(GlobalPos at, UUID who, long start, int fixed, Runnable done) { this.at = at; this.who = who; this.start = start; this.fixed = fixed; this.done = done; }
    }

    private static final Map<GlobalPos, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Session> BY = new HashMap<>();
    private static final Map<GlobalPos, Long> COOL = new HashMap<>(), CLOSE = new HashMap<>();
    /** Pods a bot / NPC is walking to (so two don't head for the same one). */
    private static final Map<GlobalPos, UUID> RESERVED = new HashMap<>();

    private HealingPods() {}

    private static GlobalPos key(ServerLevel l, BlockPos p) { return GlobalPos.of(l.dimension(), p.immutable()); }

    /** A pod is healing right now (the Tower doesn't count it against a floor's healing). */
    public static boolean podHealing;

    public static boolean inUse(ServerLevel l, BlockPos base) { return SESSIONS.containsKey(key(l, base)); }
    public static boolean inPod(Entity e) { return BY.containsKey(e.getUUID()); }
    /** Asleep in a pod (not yet waking). */
    public static boolean asleep(Entity e) { Session s = BY.get(e.getUUID()); return s != null && s.wakeAt < 0; }

    private static boolean pod(BlockState s) { return s.getBlock() instanceof HealingPodBlock && s.getValue(HealingPodBlock.HALF) == DoubleBlockHalf.LOWER; }

    private static Vec3 center(BlockPos base) { return new Vec3(base.getX() + .5, base.getY() + 3 / 16.0, base.getZ() + .5); }

    /** Free to use right now (by this entity): a pod, nobody in it, not cooling down, not claimed by someone else walking over. */
    public static boolean free(ServerLevel l, BlockPos base, Entity by) {
        if (!pod(l.getBlockState(base))) return false;
        GlobalPos k = key(l, base);
        if (SESSIONS.containsKey(k)) return false;
        Long c = COOL.get(k);
        if (c != null && c > l.getGameTime()) return false;
        UUID r = RESERVED.get(k);
        return r == null || by == null || r.equals(by.getUUID());
    }

    /**
     * Puts someone in the pod at base. fixed > 0: they sleep exactly that many ticks and the pod doesn't heal them
     * (Journey's healing step heals the party itself); done runs once they've stepped out. Returns why not, or null.
     */
    public static String start(ServerLevel l, BlockPos base, LivingEntity e, int fixed, Runnable done) {
        BlockState st = l.getBlockState(base);
        if (!pod(st)) return "That's not a healing pod";
        GlobalPos k = key(l, base);
        if (SESSIONS.containsKey(k)) return "Someone is already inside this pod";
        if (BY.containsKey(e.getUUID())) return null;
        Long c = COOL.get(k);
        if (c != null && c > l.getGameTime()) return "The pod is recharging - " + Math.max(1, (c - l.getGameTime()) / 20) + "s";
        if (!e.isAlive()) return "";
        if (e instanceof ServerPlayer p && com.xenowarriors.party.Duels.isKnockedOut(p)) return "You can't get in while you're down";
        // the modes have their own healing rules (a Journey heals only at its own steps, the Tower per floor, VR challenges):
        // a pod there (a Journey step's, left standing) doesn't work on its own
        if (fixed <= 0 && (com.xenowarriors.tower.Tower.is(l) || com.xenowarriors.vr.Vr.is(l))) return "The pod doesn't work here";
        if (e.isPassenger()) e.stopRiding();
        RESERVED.remove(k);
        Session s = new Session(k, e.getUUID(), l.getGameTime(), fixed, done);
        SESSIONS.put(k, s);
        BY.put(e.getUUID(), s);
        CLOSE.remove(k);
        Direction f = st.getValue(HealingPodBlock.FACING);
        Vec3 c0 = center(base);
        if (e instanceof ServerPlayer sp0) sp0.connection.teleport(c0.x, c0.y, c0.z, f.toYRot(), 0F);     // (facing out of the glass)
        else e.teleportTo(c0.x, c0.y, c0.z);
        e.setYRot(f.toYRot()); e.setYHeadRot(f.toYRot()); e.setYBodyRot(f.toYRot()); e.setXRot(0);
        e.setDeltaMovement(Vec3.ZERO);
        if (e instanceof Mob m) { m.getNavigation().stop(); m.setTarget(null); }
        var sp = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (sp != null && sp.getModifier(STILL) == null) sp.addTransientModifier(new AttributeModifier(STILL, "In a healing pod", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        e.addEffect(new MobEffectInstance(MobEffects.JUMP, (fixed > 0 ? fixed : SLEEP_MAX) + WAKE + 20, 128, false, false));    // (no jumping out)
        HealingPodBlock.set(l, base, false, true);
        l.playSound(null, base, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
        l.playSound(null, base, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.3F);
        if (e instanceof ServerPlayer p && fixed <= 0) p.displayClientMessage(net.minecraft.network.chat.Component.literal("Resting in the healing pod... (sneak to get out)").withStyle(net.minecraft.ChatFormatting.GREEN), true);
        return null;
    }

    /** The pod was broken or replaced: whoever was in it is let go. */
    static void broken(ServerLevel l, BlockPos base) {
        GlobalPos k = key(l, base);
        Session s = SESSIONS.get(k);
        if (s != null) release(l.getServer(), s, false);
        COOL.remove(k); CLOSE.remove(k); RESERVED.remove(k);
    }

    /** Wake up now (hit, sneaking out): the doors open straight away. */
    public static void wake(LivingEntity e) {
        Session s = BY.get(e.getUUID());
        if (s == null || s.wakeAt >= 0 || !(e.level() instanceof ServerLevel l)) return;
        open(l, s, l.getGameTime() - WAKE + 6);
    }

    private static void open(ServerLevel l, Session s, long at) {
        s.wakeAt = at;
        BlockState st = l.getBlockState(s.at.pos());
        if (pod(st)) HealingPodBlock.set(l, s.at.pos(), true, true);
        l.playSound(null, s.at.pos(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.2F);
        l.playSound(null, s.at.pos(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 1F, 1F);
        Vec3 c = center(s.at.pos());
        l.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1, c.z, 12, .35, .7, .35, .02);
    }

    /** Out of the pod: steps out the front (when there's room), the doors close a moment later. */
    private static void release(MinecraftServer server, Session s, boolean stepOut) {
        SESSIONS.remove(s.at);
        BY.remove(s.who);
        ServerLevel l = server.getLevel(s.at.dimension());
        if (l == null) return;
        long now = l.getGameTime();
        COOL.put(s.at, now + COOLDOWN);
        CLOSE.put(s.at, now + CLOSE_AFTER);
        BlockState st = l.getBlockState(s.at.pos());
        if (pod(st)) HealingPodBlock.set(l, s.at.pos(), true, false);
        Entity e = l.getEntity(s.who);
        // (a player who left this dimension mid-rest - e.g. retreating from a Journey's healing step - still gets the
        // "held still" speed modifier taken off, or they'd be stuck unable to walk; they just don't step out of the pod)
        if (e == null) e = server.getPlayerList().getPlayer(s.who);
        if (e instanceof LivingEntity le) {
            var sp = le.getAttribute(Attributes.MOVEMENT_SPEED);
            if (sp != null) sp.removeModifier(STILL);
            le.removeEffect(MobEffects.JUMP);
            if (stepOut && pod(st) && le.level() == l) {
                Direction f = st.getValue(HealingPodBlock.FACING);
                BlockPos out = s.at.pos().relative(f);
                if (l.getBlockState(out).getCollisionShape(l, out).isEmpty() && l.getBlockState(out.above()).getCollisionShape(l, out.above()).isEmpty()) {
                    Vec3 c = center(s.at.pos());
                    le.teleportTo(c.x + f.getStepX() * 1.3, s.at.pos().getY(), c.z + f.getStepZ() * 1.3);
                }
            }
        }
        if (s.done != null) try { s.done.run(); } catch (Throwable t) { XenoWarriors.LOGGER.error("healing pod callback", t); }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (!CLOSE.isEmpty()) {
            for (var it = CLOSE.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                ServerLevel l = server.getLevel(en.getKey().dimension());
                if (l == null) { it.remove(); continue; }
                if (l.getGameTime() < en.getValue()) continue;
                it.remove();
                if (SESSIONS.containsKey(en.getKey())) continue;
                BlockState st = l.getBlockState(en.getKey().pos());
                if (pod(st) && st.getValue(HealingPodBlock.OPEN)) { HealingPodBlock.set(l, en.getKey().pos(), false, false); l.playSound(null, en.getKey().pos(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 1.3F); }
            }
        }
        if (server.getTickCount() % 200 == 0) {
            COOL.values().removeIf(v -> v < server.overworld().getGameTime() - 20 * 60);
            RESERVED.entrySet().removeIf(en -> { ServerLevel l = server.getLevel(en.getKey().dimension()); return l == null || !(l.getEntity(en.getValue()) instanceof LivingEntity le) || !le.isAlive(); });
        }
        for (Session s : new ArrayList<>(SESSIONS.values())) tick(server, s);
    }

    private static void tick(MinecraftServer server, Session s) {
        ServerLevel l = server.getLevel(s.at.dimension());
        if (l == null) { SESSIONS.remove(s.at); BY.remove(s.who); return; }
        Entity en = l.getEntity(s.who);
        BlockState st = l.getBlockState(s.at.pos());
        if (!(en instanceof LivingEntity e) || !e.isAlive() || en.isRemoved() || !pod(st)) { release(server, s, false); return; }
        long now = l.getGameTime(), t = now - s.start;
        Vec3 c = center(s.at.pos());
        // held in place, facing out
        if (e.position().distanceToSqr(c) > 0.3 * 0.3) {
            if (e.position().distanceToSqr(c) > 4 * 4) { release(server, s, false); return; }     // (teleported away)
            e.teleportTo(c.x, c.y, c.z);
        }
        e.setDeltaMovement(Vec3.ZERO);
        if (e instanceof Mob m) { m.getNavigation().stop(); if (s.wakeAt < 0) m.setTarget(null); }
        if (s.wakeAt < 0) {
            if (s.fixed <= 0 && t % 10 == 0) {
                if (e.getHealth() < e.getMaxHealth()) { podHealing = true; try { e.heal(Math.max(1.5F, e.getMaxHealth() * 0.04F)); } finally { podHealing = false; } }
                for (var inst : new ArrayList<>(e.getActiveEffects()))
                    if (inst.getEffect().getCategory() == MobEffectCategory.HARMFUL) e.removeEffect(inst.getEffect());
                if (e.isOnFire()) e.clearFire();
            }
            if (t % 4 == 0) {          // soft green glow and bubbles rising past them
                l.sendParticles(new DustParticleOptions(new Vector3f(.35F, 1F, .55F), 0.9F), c.x, c.y + 0.3 + l.random.nextDouble() * 1.9, c.z, 3, 0.28, 0.1, 0.28, 0);
                l.sendParticles(ParticleTypes.BUBBLE_POP, c.x + (l.random.nextDouble() - .5) * .6, c.y + 0.2 + l.random.nextDouble() * 2, c.z + (l.random.nextDouble() - .5) * .6, 1, 0, .05, 0, 0.01);
            }
            if (t % 20 == 0) l.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1.2, c.z, 2, .25, .5, .25, 0);
            if (t % 40 == 0) l.playSound(null, s.at.pos(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.3F);
            boolean done = s.fixed > 0 ? t >= s.fixed : (t >= SLEEP_MIN && e.getHealth() >= e.getMaxHealth()) || t >= SLEEP_MAX;
            boolean sneak = e instanceof ServerPlayer p && p.isShiftKeyDown() && t > 10 && s.fixed <= 0;
            if (done || sneak) open(l, s, now);
        } else if (now - s.wakeAt >= WAKE) {
            release(server, s, true);
            l.playSound(null, s.at.pos(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.4F, 1.6F);
            if (en instanceof ServerPlayer p && s.fixed <= 0) p.displayClientMessage(net.minecraft.network.chat.Component.literal("Healed!").withStyle(net.minecraft.ChatFormatting.GREEN), true);
        }
    }

    /** A hit wakes you. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide && event.getSource().getEntity() != null && event.getSource().getEntity() != event.getEntity()) wake(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Session s = BY.get(event.getEntity().getUUID());
        if (s != null && event.getEntity().getServer() != null) release(event.getEntity().getServer(), s, false);
    }

    /** Nothing carries over into the next world (singleplayer: same JVM, other save). */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        SESSIONS.clear(); BY.clear(); COOL.clear(); CLOSE.clear(); RESERVED.clear();
    }

    // ------------------------------------------------------------------------------------------------ finding a pod

    /** The nearest pod (its lower half) this entity could use within the radius, or null. Skips chunk sections with no pod in them. */
    public static BlockPos nearestFree(ServerLevel l, Entity by, int radius) {
        BlockPos o = by.blockPosition();
        BlockPos best = null; double bd = Double.MAX_VALUE;
        int minY = Math.max(l.getMinBuildHeight(), o.getY() - 8), maxY = Math.min(l.getMaxBuildHeight() - 1, o.getY() + 8);
        for (int cx = (o.getX() - radius) >> 4; cx <= (o.getX() + radius) >> 4; cx++)
            for (int cz = (o.getZ() - radius) >> 4; cz <= (o.getZ() + radius) >> 4; cz++) {
                LevelChunk ch = l.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (int sy = l.getSectionIndex(minY); sy <= l.getSectionIndex(maxY); sy++) {
                    if (sy < 0 || sy >= ch.getSections().length) continue;
                    LevelChunkSection sec = ch.getSections()[sy];
                    if (sec.hasOnlyAir() || !sec.maybeHas(HealingPods::pod)) continue;
                    int y0 = l.getMinBuildHeight() + sy * 16;
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                        if (!pod(sec.getBlockState(x, y, z))) continue;
                        BlockPos p = new BlockPos(cx * 16 + x, y0 + y, cz * 16 + z);
                        double d = p.distSqr(o);
                        if (d < bd && d <= radius * radius && free(l, p, by)) { bd = d; best = p; }
                    }
                }
            }
        return best;
    }

    // ------------------------------------------------------------------------------------------------ bots and NPCs

    /** Party bots and the mod's human NPCs learn to go to a pod when they're low. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof PathfinderMob m)) return;
        boolean bot = m instanceof com.xenowarriors.bot.PartyBotEntity;
        if (!bot && !(m instanceof GovAgentEntity) && !(m instanceof com.xenowarriors.sr.SrMemberEntity) && !(m instanceof com.xenowarriors.npc.HumanNpcEntity)) return;
        if (m.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof SeekPod)) return;
        m.goalSelector.addGoal(0, new SeekPod(m, bot));
    }

    /** Why this one shouldn't go for a pod right now (null = it may). */
    static String why(PathfinderMob m, boolean bot) {
        if (m.getHealth() >= m.getMaxHealth() * (bot ? BOT_LOW : LOW)) return "healthy";
        if (inPod(m)) return "in a pod";
        if ((com.xenowarriors.tower.Tower.is(m.level()) && !bot) || com.xenowarriors.vr.Vr.is(m.level())) return "in a mode";   // (bots use the Tower's pods - the ones that turn up, or ones placed in creative)
        if (m.getPersistentData().getBoolean("xw_tower") || m.getPersistentData().getBoolean("xw_journey") || com.xenowarriors.tower.TowerBosses.isBoss(m)) return "mode enemy";
        long since = m.tickCount - m.getLastHurtByMobTimestamp();
        if (bot) {
            var b = (com.xenowarriors.bot.PartyBotEntity) m;
            if (b.downed() || b.journey() || b.sparring() || b.isPassenger()) return "busy";
            if ((m.getTarget() != null && m.getTarget().isAlive()) || (m.getLastHurtByMob() != null && since < 100)) return "in combat";
        } else if (m.getLastHurtByMob() != null && since < 60) return "in combat";
        return null;
    }

    /** Walk to the nearest free pod and get in. */
    public static final class SeekPod extends Goal {
        private final PathfinderMob m; private final boolean bot;
        private BlockPos pod; private int ticks; private long nextLook;
        SeekPod(PathfinderMob m, boolean bot) { this.m = m; this.bot = bot; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override
        public boolean canUse() {
            if (!(m.level() instanceof ServerLevel l) || l.getGameTime() < nextLook) return false;
            nextLook = l.getGameTime() + 20 + m.getRandom().nextInt(10);
            if (why(m, bot) != null) return false;
            pod = nearestFree(l, m, SEEK_RADIUS);
            if (pod == null) { nextLook += 80; return false; }
            return true;
        }

        @Override
        public void start() {
            ticks = 0;
            RESERVED.put(key((ServerLevel) m.level(), pod), m.getUUID());
            m.setTarget(null);
            m.getNavigation().moveTo(pod.getX() + .5, pod.getY(), pod.getZ() + .5, 1.15);
        }

        @Override
        public boolean canContinueToUse() {
            if (pod == null || !(m.level() instanceof ServerLevel l)) return false;
            if (inPod(m)) return true;                                 // (holds still in the pod)
            if (ticks > 400 || !free(l, pod, m)) return false;
            if (bot && ((com.xenowarriors.bot.PartyBotEntity) m).downed()) return false;
            return m.getLastHurtByMob() == null || m.tickCount - m.getLastHurtByMobTimestamp() > 20 || ticks < 20;   // (attacked on the way: fight back)
        }

        @Override
        public void tick() {
            ticks++;
            if (inPod(m) || pod == null) return;
            ServerLevel l = (ServerLevel) m.level();
            double dx = m.getX() - (pod.getX() + .5), dz = m.getZ() - (pod.getZ() + .5);
            m.getLookControl().setLookAt(pod.getX() + .5, pod.getY() + 1.2, pod.getZ() + .5);
            if (dx * dx + dz * dz < 1.6 * 1.6 && Math.abs(m.getY() - pod.getY()) < 1.6) {
                String why = HealingPods.start(l, pod, m, -1, null);
                if (why != null) { RESERVED.remove(key(l, pod), m.getUUID()); pod = null; }
                return;
            }
            if (ticks % 10 == 0 && (m.getNavigation().isDone() || m.getNavigation().isStuck())) {
                // stand right in front of the pod
                Direction f = l.getBlockState(pod).getBlock() instanceof HealingPodBlock ? l.getBlockState(pod).getValue(HealingPodBlock.FACING) : Direction.NORTH;
                BlockPos front = pod.relative(f);
                if (!m.getNavigation().moveTo(front.getX() + .5, front.getY(), front.getZ() + .5, 1.15)) m.getNavigation().moveTo(pod.getX() + .5, pod.getY(), pod.getZ() + .5, 1.15);
            }
        }

        @Override
        public void stop() {
            if (pod != null && m.level() instanceof ServerLevel l) RESERVED.remove(key(l, pod), m.getUUID());
            pod = null;
            m.getNavigation().stop();
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }
    }
}
