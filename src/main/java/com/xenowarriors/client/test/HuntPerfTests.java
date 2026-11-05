package com.xenowarriors.client.test;

import com.mojang.authlib.GameProfile;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Bug hunt (performance + multiplayer), focus "hperf" (or "dmzhperf" with DragonMineZ). Results: "XW-TEST hperf ..." lines.
 * <ul>
 *   <li>Per scene (30 s): server ms/tick (average, worst), our share of the server thread and its cost per tick by the
 *   event handler / entity method it entered through (a stack sampler, every 2 ms), client FPS and our share of the
 *   render thread, bytes allocated per second on each thread, and our packets per second in each direction (counted
 *   on the connections themselves).</li>
 *   <li>Scenes: idle; fight (3 bots, 50 hostiles, Karma Sense held, party HUD, with DragonMineZ every bot on a mission);
 *   mp (a second player - a real ServerPlayer on a fake connection - with 3 bots of their own).</li>
 *   <li>Leaks: every static Map/Collection of the mod measured before and after 300 kills.</li>
 *   <li>Multiplayer checks with the second player: which of our packets each one receives (party, Karma Sense sharing,
 *   team ultimate, knock-outs), and nothing addressed to one reaching the other.</li>
 * </ul>
 */
public final class HuntPerfTests {
    private HuntPerfTests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("hperf"); }

    private static boolean dmz;

    private static void log(String s) { UiTest.log("hperf " + s); }

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("hperf", t); } });
    }

    private static String f(double d) { return String.format(Locale.ROOT, "%.3f", d); }

    // ------------------------------------------------------------------------------------------------ the sampler

    /** Stack samples of one thread: by the mod method it was entered through (outermost frame of ours) and the innermost. */
    private static final class Sampler implements Runnable {
        final Map<String, int[]> entry = new HashMap<>(), self = new HashMap<>(), incl = new HashMap<>();
        int samples, ours;
        volatile Thread target;
        volatile boolean on;

        static boolean mine(StackTraceElement fr) {
            String c = fr.getClassName();
            return (c.startsWith("com.xenowarriors.") && !c.startsWith("com.xenowarriors.client.test.")) || fr.getMethodName().contains("xenowarriors");
        }

        static String name(StackTraceElement fr) {
            String c = fr.getClassName();
            String s = c.substring(c.lastIndexOf('.') + 1) + "." + fr.getMethodName();
            return s.startsWith("__") ? s.replaceAll("^__(.*)_([A-Za-z]+Event.*)\\.invoke$", "$1 [$2]") : s;
        }

        @Override public void run() {
            while (true) {
                try { Thread.sleep(2); } catch (InterruptedException e) { return; }
                Thread t = target;
                if (!on || t == null) continue;
                StackTraceElement[] st = t.getStackTrace();
                if (st.length == 0) continue;
                String top = st[0].getClassName() + "." + st[0].getMethodName();
                if (top.startsWith("java.lang.Thread.sleep") || top.startsWith("jdk.internal.misc.Unsafe.park") || top.contains("LockSupport.park")
                        || top.startsWith("java.lang.Object.wait") || top.contains("waitForTasks") || top.contains("managedBlock")) continue;
                synchronized (this) {
                    samples++;
                    String inner = null, outer = null;
                    Set<String> seen = new HashSet<>();
                    for (StackTraceElement fr : st) {
                        if (!mine(fr)) continue;
                        String n = name(fr);
                        if (inner == null) inner = n;
                        outer = n;
                        if (seen.add(n)) bump(incl, n);
                    }
                    if (inner != null) { ours++; bump(self, inner + " <- " + name(st[0])); bump(entry, outer); }
                }
            }
        }

        static void bump(Map<String, int[]> m, String k) { m.computeIfAbsent(k, x -> new int[1])[0]++; }

        synchronized void reset() { entry.clear(); self.clear(); incl.clear(); samples = 0; ours = 0; }

        /** msPer: busy milliseconds per tick (server) or per frame (client), to turn shares into time. */
        synchronized double dump(String phase, String what, double msPer, String unit, int n) {
            double share = samples == 0 ? 0 : (double) ours / samples;
            log(phase + " " + what + " samples=" + samples + " in_xw=" + ours + " share=" + f(100 * share) + "% xw_ms_per_" + unit + "=" + f(share * msPer));
            top(phase, what + "-entry", entry, n, msPer, unit);
            top(phase, what + "-incl", incl, n, msPer, unit);
            top(phase, what + "-self", self, n, msPer, unit);
            return share * msPer;
        }

        private void top(String phase, String what, Map<String, int[]> m, int n, double msPer, String unit) {
            List<Map.Entry<String, int[]>> l = new ArrayList<>(m.entrySet());
            l.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);
            for (int i = 0; i < Math.min(n, l.size()); i++) {
                double sh = (double) l.get(i).getValue()[0] / Math.max(1, samples);
                log(phase + " " + what + " " + f(100 * sh) + "% " + f(sh * msPer) + "ms/" + unit + " " + l.get(i).getKey());
            }
        }
    }

    private static final Sampler SRV = new Sampler(), CLI = new Sampler();

    // ------------------------------------------------------------------------------------------------ packet counting

    private static final Map<Integer, String> NAMES = new HashMap<>();

    /** index -> packet class, read from ModNetwork's channel (reflection; numbers only if that fails). */
    private static void names() {
        try {
            Object ch = com.xenowarriors.network.ModNetwork.CHANNEL;
            Object codec = null;
            for (Field fl : ch.getClass().getDeclaredFields()) if (fl.getType().getSimpleName().contains("IndexedMessageCodec")) { fl.setAccessible(true); codec = fl.get(ch); }
            if (codec == null) { log("names: no codec"); return; }
            for (Field fl : codec.getClass().getDeclaredFields()) {
                fl.setAccessible(true);
                if (!(fl.get(codec) instanceof Map<?, ?> m)) continue;
                for (Map.Entry<?, ?> en : m.entrySet()) {
                    if (!(en.getKey() instanceof Number idx)) continue;
                    Object h = en.getValue();
                    for (Field hf : h.getClass().getDeclaredFields()) {
                        if (hf.getType() != Class.class) continue;
                        hf.setAccessible(true);
                        Object c = hf.get(h);
                        if (c instanceof Class<?> cl) { NAMES.put(idx.intValue() & 0xff, cl.getSimpleName()); break; }
                    }
                }
            }
        } catch (Throwable t) { log("names failed " + t); }
        log("packet names known: " + NAMES.size());
    }

    private static final class Counts {
        final Map<String, AtomicLong> m = new ConcurrentHashMap<>();
        volatile long t0 = System.nanoTime();
        void reset() { m.clear(); t0 = System.nanoTime(); }
        void add(Object msg) {
            String k = key(msg);
            if (k != null) m.computeIfAbsent(k, x -> new AtomicLong()).incrementAndGet();
        }
        long total(String prefix) { long n = 0; for (var e : m.entrySet()) if (e.getKey().startsWith(prefix)) n += e.getValue().get(); return n; }
        long get(String k) { AtomicLong a = m.get(k); return a == null ? 0 : a.get(); }
        void dump(String phase, String what, int n) {
            double secs = Math.max(0.001, (System.nanoTime() - t0) / 1e9);
            List<Map.Entry<String, AtomicLong>> l = new ArrayList<>(m.entrySet());
            l.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
            log(phase + " packets " + what + " secs=" + f(secs) + " xw_total=" + total("xw:") + " xw_per_s=" + f(total("xw:") / secs) + " all_per_s=" + f(total("") / secs));
            int shown = 0;
            for (var e : l) {
                if (!e.getKey().startsWith("xw:") && !e.getKey().startsWith("vanilla:") && !e.getKey().startsWith("other:")) continue;
                if (shown++ >= n) break;
                log(phase + " packets " + what + " " + f(e.getValue().get() / secs) + "/s " + e.getKey() + " (" + e.getValue().get() + ")");
            }
        }
        Set<String> xwKinds() { Set<String> s = new TreeSet<>(); for (String k : m.keySet()) if (k.startsWith("xw:")) s.add(k.substring(3)); return s; }
    }

    static String key(Object msg) {
        try {
            if (msg instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket) return "vanilla:Bundle";
            net.minecraft.resources.ResourceLocation id = null;
            net.minecraft.network.FriendlyByteBuf data = null;
            if (msg instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket c) { id = c.getIdentifier(); data = c.getData(); }
            else if (msg instanceof net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket c) { id = c.getIdentifier(); data = c.getData(); }
            if (id != null) {
                if (id.getNamespace().equals(XenoWarriors.MOD_ID) && data != null) {
                    int idx = data.getUnsignedByte(data.readerIndex());
                    return "xw:" + NAMES.getOrDefault(idx, "#" + idx);
                }
                return "other:" + id;
            }
            return msg == null ? null : "vanilla:" + msg.getClass().getSimpleName();
        } catch (Throwable t) { return "err:" + t.getClass().getSimpleName(); }
    }

    private static final class CountHandler extends ChannelOutboundHandlerAdapter {
        final Counts c;
        CountHandler(Counts c) { this.c = c; }
        @Override public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            c.add(msg);
            super.write(ctx, msg, promise);
        }
    }

    private static final Counts TO_P1 = new Counts(), FROM_P1 = new Counts(), TO_P2 = new Counts();

    static Connection connectionOf(net.minecraft.server.network.ServerGamePacketListenerImpl l) throws Exception {
        for (Class<?> c = l.getClass(); c != null; c = c.getSuperclass())
            for (Field fl : c.getDeclaredFields()) if (fl.getType() == Connection.class) { fl.setAccessible(true); return (Connection) fl.get(l); }
        throw new NoSuchFieldException("connection");
    }

    static Channel channelOf(Connection c) throws Exception {
        for (Field fl : Connection.class.getDeclaredFields()) if (Channel.class.isAssignableFrom(fl.getType())) { fl.setAccessible(true); return (Channel) fl.get(c); }
        throw new NoSuchFieldException("channel");
    }

    private static void hook(Channel ch, Counts c, String what) {
        if (ch == null) { log("hook " + what + ": no channel"); return; }
        if (ch.pipeline().get("xw_hperf_count") != null) ch.pipeline().remove("xw_hperf_count");
        ch.pipeline().addFirst("xw_hperf_count", new CountHandler(c));
        log("hook " + what + ": " + ch.getClass().getSimpleName() + " pipeline=" + ch.pipeline().names());
    }

    // ------------------------------------------------------------------------------------------------ allocations

    private static long allocated(Thread t) {
        try {
            var mx = java.lang.management.ManagementFactory.getThreadMXBean();
            if (mx instanceof com.sun.management.ThreadMXBean s && t != null) return s.getThreadAllocatedBytes(t.getId());
        } catch (Throwable ignored) {}
        return -1;
    }

    // ------------------------------------------------------------------------------------------------ static maps (leaks)

    private static Object unsafe;
    private static Method shouldInit;

    private static List<String> classNames() {
        List<String> out = new ArrayList<>();
        try {
            var file = net.minecraftforge.fml.ModList.get().getModFileById(XenoWarriors.MOD_ID).getFile();
            java.nio.file.Path root = file.findResource("com", "xenowarriors");
            try (var w = java.nio.file.Files.walk(root)) {
                w.forEach(pth -> {
                    String s = pth.toString().replace('\\', '/');
                    if (!s.endsWith(".class")) return;
                    int i = s.indexOf("com/xenowarriors/");
                    if (i < 0) return;
                    String cn = s.substring(i, s.length() - 6).replace('/', '.');
                    if (cn.startsWith("com.xenowarriors.mixin") || cn.startsWith("com.xenowarriors.client.test")) return;
                    out.add(cn);
                });
            }
        } catch (Throwable t) { log("class list failed " + t); }
        return out;
    }

    /** class.field -> size (deep: inner maps/collections added) of every static map/collection of an already-initialized mod class. */
    private static Map<String, Integer> statics() {
        Map<String, Integer> out = new TreeMap<>();
        try {
            if (unsafe == null) {
                Class<?> u = Class.forName("sun.misc.Unsafe");
                Field tu = u.getDeclaredField("theUnsafe"); tu.setAccessible(true);
                unsafe = tu.get(null);
                shouldInit = u.getMethod("shouldBeInitialized", Class.class);
            }
        } catch (Throwable t) { log("no unsafe " + t); return out; }
        ClassLoader cl = XenoWarriors.class.getClassLoader();
        for (String cn : classNames()) {
            try {
                Class<?> c = Class.forName(cn, false, cl);
                if ((Boolean) shouldInit.invoke(unsafe, c)) continue;
                for (Field fl : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(fl.getModifiers())) continue;
                    if (!Map.class.isAssignableFrom(fl.getType()) && !Collection.class.isAssignableFrom(fl.getType()) && fl.getType() != Object.class) continue;
                    if (!fl.trySetAccessible()) continue;
                    Object v = fl.get(null);
                    int n = size(v);
                    if (n >= 0) out.put(c.getSimpleName() + "." + fl.getName(), n);
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static int size(Object v) {
        try {
            if (v instanceof Map<?, ?> m) {
                int n = m.size();
                synchronized (m) { for (Object x : new ArrayList<>(m.values())) if (x instanceof Map<?, ?> mm) n += mm.size(); else if (x instanceof Collection<?> cc) n += cc.size(); }
                return n;
            }
            if (v instanceof Collection<?> c) return c.size();
        } catch (Throwable ignored) {}
        return -1;
    }

    private static Map<String, Integer> SNAP;

    /** Static maps/sets of the mod (initialized classes) that still hold this player's UUID / entity id / name as a key or element. */
    private static List<String> keyedBy(UUID id, int eid, String name) {
        List<String> out = new ArrayList<>();
        if (shouldInit == null) return out;
        ClassLoader cl = XenoWarriors.class.getClassLoader();
        for (String cn : classNames()) {
            try {
                Class<?> c = Class.forName(cn, false, cl);
                if ((Boolean) shouldInit.invoke(unsafe, c)) continue;
                for (Field fl : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(fl.getModifiers()) || !fl.trySetAccessible()) continue;
                    Object v = fl.get(null);
                    Collection<?> keys = v instanceof Map<?, ?> m ? new ArrayList<>(m.keySet()) : v instanceof Set<?> st ? new ArrayList<>(st) : null;
                    if (keys == null) continue;
                    for (Object k : keys) {
                        if (id.equals(k) || name.equals(k) || id.toString().equals(k) || (k instanceof Integer i && i == eid && !cn.contains(".client."))) {
                            out.add(c.getSimpleName() + "." + fl.getName() + " (" + k + ")");
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static void compare(String what, Map<String, Integer> before, Map<String, Integer> after, int threshold) {
        int grown = 0;
        for (var e : after.entrySet()) {
            int b = before.getOrDefault(e.getKey(), 0);
            if (e.getValue() - b >= threshold) { grown++; log("leak? " + what + " " + e.getKey() + " " + b + " -> " + e.getValue()); }
        }
        log("leak check " + what + ": " + after.size() + " static maps/collections, " + grown + " grew by >= " + threshold);
    }

    // ------------------------------------------------------------------------------------------------ second player

    private static ServerPlayer TWO;
    private static EmbeddedChannel TWO_CH;
    private static boolean ticking;

    private static void drainTwo() {
        if (TWO_CH == null) return;
        Object o;
        while ((o = TWO_CH.readOutbound()) != null) {
            TO_P2.add(o);
            io.netty.util.ReferenceCountUtil.release(o);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (TWO != null && !TWO.isRemoved()) {
            try { TWO.doTick(); } catch (Throwable t) { log("two tick error " + t); XenoWarriors.LOGGER.error("hperf two", t); TWO = null; }
        }
        drainTwo();
    }

    private static ServerPlayer join(ServerPlayer p, String name) {
        MinecraftServer srv = p.server;
        GameProfile gp = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)), name);
        ServerPlayer two = new ServerPlayer(srv, p.serverLevel(), gp);
        Connection conn = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel ch = new EmbeddedChannel(conn);
        conn.setProtocol(ConnectionProtocol.PLAY);
        TWO_CH = ch;
        try {
            srv.getPlayerList().placeNewPlayer(conn, two);
        } catch (Throwable t) {
            log("placeNewPlayer threw " + t);
            XenoWarriors.LOGGER.error("hperf placeNewPlayer", t);
        }
        if (!ticking) { MinecraftForge.EVENT_BUS.addListener(HuntPerfTests::onServerTick); ticking = true; }
        drainTwo();
        ServerPlayer back = srv.getPlayerList().getPlayer(gp.getId());
        log("second player: joined=" + (back == two) + " in_level=" + (two.level() == p.level()) + " players=" + srv.getPlayerList().getPlayerCount()
                + " packets_at_join=" + TO_P2.total("") + " xw_at_join=" + TO_P2.total("xw:") + " kinds=" + TO_P2.xwKinds());
        return back == two ? two : null;
    }

    // ------------------------------------------------------------------------------------------------ the scenes

    public static void build(Minecraft mc, String focus, Adder l) {
        dmz = focus.startsWith("dmz");
        l.add(null, () -> {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            mc.options.hideGui = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            CLI.target = Thread.currentThread();
            var s = mc.getSingleplayerServer();
            if (s != null) SRV.target = s.getRunningThread();
            for (Sampler sm : new Sampler[]{SRV, CLI}) { Thread t = new Thread(sm, "xw-hperf-sampler"); t.setDaemon(true); t.start(); }
            names();
            try { hook(mc.getConnection() == null ? null : channelOf(mc.getConnection().getConnection()), FROM_P1, "client->server"); } catch (Throwable t) { log("hook client failed " + t); }
            UiTest.character(CharacterType.MIKIKO);
            server(p -> {
                try { hook(channelOf(connectionOf(p.connection)), TO_P1, "server->p1"); } catch (Throwable t) { log("hook p1 failed " + t); }
                p.setGameMode(GameType.SURVIVAL);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 1200, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 1200, 3, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 1200, 0, false, false));
                var t = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
                t.putBoolean(com.xenowarriors.collection.Collection.SEEN, true);
                p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, t);
                ServerLevel lv = p.serverLevel();
                lv.setDayTime(6000);
                var c = p.blockPosition();
                for (int dx = -32; dx <= 32; dx++) for (int dz = -32; dz <= 32; dz++) {
                    for (int dy = 0; dy <= 6; dy++) lv.setBlock(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    lv.setBlock(c.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                }
                if (dmz) {
                    Object d = com.xenowarriors.compat.DmzPerCharacter.live(p);
                    Dmz6Tests.call(Dmz6Tests.call(d, "getCharacter"), "setRace", "saiyan");
                    com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
                }
            });
        }, 200);
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> { SNAP = statics(); log("statics at start: " + SNAP.size()); }); }, 20);
        phase(mc, l, "idle", null);

        // ---- fight: 3 bots, 50 hostiles, Karma Sense held, party HUD
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.KAERU, CharacterType.MAX}) {
                var b = PartyBots.spawn(p, t, null);
                if (b == null) { log("bot " + t.getId() + " spawn FAILED"); continue; }
                strong(b);
            }
            log("bots=" + PartyBots.bots(p).size());
        }); }, 60);
        if (dmz) l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(HuntPerfTests::botMissions); }, 60);
        l.add(null, () -> Dmz6Tests.noDmzScreen(mc), 20);
        phase(mc, l, "fight", p -> { topUp(p, 50); sense(p); });
        l.add("hperf_fight", () -> Dmz6Tests.noDmzScreen(mc), 5);

        // ---- leaks: 300 kills (by you and by the bots), then the static maps again
        for (int k = 0; k < 6; k++) l.add(null, () -> server(HuntPerfTests::churn), 40);
        l.add(null, () -> server(p -> {
            for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(96), m -> m.getTags().contains("xw_perf"))) m.discard();
        }), 200);
        l.add(null, () -> server(p -> { Map<String, Integer> now = statics(); compare("after 300 kills", SNAP, now, 30); SNAP = now; }), 20);

        // ---- mp: a second player with three bots of their own
        l.add(null, () -> server(p -> {
            ServerPlayer two = join(p, "XwTwo");
            TWO = two;
            if (two == null) return;
            com.xenowarriors.character.CharacterManager.setCharacter(two, CharacterType.RHALIX);
            two.teleportTo(p.serverLevel(), p.getX() + 4, p.getY(), p.getZ() + 4, 0F, 0F);
            two.setGameMode(GameType.SURVIVAL);
            two.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 1200, 4, false, false));
            two.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 1200, 3, false, false));
            for (CharacterType t : new CharacterType[]{CharacterType.LEXY, CharacterType.SAM, CharacterType.TECHNICA}) {
                var b = PartyBots.spawn(two, t, null);
                if (b == null) { log("p2 bot " + t.getId() + " spawn FAILED"); continue; }
                strong(b);
            }
            log("p2 bots=" + PartyBots.bots(two).size() + " p1 bots=" + PartyBots.bots(p).size());
        }), 60);
        phase(mc, l, "mp", p -> { topUp(p, 50); sense(p); });
        l.add("hperf_mp", () -> {}, 5);
        l.add(null, () -> server(p -> { Map<String, Integer> now = statics(); compare("after the mp scene", SNAP, now, 30); }), 20);

        // ---- multiplayer checks
        Mp.build(mc, l);

        l.add(null, () -> {
            for (String s : SUMMARY) log("TABLE " + s);
            SRV.on = false; CLI.on = false;
            log("done");
        }, 10);
    }

    static ServerPlayer two() { return TWO; }
    static Counts toP1() { return TO_P1; }
    static Counts toP2() { return TO_P2; }
    static void drain() { drainTwo(); }

    private static void strong(PartyBotEntity b) {
        var hp = b.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(2000);
        b.setHealth(b.getMaxHealth());
    }

    /** Karma Sense held (topped up so it never runs out of Focus). */
    private static void sense(ServerPlayer p) {
        com.xenowarriors.karma.Karma.add(p, 200);
        if (!com.xenowarriors.karma.KarmaSensing.holding(p)) com.xenowarriors.karma.KarmaSensing.use(p, true);
    }

    /** With DragonMineZ: each bot takes a kill mission (from its own menu, like a player would). */
    private static void botMissions(ServerPlayer p) {
        Object all = null;
        try { all = Class.forName("com.dragonminez.common.quest.QuestRegistry").getMethod("getAllQuests").invoke(null); } catch (Throwable t) { log("no quests " + t); }
        if (!(all instanceof Map<?, ?> qs)) return;
        List<String> keys = new ArrayList<>();
        for (Object k : qs.keySet()) keys.add(String.valueOf(k));
        Collections.sort(keys);
        int n = 0;
        for (PartyBotEntity b : PartyBots.bots(p)) {
            com.xenowarriors.compat.DmzBots.ensureRace(b);
            if (!com.xenowarriors.compat.DmzPerCharacter.beginView(p, b)) { log("bot view failed " + b.character().getId()); continue; }
            String started = null;
            for (String k : keys) {
                try {
                    Object r = Class.forName("com.dragonminez.common.quest.QuestService").getMethod("startQuest", ServerPlayer.class, String.class).invoke(null, p, k);
                    if (r == null) { started = k; break; }
                } catch (Throwable ignored) {}
            }
            com.xenowarriors.compat.DmzPerCharacter.endView(p, "menu closed");
            if (started != null) n++;
            log("bot " + b.character().getId() + " mission " + started + " active=" + com.xenowarriors.compat.DmzBotQuests.active(b));
        }
        log("bots on missions: " + n);
    }

    /** 50 hostiles hurt by you or a bot, then killed. */
    private static void churn(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        var bots = PartyBots.bots(p);
        int made = 0;
        for (int i = 0; i < 50; i++) {
            net.minecraft.world.entity.Entity made0 = (i % 2 == 0 ? EntityType.ZOMBIE : EntityType.SKELETON).create(lv);
            if (!(made0 instanceof Mob m)) continue;
            m.moveTo(p.getX() + lv.random.nextInt(21) - 10, p.getY(), p.getZ() + lv.random.nextInt(21) - 10, 0F, 0F);
            m.finalizeSpawn(lv, lv.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null, null);
            m.addTag("xw_perf");
            lv.addFreshEntity(m);
            net.minecraft.world.entity.LivingEntity by = bots.isEmpty() || i % 3 == 0 ? p : bots.get(i % bots.size());
            var src = by instanceof ServerPlayer sp ? lv.damageSources().playerAttack(sp) : lv.damageSources().mobAttack(by);
            m.hurt(src, 1F);
            m.setHealth(1F);
            m.invulnerableTime = 0;
            m.hurt(src, 1000F);
            if (m.isAlive()) m.kill();
            made++;
        }
        log("churn: " + made + " killed");
    }

    /** Keeps n tagged hostiles around the player, each set on a bot or the player. */
    private static void topUp(ServerPlayer p, int n) {
        ServerLevel lv = p.serverLevel();
        var have = lv.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(80), m -> m.getTags().contains("xw_perf") && m.isAlive());
        List<net.minecraft.world.entity.LivingEntity> targets = new ArrayList<>(PartyBots.bots(p));
        if (TWO != null) { targets.add(TWO); targets.addAll(PartyBots.bots(TWO)); }
        EntityType<?>[] kinds = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.SPIDER, EntityType.ZOMBIE, EntityType.STRAY};
        for (int i = have.size(); i < n; i++) {
            if (!(kinds[i % kinds.length].create(lv) instanceof Mob m)) continue;
            double a = lv.random.nextDouble() * Math.PI * 2, d = 12 + lv.random.nextDouble() * 14;
            int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
            int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            m.moveTo(x + 0.5, y, z + 0.5, lv.random.nextFloat() * 360F, 0F);
            m.finalizeSpawn(lv, lv.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null, null);
            m.addTag("xw_perf");
            m.setPersistenceRequired();
            lv.addFreshEntity(m);
            if (!targets.isEmpty() && i % 4 != 0) m.setTarget(targets.get(i % targets.size()));
            else m.setTarget(p);
        }
    }

    private static final List<String> SUMMARY = new ArrayList<>();

    /** 30 s of measuring: a line every 5 s, then the tables. {@code each} runs on the server every 5 s. */
    private static void noScreen(Minecraft mc) {
        Dmz6Tests.noDmzScreen(mc);
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            log("closing screen " + mc.screen.getClass().getSimpleName());
            mc.setScreen(null);
        }
    }

    private static void phase(Minecraft mc, Adder l, String name, Consumer<ServerPlayer> each) {
        double[] acc = new double[8];   // sum mspt, worst mspt, sum fps, min fps, entities, n, srv alloc, cli alloc
        long[] alloc0 = new long[2];
        long[] dmz0 = new long[3];
        long[] t0 = new long[1];
        acc[3] = Double.MAX_VALUE;
        l.add(null, () -> {
            noScreen(mc);
            if (each != null) server(each);
            SRV.reset(); CLI.reset(); SRV.on = true; CLI.on = true;
            TO_P1.reset(); FROM_P1.reset(); TO_P2.reset();
            MinecraftServer s = mc.getSingleplayerServer();
            if (s != null) java.util.Arrays.fill(s.tickTimes, 0L);
            alloc0[0] = allocated(SRV.target); alloc0[1] = allocated(CLI.target);
            dmz0[0] = com.xenowarriors.compat.DmzPerCharacter.loadsForTest; dmz0[1] = com.xenowarriors.compat.DmzPerCharacter.savesForTest; dmz0[2] = com.xenowarriors.compat.DmzBotQuests.peekParses;
            t0[0] = System.nanoTime();
        }, 100);
        for (int i = 1; i <= 6; i++) {
            int sec = i * 5;
            l.add(null, () -> {
                noScreen(mc);
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
            double secs = (System.nanoTime() - t0[0]) / 1e9;
            double n = Math.max(1, acc[5]);
            double mspt = acc[0] / n, fps = acc[2] / n;
            long sa = allocated(SRV.target) - alloc0[0], ca = allocated(CLI.target) - alloc0[1];
            long loads = com.xenowarriors.compat.DmzPerCharacter.loadsForTest - dmz0[0], saves = com.xenowarriors.compat.DmzPerCharacter.savesForTest - dmz0[1];
            log(name + " summary mspt_avg=" + f(mspt) + " mspt_worst=" + f(acc[1]) + " fps_avg=" + f(fps)
                    + " server_alloc_MB_s=" + f(sa / 1e6 / secs) + " client_alloc_MB_s=" + f(ca / 1e6 / secs)
                    + " dmz_char_loads_per_s=" + f(loads / secs) + " dmz_char_saves_per_s=" + f(saves / secs) + " dmz_mission_parses_per_s=" + f((com.xenowarriors.compat.DmzBotQuests.peekParses - dmz0[2]) / secs));
            double xs = SRV.dump(name, "server", mspt, "tick", 25);
            double xc = CLI.dump(name, "client", fps <= 0 ? 0 : 1000.0 / fps, "frame", 20);
            drainTwo();
            TO_P1.dump(name, "to_p1", 20);
            FROM_P1.dump(name, "from_p1", 10);
            if (TWO != null) TO_P2.dump(name, "to_p2", 20);
            SUMMARY.add(name + " mspt_avg=" + f(mspt) + " mspt_worst=" + f(acc[1]) + " xw_ms_per_tick=" + f(xs) + " fps_avg=" + f(fps) + " xw_ms_per_frame=" + f(xc)
                    + " xw_packets_to_p1_per_s=" + f(TO_P1.total("xw:") / secs) + " xw_packets_from_p1_per_s=" + f(FROM_P1.total("xw:") / secs)
                    + " server_alloc_MB_s=" + f(sa / 1e6 / secs) + " client_alloc_MB_s=" + f(ca / 1e6 / secs) + " dmz_loads_per_s=" + f(loads / secs) + " entities=" + (int) acc[4]);
        }, 5);
    }

    // ------------------------------------------------------------------------------------------------ multiplayer checks

    private static final class Mp {
        static void build(Minecraft mc, Adder l) {
            // party: the second player joins yours
            l.add(null, () -> server(p -> {
                ServerPlayer two = two();
                if (two == null) { log("mp: no second player - skipped"); return; }
                for (PartyBotEntity b : PartyBots.bots(two)) b.discard();
                for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(96), m -> m.getTags().contains("xw_perf"))) m.discard();
                PartyBotEntity keep = null;
                for (PartyBotEntity b : PartyBots.bots(p)) { if (keep == null && b.character() == CharacterType.KAERU) keep = b; else b.discard(); }
                drain(); toP1().reset(); toP2().reset();
                com.xenowarriors.party.Parties.invite(p, two);
                com.xenowarriors.party.Parties.accept(two);
                log("mp party: same=" + com.xenowarriors.party.Parties.sameParty(p, two) + " members=" + com.xenowarriors.party.Parties.members(p).size());
            }), 60);
            l.add(null, () -> server(p -> {
                drain();
                log("mp party packets: to_p1=" + toP1().xwKinds() + " to_p2=" + toP2().xwKinds());
                toP1().reset(); toP2().reset();
                sense(p);                                                  // (still holding Karma Sense from the mp scene)
            }), 100);
            l.add(null, () -> server(p -> {
                drain();
                long a = toP1().get("xw:KarmaSensedPacket"), b = toP2().get("xw:KarmaSensedPacket");
                log("mp karma sense held 5s: to_p1=" + a + " to_p2(party, 4 blocks)=" + b + ((a > 0 && b > 0) ? " OK" : " FAIL") + " holding=" + com.xenowarriors.karma.KarmaSensing.holding(p));
                ServerPlayer two = two();
                if (two == null) return;
                two.teleportTo(p.serverLevel(), p.getX() + 300, p.getY() + 40, p.getZ(), 0F, 0F);
                two.getAbilities().flying = true; two.getAbilities().mayfly = true; two.onUpdateAbilities();
            }), 60);
            // the second player 300 blocks away: nothing of yours that only you or those near you should get
            l.add(null, () -> server(p -> {
                drain(); toP1().reset(); toP2().reset();
                sense(p);
                com.xenowarriors.skill.SkillManager.useSkill(p, 0);
                com.xenowarriors.skill.SkillManager.useSkill(p, 1);
            }), 100);
            l.add(null, () -> server(p -> {
                drain();
                log("mp far away (300 blocks): to_p2 kinds=" + toP2().xwKinds() + " sensed_to_p2=" + toP2().get("xw:KarmaSensedPacket")
                        + (toP2().get("xw:KarmaSensedPacket") == 0 ? " OK" : " FAIL") + " | to_p1 kinds=" + toP1().xwKinds() + " sensed_to_p1=" + toP1().get("xw:KarmaSensedPacket"));
                com.xenowarriors.karma.KarmaSensing.release(p);
                ServerPlayer two = two();
                if (two != null) {
                    two.getAbilities().flying = false; two.getAbilities().mayfly = false; two.onUpdateAbilities();
                    two.teleportTo(p.serverLevel(), p.getX() + 4, p.getY(), p.getZ() + 4, 0F, 0F);
                }
            }), 80);
            // a team ultimate between the two players (Rhalix = the second player, Mikiko = you)
            l.add(null, () -> server(p -> {
                ServerPlayer two = two();
                if (two == null) return;
                drain(); toP1().reset(); toP2().reset();
                p.setGameMode(GameType.CREATIVE); two.setGameMode(GameType.CREATIVE);
                boolean ok = com.xenowarriors.party.TeamUltimate.startNow(two, p, com.xenowarriors.party.TeamUltimate.kinds(two, p).get(0));
                log("mp team ult start=" + ok);
            }), 60);
            l.add("hperf_teamult", () -> server(p -> {
                drain();
                log("mp team ult packets: to_p1=" + toP1().xwKinds() + " to_p2=" + toP2().xwKinds()
                        + " cutscene_to_p1=" + cut(toP1()) + " cutscene_to_p2=" + cut(toP2()));
            }), 300);
            // knocked out (duel rules) - the other one sees it
            l.add(null, () -> server(p -> {
                ServerPlayer two = two();
                if (two == null) return;
                p.setGameMode(GameType.SURVIVAL); two.setGameMode(GameType.SURVIVAL);
                drain(); toP1().reset(); toP2().reset();
                two.removeAllEffects();
                two.invulnerableTime = 0;
                net.minecraft.world.entity.Entity z = EntityType.ZOMBIE.create(p.serverLevel());
                if (z instanceof net.minecraft.world.entity.LivingEntity le) {
                    le.moveTo(two.getX() + 1, two.getY(), two.getZ(), 0F, 0F);
                    two.hurt(p.serverLevel().damageSources().mobAttack(le), 1000F);
                }
                log("mp p2 hit hard: alive=" + two.isAlive() + " hp=" + two.getHealth() + " downed=" + com.xenowarriors.party.Duels.isDowned(two) + " knocked_out=" + com.xenowarriors.party.Duels.isKnockedOut(two));
            }), 40);
            l.add("hperf_p2down", () -> server(p -> {
                drain();
                log("mp p2 down packets: to_p1=" + toP1().xwKinds() + " to_p2=" + toP2().xwKinds() + " vanilla_to_p1_sounds=" + toP1().get("vanilla:ClientboundSoundPacket")
                        + " to_p2_sounds=" + toP2().get("vanilla:ClientboundSoundPacket"));
            }), 60);
            // second player leaves: everything keyed by them goes
            l.add(null, () -> server(p -> {
                ServerPlayer two = two();
                if (two == null) return;
                Map<String, Integer> before = statics();
                UUID id2 = two.getUUID();
                int eid2 = two.getId();
                String name2 = two.getGameProfile().getName();
                two.connection.onDisconnect(net.minecraft.network.chat.Component.literal("hperf test over"));
                TWO = null;
                for (String k : keyedBy(id2, eid2, name2)) log("mp after logout still keyed by p2: " + k);
                Map<String, Integer> after = statics();
                for (var e : before.entrySet()) {
                    int a = after.getOrDefault(e.getKey(), 0);
                    if (a < e.getValue()) log("mp logout cleared " + e.getKey() + " " + e.getValue() + " -> " + a);
                }
                log("mp p2 logged out: players=" + p.server.getPlayerList().getPlayerCount() + " party_members=" + com.xenowarriors.party.Parties.members(p).size());
            }), 40);
        }

        private static long cut(Counts c) {
            long n = 0;
            for (var e : c.m.entrySet()) if (e.getKey().startsWith("xw:") && (e.getKey().contains("Cutscene") || e.getKey().contains("TeamUlt") || e.getKey().contains("SkillAnim"))) n += e.getValue().get();
            return n;
        }
    }
}
