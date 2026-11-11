package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.compat.*;
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
 * UI test (focus "dmzbotskills", and "packbotskills" in the user's real mod pack): with DragonMineZ, a bot near King
 * Kai with spare TP learns Kaioken at DragonMineZ's price, raises it a level, goes Kaioken against a strong enemy (its
 * health drains) and drops it at low health; a bot near Goku learns Fly and flies after its flying owner; the TP its
 * next mission's level needs is kept; the player's own data never changes. Logged as "XW-TEST botskills ...".
 */
public final class BotSkillsTests {
    private BotSkillsTests() {}

    public static boolean handles(String focus) { return focus != null && (focus.startsWith("dmzbotskills") || focus.startsWith("packbotskills")); }

    private static Consumer<UiTest.Step> L;
    private static int ok, fail;
    private static void log(String s) { UiTest.log("botskills " + s); }
    private static void check(String what, boolean good, String detail) {
        if (good) ok++; else fail++;
        log(what + ": " + detail + (good ? " OK" : " FAIL"));
    }
    private static void server(Consumer<ServerPlayer> f) {
        UiTest.server(p -> { try { f.accept(p); } catch (Throwable t) { check("server step", false, String.valueOf(t)); com.xenowarriors.XenoWarriors.LOGGER.error("XW-TEST botskills", t); } });
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

    private static String mine(ServerPlayer p) {
        CompoundTag t = DmzPerCharacter.saveData(DmzPerCharacter.live(p));
        if (t == null) return "-";
        List<String> sk = new ArrayList<>();
        var list = t.getCompound("Skills").getList("SkillsList", 10);
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getInt("Level") > 0) sk.add(list.getCompound(i).toString());
        Collections.sort(sk);
        return "tech " + t.getCompound("Techniques").getList("UnlockedTechniques", 10).size() + " skills " + sk + " stats " + t.getCompound("Stats").hashCode();
    }
    private static float myTp(ServerPlayer p) {
        CompoundTag t = DmzPerCharacter.saveData(DmzPerCharacter.live(p));
        return t == null ? -1 : t.getCompound("Resources").getFloat("TrainingPointsF");
    }
    private static String MINE0;
    private static float TP0;
    private static boolean untouched(ServerPlayer p) { return mine(p).equals(MINE0) && myTp(p) >= TP0; }

    private static float tp(PartyBotEntity b) { float[] s = DmzBotTechniques.spareForTest(b); return s == null ? -1 : s[0]; }

    private static void dump() {
        synchronized (DmzBotTechniques.LOG) { for (String s : DmzBotTechniques.LOG) log("log " + s); DmzBotTechniques.LOG.clear(); }
        synchronized (DmzBotPower.LOG) { for (String s : DmzBotPower.LOG) if (s.contains("Kaioken") || s.contains("transform")) log("power " + s); DmzBotPower.LOG.clear(); }
        synchronized (DmzBotSkills.LOG) { for (String s : DmzBotSkills.LOG) log("skills " + s); DmzBotSkills.LOG.clear(); }
    }

    /** "learned <skill> ... for <c> TP (TP x -> y)" with x - y == c, in the log. */
    private static boolean paid(String skill, int c) {
        synchronized (DmzBotTechniques.LOG) {
            for (String s : DmzBotTechniques.LOG) {
                var mt = java.util.regex.Pattern.compile("(?:learned|raised) " + skill + " .*for (\\d+) TP \\(TP ([0-9.E]+) -> ([0-9.E]+)").matcher(s);
                if (mt.find() && Integer.parseInt(mt.group(1)) == c && Math.abs(Float.parseFloat(mt.group(2)) - Float.parseFloat(mt.group(3)) - c) < 0.5F) return true;
            }
        }
        return false;
    }

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

    /** Puts the camera (the player) somewhere - hovering, so a fall never counts as the owner being in danger. */
    private static void cam(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.getAbilities().flying = true; p.onUpdateAbilities();
        p.fallDistance = 0;
        p.connection.teleport(x, y, z, yaw, pitch);
    }

    /** No stray monsters about (a night spawn would keep the bot from walking off to a master). */
    private static void clearMobs(ServerPlayer p) {
        for (var m : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(64), x -> x instanceof net.minecraft.world.entity.monster.Enemy)) m.discard();
        p.serverLevel().setDayTime(6000);
    }

    private static String[] T;
    private static CompoundTag STATS0;
    private static final Object[] ST = new Object[4];
    private static final float[] A = new float[12];

    /** Fresh: no missions, no techniques or skills, this TP, alignment 80, ki full. */
    private static void fresh(PartyBotEntity b, float tp) {
        DmzBotTraining.setupForTest(b, STATS0, 0F);
        DmzBotTechniques.setupForTest(b, tp, true, 80);
        DmzBotTechniques.wipeSkillsForTest(b);
        DmzBotPower.setKiForTest(b, 1F);
    }

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
            call(call(d, "getResources"), "setTrainingPoints", 44444F);
            log("focus " + focus);
            PartyBots.spawn(p, CharacterType.KAERU, null);
        }); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) { check("setup", false, "no bot"); return; }
            DmzBots.ensureRace(b);
            b.setNoAi(true);
            CompoundTag tag = DmzPerCharacter.saved(p, DmzPerCharacter.botKey(b));
            STATS0 = tag == null ? null : tag.getCompound("Stats").copy();
            T = target();
            MINE0 = mine(p); TP0 = myTp(p);
            check("setup", STATS0 != null, "bot " + DmzPerCharacter.botKey(b) + " class " + DmzBotTraining.splitForTest(b) + " natural flier " + b.naturalFlier());
            for (String m : new String[]{"kingkai", "goku", "roshi", "piccolo", "vegeta", "frieza", "cell", "gohan", "krillin", "yamcha", "trunks", "oldkai"})
                log("skills sold by " + m + ": " + DmzBotTechniques.teachesSkillsForTest(m));
        }); }, 20);

        // 1. the TP for its next mission's level is kept: just above it, King Kai has nothing for it
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || T == null) { check("reserve", false, "no level-blocked mission"); return; }
            fresh(b, 1F);
            DmzBotTraining.setupForTest(b, STATS0, 0F, Arrays.copyOfRange(T, 1, T.length));
            float keep = DmzBotTechniques.spareForTest(b)[1];
            DmzBotTechniques.setupForTest(b, keep + 500F, false, 80);
            String les = DmzBotTechniques.lessonForTest(b, "kingkai");
            DmzBotTechniques.setupForTest(b, keep + 1000F, false, 80);
            String les2 = DmzBotTechniques.lessonForTest(b, "kingkai");
            check("reserve: keeps " + keep + " TP; 500 above it buys nothing from King Kai", keep > 0 && les.startsWith("null"), les);
            check("reserve: 1000 above it, Kaioken", les2.startsWith("kaioken"), les2);
            String up = DmzBotTechniques.upgrade(b, true);
            check("reserve: no skill level bought from it either", !up.startsWith("raised"), up);
        }); }, 10);

        // 2. King Kai: learns Kaioken (DragonMineZ's price, Kaioken before the cheaper passive skills)
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            Entity k = DmzTalkNpcs.realForTest(p, "kingkai", at.x + 12, at.y, at.z);
            ST[1] = k;
            check("king kai spawned", k != null && "kingkai".equals(DmzBotTechniques.masterForTest(k)), String.valueOf(k));
            fresh(b, 0F);
            int c = DmzBotTechniques.costForTest(b, "kaioken");
            A[0] = c;
            DmzBotTechniques.setupForTest(b, c, false, 80);
            A[1] = DmzBotTechniques.learned;
            b.moveTo(at.x, at.y, at.z, 0F, 0F);
            clearMobs(p);
            b.setNoAi(false);
            DmzBotMasters.lookNowForTest(b);
            cam(p, at.x + 6, at.y + 9, at.z + 9, 180F, 45F);
            log("king kai: kaioken costs " + c + " | lesson " + DmzBotTechniques.lessonForTest(b, "kingkai"));
        }); }, 200);
        step("botskills_kaioken_learned", () -> mc.setScreen(null), 40);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            int lv = DmzBotTechniques.skillLevelForTest(b, "kaioken");
            check("king kai: learned Kaioken", lv == 1, "level " + lv + " TP " + tp(b));
            check("king kai: paid DragonMineZ's price", paid("kaioken", (int) A[0]) && DmzBotTechniques.learned == A[1] + 1, "price " + A[0]);
            check("king kai: player untouched", untouched(p), mine(p) + " TP " + TP0 + " -> " + myTp(p));
            dump();
            if (ST[1] instanceof Entity k) k.discard();
            // a level up (DragonMineZ sells skill levels in its skill menu, no master needed): Kaioken 2 = x3
            b.setNoAi(true);
            int c2 = DmzBotTechniques.costForTest(b, "kaioken");                     // (-1: it has it now; the level price is logged)
            DmzBotTechniques.setupForTest(b, 3100F, false, 80);
            String up = DmzBotTechniques.upgrade(b, true);
            int lv2 = DmzBotTechniques.skillLevelForTest(b, "kaioken");
            check("level up: Kaioken level 2 at DragonMineZ's price", lv2 == 2 && up.startsWith("raised kaioken") && paid("kaioken", 1500), up + " (buy price now " + c2 + ")");
            dump();
        }); }, 10);

        // 3. a fight against a strong enemy: Kaioken (x3), its health drains; at low health it drops it
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(at.x, at.y, at.z + 6, 180F, 0F); z.setNoAi(true); z.getPersistentData().putBoolean("xw_mb_rolled", true);
            p.serverLevel().addFreshEntity(z);
            ST[2] = z;
            b.moveTo(at.x, at.y, at.z, 0F, 0F);
            b.setHealth(b.getMaxHealth());
            cam(p, at.x - 8, at.y + 2, at.z + 3, -90F, 10F);
            A[2] = DmzBotPower.kaiokens;
        }); }, 30);
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            LivingEntity z = (LivingEntity) ST[2];
            if (b == null || z == null) return;
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(5000); z.setHealth(5000);
            b.setNoAi(false);
            b.setTarget(z);
            A[3] = DmzBotPower.kaiokenDrainForTest(b);
            for (int i = 1; i <= 100; i++) com.xenowarriors.skill.ServerScheduler.later(i * 2, () -> { if (b.getTarget() != z && z.isAlive()) b.setTarget(z); if (z.getHealth() < 2500) z.setHealth(5000); });
        }), 100);
        step("botskills_kaioken_fight", () -> {}, 5);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            String k = DmzBotPower.kaiokenForTest(b);
            float drained = DmzBotPower.kaiokenDrainForTest(b) - A[3];
            log("fight: " + DmzBotPower.state(b) + " health " + b.getHealth() + "/" + b.getMaxHealth() + " drained " + drained);
            check("fight: went Kaioken against the strong enemy", k != null && DmzBotPower.kaiokens > A[2], "kaioken " + k);
            check("fight: Kaioken costs it health", drained > 0, drained + " health");
            b.setHealth(b.getMaxHealth() * 0.3F);                                    // (hurt badly)
            dump();
        }); }, 30);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            String k = DmzBotPower.kaiokenForTest(b);
            check("fight: dropped Kaioken at low health", k == null, "kaioken " + k + " health " + Math.round(b.getHealth()) + "/" + Math.round(b.getMaxHealth()));
            dump();
            b.setTarget(null);
            if (ST[2] instanceof Entity z) z.discard();
            b.setHealth(b.getMaxHealth());
        }); }, 10);

        // 4. Goku: learns Fly, then follows its flying owner through the air
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            Entity g = DmzTalkNpcs.realForTest(p, "goku", at.x + 12, at.y, at.z);
            ST[1] = g;
            fresh(b, 0F);
            int c = DmzBotTechniques.costForTest(b, "fly");
            A[4] = c;
            DmzBotTechniques.setupForTest(b, c, false, 80);                           // (Fly's price: less than Kamehameha)
            A[5] = DmzBotTechniques.learned;
            check("goku: can't fly before", !b.canFly(), "canFly " + b.canFly());
            b.moveTo(at.x, at.y, at.z, 0F, 0F);
            clearMobs(p);
            b.setNoAi(false);
            DmzBotMasters.lookNowForTest(b);
            cam(p, at.x + 6, at.y + 9, at.z + 9, 180F, 45F);
            log("goku: fly costs " + c + " | lesson " + DmzBotTechniques.lessonForTest(b, "goku"));
        }); }, 220);
        step("botskills_fly_learned", () -> mc.setScreen(null), 20);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            int lv = DmzBotSkills.flyLevelForTest(b);
            check("goku: learned Fly", lv == 1 && DmzBotTechniques.learned == A[5] + 1, "level " + lv);
            check("goku: paid DragonMineZ's price", paid("fly", (int) A[4]), "price " + A[4]);
            if (ST[1] instanceof Entity g) g.discard();
            dump();
            // the owner takes off and flies away, up high
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.getAbilities().flying = true; p.onUpdateAbilities();
            cam(p, at.x - 6, at.y + 14, at.z - 16, 180F, 20F);
            DmzBotPower.setKiForTest(b, 1F);
            A[6] = (float) at.y;
        }); }, 60);
        step(null, () -> server(p -> { p.getAbilities().flying = true; p.onUpdateAbilities(); }), 60);
        step("botskills_fly_follow", () -> {}, 5);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            double up = b.getY() - A[6], dist = b.distanceTo(p);
            float ki = DmzBotPower.ki(b) == null ? -1 : DmzBotPower.ki(b)[0];
            log("fly: bot at " + b.blockPosition().toShortString() + " " + String.format(Locale.ROOT, "%.1f", up) + " up, " + String.format(Locale.ROOT, "%.1f", dist)
                    + " from its owner, onGround " + b.onGround() + " noGravity " + b.isNoGravity() + " canFly " + b.canFly() + " | " + DmzBotPower.state(b));
            check("fly: up in the air with its flying owner", b.canFly() && !b.onGround() && up > 6 && dist < 10, String.format(Locale.ROOT, "%.1f up, %.1f away", up, dist));
            check("fly: flying costs it ki", DmzBotPower.state(b).contains("ki=") && !DmzBotPower.state(b).startsWith("ki=100%"), DmzBotPower.state(b));
            check("fly: player untouched", untouched(p), mine(p) + " TP " + TP0 + " -> " + myTp(p));
            dump();
        }); }, 5);
        step(null, () -> server(p -> {
            p.getAbilities().flying = false; p.onUpdateAbilities();
            PartyBotEntity b = bot(p);
            if (b != null) { DmzBotTechniques.setupForTest(b, 0F, true, -1); DmzBotTechniques.wipeSkillsForTest(b); }
            log("summary ok=" + ok + " fail=" + fail);
        }), 20);
    }
}
