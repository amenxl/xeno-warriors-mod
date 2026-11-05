package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotTeamUlt;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.entity.TeamUltEntity;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.party.TeamUltimate;
import com.xenowarriors.skill.CutsceneBreak;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.skill.SkillPool;
import com.xenowarriors.skill.rhalix.JudgmentAbsorptionSkill;
import com.xenowarriors.stats.Stats;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * UI tests for Judgment Absorption and the Blazing Judgment team ultimate (focus "judgment", "dmzjudgment" with
 * DragonMineZ): the skill locked below Character Level 30 and unlocked at 30, the pull (enemies in, allies left
 * alone), Blazing Judgment with a bot partner, two bots doing it on their own, and a bot asking its player for a team
 * ultimate (and not asking again straight away). Results are logged as "XW-TEST judgment ...".
 */
public final class JudgmentTests {
    private JudgmentTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("judgment"); }

    private static void log(String s) { UiTest.log("judgment " + s); }

    private static final List<Husk> HUSKS = new ArrayList<>();
    private static Wolf wolf;
    private static PartyBotEntity bot, bot2;
    private static final double[] D = new double[16];
    private static final Vec3[] A = new Vec3[2];
    private static Vec3 origin;
    private static int askedBefore;

    private static int jaIndex() {
        List<SkillPool.Entry> l = SkillPool.of(CharacterType.RHALIX);
        for (int i = 0; i < l.size(); i++) if (l.get(i).skill().getId().equals(JudgmentAbsorptionSkill.ID)) return i;
        return -1;
    }

    private static void stats(ServerPlayer p, int a, int b, int c) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.statLevel[0] = a; d.statLevel[1] = b; d.statLevel[2] = c; });
    }

    private static void clearArea(ServerPlayer p) {
        for (var e : p.serverLevel().getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(48))) e.discard();
        for (var e : p.serverLevel().getEntitiesOfClass(Wolf.class, p.getBoundingBox().inflate(48))) e.discard();
        for (var e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(48))) e.discard();
        for (var b : new ArrayList<>(PartyBots.bots(p))) b.discard();
        HUSKS.clear();
        TeamUltimate.clearForTest();
    }

    private static Husk husk(ServerPlayer p, Vec3 at, float hp) {
        Husk h = EntityType.HUSK.create(p.serverLevel());
        h.moveTo(at.x, at.y, at.z, 180, 0);
        h.setPersistenceRequired();
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        h.setHealth(hp);
        h.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);   // (they stay where they're put unless something moves them)
        p.serverLevel().addFreshEntity(h);
        HUSKS.add(h);
        return h;
    }

    private static double avgDist(Vec3 c) {
        double s = 0; int n = 0;
        for (Husk h : HUSKS) if (h.isAlive()) { s += h.position().add(0, h.getBbHeight() * 0.5, 0).distanceTo(c); n++; }
        return n == 0 ? -1 : s / n;
    }

    private static float huskHp() { float s = 0; for (Husk h : HUSKS) s += h.isAlive() ? h.getHealth() : 0; return s; }

    private static void ready(ServerPlayer p) {
        SkillManager.resetCooldowns(p);
        Karma.add(p, 200);
        p.setHealth(p.getMaxHealth());
    }

    private static String f(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        // ---- set up: Rhalix in survival on open ground, nothing around
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.RHALIX);
            UiTest.server(p -> { p.setGameMode(GameType.SURVIVAL); p.serverLevel().setDayTime(6000); p.setHealth(p.getMaxHealth()); clearArea(p);
                JudgmentAbsorptionSkill.clear(); }); }, 40));
        // ---- 1. locked below Character Level 30, unlocked at 30 (Season 1: no season unlock needed)
        l.add(new Step(null, () -> UiTest.server(p -> {
            int idx = jaIndex();
            SkillPool.Entry e = SkillPool.entry(CharacterType.RHALIX, idx);
            log("pool index=" + idx + " unlockLevel=" + (e == null ? -1 : e.unlockLevel()) + " season=" + (e == null ? -1 : e.season())
                    + " available(29)=" + SkillPool.available(e, 29, false, 1) + " available(30)=" + SkillPool.available(e, 30, false, 1)
                    + " -> " + (e != null && e.season() == 1 && !SkillPool.available(e, 29, false, 1) && SkillPool.available(e, 30, false, 1) ? "PASS" : "FAIL"));
            stats(p, 10, 10, 9);
            int lv = Stats.characterLevel(p);
            SkillManager.setSlot(p, 0, idx);
            int got = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadout(CharacterType.RHALIX)[0]).orElse(-9);
            log("level " + lv + ": equip -> slot0=" + got + " (want not " + idx + ") -> " + (lv == 29 && got != idx ? "PASS" : "FAIL"));
            stats(p, 10, 10, 10);
            lv = Stats.characterLevel(p);
            SkillManager.setSlot(p, 0, idx);
            got = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadout(CharacterType.RHALIX)[0]).orElse(-9);
            log("level " + lv + ": equip -> slot0=" + got + " (want " + idx + ") -> " + (lv == 30 && got == idx ? "PASS" : "FAIL"));
        }), 10));
        // ---- 2. the pull: three husks come in, a sitting pet wolf and a bot teammate stay put
        l.add(new Step(null, () -> UiTest.server(p -> {
            origin = p.position();
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);       // facing +z
            Vec3 c = origin.add(0, 0, 9);
            husk(p, c, 60);                                                      // the one aimed at
            husk(p, c.add(7, 0, 0), 60);
            husk(p, c.add(-6, 0, 3), 60);
            husk(p, c.add(2, 0, 7), 60);
            wolf = EntityType.WOLF.create(p.serverLevel());
            wolf.moveTo(c.x - 5, c.y, c.z - 3, 0, 0);
            wolf.tame(p); wolf.setOrderedToSit(true);
            p.serverLevel().addFreshEntity(wolf);
            bot = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (bot != null) { bot.moveTo(c.x + 5, c.y, c.z - 4, 0, 0); bot.setNoAi(true); }
        }), 30));
        l.add(new Step(null, () -> {
            mc.player.setYRot(0F); mc.player.setXRot(0F); mc.player.yRotO = 0F; mc.player.xRotO = 0F;   // (looking at the first husk)
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.gui.getChat().clearMessages(false);
        }, 6));
        l.add(new Step(null, () -> UiTest.server(p -> {
            ready(p);
            p.setYRot(0F); p.setXRot(0F); p.setYHeadRot(0F);
            Vec3 c = origin.add(0, 1, 9);
            D[0] = avgDist(c); D[3] = huskHp();
            A[0] = wolf.position(); A[1] = bot == null ? null : bot.position();
            JudgmentAbsorptionSkill.lastPoint = null;
            SkillManager.useSkill(p, 0);
            Vec3 pt = JudgmentAbsorptionSkill.lastPoint;
            log("used from slot 0: point=" + (pt == null ? "none" : pt.subtract(origin)) + " open=" + JudgmentAbsorptionSkill.open() + " karma=" + f(Karma.get(p))
                    + " -> " + (pt != null && JudgmentAbsorptionSkill.open() == 1 ? "PASS" : "FAIL"));
        }), 26));
        l.add(new Step("judg_pull", () -> {}, 2));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Vec3 c = JudgmentAbsorptionSkill.lastPoint == null ? origin.add(0, 1, 9) : JudgmentAbsorptionSkill.lastPoint;
            double after = avgDist(c), w = wolf.position().distanceTo(A[0]), b = bot == null || A[1] == null ? -1 : bot.position().distanceTo(A[1]);
            log("husks avg dist to point " + f(avgDist(origin.add(0, 1, 9))) + "(from aim spot) / before " + f(D[0]) + " -> " + f(after) + " -> " + (after >= 0 && after < 2.5 ? "PASS" : "FAIL"));
            log("allies moved: wolf " + f(w) + " blocks (was " + f(A[0].distanceTo(c)) + " from the point), bot " + f(b) + " blocks -> "
                    + (w < 0.5 && b < 0.5 ? "PASS" : "FAIL"));
        }), 50));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("collapsed: open=" + JudgmentAbsorptionSkill.open() + " husk hp " + f(D[3]) + " -> " + f(huskHp()) + " pulled=" + JudgmentAbsorptionSkill.lastPulled
                    + " -> " + (JudgmentAbsorptionSkill.open() == 0 && huskHp() < D[3] - 1 ? "PASS" : "FAIL"));
            clearArea(p);
            mc.execute(() -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }), 20));
        // ---- 3. Blazing Judgment: player Rhalix (level 30) + a Mikiko bot
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
            Vec3 c = origin.add(0, 0, 10);
            for (int i = 0; i < 5; i++) husk(p, c.add(Math.cos(i * 1.3) * (4 + i), 0, Math.sin(i * 1.3) * (4 + i)), 200);
            bot = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (bot != null) bot.moveTo(origin.x + 3, origin.y, origin.z, 0, 0);
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            ready(p);
            if (bot != null) BotTeamUlt.readyForTest(bot);
            D[3] = huskHp();
            TeamUltEntity.lastKind = -1;
            List<LivingEntity> ps = TeamUltimate.partners(p);
            log("partners=" + ps.stream().map(TeamUltimate::name).toList() + " kinds=" + (ps.isEmpty() ? "[]" : TeamUltimate.kinds(p, ps.get(0)).stream().map(k -> k.title).toList()));
            p.setShiftKeyDown(true);
            boolean took = TeamUltimate.onUltimateKey(p);
            p.setShiftKeyDown(false);
            var u = p.serverLevel().getEntitiesOfClass(TeamUltEntity.class, p.getBoundingBox().inflate(40));
            log("sneak+ultimate with a bot partner: handled=" + took + " entities=" + u.size() + " kind=" + (u.isEmpty() ? -1 : u.get(0).kind())
                    + " botKarma=" + (bot == null ? -1 : f(bot.karma)) + " playerKarma=" + f(Karma.get(p))
                    + " -> " + (took && u.size() == 1 && u.get(0).kind() == TeamUltEntity.BLAZING_JUDGMENT ? "PASS" : "FAIL"));
        }), 20));
        l.add(new Step("judg_bj_pull", () -> {}, 12));
        l.add(new Step("judg_bj_tornado", () -> {}, 45));
        l.add(new Step("judg_bj_finale", () -> {}, 22));
        l.add(new Step(null, () -> {}, 25));
        l.add(new Step(null, () -> UiTest.server(p -> {
            double lost = D[3] - huskHp();
            log("Blazing Judgment done: held at most " + TeamUltEntity.lastHeld + " of 5, Rhalix dealt " + f(TeamUltEntity.lastRhalixDamage) + ", Mikiko (bot) dealt " + f(TeamUltEntity.lastMikikoDamage)
                    + ", husk hp lost " + f(lost) + " interrupted=" + (CutsceneBreak.lastAt > 0 ? CutsceneBreak.lastWhat : "no")
                    + " -> " + (TeamUltEntity.lastHeld >= 3 && TeamUltEntity.lastRhalixDamage > 0 && TeamUltEntity.lastMikikoDamage > 0 ? "PASS" : "FAIL"));
            log("bot gravity restored: noGravity=" + (bot == null ? "?" : bot.isNoGravity()) + " busy=" + (bot == null ? "?" : TeamUltimate.busy(bot)));
        }), 30));
        // ---- 4. a bot asks its player: player Mikiko, Rhalix bot (level 30)
        l.add(new Step(null, () -> { UiTest.character(CharacterType.MIKIKO); UiTest.server(p -> { clearArea(p); p.setGameMode(GameType.SURVIVAL); }); }, 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
            Vec3 c = origin.add(0, 0, 9);
            for (int i = 0; i < 4; i++) husk(p, c.add(i - 1.5, 0, i % 2), 200);
            bot = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (bot != null) { bot.moveTo(origin.x - 3, origin.y, origin.z, 0, 0); bot.applyLevel(30); }
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            ready(p);
            if (bot == null) { log("FAIL no bot"); return; }
            BotTeamUlt.readyForTest(bot);
            askedBefore = BotTeamUlt.asked;
            bot.setTarget(HUSKS.get(0));
            boolean did = BotTeamUlt.tick(bot, HUSKS.get(0));
            Object[] pend = TeamUltimate.pending(p);
            log("bot request: did=" + did + " pending=" + (pend == null ? "none" : ((TeamUltimate.Kind) pend[1]).title + " from bot=" + bot.getUUID().equals(pend[0]))
                    + " -> " + (did && pend != null && bot.getUUID().equals(pend[0]) ? "PASS" : "FAIL"));
            boolean again = BotTeamUlt.tick(bot, HUSKS.get(0));
            log("asks again at once: " + again + " asked " + (BotTeamUlt.asked - askedBefore) + " time(s) -> " + (!again && BotTeamUlt.asked - askedBefore == 1 ? "PASS" : "FAIL"));
        }), 14));
        l.add(new Step("judg_request", () -> {}, 4));
        l.add(new Step(null, () -> UiTest.server(p -> {
            boolean took = TeamUltimate.onUltimateKey(p);                       // the player answers with the ultimate key
            var u = p.serverLevel().getEntitiesOfClass(TeamUltEntity.class, p.getBoundingBox().inflate(40));
            log("player accepts with the ultimate key: handled=" + took + " team ult running=" + u.size() + " kind=" + (u.isEmpty() ? "-" : u.get(0).kind())
                    + " rhalix is the bot=" + (!u.isEmpty() && bot != null && u.get(0).rhalixId() == bot.getId()) + " -> " + (took && u.size() == 1 ? "PASS" : "FAIL"));
        }), 40));
        l.add(new Step("judg_bot_rhalix", () -> {}, 30));
        l.add(new Step(null, () -> {}, 55));
        // ---- 5. two bots on their own: player Kyrie, Rhalix + Mikiko bots
        l.add(new Step(null, () -> { UiTest.character(CharacterType.KYRIE); UiTest.server(p -> { clearArea(p); p.setGameMode(GameType.CREATIVE); }); }, 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.connection.teleport(origin.x, origin.y, origin.z - 6, 0F, 0F);
            Vec3 c = origin.add(0, 0, 9);
            for (int i = 0; i < 4; i++) husk(p, c.add(Math.cos(i * 1.6) * 5, 0, Math.sin(i * 1.6) * 5), 200);
            bot = PartyBots.spawn(p, CharacterType.RHALIX, null);
            bot2 = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (bot != null) { bot.moveTo(origin.x - 2, origin.y, origin.z, 0, 0); bot.applyLevel(30); }
            if (bot2 != null) bot2.moveTo(origin.x + 2, origin.y, origin.z, 0, 0);
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            if (bot == null || bot2 == null) { log("FAIL bots missing"); return; }
            BotTeamUlt.readyForTest(bot); BotTeamUlt.readyForTest(bot2);
            int before = BotTeamUlt.botPairs, tries = 0;
            TeamUltEntity.lastKind = -1;
            while (BotTeamUlt.botPairs == before && tries++ < 30) BotTeamUlt.tick(bot, HUSKS.get(0));
            var u = p.serverLevel().getEntitiesOfClass(TeamUltEntity.class, p.getBoundingBox().inflate(40));
            log("bot + bot: started after " + tries + " tick(s), kind=" + TeamUltEntity.lastKind + " running=" + u.size() + " rhalix bot karma=" + f(bot.karma) + " mikiko bot karma=" + f(bot2.karma)
                    + " -> " + (BotTeamUlt.botPairs > before && u.size() == 1 ? "PASS" : "FAIL"));
            D[4] = huskHp();
        }), 40));
        l.add(new Step("judg_botpair", () -> {}, 30));
        l.add(new Step(null, () -> {}, 55));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("bot + bot done: husk hp lost " + f(D[4] - huskHp()) + " -> " + (D[4] - huskHp() > 20 ? "PASS" : "FAIL")
                    + "; bots back on the ground: " + (bot != null && !bot.isNoGravity() && bot2 != null && !bot2.isNoGravity() ? "PASS" : "FAIL"));
            clearArea(p);
        }), 20));
    }
}
