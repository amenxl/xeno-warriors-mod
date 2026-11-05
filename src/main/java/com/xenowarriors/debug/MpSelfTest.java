package com.xenowarriors.debug;

import com.mojang.authlib.GameProfile;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.skill.SkillManager;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.*;

/**
 * CI only (the server self-test with "mp" among its extras): two players on fake connections play together on the
 * dedicated server - a party with bots, hostiles, every character's skills (and their supers and ultimates), Karma
 * Sense shared, a team ultimate, a teammate downed and warped to, one of them leaving. Catches client-only code reached
 * on a server and packets sent to the wrong player. Results: "XW-MPTEST ..." lines, then "XW-MPTEST OK" (or FAIL).
 */
public final class MpSelfTest {
    private MpSelfTest() {}

    public static boolean wanted() {
        String e = System.getenv("XW_UITEST_EXTRA");
        return e != null && e.contains("mp");
    }

    private static void log(String s) { XenoWarriors.LOGGER.info("XW-MPTEST " + s); }

    private record P(ServerPlayer p, EmbeddedChannel ch, Map<String, Integer> got) {}

    private static final List<P> PLAYERS = new ArrayList<>();
    private static final List<Object[]> STEPS = new ArrayList<>();   // {tick, name, Runnable}
    private static int tick, errors, next;
    private static MinecraftServer server;

    private static void at(int t, String name, Runnable r) { STEPS.add(new Object[]{t, name, r}); }

    private static P join(String name, BlockPos at) {
        ServerLevel lv = server.overworld();
        GameProfile gp = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)), name);
        ServerPlayer sp = new ServerPlayer(server, lv, gp);
        Connection conn = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel ch = new EmbeddedChannel(conn);
        conn.setProtocol(ConnectionProtocol.PLAY);
        server.getPlayerList().placeNewPlayer(conn, sp);
        sp.teleportTo(lv, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
        P p = new P(sp, ch, new TreeMap<>());
        PLAYERS.add(p);
        return p;
    }

    private static String key(Object msg) {
        if (msg instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket c && c.getIdentifier().getNamespace().equals(XenoWarriors.MOD_ID)) {
            var d = c.getData();
            return "xw#" + d.getUnsignedByte(d.readerIndex());
        }
        return null;
    }

    private static void drain() {
        for (P p : PLAYERS) {
            Object o;
            while ((o = p.ch.readOutbound()) != null) {
                try { String k = key(o); if (k != null) p.got.merge(k, 1, Integer::sum); } catch (Throwable ignored) {}
                io.netty.util.ReferenceCountUtil.release(o);
            }
        }
    }

    private static Map<Integer, String> names;

    private static String named(String k) {
        if (names == null) {
            names = new HashMap<>();
            try {
                Object ch = com.xenowarriors.network.ModNetwork.CHANNEL;
                for (var fl : ch.getClass().getDeclaredFields()) {
                    if (!fl.getType().getSimpleName().contains("IndexedMessageCodec")) continue;
                    fl.setAccessible(true);
                    Object codec = fl.get(ch);
                    for (var cf : codec.getClass().getDeclaredFields()) {
                        cf.setAccessible(true);
                        if (!(cf.get(codec) instanceof Map<?, ?> m)) continue;
                        for (var en : m.entrySet()) {
                            if (!(en.getKey() instanceof Number idx)) continue;
                            for (var hf : en.getValue().getClass().getDeclaredFields()) if (hf.getType() == Class.class) { hf.setAccessible(true); names.put(idx.intValue() & 0xff, ((Class<?>) hf.get(en.getValue())).getSimpleName()); break; }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (!k.startsWith("xw#")) return k;
        return names.getOrDefault(Integer.parseInt(k.substring(3)), k);
    }

    private static Set<String> kinds(P p) { Set<String> s = new TreeSet<>(); for (String k : p.got.keySet()) s.add(named(k)); return s; }
    private static int count(P p, String simpleName) { int n = 0; for (var e : p.got.entrySet()) if (named(e.getKey()).equals(simpleName)) n += e.getValue(); return n; }
    private static void reset() { drain(); for (P p : PLAYERS) p.got.clear(); }

    private static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        tick++;
        for (P p : PLAYERS) {
            if (p.p.isRemoved() || p.p.hasDisconnected()) continue;
            try { p.p.doTick(); } catch (Throwable t) { errors++; XenoWarriors.LOGGER.error("XW-MPTEST ERROR ticking " + p.p.getGameProfile().getName(), t); }
        }
        drain();
        while (next < STEPS.size() && (Integer) STEPS.get(next)[0] <= tick) {
            Object[] st = STEPS.get(next++);
            try { ((Runnable) st[2]).run(); }
            catch (Throwable t) { errors++; XenoWarriors.LOGGER.error("XW-MPTEST ERROR in step " + st[1], t); }
        }
    }

    private static void ready(ServerPlayer p) {
        p.setGameMode(GameType.CREATIVE);
        p.getAbilities().flying = false; p.onUpdateAbilities();
        SkillManager.resetCooldowns(p);
        com.xenowarriors.karma.Karma.add(p, 500);
    }

    /** Starts it (the server keeps running until the last step halts it). */
    public static void start(MinecraftServer srv) {
        server = srv;
        ServerLevel lv = srv.overworld();
        BlockPos spawn = lv.getSharedSpawnPos();
        BlockPos base = new BlockPos(spawn.getX(), lv.getMaxBuildHeight() - 40, spawn.getZ());
        for (int dx = -16; dx <= 16; dx++) for (int dz = -16; dz <= 16; dz++) lv.setBlock(base.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        MinecraftForge.EVENT_BUS.addListener(MpSelfTest::onTick);
        final P[] one = new P[1], two = new P[1];
        int t = 5;
        at(t, "join", () -> {
            one[0] = join("XwOne", base);
            two[0] = join("XwTwo", base.offset(4, 0, 4));
            log("joined players=" + srv.getPlayerList().getPlayerCount() + " dedicated=" + srv.isDedicatedServer());
            for (P p : PLAYERS) p.p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 600, 4, false, false));
            CharacterManager.setCharacter(one[0].p, CharacterType.MIKIKO);
            CharacterManager.setCharacter(two[0].p, CharacterType.RHALIX);
        });
        at(t += 20, "party", () -> {
            com.xenowarriors.party.Parties.invite(one[0].p, two[0].p);
            com.xenowarriors.party.Parties.accept(two[0].p);
            for (CharacterType c : new CharacterType[]{CharacterType.KAERU, CharacterType.MAX}) {
                PartyBotEntity b = PartyBots.spawn(one[0].p, c, null);
                if (b != null) { var hp = b.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH); if (hp != null) hp.setBaseValue(2000); b.setHealth(b.getMaxHealth()); }
            }
            log("party same=" + com.xenowarriors.party.Parties.sameParty(one[0].p, two[0].p) + " bots=" + PartyBots.bots(one[0].p).size());
        });
        at(t += 20, "hostiles", () -> {
            for (int i = 0; i < 16; i++) {
                Mob m = EntityType.ZOMBIE.create(lv);
                if (m == null) continue;
                m.moveTo(base.getX() + 8 - (i % 4) * 4, base.getY(), base.getZ() + 8 - (i / 4) * 4, 0F, 0F);
                m.finalizeSpawn(lv, lv.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null, null);
                m.addTag("xw_mptest");
                lv.addFreshEntity(m);
            }
            reset();
        });
        // every character's skills on the first player, the second one beside them
        for (CharacterType c : CharacterType.PLAYABLE) {
            if (c == CharacterType.RHALIX || c == CharacterType.AIONI) continue;      // (the second player is Rhalix - and Ai-oni can't be partied with him: her turn comes last)
            at(t += 40, "skills " + c.getId(), () -> {
                ServerPlayer p = one[0].p;
                CharacterManager.setCharacter(p, c);
                ready(p);
                for (int s = 0; s < SkillManager.SLOTS; s++) { SkillManager.useSkill(p, s); ready(p); }
                SkillManager.useSkill(p, SkillManager.SUPER_SLOT);
            });
        }
        at(t += 40, "skills rhalix (second player)", () -> {
            ServerPlayer p = two[0].p;
            ready(p);
            for (int s = 0; s < SkillManager.SLOTS; s++) { SkillManager.useSkill(p, s); ready(p); }
            SkillManager.useSkill(p, SkillManager.SUPER_SLOT);
        });
        at(t += 60, "skills done", () -> {
            drain();
            log("after every skill: errors=" + errors + " xw kinds to one=" + kinds(one[0]) + " to two=" + kinds(two[0]));
            reset();
            CharacterManager.setCharacter(one[0].p, CharacterType.MIKIKO);
            one[0].p.teleportTo(lv, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0F, 0F);
            two[0].p.teleportTo(lv, base.getX() + 4.5, base.getY(), base.getZ() + 4.5, 0F, 0F);
            if (!com.xenowarriors.party.Parties.sameParty(one[0].p, two[0].p)) { com.xenowarriors.party.Parties.invite(one[0].p, two[0].p); com.xenowarriors.party.Parties.accept(two[0].p); }
            log("back together: same party=" + com.xenowarriors.party.Parties.sameParty(one[0].p, two[0].p) + " distance=" + Math.round(one[0].p.distanceTo(two[0].p)));
            for (P p : PLAYERS) { p.p.setGameMode(GameType.SURVIVAL); com.xenowarriors.karma.Karma.add(p.p, 500); }
            com.xenowarriors.karma.KarmaSensing.use(one[0].p, true);
        });
        at(t += 60, "karma sense shared", () -> {
            drain();
            int a = count(one[0], "KarmaSensedPacket"), b = count(two[0], "KarmaSensedPacket");
            log("karma sense: to_one=" + a + " to_two(party, 4 blocks)=" + b + (a > 0 && b > 0 ? " OK" : " FAIL"));
            com.xenowarriors.karma.KarmaSensing.release(one[0].p);
            reset();
            for (P p : PLAYERS) { p.p.setGameMode(GameType.CREATIVE); SkillManager.resetCooldowns(p.p); }
            var ks = com.xenowarriors.party.TeamUltimate.kinds(two[0].p, one[0].p);
            log("team ult start=" + com.xenowarriors.party.TeamUltimate.startNow(two[0].p, one[0].p, ks.get(ks.size() - 1)) + " kind=" + ks.get(ks.size() - 1));
        });
        at(t += 260, "team ult over", () -> {
            drain();
            log("team ult: to_one=" + kinds(one[0]) + " to_two=" + kinds(two[0]));
            reset();
            for (P p : PLAYERS) { p.p.setGameMode(GameType.SURVIVAL); p.p.removeAllEffects(); }
            ServerPlayer v = two[0].p;
            v.invulnerableTime = 0;
            var z = EntityType.ZOMBIE.create(lv);
            if (z != null) { z.moveTo(v.getX() + 1, v.getY(), v.getZ(), 0F, 0F); v.hurt(lv.damageSources().mobAttack(z), 1000F); }
            log("second player hit: alive=" + v.isAlive() + " downed=" + com.xenowarriors.party.Duels.isDowned(v) + " same party=" + com.xenowarriors.party.Parties.sameParty(one[0].p, v));
        });
        at(t += 20, "revive warp", () -> {
            drain();
            ServerPlayer u = one[0].p, v = two[0].p;
            log("downed packets: knockout_to_one=" + count(one[0], "KnockoutPacket") + " knockout_to_two=" + count(two[0], "KnockoutPacket"));
            u.teleportTo(lv, v.getX() + 12, v.getY(), v.getZ(), 0F, 0F);
            CharacterManager.setCharacter(u, CharacterType.RHALIX);
            com.xenowarriors.karma.Karma.add(u, 500);
            boolean ok = com.xenowarriors.party.ReviveWarp.warp(u, v);
            log("revive warp: " + ok + " (" + com.xenowarriors.party.ReviveWarp.last + ") dist now " + Math.round(u.distanceTo(v)));
            reset();
        });
        at(t += 80, "revived", () -> {
            drain();
            log("after the warp: downed=" + com.xenowarriors.party.Duels.isDowned(two[0].p) + " to_one=" + kinds(one[0]) + " to_two=" + kinds(two[0]));
            reset();
        });
        at(t += 20, "skills aioni", () -> {
            ServerPlayer p = one[0].p;
            CharacterManager.setCharacter(p, CharacterType.AIONI);
            ready(p);
            for (int s = 0; s < SkillManager.SLOTS; s++) { SkillManager.useSkill(p, s); ready(p); }
            SkillManager.useSkill(p, SkillManager.SUPER_SLOT);
        });
        at(t += 40, "leave", () -> {
            log("with Ai-oni and Rhalix: same party=" + com.xenowarriors.party.Parties.sameParty(one[0].p, two[0].p) + " - never, by design");
            ServerPlayer v = two[0].p;
            v.connection.onDisconnect(net.minecraft.network.chat.Component.literal("mp self-test over"));
            log("second player left: players=" + srv.getPlayerList().getPlayerCount() + " party=" + com.xenowarriors.party.Parties.members(one[0].p).size());
        });
        at(t += 40, "end", () -> {
            for (var m : lv.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(base).inflate(64), m -> m.getTags().contains("xw_mptest"))) m.discard();
            log(errors == 0 ? "OK" : "FAIL errors=" + errors);
            srv.halt(false);
        });
        log("started: " + STEPS.size() + " steps over " + t + " ticks");
    }
}
