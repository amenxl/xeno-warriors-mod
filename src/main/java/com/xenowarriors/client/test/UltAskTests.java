package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotTeamUlt;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.entity.TeamUltEntity;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.karma.KarmaSensing;
import com.xenowarriors.party.TeamUltimate;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.statboost.StatBoosts;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * UI tests for when bots go for a team ultimate (focus "ultask", "dmzultask" with DragonMineZ): only against a lot of
 * enemies ({@link BotTeamUlt#CROWD}+ within {@link BotTeamUlt#CROWD_RADIUS} blocks) or a very strong one (a boss, or
 * 3x the player's Karma Sense strength). A bot asking its player: 2 weak mobs - no request; 8 mobs - a request; one very
 * strong mob - a request; a boss - a request. Two bots: no team ultimate (and no held-back ultimate) on 2 weak mobs, one
 * on a crowd. Results are logged as "XW-TEST ultask ...".
 */
public final class UltAskTests {
    private UltAskTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("ultask"); }

    private static void log(String s) { UiTest.log("ultask " + s); }

    private static final List<Mob> MOBS = new ArrayList<>();
    private static PartyBotEntity bot, bot2;
    private static Vec3 origin;
    private static int before, before0;
    private static int pass, fail;

    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    private static void clearArea(ServerPlayer p) {
        for (var e : p.serverLevel().getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(48))) e.discard();
        for (var e : p.serverLevel().getEntitiesOfClass(TeamUltEntity.class, p.getBoundingBox().inflate(48))) e.discard();
        for (var e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(48))) e.discard();
        MOBS.clear();
        TeamUltimate.clearForTest();
    }

    private static void clearMobs(ServerPlayer p) {
        for (Mob m : MOBS) m.discard();
        MOBS.clear();
        TeamUltimate.clearForTest();
    }

    private static <T extends Mob> T mob(ServerPlayer p, EntityType<T> type, Vec3 at, float hp) {
        T h = type.create(p.serverLevel());
        h.moveTo(at.x, at.y, at.z, 180, 0);
        h.setPersistenceRequired();
        h.setNoAi(true);                                                        // (they stand still and don't hurt anyone)
        if (hp > 0) { h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp); h.setHealth(hp); }
        p.serverLevel().addFreshEntity(h);
        MOBS.add(h);
        return h;
    }

    /** {@code n} husks of {@code hp} in a loose group 8 blocks in front of the player. */
    private static void husks(ServerPlayer p, int n, float hp) {
        Vec3 c = origin.add(0, 0, 8);
        for (int i = 0; i < n; i++) mob(p, EntityType.HUSK, c.add((i % 4) * 1.6 - 2.4, 0, (i / 4) * 1.8), hp);
    }

    private static void ready(ServerPlayer p) {
        SkillManager.resetCooldowns(p);
        Karma.add(p, 200);
        p.setHealth(p.getMaxHealth());
        if (bot != null) BotTeamUlt.readyForTest(bot);
        if (bot2 != null) BotTeamUlt.readyForTest(bot2);
        TeamUltimate.clearForTest();
    }

    private static String f(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    private static String strengths(ServerPlayer p) {
        float ps = KarmaSensing.strength(p), top = 0;
        boolean anyVery = false;
        for (Mob m : MOBS) { top = Math.max(top, KarmaSensing.strength(m)); anyVery |= StatBoosts.veryStrong(m, p); }
        return "mobs=" + MOBS.size() + " foesNear(16)=" + TeamUltimate.foesNear(p, BotTeamUlt.CROWD_RADIUS).size()
                + " player strength=" + f(ps) + " strongest mob=" + f(top) + " (" + (top / Math.max(0.01F, ps)) + "x) veryStrong=" + anyVery;
    }

    /** One "bot asks its player" case: mobs spawned by {@code spawn}, then the bot's team-ultimate check. */
    private static void askCase(List<Step> l, String name, java.util.function.Consumer<ServerPlayer> spawn, boolean expect, String shot) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            clearMobs(p);
            if (bot != null) BotTeamUlt.readyForTest(bot);
            before0 = BotTeamUlt.asked;
            spawn.accept(p);
        }), 8));
        l.add(new Step(null, () -> UiTest.server(p -> {
            if (bot == null) { check(name, false, "no bot"); return; }
            int aiAsked = BotTeamUlt.asked - before0;                          // (the bot's own fight AI in the meantime)
            ready(p);
            before = BotTeamUlt.asked;
            LivingEntity target = MOBS.get(0);
            boolean worth = BotTeamUlt.worthIt(bot, target);
            for (int i = 0; i < 3; i++) BotTeamUlt.tick(bot, target);            // (asks once at most: the rate limits stay)
            Object[] pend = TeamUltimate.pending(p);
            int asked = BotTeamUlt.asked - before;
            boolean fromBot = pend != null && bot.getUUID().equals(pend[0]);
            check(name, expect ? asked == 1 && fromBot && worth : asked == 0 && aiAsked == 0 && pend == null && !worth,
                    strengths(p) + " worthIt=" + worth + " requests=" + asked + " pending=" + (pend == null ? "none" : ((TeamUltimate.Kind) pend[1]).title + " from bot=" + fromBot)
                            + " byItsOwnAiMeanwhile=" + aiAsked + " (expected " + (expect ? "a request" : "no request") + ")");
        }), shot == null ? 6 : 4));
        if (shot != null) l.add(new Step(shot, () -> {}, 6));
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        pass = 0; fail = 0;
        // ---- set up: Mikiko (player) in survival on a flat arena up in the air, a level 30 Rhalix bot beside her
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO);
            UiTest.server(p -> { p.setGameMode(GameType.SURVIVAL); p.serverLevel().setDayTime(6000); p.setHealth(p.getMaxHealth());
                for (var b : new ArrayList<>(PartyBots.bots(p))) b.discard();
                clearArea(p);
                var lv = p.serverLevel();
                BlockPos base = BlockPos.containing(p.getX(), 150, p.getZ());
                for (int x = -20; x <= 20; x++) for (int z = -14; z <= 26; z++) {
                    lv.setBlock(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    for (int y = 0; y < 8; y++) lv.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
                origin = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
                p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
                p.fallDistance = 0; }); }, 40));
        l.add(new Step(null, () -> UiTest.server(p -> {
            bot = PartyBots.spawn(p, CharacterType.RHALIX, null);
            bot2 = null;
            if (bot != null) { bot.moveTo(origin.x - 3, origin.y, origin.z, 0, 0); bot.applyLevel(30); }
            log("thresholds: crowd " + BotTeamUlt.CROWD + "+ foes within " + BotTeamUlt.CROWD_RADIUS + " blocks; very strong = boss or "
                    + StatBoosts.STRONG_RATIO + "x the player's strength (and at least " + StatBoosts.STRONG_MIN + ")");
        }), 30));
        // ---- the bot asking its player
        askCase(l, "2 weak mobs - no request", p -> husks(p, 2, 60), false, "ultask_weak");
        askCase(l, "8 mobs - request", p -> husks(p, 8, 60), true, "ultask_crowd");
        askCase(l, "5 mobs (under the crowd size) - no request", p -> husks(p, 5, 60), false, null);
        askCase(l, "1 very strong mob - request", p -> mob(p, EntityType.HUSK, origin.add(0, 0, 8), 1000), true, null);
        askCase(l, "1 boss (Warden) - request", p -> mob(p, EntityType.WARDEN, origin.add(0, 0, 9), 0), true, "ultask_boss");
        // ---- two bots on their own: player Kyrie, Rhalix + Mikiko bots
        l.add(new Step(null, () -> { UiTest.character(CharacterType.KYRIE); UiTest.server(p -> { clearArea(p); for (var b : new ArrayList<>(PartyBots.bots(p))) b.discard(); p.setGameMode(GameType.CREATIVE); }); }, 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.connection.teleport(origin.x, origin.y, origin.z - 4, 0F, 0F);
            bot = PartyBots.spawn(p, CharacterType.RHALIX, null);
            bot2 = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (bot != null) { bot.moveTo(origin.x - 2, origin.y, origin.z, 0, 0); bot.applyLevel(30); }
            if (bot2 != null) bot2.moveTo(origin.x + 2, origin.y, origin.z, 0, 0);
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> { husks(p, 2, 60); }), 8));
        l.add(new Step(null, () -> UiTest.server(p -> {
            if (bot == null || bot2 == null) { check("bot + bot, 2 weak mobs", false, "bots missing"); return; }
            ready(p);
            before = BotTeamUlt.botPairs;
            boolean save = BotTeamUlt.saveUltimate(bot, MOBS.get(0));
            for (int i = 0; i < 30; i++) BotTeamUlt.tick(bot, MOBS.get(0));
            int n = p.serverLevel().getEntitiesOfClass(TeamUltEntity.class, p.getBoundingBox().inflate(40)).size();
            check("bot + bot, 2 weak mobs - no team ultimate, solo ultimate not held back", BotTeamUlt.botPairs == before && n == 0 && !save,
                    strengths(p) + " pairs started=" + (BotTeamUlt.botPairs - before) + " running=" + n + " holdsBackUlt=" + save);
        }), 6));
        l.add(new Step(null, () -> UiTest.server(p -> { clearMobs(p); husks(p, 8, 200); }), 8));
        l.add(new Step(null, () -> UiTest.server(p -> {
            if (bot == null || bot2 == null) { check("bot + bot, 8 mobs", false, "bots missing"); return; }
            ready(p);
            before = BotTeamUlt.botPairs;
            boolean save = BotTeamUlt.saveUltimate(bot, MOBS.get(0));
            int tries = 0;
            while (BotTeamUlt.botPairs == before && tries++ < 30) BotTeamUlt.tick(bot, MOBS.get(0));
            int n = p.serverLevel().getEntitiesOfClass(TeamUltEntity.class, p.getBoundingBox().inflate(40)).size();
            check("bot + bot, 8 mobs - team ultimate (and the solo ultimate held back before it)", BotTeamUlt.botPairs > before && n == 1 && save,
                    strengths(p) + " started after " + tries + " tick(s), kind=" + TeamUltEntity.lastKind + " running=" + n + " holdsBackUlt=" + save);
        }), 30));
        l.add(new Step("ultask_botpair", () -> {}, 120));
        l.add(new Step(null, () -> UiTest.server(p -> {
            clearArea(p);
            for (var b : new ArrayList<>(PartyBots.bots(p))) b.discard();
            log("summary: " + pass + " passed, " + fail + " failed -> " + (fail == 0 ? "PASS" : "FAIL"));
        }), 20));
    }
}
