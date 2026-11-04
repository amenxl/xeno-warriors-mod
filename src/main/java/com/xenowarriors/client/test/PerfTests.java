package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Performance probe (focus "perf4", or "dmzperf4" with DragonMineZ): measures the integrated server's tick time
 * (MinecraftServer's last-100-ticks average and the worst tick) and the client FPS every 5 s in a few scenes, and
 * samples the server and render threads' stacks (a plain Thread.getStackTrace sampler, every 4 ms) to name the
 * hottest Xeno Warriors code in each. Results: "XW-TEST perf4 ..." lines.
 * <ul>
 *   <li>idle - standing in the overworld</li>
 *   <li>bots - 3 party bots and the player against 40 hostile mobs (kept topped up)</li>
 *   <li>tower - a Tower floor wave with the bots along</li>
 *   <li>city - Aurora City in the Recarnet dimension, with 30 townsfolk about</li>
 * </ul>
 */
public final class PerfTests {
    private PerfTests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("perf4"); }

    private static void log(String s) { UiTest.log("perf4 " + s); }

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { log("step error " + t); com.xenowarriors.XenoWarriors.LOGGER.error("perf4", t); } });
    }

    private static String f(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    // ------------------------------------------------------------------------------------------------ the sampler

    /** Stack samples of one thread, by method: inclusive (anywhere on the stack) and "self" (our deepest frame + the leaf). */
    private static final class Sampler implements Runnable {
        final Map<String, int[]> incl = new HashMap<>(), self = new HashMap<>(), leaf = new HashMap<>();
        int samples, ours;
        volatile Thread target;
        volatile boolean on;

        static boolean mine(StackTraceElement f) {
            String c = f.getClassName();
            return (c.startsWith("com.xenowarriors.") && !c.startsWith("com.xenowarriors.client.test.")) || f.getMethodName().contains("xenowarriors");
        }

        static String name(StackTraceElement f) {
            String c = f.getClassName();
            return c.substring(c.lastIndexOf('.') + 1) + "." + f.getMethodName();
        }

        @Override public void run() {
            while (true) {
                try { Thread.sleep(4); } catch (InterruptedException e) { return; }
                Thread t = target;
                if (!on || t == null) continue;
                StackTraceElement[] st = t.getStackTrace();
                if (st.length == 0) continue;
                String top = st[0].getClassName() + "." + st[0].getMethodName();
                // idle: waiting for the next tick / frame
                if (top.startsWith("java.lang.Thread.sleep") || top.startsWith("jdk.internal.misc.Unsafe.park") || top.contains("LockSupport.park")
                        || top.startsWith("java.lang.Object.wait") || top.contains("waitForTasks") || top.contains("managedBlock")) continue;
                synchronized (this) {
                    samples++;
                    bump(leaf, name(st[0]));
                    Set<String> seen = new HashSet<>();
                    String deepest = null;
                    for (StackTraceElement fr : st) {
                        if (!mine(fr)) continue;
                        String n = name(fr);
                        if (deepest == null) deepest = n;
                        if (seen.add(n)) bump(incl, n);
                    }
                    if (deepest != null) { ours++; bump(self, deepest + " <- " + name(st[0])); }
                }
            }
        }

        static void bump(Map<String, int[]> m, String k) { m.computeIfAbsent(k, x -> new int[1])[0]++; }

        synchronized void reset() { incl.clear(); self.clear(); leaf.clear(); samples = 0; ours = 0; }

        synchronized void dump(String phase, String what, int n) {
            log(phase + " " + what + " samples=" + samples + " in_xw=" + ours + " (" + f(samples == 0 ? 0 : 100.0 * ours / samples) + "%)");
            top(phase, what + "-incl", incl, n);
            top(phase, what + "-self", self, n);
            top(phase, what + "-leaf", leaf, 8);
        }

        private void top(String phase, String what, Map<String, int[]> m, int n) {
            List<Map.Entry<String, int[]>> l = new ArrayList<>(m.entrySet());
            l.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);
            for (int i = 0; i < Math.min(n, l.size()); i++)
                log(phase + " " + what + " " + f(100.0 * l.get(i).getValue()[0] / Math.max(1, samples)) + "% " + l.get(i).getKey());
        }
    }

    private static final Sampler SRV = new Sampler(), CLI = new Sampler();

    // ------------------------------------------------------------------------------------------------ the scenes

    private static final List<double[]> STATS = new ArrayList<>();

    public static void build(Minecraft mc, String focus, Adder l) {
        l.add(null, () -> {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            mc.options.hideGui = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            CLI.target = Thread.currentThread();
            var s = mc.getSingleplayerServer();
            if (s != null) SRV.target = s.getRunningThread();
            for (Sampler sm : new Sampler[]{SRV, CLI}) { Thread t = new Thread(sm, "xw-perf-sampler"); t.setDaemon(true); t.start(); }
            UiTest.character(CharacterType.MIKIKO);
            server(p -> {
                p.setGameMode(GameType.SURVIVAL);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 900, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 900, 3, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 900, 0, false, false));
                var t = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
                t.putBoolean(com.xenowarriors.collection.Collection.SEEN, true);
                p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, t);
                // a 61x61 stone yard to fight on
                ServerLevel lv = p.serverLevel();
                var c = p.blockPosition();
                for (int dx = -30; dx <= 30; dx++) for (int dz = -30; dz <= 30; dz++) {
                    for (int dy = 0; dy <= 5; dy++) lv.setBlock(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    lv.setBlock(c.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                }
            });
        }, 200);
        phase(mc, l, "idle", null);
        // bots + 40 hostiles
        l.add(null, () -> server(p -> {
            for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.KAERU, CharacterType.MAX}) {
                var b = com.xenowarriors.bot.PartyBots.spawn(p, t, null);
                if (b == null) { log("bot " + t.getId() + " spawn FAILED"); continue; }
                var hp = b.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(2000);
                b.setHealth(b.getMaxHealth());
            }
            log("bots=" + com.xenowarriors.bot.PartyBots.bots(p).size());
        }), 60);
        phase(mc, l, "bots", PerfTests::topUp);
        l.add(null, () -> server(p -> { for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(80), m -> m.getTags().contains("xw_perf"))) m.discard(); }), 20);
        // a Tower floor with the bots
        l.add(null, () -> { mc.setScreen(null); server(p -> com.xenowarriors.tower.Tower.enter(p)); }, 200);
        l.add(null, () -> { mc.setScreen(null); server(p -> log("tower: in=" + com.xenowarriors.tower.Tower.is(p.level()) + " " + com.xenowarriors.tower.Tower.testInfo(p)
                + " bots_here=" + com.xenowarriors.bot.PartyBots.bots(p).stream().filter(b -> b.level() == p.level()).count())); }, 5);
        phase(mc, l, "tower", null);
        l.add("perf4_tower", () -> server(p -> log("tower after: " + com.xenowarriors.tower.Tower.testInfo(p))), 5);
        l.add(null, () -> server(p -> com.xenowarriors.tower.Tower.leave(p, "perf test")), 100);
        // Aurora City
        l.add(null, () -> { mc.setScreen(null); server(p -> {
            ServerLevel r = p.server.getLevel(com.xenowarriors.recarnet.Recarnet.LEVEL);
            if (r == null) { log("no recarnet level"); return; }
            r.setDayTime(5000);
            p.teleportTo(r, 40.5, com.xenowarriors.recarnet.Recarnet.BASE_Y + 40, 40.5, 0F, 20F);
            p.setGameMode(GameType.CREATIVE); p.getAbilities().flying = true; p.onUpdateAbilities();
        }); }, 200);
        l.add(null, () -> server(p -> {
            ServerLevel r = p.serverLevel();
            int top = r.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 40, 40);
            if (top < com.xenowarriors.recarnet.RoyalCapitalFeature.FLOOR_Y - 5) p.teleportTo(40.5, top + 1, 40.5);
            int made = 0;
            for (int i = 0; i < 40 && made < 30; i++) {
                int x = 40 + r.random.nextInt(61) - 30, z = 40 + r.random.nextInt(61) - 30;
                int y = r.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y >= com.xenowarriors.recarnet.RoyalCapitalFeature.FLOOR_Y - 5) continue;
                var h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(r);
                if (h == null) continue;
                h.moveTo(x + 0.5, y, z + 0.5, r.random.nextFloat() * 360F, 0F);
                h.finalizeSpawn(r, r.getCurrentDifficultyAt(h.blockPosition()), MobSpawnType.NATURAL, null, null);
                r.addFreshEntity(h); made++;
            }
            log("city: at " + p.blockPosition().toShortString() + " townsfolk_added=" + made + " humans_near=" + r.getEntitiesOfClass(com.xenowarriors.npc.HumanNpcEntity.class, p.getBoundingBox().inflate(96)).size()
                    + " bots_here=" + com.xenowarriors.bot.PartyBots.bots(p).stream().filter(b -> b.level() == p.level()).count());
        }), 100);
        phase(mc, l, "city", null);
        l.add("perf4_city", () -> {}, 5);
        l.add(null, () -> {
            String[] names = {"idle", "bots", "tower", "city"};
            for (int i = 0; i < STATS.size() && i < names.length; i++) {
                double[] s = STATS.get(i);
                log("TABLE " + names[i] + " mspt_avg=" + f(s[0]) + " mspt_worst=" + f(s[1]) + " fps_avg=" + f(s[2]) + " fps_min=" + f(s[3]) + " entities=" + (int) s[4]);
            }
            SRV.on = false; CLI.on = false;
            log("done");
        }, 10);
    }

    /** Keeps 40 tagged hostiles around the player, each one set on a bot or the player. */
    private static void topUp(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        var have = lv.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(80), m -> m.getTags().contains("xw_perf") && m.isAlive());
        var bots = com.xenowarriors.bot.PartyBots.bots(p);
        EntityType<?>[] kinds = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.SPIDER, EntityType.ZOMBIE, EntityType.STRAY};
        for (int i = have.size(); i < 40; i++) {
            if (!(kinds[i % kinds.length].create(lv) instanceof Mob m)) continue;
            double a = lv.random.nextDouble() * Math.PI * 2, d = 12 + lv.random.nextDouble() * 12;
            int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
            int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            m.moveTo(x + 0.5, y, z + 0.5, lv.random.nextFloat() * 360F, 0F);
            m.finalizeSpawn(lv, lv.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null, null);
            m.addTag("xw_perf");
            m.setPersistenceRequired();
            lv.addFreshEntity(m);
            if (!bots.isEmpty() && i % 4 != 0) m.setTarget(bots.get(i % bots.size()));
            else m.setTarget(p);
        }
    }

    /** 30 s of measuring: a line every 5 s, then the hot-method tables. {@code each} runs on the server every 5 s. */
    private static void phase(Minecraft mc, Adder l, String name, Consumer<ServerPlayer> each) {
        double[] acc = new double[6];   // sum mspt, worst mspt, sum fps, min fps, entities, n
        acc[3] = Double.MAX_VALUE;
        l.add(null, () -> {
            if (each != null) server(each);
            SRV.reset(); CLI.reset(); SRV.on = true; CLI.on = true;
            MinecraftServer s = mc.getSingleplayerServer();
            if (s != null) java.util.Arrays.fill(s.tickTimes, 0L);
        }, 100);
        for (int i = 1; i <= 6; i++) {
            int sec = i * 5;
            l.add(null, () -> {
                MinecraftServer s = mc.getSingleplayerServer();
                if (s == null) return;
                long worst = 0; double sum = 0; int n = 0;
                for (long t : s.tickTimes) if (t > 0) { worst = Math.max(worst, t); sum += t; n++; }
                double avg = n == 0 ? 0 : sum / n / 1e6, w = worst / 1e6;
                int fps = mc.getFps();
                int ents = 0;
                for (ServerLevel lv : s.getAllLevels()) for (var e : lv.getAllEntities()) if (e != null) ents++;
                log(name + " t=" + sec + "s mspt_avg=" + f(avg) + " mspt_worst=" + f(w) + " fps=" + fps + " entities=" + ents
                        + " client_entities=" + (mc.level == null ? 0 : mc.level.getEntityCount()));
                acc[0] += avg; acc[1] = Math.max(acc[1], w); acc[2] += fps; acc[3] = Math.min(acc[3], fps); acc[4] = ents; acc[5]++;
                java.util.Arrays.fill(s.tickTimes, 0L);
                if (each != null) server(each);
            }, 100);
        }
        l.add(null, () -> {
            SRV.on = false; CLI.on = false;
            double n = Math.max(1, acc[5]);
            STATS.add(new double[]{acc[0] / n, acc[1], acc[2] / n, acc[3] == Double.MAX_VALUE ? 0 : acc[3], acc[4]});
            log(name + " summary mspt_avg=" + f(acc[0] / n) + " mspt_worst=" + f(acc[1]) + " fps_avg=" + f(acc[2] / n));
            SRV.dump(name, "server", 25);
            CLI.dump(name, "client", 15);
        }, 5);
    }
}
