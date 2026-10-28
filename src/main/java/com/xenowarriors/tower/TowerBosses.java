package com.xenowarriors.tower;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Tower boss floors (every 10th floor): one lore boss, rotating - a Government Commander with every human mod, a named
 * Sky Rebellion fighter, an Order of Assassins master (Season 3 unlocked; otherwise skipped), and a Dark Possessed brute
 * that calls corrupted souls. They get tougher each time round. Each has a special move every 8 seconds and gets
 * enraged at half health. (Ultima is saved for his own boss fight.)
 */
public final class TowerBosses {
    public static final int GOV = 0, SR = 1, ASSASSIN = 2, BRUTE = 3;
    private static final String[] TITLE = {"Government Commander", "", "Order Assassin Master", "Dark Possessed Brute"};
    private static final BossEvent.BossBarColor[] COLOR = {BossEvent.BossBarColor.BLUE, BossEvent.BossBarColor.RED, BossEvent.BossBarColor.WHITE, BossEvent.BossBarColor.PURPLE};

    private static final class Boss {
        final UUID id; final int kind; final ServerBossEvent bar; final BlockPos center; final List<UUID> players;
        boolean enraged; int tick;
        Boss(UUID id, int kind, ServerBossEvent bar, BlockPos center, List<UUID> players) { this.id = id; this.kind = kind; this.bar = bar; this.center = center; this.players = players; }
    }
    private static final Map<UUID, Boss> LIVE = new HashMap<>();
    private static long lastTick = -1;
    private static List<Mob> lastSpawned = List.of();

    private TowerBosses() {}

    /** Which boss a boss floor gets. */
    public static int kindFor(int floor, net.minecraft.server.MinecraftServer server) {
        int[] order = com.xenowarriors.character.Seasons.unlocked(server, 3) ? new int[]{GOV, SR, ASSASSIN, BRUTE} : new int[]{GOV, SR, BRUTE};
        return order[Math.floorMod(floor / 10 - 1, order.length)];
    }

    public static String name(int kind, Mob boss) { return kind == SR ? boss.getDisplayName().getString() : TITLE[kind]; }

    /** Spawns this floor's boss (and two helpers); returns everything spawned. */
    public static List<Mob> spawn(ServerLevel tl, BlockPos c, int floor, List<ServerPlayer> players) {
        int kind = kindFor(floor, tl.getServer());
        int round = floor / 10;                                  // 1, 2, 3...
        float hp = 160F * (1F + 0.55F * (round - 1)), dmg = 9F + 2.5F * (round - 1);
        List<Mob> out = new ArrayList<>();
        Mob boss = switch (kind) {
            case GOV -> {
                var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(tl);
                if (g != null) { at(g, c, 0, -8); g.setup(true, 0, c); g.makeCommander(hp, dmg); }
                yield g;
            }
            case SR -> {
                var m = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(tl);
                if (m != null) {
                    at(m, c, 0, -8);
                    String[] pick = {"logan", "kill", "keita", "maddie", "senju"};
                    String who = pick[Math.floorMod(round / 3, pick.length)];
                    int idx = java.util.Arrays.asList(com.xenowarriors.sr.SrMemberEntity.NAMED).indexOf(who);
                    m.setup(2 + Math.max(0, idx), c);
                    scale(m, hp / 200F * 1.1F, dmg);
                }
                yield m;
            }
            case ASSASSIN -> {
                Mob a = create(tl, "xenowarriors:order_assassin");
                if (a != null) { at(a, c, 0, -8); prep(tl, a); setStats(a, hp * 0.85F, dmg * 1.15F); }
                yield a;
            }
            default -> {
                Mob b = create(tl, "xenowarriors:dark_possessed");
                if (b != null) { at(b, c, 0, -8); prep(tl, b); setStats(b, hp * 1.25F, dmg); }
                yield b;
            }
        };
        if (boss == null) return out;
        boss.setPersistenceRequired();
        boss.getPersistentData().putBoolean("xw_tower", true);
        boss.getPersistentData().putBoolean("xw_tower_boss", true);
        if (kind != SR) boss.setCustomName(Component.literal(TITLE[kind]).withStyle(ChatFormatting.GOLD));
        if (!players.isEmpty()) boss.setTarget(players.get(0));
        tl.addFreshEntity(boss);
        out.add(boss);
        ServerBossEvent bar = null;
        if (kind != SR) {           // (named SR fighters already have their own bar)
            bar = new ServerBossEvent(Component.literal(TITLE[kind] + " - Floor " + floor), COLOR[kind], BossEvent.BossBarOverlay.NOTCHED_10);
            for (ServerPlayer p : players) bar.addPlayer(p);
        }
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer p : players) ids.add(p.getUUID());
        LIVE.put(boss.getUUID(), new Boss(boss.getUUID(), kind, bar, c, ids));
        // two helpers to start with
        for (int i = 0; i < 2; i++) { Mob h = helper(tl, kind, c, i == 0 ? -4 : 4, -6, players); if (h != null) out.add(h); }
        tl.playSound(null, c, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7F, 1.2F);
        tl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1, boss.getZ(), 1, 0, 0, 0, 0);
        for (ServerPlayer p : players) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 50, 10));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(name(kind, boss)).withStyle(ChatFormatting.GOLD)));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("BOSS FLOOR " + floor).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        }
        return out;
    }

    private static void at(Entity e, BlockPos c, int dx, int dz) { e.moveTo(c.getX() + .5 + dx, Tower.Y + 1, c.getZ() + .5 + dz, 0, 0); }

    private static Mob create(ServerLevel tl, String id) {
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id));
        if (t == null) return null;
        Entity e = t.create(tl);
        return e instanceof Mob m ? m : null;
    }

    private static void prep(ServerLevel tl, Mob m) {
        try { m.finalizeSpawn(tl, tl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
    }

    private static void setStats(Mob m, float hp, float dmg) {
        var h = m.getAttribute(Attributes.MAX_HEALTH); if (h != null) h.setBaseValue(hp);
        var d = m.getAttribute(Attributes.ATTACK_DAMAGE); if (d != null) d.setBaseValue(dmg);
        var a = m.getAttribute(Attributes.ARMOR); if (a != null) a.setBaseValue(Math.max(a.getBaseValue(), 8));
        var k = m.getAttribute(Attributes.KNOCKBACK_RESISTANCE); if (k != null) k.setBaseValue(0.7);
        m.setHealth(m.getMaxHealth());
    }

    private static void scale(Mob m, float hpMult, float dmg) {
        var h = m.getAttribute(Attributes.MAX_HEALTH); if (h != null) h.setBaseValue(h.getBaseValue() * hpMult);
        var d = m.getAttribute(Attributes.ATTACK_DAMAGE); if (d != null) d.setBaseValue(Math.max(d.getBaseValue(), dmg));
        m.setHealth(m.getMaxHealth());
    }

    private static Mob helper(ServerLevel tl, int kind, BlockPos c, int dx, int dz, List<ServerPlayer> players) {
        Mob h = switch (kind) {
            case GOV -> { var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(tl); if (g != null) { at(g, c, dx, dz); g.setup(true, 1, c); } yield g; }
            case SR -> { var m = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(tl); if (m != null) { at(m, c, dx, dz); m.setup(0, c); } yield m; }
            case ASSASSIN -> { Mob a = create(tl, "xenowarriors:order_assassin"); if (a != null) { at(a, c, dx, dz); prep(tl, a); } yield a; }
            default -> { Mob s = create(tl, "xenowarriors:corrupted_soul"); if (s != null) { at(s, c, dx, dz); prep(tl, s); } yield s; }
        };
        if (h == null) return null;
        h.setPersistenceRequired();
        h.getPersistentData().putBoolean("xw_tower", true);
        if (!players.isEmpty()) h.setTarget(players.get(tl.random.nextInt(players.size())));
        tl.addFreshEntity(h);
        return h;
    }

    /** Called by the Tower every 5 ticks: boss bars, special moves, enrage; returns helpers spawned (to count as wave enemies). */
    public static List<Mob> tick(ServerLevel tl) {
        if (tl.getGameTime() == lastTick) return lastSpawned;          // (the Tower and Journey mode both tick bosses: once per tick, same answer)
        List<Mob> spawned = new ArrayList<>();
        lastTick = tl.getGameTime();
        lastSpawned = spawned;
        Iterator<Boss> it = LIVE.values().iterator();
        while (it.hasNext()) {
            Boss b = it.next();
            Entity e = tl.getEntity(b.id);
            if (!(e instanceof Mob m) || !m.isAlive()) { if (b.bar != null) b.bar.removeAllPlayers(); it.remove(); continue; }
            if (b.bar != null) b.bar.setProgress(Math.max(0F, m.getHealth() / m.getMaxHealth()));
            List<ServerPlayer> ps = new ArrayList<>();
            for (UUID id : b.players) { ServerPlayer p = tl.getServer().getPlayerList().getPlayer(id); if (p != null && p.level() == tl) ps.add(p); }
            if (ps.isEmpty()) continue;
            if (!b.enraged && m.getHealth() < m.getMaxHealth() / 2) {
                b.enraged = true;
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 0, false, true));
                m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, -1, 0, false, true));
                tl.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + 2, m.getZ(), 8, .5, .3, .5, 0);
                for (ServerPlayer p : ps) p.displayClientMessage(Component.literal(name(b.kind, m) + " is enraged!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
            if ((b.tick += 5) % (b.enraged ? 110 : 160) != 0) continue;
            int near = tl.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(b.center).inflate(Tower.ARENA_R + 4, 12, Tower.ARENA_R + 4),
                    x -> x.isAlive() && x.getPersistentData().getBoolean("xw_tower")).size();
            boolean canCall = near <= HELPER_CAP;
            LivingEntity t = m.getTarget() != null && m.getTarget().isAlive() ? m.getTarget() : ps.get(tl.random.nextInt(ps.size()));
            switch (b.kind) {
                case GOV -> {            // calls in a guard and dashes at you
                    if (canCall) { Mob h = helper(tl, GOV, b.center, tl.random.nextInt(9) - 4, tl.random.nextInt(9) - 4, ps); if (h != null) spawned.add(h); }
                    Vec3 d = t.position().subtract(m.position()).normalize();
                    m.setDeltaMovement(d.x * 1.4, 0.2, d.z * 1.4); m.hurtMarked = true;
                    tl.playSound(null, m.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1F, 1.5F);
                    tl.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + .5, m.getZ(), 12, .4, .2, .4, .03);
                }
                case ASSASSIN -> {       // vanishes and reappears behind you
                    Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6));
                    tl.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + 1, m.getZ(), 20, .3, .6, .3, .02);
                    m.teleportTo(behind.x, t.getY(), behind.z);
                    m.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
                    m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 1, false, false));
                    m.setTarget(t);
                    tl.playSound(null, m.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 1.3F);
                }
                case BRUTE -> {          // a shockwave, and corrupted souls crawl out
                    tl.sendParticles(ParticleTypes.SONIC_BOOM, m.getX(), m.getY() + 1, m.getZ(), 1, 0, 0, 0, 0);
                    tl.playSound(null, m.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1F, 0.7F);
                    for (ServerPlayer p : ps) if (p.distanceTo(m) < 6) {
                        p.hurt(m.damageSources().mobAttack(m), 6F);
                        Vec3 d = p.position().subtract(m.position()).normalize();
                        p.push(d.x * 1.4, 0.5, d.z * 1.4); p.hurtMarked = true;
                    }
                    for (int i = 0; i < (b.enraged ? 3 : 2) && canCall; i++) { Mob h = helper(tl, BRUTE, b.center, tl.random.nextInt(11) - 5, tl.random.nextInt(11) - 5, ps); if (h != null) spawned.add(h); }
                }
                default -> {             // SR fighters have their own moves; the boss floor adds a rebel now and then
                    if (canCall && tl.random.nextInt(2) == 0) { Mob h = helper(tl, SR, b.center, tl.random.nextInt(9) - 4, tl.random.nextInt(9) - 4, ps); if (h != null) spawned.add(h); }
                }
            }
        }
        return spawned;
    }

    /** Someone left the Tower: off every boss bar and out of the bosses' reach. */
    public static void removePlayer(ServerPlayer p) {
        for (Boss b : LIVE.values()) { if (b.bar != null) b.bar.removePlayer(p); b.players.remove(p.getUUID()); }
    }

    /** Someone joined a run mid-boss: on its bar too. */
    public static void addPlayer(ServerPlayer p, BlockPos center) {
        for (Boss b : LIVE.values()) if (b.center.equals(center)) { if (b.bar != null) b.bar.addPlayer(p); if (!b.players.contains(p.getUUID())) b.players.add(p.getUUID()); }
    }

    /** Whether this entity is a live Tower boss. */
    public static boolean isBoss(Entity e) { return LIVE.containsKey(e.getUUID()); }

    /** How many helpers a boss may still call in (keeps them from piling up). */
    private static final int HELPER_CAP = 10;

    /** A run ended: drop any bars. */
    public static void clear(BlockPos center) {
        LIVE.values().removeIf(b -> { if (b.center.equals(center)) { if (b.bar != null) b.bar.removeAllPlayers(); return true; } return false; });
    }
}
