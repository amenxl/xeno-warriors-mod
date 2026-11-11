package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.compat.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

/**
 * UI test (focus "dmzbotki", and "packbotki" in the user's real mod pack): with DragonMineZ, a bot with spare TP makes
 * its own ki attack (DragonMineZ's price, usable in a fight); one short of TP makes none; its TP for its next mission's
 * level is kept; a bot near Goku walks over and learns Kamehameha and a strike move at DragonMineZ's price (not with
 * too little TP, not with too low an alignment); with nothing in its equipped slots it fights with every technique it
 * knows; its Dragon Ball menu still opens on it; the player's own data never changes. Logged as "XW-TEST botki ...".
 */
public final class BotKiTests {
    private BotKiTests() {}

    public static boolean handles(String focus) { return focus != null && (focus.startsWith("dmzbotki") || focus.startsWith("packbotki")); }

    private static Consumer<UiTest.Step> L;
    private static int ok, fail;
    private static void log(String s) { UiTest.log("botki " + s); }
    private static void check(String what, boolean good, String detail) {
        if (good) ok++; else fail++;
        log(what + ": " + detail + (good ? " OK" : " FAIL"));
    }
    private static void server(Consumer<ServerPlayer> f) {
        UiTest.server(p -> { try { f.accept(p); } catch (Throwable t) { check("server step", false, String.valueOf(t)); com.xenowarriors.XenoWarriors.LOGGER.error("XW-TEST botki", t); } });
    }
    private static void step(String shot, Runnable a, int delay) { L.accept(new UiTest.Step(shot, a, delay)); }

    private static Object call(Object o, String m, Object... args) {
        if (o == null) return null;
        try {
            for (var mm : o.getClass().getMethods()) if (mm.getName().equals(m) && mm.getParameterCount() == args.length) return mm.invoke(o, args);
        } catch (Throwable t) { return "?" + t.getClass().getSimpleName(); }
        return "?nomethod";
    }

    private static PartyBotEntity bot(ServerPlayer p) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == CharacterType.KAERU) return b;
        return null;
    }

    /** The player's own DragonMineZ techniques, skills and stats (must never change here; their TP only grows). */
    private static String mine(ServerPlayer p) {
        CompoundTag t = DmzPerCharacter.saveData(DmzPerCharacter.live(p));
        if (t == null) return "-";
        List<String> sk = new ArrayList<>();
        var list = t.getCompound("Skills").getList("SkillsList", 10);
        for (int i = 0; i < list.size(); i++) sk.add(list.getCompound(i).toString());
        Collections.sort(sk);
        return "tech " + t.getCompound("Techniques").getList("UnlockedTechniques", 10).size() + " " + t.getCompound("Techniques").getCompound("EquippedSlots")
                + " skills " + sk.size() + "/" + sk.hashCode() + " stats " + t.getCompound("Stats").hashCode();
    }
    private static String skillsOf(ServerPlayer p) {
        CompoundTag t = DmzPerCharacter.saveData(DmzPerCharacter.live(p));
        return t == null ? "-" : t.getCompound("Skills").toString();
    }
    private static float myTp(ServerPlayer p) {
        CompoundTag t = DmzPerCharacter.saveData(DmzPerCharacter.live(p));
        return t == null ? -1 : t.getCompound("Resources").getFloat("TrainingPointsF");
    }
    private static String MINE0;
    private static float TP0;
    private static boolean untouched(ServerPlayer p) { return mine(p).equals(MINE0) && myTp(p) >= TP0; }

    private static float tp(PartyBotEntity b) { float[] s = DmzBotTechniques.spareForTest(b); return s == null ? -1 : s[0]; }

    private static int dumped;
    private static void dump() {
        synchronized (DmzBotTechniques.LOG) { for (int i = Math.min(dumped, DmzBotTechniques.LOG.size()); i < DmzBotTechniques.LOG.size(); i++) log("log " + DmzBotTechniques.LOG.get(i)); dumped = DmzBotTechniques.LOG.size(); }
    }

    /** A saga step that asks for a level above the bot's, and the steps before it (as BotTrainingTests). */
    @SuppressWarnings("unchecked")
    private static String[] target() {
        try {
            Class<?> reg = Class.forName("com.dragonminez.common.quest.QuestRegistry");
            var keyOf = Class.forName("com.dragonminez.common.quest.PlayerQuestData").getMethod("sagaQuestKey", String.class, int.class);
            Map<String, Object> sagas = new TreeMap<>((Map<String, Object>) reg.getMethod("getAllSagas").invoke(null));
            List<String> ids = new ArrayList<>(sagas.keySet());
            if (ids.remove("saiyan_saga")) ids.add(0, "saiyan_saga");
            for (String id : ids) {
                List<String> before = new ArrayList<>();
                for (Object q : (List<Object>) call(sagas.get(id), "getQuests")) {
                    String k = (String) keyOf.invoke(null, call(sagas.get(id), "getId"), call(q, "getId"));
                    if (DmzBotTraining.requiredLevelForTest(k) > 2) {
                        String[] out = new String[before.size() + 1];
                        out[0] = k;
                        for (int i = 0; i < before.size(); i++) out[i + 1] = before.get(i);
                        return out;
                    }
                    before.add(k);
                }
            }
        } catch (Throwable t) { log("target: " + t); }
        return null;
    }

    private static String[] T;
    private static CompoundTag STATS0;
    private static final Object[] ST = new Object[4];          // bot, Goku, dummy
    private static final float[] A = new float[8];
    private static final List<String> MADE = new ArrayList<>();
    private static final Set<String> SEEN = new LinkedHashSet<>();

    public static void build(Minecraft mc, Consumer<UiTest.Step> l, String focus) {
        L = l;
        step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            Object d = DmzPerCharacter.live(p);
            call(call(d, "getCharacter"), "setRace", "saiyan");
            DmzPerCharacter.markCreatedForTest(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            call(call(d, "getResources"), "setTrainingPoints", 55555F);              // (the player's own TP: must stay theirs)
            log("focus " + focus);
            PartyBots.spawn(p, CharacterType.KAERU, null);
        }); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) { check("setup", false, "no bot"); return; }
            ST[0] = b;
            DmzBots.ensureRace(b);
            b.setNoAi(true);
            CompoundTag tag = DmzPerCharacter.saved(p, DmzPerCharacter.botKey(b));
            STATS0 = tag == null ? null : tag.getCompound("Stats").copy();
            T = target();
            MINE0 = mine(p); TP0 = myTp(p);
            check("setup", STATS0 != null, "bot " + DmzPerCharacter.botKey(b) + " class " + DmzBotTraining.splitForTest(b) + " author '" + DmzBotTechniques.authorForTest(b)
                    + "' | level-blocked mission " + (T == null ? "-" : T[0] + " (level " + DmzBotTraining.requiredLevelForTest(T[0]) + ")"));
            log("goku teaches " + DmzBotTechniques.teachesForTest("goku") + " | roshi " + DmzBotTechniques.teachesForTest("roshi") + " | piccolo " + DmzBotTechniques.teachesForTest("piccolo")
                    + " | kingkai " + DmzBotTechniques.teachesForTest("kingkai") + " | vegeta " + DmzBotTechniques.teachesForTest("vegeta"));
        }); }, 20);

        // 1. its own ki attack: TP taken = DragonMineZ's price, the attack is in its list, and it fights with it
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            DmzBotTraining.setupForTest(b, STATS0, 0F);                              // (no missions: nothing kept for training)
            DmzBotTechniques.setupForTest(b, 100000F, true, 80);
            float[] s0 = DmzBotTechniques.spareForTest(b);
            int c0 = DmzBotTechniques.created;
            String r = DmzBotTechniques.create(b, true);
            float after = tp(b);
            String known = DmzBotTechniques.knownForTest(b);
            log("create 1: " + r + " | TP " + Arrays.toString(s0) + " -> " + after + " | known " + known);
            int cost = r.startsWith("made ") ? Integer.parseInt(r.substring(r.lastIndexOf(' ') + 1)) : -1;
            String id = r.startsWith("made ") ? r.substring(5, r.indexOf(" for ")) : "-";
            if (!id.equals("-")) MADE.add(id);
            check("create: made one", DmzBotTechniques.created == c0 + 1 && known.contains(id), r);
            check("create: TP taken = its DragonMineZ price", cost > 0 && Math.abs((s0[0] - after) - cost) < 0.5F && cost <= s0[2] * DmzBotTechniques.BUDGET_SHARE + 0.5F,
                    "spent " + (s0[0] - after) + ", price " + cost + ", half the spare " + s0[2] * DmzBotTechniques.BUDGET_SHARE);
            String r2 = DmzBotTechniques.create(b, true);
            log("create 2: " + r2 + " | known " + DmzBotTechniques.knownForTest(b));
            if (r2.startsWith("made ")) MADE.add(r2.substring(5, r2.indexOf(" for ")));
            check("create: a second, different one", MADE.size() == 2 && !MADE.get(0).equals(MADE.get(1)), String.valueOf(MADE));
            check("create: not again so soon by itself", DmzBotTechniques.create(b, false).equals("too soon"), "-");
            List<String> uses = DmzBotKi.techniques(b, () -> PartyBots.standIn(b));
            check("create: in the techniques it fights with", MADE.stream().allMatch(x -> uses.stream().anyMatch(u -> u.startsWith(x + "("))), String.valueOf(uses));
            check("create: player untouched", untouched(p), mine(p) + " TP " + TP0 + " -> " + myTp(p));
            dump();
        }); }, 10);
        step("botki_created_chat", () -> mc.setScreen(null), 30);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {                // ... and actually throws it
            PartyBotEntity b = bot(p);
            if (b == null || MADE.isEmpty()) return;
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(at.x, at.y, at.z + 10, 180F, 0F); z.setNoAi(true); z.getPersistentData().putBoolean("xw_mb_rolled", true);
            p.serverLevel().addFreshEntity(z);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(4000); z.setHealth(4000);
            ST[2] = z;
            b.moveTo(at.x, at.y, at.z, 0F, 0F);
            p.connection.teleport(at.x - 9, at.y + 2, at.z + 5, -90F, 10F);
        }); }, 40);
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            LivingEntity z = (LivingEntity) ST[2];
            if (b == null || z == null || MADE.isEmpty()) return;
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(4000); z.setHealth(4000);
            DmzBotPower.setKiForTest(b, 1F);
            DmzBotKi.USES.remove(b.getUUID());
            boolean fired = false;
            for (int i = 0; i < 12 && !fired; i++) {                                 // (its pick is weighted: try until it picks its own one)
                DmzBotKi.resetForTest(b.getUUID());
                DmzBotPower.setKiForTest(b, 1F);
                Map<String, Integer> before = new HashMap<>(DmzBotKi.USES.getOrDefault(b.getUUID(), Map.of()));
                boolean started = DmzBotKi.tryFire(b, z, () -> PartyBots.standIn(b), true);
                Map<String, Integer> now = DmzBotKi.USES.getOrDefault(b.getUUID(), Map.of());
                for (String id : MADE) if (started && now.getOrDefault(id, 0) > before.getOrDefault(id, 0)) fired = true;
                if (!fired) DmzBotKi.resetForTest(b.getUUID());
            }
            check("create: it throws its own attack", fired, "uses " + DmzBotKi.USES.get(b.getUUID()));
        }), 5);
        step("botki_own_attack_a", () -> {}, 12);
        step("botki_own_attack_b", () -> {}, 25);

        // 2. too little TP: nothing made, nothing spent
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            DmzBotTechniques.setupForTest(b, 10F, true, 80);
            int c0 = DmzBotTechniques.created;
            String r = DmzBotTechniques.create(b, true);
            check("too little TP: none made", DmzBotTechniques.created == c0 && tp(b) == 10F && r.startsWith("can't afford"), r + " TP " + tp(b));
        }); }, 10);

        // 3. the TP its next mission's level costs is kept
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || T == null) { check("reserve", T != null, "no level-blocked mission to test with"); return; }
            DmzBotTraining.setupForTest(b, STATS0, 0F, Arrays.copyOfRange(T, 1, T.length));
            DmzBotTechniques.setupForTest(b, 1F, true, 80);
            float[] s = DmzBotTechniques.spareForTest(b);
            float keep = s[1];
            log("reserve: next mission " + T[0] + " needs level " + DmzBotTraining.requiredLevelForTest(T[0]) + " -> keeps " + keep + " TP");
            DmzBotTechniques.setupForTest(b, keep + 50F, true, 80);                   // (just above what training needs: no spare worth a technique)
            String r1 = DmzBotTechniques.create(b, true);
            float t1 = tp(b);
            DmzBotTechniques.setupForTest(b, keep + 200000F, true, 80);              // (plenty above it)
            String r2 = DmzBotTechniques.create(b, true);
            float t2 = tp(b);
            check("reserve: something to keep", keep > 0, keep + " TP");
            check("reserve: only reserve + 50 -> nothing made", t1 == keep + 50F && !r1.startsWith("made"), r1);
            check("reserve: with plenty, still keeps it", r2.startsWith("made") && t2 >= keep, r2 + " TP left " + t2 + " >= " + keep);
            DmzBotTraining.setupForTest(b, STATS0, 0F);                              // (missions wiped again)
            dump();
        }); }, 10);

        // 4. masters: Goku 12 blocks away. Too little TP / too low alignment: it doesn't go. Enough: Kamehameha, then a strike move
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            if (ST[2] instanceof Entity z) z.discard();
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            Entity g = DmzTalkNpcs.realForTest(p, "goku", at.x + 12, at.y, at.z);
            ST[1] = g;
            b.moveTo(at.x, at.y, at.z, 0F, 0F);
            p.connection.teleport(at.x + 6, at.y + 3, at.z + 10, 180F, 15F);       // (looking at the bot and Goku side by side)
            List<String> teach = DmzBotTechniques.teachesForTest("goku");
            int min = Integer.MAX_VALUE;
            StringBuilder costs = new StringBuilder();
            for (String s : teach) { int c = DmzBotTechniques.costForTest(b, s); costs.append(s).append('=').append(c).append(' '); if (c >= 0) min = Math.min(min, c); }
            A[0] = min;
            log("goku: " + (g == null ? "none" : DmzBotTechniques.masterForTest(g) + " at " + g.blockPosition().toShortString()) + " teaches " + costs);
            check("goku spawned", g != null && "goku".equals(DmzBotTechniques.masterForTest(g)), String.valueOf(g));
            DmzBotTechniques.setupForTest(b, min - 300F, true, 80);                   // (well short: it earns passive TP while it waits)
            A[5] = DmzBotTechniques.learned;
            b.setNoAi(false);
            DmzBotMasters.lookNowForTest(b);
            log("short of TP: lesson " + DmzBotTechniques.lessonForTest(b, "goku"));
        }); }, 160);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            check("master, too little TP: learned nothing", tp(b) >= A[0] - 300F && DmzBotTechniques.learned == A[5] && !DmzBotTechniques.knownForTest(b).contains("kamehameha"), "TP " + tp(b) + " (+ passive TP) known " + DmzBotTechniques.knownForTest(b));
            int k = DmzBotTechniques.costForTest(b, "kamehameha"), f = DmzBotTechniques.costForTest(b, "dragon_fist");
            A[1] = k; A[2] = f;
            DmzBotTechniques.setupForTest(b, k + f, true, 20);                        // (enough TP, but Goku won't teach an alignment of 20)
            A[5] = DmzBotTechniques.learned;
            DmzBotMasters.lookNowForTest(b);
            log("low alignment: lesson " + DmzBotTechniques.lessonForTest(b, "goku"));
        }); }, 160);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            check("master, alignment too low: learned nothing", tp(b) >= A[1] + A[2] && DmzBotTechniques.learned == A[5] && !DmzBotTechniques.knownForTest(b).contains("kamehameha"), "TP " + tp(b));
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            b.moveTo(at.x, at.y, at.z, 0F, 0F);
            DmzBotTechniques.setupForTest(b, A[1] + A[2], true, 80);
            A[3] = DmzBotTechniques.learned;
            DmzBotMasters.lookNowForTest(b);
            log("enough TP: lesson " + DmzBotTechniques.lessonForTest(b, "goku"));
        }); }, 70);
        step("botki_goku_walk", () -> {}, 60);
        step("botki_goku_learned", () -> mc.setScreen(null), 140);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            Entity g = (Entity) ST[1];
            if (b == null) return;
            String known = DmzBotTechniques.knownForTest(b);
            log("after goku: " + known + " TP " + tp(b) + " distance to goku " + (g == null ? "-" : String.format(Locale.ROOT, "%.1f", b.distanceTo(g))));
            check("master: learned Kamehameha", known.contains("kamehameha"), known);
            check("master: learned a strike move (Dragon Fist)", known.contains("dragon_fist"), known);
            boolean paidK = false, paidF = false;
            synchronized (DmzBotTechniques.LOG) {
                for (String s : DmzBotTechniques.LOG) {
                    java.util.regex.Matcher mt = java.util.regex.Pattern.compile("learned (\\w+) .* for (\\d+) TP \\(TP ([0-9.E]+) -> ([0-9.E]+)").matcher(s);
                    if (!mt.find()) continue;
                    float spent = Float.parseFloat(mt.group(3)) - Float.parseFloat(mt.group(4));
                    if (mt.group(1).equals("kamehameha") && Integer.parseInt(mt.group(2)) == (int) A[1] && Math.abs(spent - A[1]) < 0.5F) paidK = true;
                    if (mt.group(1).equals("dragon_fist") && Integer.parseInt(mt.group(2)) == (int) A[2] && Math.abs(spent - A[2]) < 0.5F) paidF = true;
                }
            }
            check("master: paid DragonMineZ's price for each", paidK && paidF && DmzBotTechniques.learned == A[3] + 2 && tp(b) < Math.min(A[1], A[2]),
                    "TP " + (A[1] + A[2]) + " -> " + tp(b) + " (kamehameha " + A[1] + " + dragon fist " + A[2] + "; passive TP meanwhile)");
            check("master: player untouched", untouched(p), mine(p) + " TP " + TP0 + " -> " + myTp(p));
            dump();
        }); }, 10);

        // 5. nothing in its slots: it fights with every technique it knows (its own two, Kamehameha, Dragon Fist)
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            if (ST[1] instanceof Entity g) g.discard();
            DmzBotTechniques.setupForTest(b, 100000F, false, 80);                     // (keeps what it knows, slots emptied)
            for (int i = 0; i < 2; i++) { String r = DmzBotTechniques.create(b, true); log("combat setup: " + r); }
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(at.x, at.y, at.z + 9, 180F, 0F); z.getPersistentData().putBoolean("xw_mb_rolled", true);
            z.setNoAi(true);
            p.serverLevel().addFreshEntity(z);
            ST[2] = z;
            b.moveTo(at.x, at.y, at.z, 0F, 0F);
            p.connection.teleport(at.x - 10, at.y + 2, at.z + 4, -90F, 10F);
        }); }, 40);
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            LivingEntity z = (LivingEntity) ST[2];
            if (b == null || z == null) return;
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(5000); z.setHealth(5000);
            DmzBotKi.USES.remove(b.getUUID());
            List<String> list = DmzBotKi.techniques(b, () -> PartyBots.standIn(b));
            int[] cnt = DmzBotKi.countsForTest(b);
            log("fight: known " + DmzBotTechniques.knownForTest(b) + " | fights with " + list);
            check("no slots: fights with all it knows", cnt[0] >= 4 && cnt[1] == 0, cnt[0] + " techniques, " + cnt[1] + " of them equipped");
            b.setTarget(z);
            for (int i = 1; i <= 450; i++) { int ii = i; com.xenowarriors.skill.ServerScheduler.later(i * 2, () -> {   // 30 s of its own fighting
                if (!z.isAlive()) return;
                if (z.getHealth() < 2500) z.setHealth(5000);
                if (b.getTarget() != z) b.setTarget(z);
                if (ii % 20 == 0) DmzBotPower.setKiForTest(b, 1F);                    // (ki isn't what's tested here)
            }); }
        }), 900);
        step("botki_fight", () -> {}, 5);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            Map<String, Integer> u = DmzBotKi.USES.getOrDefault(b.getUUID(), Map.of());
            log("fight: uses " + u);
            synchronized (DmzBotKi.LOG) { for (String s : DmzBotKi.LOG) log("fight log: " + s); DmzBotKi.LOG.clear(); }
            check("no slots: used several different techniques in one fight", u.size() >= 3, u.size() + " different: " + u.keySet());
            check("no slots: its slots still empty (nothing bound)", DmzBotTechniques.knownForTest(b).endsWith("equipped []"), DmzBotTechniques.knownForTest(b));
            b.setTarget(null);
            if (ST[2] instanceof Entity z) z.discard();
        }); }, 10);

        // 6. its Dragon Ball menu still opens on it, with everything it knows
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            String known = DmzBotTechniques.knownForTest(b);
            String sk0 = skillsOf(p);
            boolean open = DmzPerCharacter.beginView(p, b);
            Object live = DmzPerCharacter.live(p);
            Object m = call(call(live, "getTechniques"), "getUnlockedTechniques");
            int n = m instanceof Map<?, ?> mm ? mm.size() : -1;
            DmzPerCharacter.endView(p, "test");
            boolean all = m instanceof Map<?, ?> mm2 && mm2.keySet().stream().allMatch(x -> known.contains(String.valueOf(x)));
            check("menu: opens on the bot with all its techniques", open && n >= 4 && all, "open " + open + " live " + n + " | saved " + known);
            check("menu: player untouched after", untouched(p), mine(p) + " TP " + TP0 + " -> " + myTp(p));
            String sk1 = skillsOf(p);
            if (!sk0.equals(sk1)) log("menu: player's skills before " + sk0 + " | after " + sk1);
        }); }, 20);

        // 7. by itself: with its timer cleared it makes one at its next look (every 10 s)
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            b.setNoAi(true);
            DmzBotTechniques.setupForTest(b, 100000F, true, 80);
            DmzBotTechniques.allowCreateForTest(b);
            A[4] = DmzBotTechniques.created;
        }); }, 230);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            check("by itself: made one within 10 s", DmzBotTechniques.created == A[4] + 1, "made " + (DmzBotTechniques.created - A[4]) + " | " + DmzBotTechniques.knownForTest(b));
            dump();
            DmzBotTechniques.setupForTest(b, 0F, true, -1);
            log("summary ok=" + ok + " fail=" + fail);
        }); }, 20);
    }
}
