package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotTeamUlt;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.CutsceneGuardHud;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.UltimateCutscene;
import com.xenowarriors.entity.GravityBarrageEntity;
import com.xenowarriors.entity.TeamUltEntity;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.CutsceneGuardPacket;
import com.xenowarriors.party.TeamUltimate;
import com.xenowarriors.skill.CutsceneBreak;
import com.xenowarriors.skill.SkillEvents;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.stats.Stat;
import com.xenowarriors.stats.Stats;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * UI tests for the cutscene guard (skill.CutsceneBreak; focus "ultbreak", "dmzultbreak" with DragonMineZ): small hits at
 * Defense 0 (2 don't cut Gravity Barrage, the 3rd does), max Defense (shrugged-off hits don't count; forced rolls), one
 * big hit at max Defense, cumulative damage at max Defense, the count chance by Defense (formula + simulation), and a
 * team ultimate (a hit on the bot partner is ignored; the player's guard holds 2 hits, the 3rd cuts it).
 * Hits are sized as a share of max health after every reduction (measured with a probe hit first, so DragonMineZ's scaled
 * damage and health work the same). Results are logged as "XW-TEST ultbreak ...".
 */
public final class UltBreakTests {
    private UltBreakTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("ultbreak"); }

    private static void log(String s) { UiTest.log("ultbreak " + s); }
    private static String f(double v) { return String.format(Locale.ROOT, "%.3f", v); }
    private static String pass(boolean b) { return b ? "PASS" : "FAIL"; }

    private static Vec3 origin;
    private static Zombie attacker;
    private static PartyBotEntity bot;
    /** Damage that lands per 1 raw damage from the attacker (after Defense, armor, DragonMineZ...). */
    private static double ratio = 1;
    private static final List<String> RES = new ArrayList<>();

    private static void defense(ServerPlayer p, int lv) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.statLevel[Stat.DEFENSE.ordinal()] = lv);
        Stats.reapply(p);
        p.setHealth(p.getMaxHealth());
    }

    private static void clear(ServerPlayer p) {
        for (var e : p.serverLevel().getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(64))) e.discard();
        for (var b : new ArrayList<>(PartyBots.bots(p))) b.discard();
        TeamUltimate.clearForTest();
        attacker = null;
    }

    private static Zombie attacker(ServerPlayer p) {
        if (attacker != null && attacker.isAlive()) return attacker;
        Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
        z.moveTo(origin.x - 30, origin.y, origin.z - 30, 0, 0);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.setSilent(true);
        p.serverLevel().addFreshEntity(z);
        return attacker = z;
    }

    private static void target(ServerPlayer p, Vec3 at) {
        var h = EntityType.HUSK.create(p.serverLevel());
        h.moveTo(at.x, at.y, at.z, 180, 0);
        h.setNoAi(true);
        h.setPersistenceRequired();
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        h.setHealth(500);
        p.serverLevel().addFreshEntity(h);
    }

    /** A probe hit outside any cutscene: how much of a raw hit lands at the current Defense. */
    private static void probe(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        float raw = Math.max(2F, p.getMaxHealth() * 0.05F), before = p.getHealth();
        p.invulnerableTime = 0;
        p.hurt(p.damageSources().mobAttack(attacker(p)), raw);
        float lost = before - p.getHealth();
        ratio = lost > 0.0001F ? lost / raw : 1;
        p.invulnerableTime = 0;
        p.setHealth(p.getMaxHealth());
        log("probe: Defense " + Stats.level(p, Stat.DEFENSE) + " maxHp " + f(p.getMaxHealth()) + " raw " + f(raw) + " -> lost " + f(lost) + " ratio " + f(ratio));
    }

    /** One enemy hit landing {@code share} of max health; true if it cut the cutscene. */
    private static boolean hit(ServerPlayer p, float share) {
        long before = CutsceneBreak.lastAt;
        float hp = p.getHealth();
        p.invulnerableTime = 0;
        p.hurt(p.damageSources().mobAttack(attacker(p)), (float) (share * p.getMaxHealth() / ratio));
        p.invulnerableTime = 0;
        boolean cut = CutsceneBreak.lastAt != before;
        log("  hit " + f(share * 100) + "% -> took " + f((hp - p.getHealth()) / p.getMaxHealth() * 100) + "% cut=" + cut + " guard: " + CutsceneBreak.lastGuard);
        return cut;
    }

    private static boolean inBarrage(ServerPlayer p) {
        return !p.serverLevel().getEntitiesOfClass(GravityBarrageEntity.class, p.getBoundingBox().inflate(96), g -> p.getUUID().equals(g.ownerUuid()) && g.isAlive()).isEmpty()
                && p.getPersistentData().getLong(SkillEvents.ULT_UNTIL) >= p.level().getGameTime();
    }

    private static void startUlt(ServerPlayer p) {
        p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
        p.getPersistentData().remove("xw_karma_lock_until");
        SkillManager.resetCooldowns(p);
        Karma.add(p, 200);
        p.setHealth(p.getMaxHealth());
        SkillManager.useSkill(p, SkillManager.ULTIMATE_SLOT);
    }

    private static void result(String name, boolean ok, String detail) {
        RES.add(name + "=" + pass(ok));
        log(name + ": " + detail + " -> " + pass(ok));
    }

    /** One Gravity Barrage scenario: set Defense, probe, start the ultimate, then run {@code hits} a few ticks in. */
    private static void barrage(Minecraft mc, List<Step> l, String name, int def, java.util.function.Consumer<ServerPlayer> hits, String shot) {
        l.add(new Step(null, () -> UiTest.server(p -> { CutsceneBreak.forceRoll = null; defense(p, def); probe(p); startUlt(p); }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log(name + ": in cutscene=" + inBarrage(p) + " (Defense " + Stats.level(p, Stat.DEFENSE) + ", count chance " + f(CutsceneBreak.countChance(Stats.level(p, Stat.DEFENSE))) + ")");
            hits.accept(p);
        }), 2));
        if (shot != null) l.add(new Step(shot, () -> log(name + ": client guard hud showing=" + CutsceneGuardHud.showing() + " last=" + CutsceneGuardPacket.last + " cutscene=" + UltimateCutscene.active()), 1));
        l.add(new Step(null, () -> UiTest.server(p -> { CutsceneBreak.forceRoll = null; p.setHealth(p.getMaxHealth()); }), 170));   // (let it end either way)
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.RHALIX); mc.options.setCameraType(CameraType.FIRST_PERSON);
            UiTest.server(p -> {
                p.setGameMode(GameType.SURVIVAL);
                p.serverLevel().setDayTime(6000);
                var l0 = p.serverLevel();
                net.minecraft.core.BlockPos base = net.minecraft.core.BlockPos.containing(p.getX(), 150, p.getZ());
                for (int x = -20; x <= 20; x++) for (int z = -14; z <= 26; z++) {
                    l0.setBlock(base.offset(x, -1, z), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    for (int y = 0; y < 16; y++) l0.setBlock(base.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
                origin = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
                clear(p);
                p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
                p.fallDistance = 0;
                target(p, origin.add(-2, 0, 8));
                target(p, origin.add(2, 0, 8));
                RES.clear();
            }); }, 40));

        // ---- 0. the formula and a simulation (hits needed on average to cut a cutscene, by Defense)
        l.add(new Step(null, () -> {
            int[] defs = {0, 10, 50, 100, 200, 500, 1000, 3000, 10000, Stat.MAX_LEVEL};
            java.util.Random r = new java.util.Random(42);
            StringBuilder sb = new StringBuilder();
            float prevC = 2F; boolean mono = true;
            for (int d : defs) {
                float c = CutsceneBreak.countChance(d);
                long total = 0; int runs = 20000;
                for (int i = 0; i < runs; i++) { int counted = 0, n = 0; while (counted < CutsceneBreak.HITS) { n++; if (r.nextFloat() < c) counted++; } total += n; }
                double avg = total / (double) runs;
                if (c > prevC) mono = false;
                prevC = c;
                sb.append(String.format(Locale.ROOT, " D%d: chance %.1f%% avgHits %.2f;", d, c * 100, avg));
            }
            log("formula:" + sb);
            float c0 = CutsceneBreak.countChance(0), cMax = CutsceneBreak.countChance(Stat.MAX_LEVEL);
            result("formula", c0 == 1F && Math.abs(cMax - CutsceneBreak.FLOOR) < 1e-4 && mono && CutsceneBreak.countChance(200) < CutsceneBreak.countChance(50),
                    "chance(0)=" + f(c0) + " chance(max)=" + f(cMax) + " floor " + CutsceneBreak.FLOOR + " chance never rises with Defense=" + mono);
        }, 2));

        // ---- 1. Defense 0: two small hits don't cut it, the third does
        barrage(mc, l, "def0", 0, p -> {
            boolean a = hit(p, 0.02F), b = hit(p, 0.02F);
            boolean still = inBarrage(p);
            boolean c = hit(p, 0.02F);
            result("def0_three_hits", !a && !b && still && c && "gravity_barrage".equals(CutsceneBreak.lastWhat) && !inBarrage(p),
                    "hit1 cut=" + a + " hit2 cut=" + b + " still in cutscene=" + still + " hit3 cut=" + c + " what=" + CutsceneBreak.lastWhat);
        }, null);
        // the guard indicator: a counted hit at Defense 0, screenshot while the cutscene plays
        barrage(mc, l, "hud_counted", 0, p -> hit(p, 0.02F), "ultbreak_guard_counted");
        // ---- 2. max Defense: shrugged-off hits (forced roll 0.5 >= 20%) never count; forced counting rolls cut it on the 3rd
        barrage(mc, l, "defmax", Stat.MAX_LEVEL, p -> {
            CutsceneBreak.forceRoll = 0.5F;
            boolean any = false;
            for (int i = 0; i < 5; i++) any |= hit(p, 0.02F);
            boolean still = inBarrage(p);
            CutsceneBreak.forceRoll = 0.1F;
            boolean a = hit(p, 0.02F), b = hit(p, 0.02F), c = hit(p, 0.02F);
            result("defmax_resists", !any && still && !a && !b && c,
                    "5 resisted hits cut=" + any + " still=" + still + " then counted hits cut " + a + "/" + b + "/" + c);
        }, null);
        // the resisted indicator at max Defense (screenshot)
        barrage(mc, l, "hud_resisted", Stat.MAX_LEVEL, p -> { CutsceneBreak.forceRoll = 0.9F; hit(p, 0.02F); }, "ultbreak_guard_resisted");
        // ---- 2b. max Defense, real rolls: how many small hits it took (20% each count)
        barrage(mc, l, "defmax_random", Stat.MAX_LEVEL, p -> {
            int n = 0; boolean cut = false;
            while (!cut && n < 40) { n++; cut = hit(p, 0.005F); }
            result("defmax_random", cut && n >= CutsceneBreak.HITS, "cut after " + n + " small hits (expected ~15 at 20%)");
        }, null);
        // ---- 3. one big hit (25% of max health) at max Defense
        barrage(mc, l, "big", Stat.MAX_LEVEL, p -> {
            CutsceneBreak.forceRoll = 0.99F;           // (would never count by itself)
            boolean c = hit(p, 0.25F);
            result("big_hit", c && CutsceneBreak.lastGuard.contains("big hit"), "one 25% hit cut=" + c + " (" + CutsceneBreak.lastGuard + ")");
        }, null);
        // ---- 4. cumulative damage: ~14% hits at max Defense, rolls never count -> the 3rd (~40% total) cuts it
        barrage(mc, l, "heavy", Stat.MAX_LEVEL, p -> {
            CutsceneBreak.forceRoll = 0.99F;
            boolean a = hit(p, 0.14F), b = hit(p, 0.14F), c = hit(p, 0.14F);
            result("heavy_total", !a && !b && c && CutsceneBreak.lastGuard.contains("heavy damage"), "14% hits cut " + a + "/" + b + "/" + c + " (" + CutsceneBreak.lastGuard + ")");
        }, null);

        // ---- 5. team ultimate with a Mikiko bot: the bot can't be hit; the player's guard holds 2 hits, the 3rd cuts it
        l.add(new Step(null, () -> UiTest.server(p -> {
            CutsceneBreak.forceRoll = null;
            defense(p, 0);
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.statLevel[0] = 10; d.statLevel[1] = 10; });
            bot = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (bot != null) bot.moveTo(origin.x + 3, origin.y, origin.z, 0, 0);
            probe(p);
        }), 30));
        final float[] botHp = new float[2];
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.getPersistentData().remove("xw_karma_lock_until");
            SkillManager.resetCooldowns(p);
            Karma.add(p, 200);
            p.setHealth(p.getMaxHealth());
            if (bot != null) { bot.moveTo(origin.x + 3, origin.y, origin.z, 0, 0); bot.getNavigation().stop(); BotTeamUlt.readyForTest(bot); }
            log("team: bot=" + (bot == null ? "none" : bot.character() + " alive=" + bot.isAlive() + " dist " + f(bot.distanceTo(p)) + " sparring=" + bot.sparring() + " downed=" + bot.downed())
                    + " player busy=" + TeamUltimate.busy(p) + " partners=" + TeamUltimate.partners(p).size());
            p.setShiftKeyDown(true);
            boolean took = TeamUltimate.onUltimateKey(p);
            p.setShiftKeyDown(false);
            log("team: sneak+ultimate handled=" + took + " entities=" + p.serverLevel().getEntitiesOfClass(TeamUltEntity.class, p.getBoundingBox().inflate(40)).size());
        }), 20));
        l.add(new Step(null, () -> UiTest.server(p -> {
            var u = p.serverLevel().getEntitiesOfClass(TeamUltEntity.class, p.getBoundingBox().inflate(60));
            if (bot == null || u.isEmpty()) { result("team", false, "no bot or no team ultimate (" + u.size() + ")"); return; }
            botHp[0] = bot.getHealth();
            bot.invulnerableTime = 0;
            boolean botHurt = bot.hurt(p.damageSources().mobAttack(attacker(p)), 6F);
            botHp[1] = bot.getHealth();
            long before = CutsceneBreak.lastAt;
            boolean a = hit(p, 0.02F), b = hit(p, 0.02F);
            boolean alive = !u.get(0).isRemoved();
            boolean c = hit(p, 0.02F);
            result("team", !botHurt && botHp[1] == botHp[0] && !a && !b && alive && c && "team_ultimate".equals(CutsceneBreak.lastWhat),
                    "bot hit accepted=" + botHurt + " bot hp " + f(botHp[0]) + "->" + f(botHp[1]) + "; player hits cut " + a + "/" + b + " ult alive after 2=" + alive + " 3rd cut=" + c + " what=" + CutsceneBreak.lastWhat + " (before " + before + ")");
        }), 2));
        l.add(new Step("ultbreak_team_after", () -> {}, 20));
        l.add(new Step(null, () -> UiTest.server(p -> {
            clear(p);
            CutsceneBreak.forceRoll = null;
            defense(p, 0);
            log("SUMMARY " + RES + " -> " + pass(!RES.isEmpty() && RES.stream().allMatch(s -> s.endsWith("PASS"))));
        }), 10));
    }
}
