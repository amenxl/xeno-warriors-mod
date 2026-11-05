package com.xenowarriors.client.test;

import com.xenowarriors.boss.ZombieSaiyanEntity;
import com.xenowarriors.boss.ZombieSaiyans;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.compat.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;

/**
 * UI tests for the DragonMineZ bots / missions / Zombie Saiyan work (focus "dmz5..."). One dispatch line in UiTest;
 * results are logged as "XW-TEST dmz5 ...". Sub-focuses: dmz5 (everything), dmz5saiyan (only the Zombie Saiyan).
 */
public final class DmzTests {
    private DmzTests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    private static Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;

    public static boolean handles(String focus) { return focus != null && focus.startsWith("dmz5"); }

    public static void build(Minecraft mc, String focus, Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character;
        setup(mc);
        if (!focus.equals("dmz5saiyan")) {
            bots(mc);
            quests(mc);
            kaioken(mc);
        }
        saiyan(mc);
        step(null, () -> server(p -> { dumpLogs(); log("done"); }), 20);
    }

    private static void log(String s) { LOG.accept("dmz5 " + s); }
    private static void server(Consumer<ServerPlayer> f) { SERVER.accept(f); }
    private static void step(String shot, Runnable a, int delay) { S.add(shot, a, delay); }

    private static void noDmzScreen(Minecraft mc) {
        if (mc.screen != null && mc.screen.getClass().getName().startsWith("com.dragonminez")) mc.setScreen(null);
    }

    private static int dumped0, dumped1, dumped2;

    private static void dumpLogs() {
        synchronized (DmzBots.LOG) { for (int i = dumped0; i < DmzBots.LOG.size(); i++) log("botlog " + DmzBots.LOG.get(i)); dumped0 = DmzBots.LOG.size(); }
        synchronized (DmzQuestHelp.LOG) { for (int i = dumped1; i < DmzQuestHelp.LOG.size(); i++) log("questlog " + DmzQuestHelp.LOG.get(i)); dumped1 = DmzQuestHelp.LOG.size(); }
        synchronized (ZombieSaiyans.LOG) { for (int i = dumped2; i < ZombieSaiyans.LOG.size(); i++) log("saiyanlog " + ZombieSaiyans.LOG.get(i)); dumped2 = ZombieSaiyans.LOG.size(); }
    }

    // ---------------------------------------------------------------- DragonMineZ reflection helpers

    static Object data(ServerPlayer p) { return DmzPerCharacter.live(p); }

    static Object call(Object o, String m, Object... args) {
        try {
            for (var mm : o.getClass().getMethods()) if (mm.getName().equals(m) && mm.getParameterCount() == args.length) return mm.invoke(o, args);
        } catch (Throwable t) { return "?" + t.getClass().getSimpleName(); }
        return "?nomethod";
    }

    static void make(ServerPlayer p, String race) {
        Object d = data(p);
        Object ch = call(d, "getCharacter");
        call(ch, "setRace", race);
        call(call(d, "getStatus"), "setHasCreatedCharacter", true);
        DmzPerCharacter.syncStats(p);
    }

    static String info(ServerPlayer p) {
        Object d = data(p);
        if (d == null) return "-";
        Object ch = call(d, "getCharacter"), st = call(d, "getStats"), res = call(d, "getResources");
        return "race=" + call(ch, "getRaceName") + " class=" + call(ch, "getCharacterClass") + " level=" + call(d, "getLevel") + " STR=" + call(st, "getStrength")
                + " total=" + call(st, "getTotalStats") + " TP=" + call(res, "getTrainingPoints") + " created=" + call(call(d, "getStatus"), "isHasCreatedCharacter");
    }

    static String tagInfo(CompoundTag t) {
        if (t == null) return "none";
        return "race=" + t.getCompound("Character").getString("Race") + "/" + t.getCompound("Character").getString("Class") + " created=" + t.getCompound("Status").getBoolean("HasCreatedChar")
                + " STR=" + t.getCompound("Stats").getInt("STR") + " TP=" + t.getCompound("Resources").getFloat("TrainingPointsF");
    }

    private static final Object[] BOT = new Object[1];

    /** Keeps the test player alive and clears hostile mobs around (summoned story fighters, leftovers). */
    static void protect(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60 * 30, 5, false, false));
        p.setHealth(p.getMaxHealth());
        for (var m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), m -> m instanceof net.minecraft.world.entity.monster.Enemy && !(m instanceof ZombieSaiyanEntity))) m.discard();
    }

    private static PartyBotEntity bot(ServerPlayer p) {
        if (BOT[0] instanceof PartyBotEntity b && b.isAlive()) return b;
        for (PartyBotEntity b : PartyBots.bots(p)) return b;
        return null;
    }

    // ---------------------------------------------------------------- setup

    private static void setup(Minecraft mc) {
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            CHAR.accept(CharacterType.MIKIKO); }, 60);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            make(p, "saiyan");
            log("story mode " + DmzQuests.storyMode(true));
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60 * 30, 5, false, false));
            p.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
            p.serverLevel().setDayTime(6000);
            log("owner " + info(p));
        }); }, 40);
    }

    // ---------------------------------------------------------------- 1-3: bots

    private static final Object[] OWN = new Object[1];

    private static void bots(Minecraft mc) {
        final float[] tp0 = new float[1];
        final long[] t0 = new long[1];
        step(null, () -> { noDmzScreen(mc); server(p -> {
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("bot spawn FAILED"); return; }
            b.moveTo(at.x + 2, at.y, at.z + 2, 0, 0);
            BOT[0] = b;
            String key = DmzPerCharacter.botKey(b);
            log("bot " + key + " before: " + tagInfo(DmzPerCharacter.saved(p, key)));
            String picked = DmzBots.ensureRace(b);
            log("bot race picked: " + picked + " -> " + tagInfo(DmzPerCharacter.saved(p, key)));
            log("bot race picked again (should be null): " + DmzBots.ensureRace(b));
            log("passive TP per 5 s = " + DmzBots.passive());
        }); }, 40);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p); if (b == null) return; tp0[0] = DmzBots.tp(b); t0[0] = p.level().getGameTime(); log("bot TP at start " + tp0[0]); }), 20 * 60);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p); if (b == null) return;
            float now = DmzBots.tp(b);
            log("bot TP after " + (p.level().getGameTime() - t0[0]) / 20 + " s: " + now + " (gained " + (now - tp0[0]) + ")");
            // a kill: TP for its DMZ character
            var z = EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(b.getX() + 1, b.getY(), b.getZ(), 0, 0); p.serverLevel().addFreshEntity(z);
            float before = DmzBots.tp(b);
            z.hurt(b.damageSources().mobAttack(b), 10000F);
            log("bot kill TP: " + before + " -> " + DmzBots.tp(b) + " (zombie, DMZ formula " + DmzBots.killTp(z) + ")");
        }), 10);
        // 3: its Dragon Ball menu
        step(null, () -> server(p -> { PartyBotEntity b = bot(p); if (b == null) return;
            DmzBots.giveTp(b, 5000);
            CompoundTag own = DmzPerCharacter.saveData(data(p));
            OWN[0] = own == null ? null : own.copy();
            log("menu: owner before " + info(p) + " | bot saved " + tagInfo(DmzPerCharacter.saved(p, DmzPerCharacter.botKey(b))));
            protect(p);
            PartyBots.action(p, b.getId(), PartyBots.ACT_DMZ, 0);
            log("menu: viewing=" + DmzPerCharacter.viewing(p) + " live now " + info(p));
        }), 10);
        step("dmz5_botmenu", () -> log("menu: client screen " + (mc.screen == null ? "none" : mc.screen.getClass().getName()) + " showing=" + com.xenowarriors.client.compat.DmzBotView.showing()), 40);
        step(null, () -> {                                                       // spend points the way DragonMineZ's screen does
            try {
                Class<?> st = Class.forName("com.dragonminez.common.network.C2S.IncreaseStatC2S$StatType");
                Object str = st.getField("STR").get(null);
                Object pkt = Class.forName("com.dragonminez.common.network.C2S.IncreaseStatC2S").getConstructor(st, int.class).newInstance(str, 5);
                Class.forName("com.dragonminez.common.network.NetworkHandler").getMethod("sendToServer", Object.class).invoke(null, pkt);
                log("menu: sent IncreaseStatC2S STR x5");
            } catch (Throwable t) { log("menu: IncreaseStatC2S FAILED " + t); }
        }, 20);
        step("dmz5_botmenu_spent", () -> server(p -> log("menu: live after spending " + info(p))), 30);
        step(null, () -> { mc.setScreen(null); }, 30);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p); if (b == null) return;
            log("menu: closed -> viewing=" + DmzPerCharacter.viewing(p) + " owner now " + info(p));
            log("menu: bot saved now " + tagInfo(DmzPerCharacter.saved(p, DmzPerCharacter.botKey(b))));
            CompoundTag now = DmzPerCharacter.saveData(data(p));
            CompoundTag was = (CompoundTag) OWN[0];
            log("menu: owner data unchanged = " + (was != null && now != null && was.getCompound("Stats").equals(now.getCompound("Stats"))
                    && was.getCompound("Character").getString("Race").equals(now.getCompound("Character").getString("Race"))
                    && Math.abs(was.getCompound("Resources").getFloat("TrainingPointsF") - now.getCompound("Resources").getFloat("TrainingPointsF")) < 50));
            // safety: logging out mid-menu, and a crash mid-menu (the server forgets; the next check puts it back)
            DmzPerCharacter.beginView(p, b);
            log("menu: reopened (viewing=" + DmzPerCharacter.viewing(p) + ") live " + info(p));
            DmzPerCharacter.endView(p, "logged out");
            log("menu: after logout restore viewing=" + DmzPerCharacter.viewing(p) + " owner " + info(p));
            DmzPerCharacter.beginView(p, b);
            DmzPerCharacter.forgetOpenForTest(p);
            log("menu: 'crash' with bot data live: " + info(p));
        }), 30);
        step(null, () -> server(p -> log("menu: after crash recovery viewing=" + DmzPerCharacter.viewing(p) + " owner " + info(p)
                + " unchanged=" + (OWN[0] != null && ((CompoundTag) OWN[0]).getCompound("Stats").equals(DmzPerCharacter.saveData(data(p)).getCompound("Stats"))))), 50);
        step(null, () -> { noDmzScreen(mc); server(p -> dumpLogs()); }, 5);
    }

    // ---------------------------------------------------------------- 4-6: missions

    /** {quest id, objective index, entity or item id, kind} for the first objective that fits. */
    @SuppressWarnings("unchecked")
    private static String[] findQuest(ServerPlayer p, String kind) {
        try {
            Map<String, Object> all = (Map<String, Object>) Class.forName("com.dragonminez.common.quest.QuestRegistry").getMethod("getAllQuests").invoke(null);
            for (var e : all.entrySet()) {
                List<?> objs = (List<?>) call(e.getValue(), "getObjectives");
                for (int i = 0; i < objs.size(); i++) {
                    Object o = objs.get(i);
                    String cls = o.getClass().getSimpleName();
                    if (kind.equals("item") && cls.equals("ItemObjective")) return new String[]{e.getKey(), "" + i, String.valueOf(call(o, "getItemId"))};
                    if (!cls.equals("KillObjective") || Boolean.TRUE.equals(call(o, "isTag"))) continue;
                    if (!"NATURAL".equals(String.valueOf(call(o, "getSpawnMode")))) continue;
                    EntityType<?> t = (EntityType<?>) call(o, "resolveEntityType");
                    if (t == null) continue;
                    var c = t.create(p.serverLevel());
                    boolean saga = c != null && c.getClass().getName().contains(".sagas.");
                    if (c != null) c.discard();
                    if (kind.equals("kill") && !saga && "ANY_MATCHING".equals(String.valueOf(call(o, "getCountMode")))) return new String[]{e.getKey(), "" + i, ForgeRegistries.ENTITY_TYPES.getKey(t).toString()};
                    if (kind.equals("story") && saga) return new String[]{e.getKey(), "" + i, ForgeRegistries.ENTITY_TYPES.getKey(t).toString()};
                }
            }
        } catch (Throwable t) { log("quest lookup failed " + t); }
        return null;
    }

    private static void quests(Minecraft mc) {
        final String[][] q = new String[3][];
        step(null, () -> { noDmzScreen(mc); server(p -> {
            q[0] = findQuest(p, "kill"); q[1] = findQuest(p, "story"); q[2] = findQuest(p, "item");
            log("quests found: kill=" + Arrays.toString(q[0]) + " story=" + Arrays.toString(q[1]) + " item=" + Arrays.toString(q[2]));
        }); }, 20);
        // 4: a bot's kill counts for your kill objective
        step(null, () -> server(p -> {
            if (q[0] == null) { log("kill credit: no suitable quest"); return; }
            PartyBotEntity b = bot(p);
            DmzQuests.accept(p, q[0][0]);
            int idx = Integer.parseInt(q[0][1]);
            log("kill credit: accepted " + q[0][0] + " " + DmzQuests.progress(p, q[0][0], idx));
            for (int k = 0; k < 2; k++) {
                var t = ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation(q[0][2]));
                var e = t == null ? null : t.create(p.serverLevel());
                if (!(e instanceof LivingEntity le) || b == null) { log("kill credit: can't make " + q[0][2]); return; }
                le.moveTo(b.getX() + 1.5, b.getY(), b.getZ(), 0, 0);
                p.serverLevel().addFreshEntity(le);
                le.hurt(b.damageSources().mobAttack(b), 1.0E6F);
                log("kill credit: bot killed " + q[0][2] + " dead=" + le.isDeadOrDying() + " -> " + DmzQuests.progress(p, q[0][0], idx));
            }
        }), 20);
        // 5: a story enemy summoned for its objective
        step(null, () -> server(p -> {
            if (q[1] == null) { log("summon: no story-only kill objective found"); return; }
            DmzQuestHelp.resetSummonedForTest(p);
            DmzQuests.accept(p, q[1][0]);
            log("summon: accepted " + q[1][0] + " " + DmzQuests.progress(p, q[1][0], Integer.parseInt(q[1][1])) + " wants " + q[1][2]);
        }), 20 * 14);
        step("dmz5_summoned", () -> server(p -> {
            dumpLogs();
            if (q[1] == null) return;
            var t = ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation(q[1][2]));
            var near = p.serverLevel().getEntities(t, p.getBoundingBox().inflate(64), e -> e.getPersistentData().getBoolean("xw_quest_summon"));
            log("summon: " + near.size() + " x " + q[1][2] + " near the player; tags " + (near.isEmpty() ? "-" : near.get(0).getPersistentData().getString("dmz_quest_key") + "#" + near.get(0).getPersistentData().getInt("dmz_quest_objective_index")));
            for (var e : near) if (e instanceof Mob m) { m.setNoAi(true); m.lookAt(p, 180, 0); }
            // kill one: it counts for the objective
            if (!near.isEmpty() && near.get(0) instanceof LivingEntity le) {
                int n = 0;
                while (le.isAlive() && n++ < 40) { le.invulnerableTime = 0; le.hurt(p.damageSources().playerAttack(p), 1.0E7F); }
                if (le.isAlive()) le.kill();
                log("summon: killed one (" + n + " hits, dead=" + le.isDeadOrDying() + ") -> " + DmzQuests.progress(p, q[1][0], Integer.parseInt(q[1][1])));
            }
            for (var e : near) e.discard();
        }), 40);
        // 6: mission items from kills
        step(null, () -> server(p -> {
            if (q[2] == null) { log("items: no item objective found"); return; }
            DmzQuests.accept(p, q[2][0]);
            log("items: accepted " + q[2][0] + " needs " + DmzQuestHelp.needs(p));
            int rolls0 = DmzQuestHelp.itemRolls, drops0 = DmzQuestHelp.itemDrops;
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            int n = 100;
            for (int i = 0; i < n; i++) {
                var z = EntityType.ZOMBIE.create(p.serverLevel());
                z.moveTo(at.x + 4, at.y, at.z + 4, 0, 0);
                p.serverLevel().addFreshEntity(z);
                PartyBotEntity b = bot(p);
                if (i % 4 == 3 && b != null) z.hurt(b.damageSources().mobAttack(b), 1.0E6F);
                else z.hurt(p.damageSources().playerAttack(p), 1.0E6F);
            }
            int rolls = DmzQuestHelp.itemRolls - rolls0, drops = DmzQuestHelp.itemDrops - drops0;
            log("items: " + drops + " drops in " + rolls + " counted kills of " + n + " (" + Math.round(100.0 * drops / Math.max(1, rolls)) + "%, want ~30%)");
            var items = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(40),
                    it -> ForgeRegistries.ITEMS.getKey(it.getItem().getItem()).toString().equals(q[2][2]));
            log("items: " + items.size() + " stacks of " + q[2][2] + " on the ground");
            for (var it : items) it.discard();
            for (var it : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(40))) it.discard();
        }), 20);
        step(null, () -> server(p -> dumpLogs()), 5);
    }

    // ---------------------------------------------------------------- 7: Kaioken

    private static void kaioken(Minecraft mc) {
        final double[] got = new double[2];
        step(null, () -> server(p -> {
            protect(p);
            DmzKaioken.resetForTest(p);
            DmzKaioken.poll(p);                                                  // (baseline)
            double before = DmzKaioken.mastery(p, "x2");
            add(p, 1.0);                                                         // as DragonMineZ would while Kaioken x2 is used
            double bonus = DmzKaioken.poll(p);
            got[0] = DmzKaioken.mastery(p, "x2") - before;
            log("kaioken Mikiko: x2 mastery " + before + " -> " + DmzKaioken.mastery(p, "x2") + " (+" + got[0] + " from +1.0, bonus " + bonus + ")");
        }), 10);
        step(null, () -> { CHAR.accept(CharacterType.KAERU); }, 60);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            make(p, "human");
            DmzKaioken.resetForTest(p);
            DmzKaioken.poll(p);
            double before = DmzKaioken.mastery(p, "x2");
            add(p, 1.0);
            double bonus = DmzKaioken.poll(p);
            got[1] = DmzKaioken.mastery(p, "x2") - before;
            log("kaioken Kaeru: x2 mastery " + before + " -> " + DmzKaioken.mastery(p, "x2") + " (+" + got[1] + " from +1.0, bonus " + bonus + ")");
            log("kaioken ratio Mikiko/other = " + (got[1] > 0 ? got[0] / got[1] : -1));
        }); }, 30);
        step(null, () -> { CHAR.accept(CharacterType.MIKIKO); }, 60);
        // the real thing: Kaioken x2 switched on for a while, DragonMineZ's own tick raising the mastery
        step(null, () -> { noDmzScreen(mc); server(p -> {
            DmzKaioken.resetForTest(p);
            DmzKaioken.poll(p);
            Object ch = call(data(p), "getCharacter");
            log("kaioken live: setActiveStackForm " + call(ch, "setActiveStackForm", "kaioken", "x2") + " active=" + call(ch, "getActiveStackFormGroup") + "/" + call(ch, "getActiveStackForm")
                    + " mastery " + DmzKaioken.mastery(p, "x2") + " passive/5s " + passive5());
        }); }, 10);
        step(null, () -> server(p -> {
            Object ch = call(data(p), "getCharacter");
            log("kaioken live after 20 s: active=" + call(ch, "getActiveStackFormGroup") + "/" + call(ch, "getActiveStackForm") + " mastery " + DmzKaioken.mastery(p, "x2")
                    + " bonus given " + DmzKaioken.GIVEN.get(p.getGameProfile().getName()));
            call(ch, "clearActiveStackForm");
        }), 400);
    }

    private static Object passive5() {
        try {
            Object fd = Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getStackForm", String.class, String.class).invoke(null, "kaioken", "x2");
            return fd == null ? "no form" : call(fd, "getPassiveMasteryEveryFiveSeconds");
        } catch (Throwable t) { return "?" + t; }
    }

    private static void add(ServerPlayer p, double amt) {
        Object fm = call(call(data(p), "getCharacter"), "getStackFormMasteries");
        call(fm, "addMastery", "kaioken", "x2", amt, 100.0);
    }

    // ---------------------------------------------------------------- 8: the Zombie Saiyan

    private static final int[] ID = {-1}, CAM = {-1};

    private static void camera(Minecraft mc, double dx, double dy, double dz, float yaw, float pitch) {
        step(null, () -> server(p -> {
            if (CAM[0] >= 0 && p.serverLevel().getEntity(CAM[0]) != null) p.serverLevel().getEntity(CAM[0]).discard();
            var a = EntityType.ARMOR_STAND.create(p.serverLevel());
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            a.moveTo(at.x + dx, at.y + dy, at.z + dz, yaw, pitch); a.setNoGravity(true); a.setInvisible(true);
            p.serverLevel().addFreshEntity(a); CAM[0] = a.getId();
        }), 10);
        step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.level.getEntity(CAM[0]) instanceof ArmorStand a) {
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(pitch); a.xRotO = pitch; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a); } }, 5);
    }

    private static ZombieSaiyanEntity saiyan(ServerPlayer p) {
        return p.serverLevel().getEntity(ID[0]) instanceof ZombieSaiyanEntity z ? z : null;
    }

    private static boolean guarded;

    private static void saiyan(Minecraft mc) {
        if (!guarded) {                                                          // (its hits go through DragonMineZ's damage: the test player just can't die)
            guarded = true;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, false, net.minecraftforge.event.entity.living.LivingDamageEvent.class, e -> {
                if (e.getEntity() instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer) && p.getHealth() - e.getAmount() < 4F) { e.setAmount(Math.max(0, p.getHealth() - 4F)); }
            });
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false, net.minecraftforge.event.entity.living.LivingDeathEvent.class, e -> {
                if (e.getEntity() instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer)) { e.setCanceled(true); p.setHealth(p.getMaxHealth()); log("guard: the test player would have died (" + e.getSource().getMsgId() + ")"); }
            });
        }
        step(null, () -> { noDmzScreen(mc); mc.setScreen(null); server(p -> {
            for (PartyBotEntity b : PartyBots.bots(p)) PartyBots.action(p, b.getId(), PartyBots.ACT_DISMISS, 0);   // (sent home, so it doesn't come back)
            for (PartyBotEntity b : p.serverLevel().getEntitiesOfClass(PartyBotEntity.class, p.getBoundingBox().inflate(64))) b.discard();
            protect(p);
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.teleportTo(at.x, at.y, at.z - 12);
            p.serverLevel().setDayTime(6000);
            var z = ZombieSaiyans.ZOMBIE_SAIYAN.get().create(p.serverLevel());
            z.moveTo(at.x, at.y, at.z, 180F, 0F);
            z.setYHeadRot(180F); z.yBodyRot = 180F;
            z.setNoAi(true); z.setPersistenceRequired();
            p.serverLevel().addFreshEntity(z);
            ID[0] = z.getId();
            log("saiyan spawned: hp " + z.getMaxHealth() + " lv " + z.getPersistentData().getInt("xw_lvl") + " ki kit " + DmzBossKi.kit() + " egg in registry " + ForgeRegistries.ITEMS.getKey(ZombieSaiyans.SPAWN_EGG.get()));
        }); }, 30);
        camera(mc, 0, 0.3, -3.0, 0F, 4F);
        String[] names = {"base", "ssj1", "ssj2", "ssj3", "ssj4"};
        for (int f = 0; f <= 4; f++) {
            final int ff = f;
            step(null, () -> server(p -> { var z = saiyan(p); if (z != null) { z.setFormForTest(ff); z.setYRot(180F); z.setYHeadRot(180F); z.yBodyRot = 180F; } }), 30);
            step("dmz5_saiyan_" + names[f], () -> {}, 3);
        }
        camera(mc, 3.0, 0.6, -2.0, 55F, 8F);
        step("dmz5_saiyan_ssj4_side", () -> {}, 10);
        step(null, () -> server(p -> { var z = saiyan(p); if (z != null) z.setFormForTest(3); }), 20);
        step("dmz5_saiyan_ssj3_side", () -> {}, 3);
        camera(mc, 2.0, 0.6, 3.0, 145F, 8F);
        step("dmz5_saiyan_ssj3_back", () -> {}, 10);
        step(null, () -> server(p -> { var z = saiyan(p); if (z != null) { z.setFormForTest(3); z.knockOutForTest(); } }), 20);
        camera(mc, 2.5, 1.4, -2.5, 45F, 30F);
        step("dmz5_saiyan_knocked_out", () -> {}, 10);
        // the fight for real: AI on, hits taking it down through every form
        step(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; server(p -> {
            var z = saiyan(p);
            if (z != null) z.discard();
            protect(p);
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.teleportTo(at.x, at.y, at.z - 9);
            p.setYRot(0); p.setXRot(5);
            var n = ZombieSaiyans.ZOMBIE_SAIYAN.get().create(p.serverLevel());
            n.moveTo(at.x, at.y, at.z, 180F, 0F); n.setPersistenceRequired();
            p.serverLevel().addFreshEntity(n);
            ID[0] = n.getId();
        }); }, 40);
        step("dmz5_saiyan_fight", () -> server(p -> { var z = saiyan(p); if (z != null) log("fight: start form " + z.form() + " hp " + z.getHealth() + "/" + z.getMaxHealth() + " target " + z.getTarget()); }), 60);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            step(i == 2 ? "dmz5_saiyan_fight_ssj3" : i == 1 ? "dmz5_saiyan_fight_ssj2" : null, () -> server(p -> {
                var z = saiyan(p);
                if (z == null) return;
                z.invulnerableTime = 0;
                z.hurt(p.damageSources().magic(), z.getMaxHealth() * 0.31F);
                log("fight: hit " + k + " -> form " + z.form() + " act " + z.act() + " hp " + Math.round(z.getHealth()) + "/" + Math.round(z.getMaxHealth()) + " ko " + z.ko());
            }), 70);
        }
        step("dmz5_saiyan_fight_ko", () -> server(p -> { var z = saiyan(p); if (z == null) return;
            z.invulnerableTime = 0; z.hurt(p.damageSources().playerAttack(p), z.getMaxHealth() * 2F);
            log("fight: 'killing' blow at " + ZombieSaiyanEntity.formName(z.form()) + " -> ko " + z.ko() + " alive " + z.isAlive() + " hp " + z.getHealth()); }), 20);
        step("dmz5_saiyan_fight_scream", () -> server(p -> { var z = saiyan(p); if (z != null) log("fight: after KO -> form " + z.form() + " act " + z.act() + " ko " + z.ko()); }), 45);
        step("dmz5_saiyan_fight_ssj4", () -> server(p -> { var z = saiyan(p); if (z != null) log("fight: revived -> form " + z.form() + " hp " + Math.round(z.getHealth()) + "/" + Math.round(z.getMaxHealth())
                + " dmg " + z.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) + " ki " + z.kiKindForTest()); }), 40);
        step(null, () -> server(p -> { var z = saiyan(p); if (z != null) log("fight: SSJ4 after a while: form " + z.form() + " hp " + Math.round(z.getHealth()) + "/" + Math.round(z.getMaxHealth()) + " ki " + z.kiKindForTest() + " target " + (z.getTarget() == null ? "-" : z.getTarget().getName().getString())
                + " player hp " + p.getHealth()); dumpLogs(); }), 100);
        step(null, () -> server(p -> { var z = saiyan(p); if (z == null) return;
            ZombieSaiyans.ARMOR_CHANCE = 1F;
            for (int i = 0; i < 12 && z.isAlive(); i++) { z.invulnerableTime = 0; z.hurt(p.damageSources().magic(), z.getMaxHealth() * 0.6F); }
            log("fight: final blows -> alive " + z.isAlive() + " dead " + z.isDeadOrDying());
        }), 10);
        step("dmz5_saiyan_loot", () -> server(p -> {
            dumpLogs();
            for (var it : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(30))) {
                var st = it.getItem();
                log("loot: " + st.getCount() + "x " + ForgeRegistries.ITEMS.getKey(st.getItem()) + (st.isEnchanted() ? " enchantments " + st.getEnchantmentTags() : ""));
            }
            // and the armour roll on its own, a few times
            for (int i = 0; i < 2; i++) { var g = ZombieSaiyans.gokuArmor(new Random(i)); log("goku armour roll: " + ForgeRegistries.ITEMS.getKey(g.getItem()) + " " + net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(g)); }
            // a natural spawn (night, near the player)
            var s = ZombieSaiyans.spawnNear(p, 20, 30, false);
            log("natural-style spawn: " + (s == null ? "none (no spot)" : s.blockPosition().toShortString()));
            if (s != null) s.discard();
        }), 40);
    }
}
