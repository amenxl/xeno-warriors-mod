package com.xenowarriors.client.test;

import com.mojang.authlib.GameProfile;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.faction.Faction;
import com.xenowarriors.faction.Reputation;
import com.xenowarriors.gov.GovAgentEntity;
import com.xenowarriors.npc.HumanNpcEntity;
import com.xenowarriors.raid.FactionRaids;
import com.xenowarriors.raid.FactionRaids.Kind;
import com.xenowarriors.raid.FactionRaids.Raid;
import com.xenowarriors.raid.RaidPilot;
import com.xenowarriors.sr.SrMemberEntity;
import com.xenowarriors.vehicle.VehicleEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Faction raids (focus "raids"; {@link #pack} inside the real-pack test). Logs "XW-TEST raids ... PASS/FAIL":
 * the standing roll (bad standing + a forced roll starts one, good standing / Sky Patrol hate / peaceful never do),
 * a Government raid (helicopters packed with soldiers + a convoy; soldiers shooting from their seats, drivers too; the
 * helicopter dropping its squad; bots joining in; waves progressing; kills not costing standing; winning: standing
 * back, everything cleared), a Sky Rebellion raid (rebels diving in on their wings, a named leader), a Townsfolk raid
 * (armed militia with torches, no work goals), fleeing ending one, stray raiders refused, and no blocks broken.
 * Stills: raids_gov_helis, raids_gov_convoy, raids_gov_drop, raids_sr, raids_town.
 */
public final class RaidTests {
    private RaidTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("raids"); }

    private static int pass, fail;
    private static final List<String> FAILED = new ArrayList<>();

    private static void log(String s) { UiTest.log("raids " + s); }
    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else { fail++; FAILED.add(what); }
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }
    private static String f(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    // ---------------------------------------------------------------- counters (server side)

    private static boolean listening;
    /** Projectiles fired by Gov soldiers while riding: by the driver / a passenger, from a helicopter / a ground vehicle. */
    static int shotsDriver, shotsPassenger, shotsHeli, shotsGround;
    /** Attacks on the player by raiders (any / while seated in a vehicle), by faction. */
    static final int[] ATTACKS = new int[3], SEATED = new int[3];
    /** Hits by a party bot on raiders. */
    static int botHits;

    private static void listen() {
        if (listening) return;
        listening = true;
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel().isClientSide || !(e.getEntity() instanceof Projectile pr) || !(pr.getOwner() instanceof GovAgentEntity g)) return;
            if (!FactionRaids.isRaider(g) || !(g.getVehicle() instanceof VehicleEntity v)) return;
            if (v.getControllingPassenger() == g) shotsDriver++; else shotsPassenger++;
            if (v.kind.heli()) shotsHeli++; else shotsGround++;
        });
        MinecraftForge.EVENT_BUS.addListener((LivingAttackEvent e) -> {
            if (!e.getEntity().level().isClientSide && FactionRaids.isRaider(e.getEntity())
                    && (e.getSource().getEntity() instanceof PartyBotEntity || PartyBots.botOf(e.getSource().getEntity()) != null)) botHits++;
            if (e.getEntity().level().isClientSide || !(e.getEntity() instanceof ServerPlayer)) return;
            Entity src = e.getSource().getEntity();
            Raid r = src == null ? null : FactionRaids.of(src);
            if (r == null) return;
            ATTACKS[r.kind.ordinal()]++;
            if (src.getVehicle() instanceof VehicleEntity) SEATED[r.kind.ordinal()]++;
        });
    }

    private static void resetCounters() {
        shotsDriver = shotsPassenger = shotsHeli = shotsGround = botHits = 0;
        java.util.Arrays.fill(ATTACKS, 0);
        java.util.Arrays.fill(SEATED, 0);
    }

    // ---------------------------------------------------------------- stage

    static Vec3 O;

    /** A flat, cleared stone field (+-40) on the ground where the player is, and the player standing in it, protected. */
    static void stage(ServerPlayer p, int part) {
        ServerLevel l = p.serverLevel();
        if (part == 0) {
            int x = p.getBlockX(), z = p.getBlockZ();
            int y = Math.max(l.getSeaLevel() + 1, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            O = new Vec3(x + 0.5, y, z + 0.5);
        }
        BlockPos b = BlockPos.containing(O);
        for (int dx = -40 + part * 20; dx < -20 + part * 20 + (part == 3 ? 1 : 0); dx++) for (int dz = -40; dz <= 40; dz++) {
            l.setBlock(b.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int dy = 0; dy <= 20; dy++) if (!l.getBlockState(b.offset(dx, dy, dz)).isAir()) l.setBlock(b.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    static void ready(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.connection.teleport(O.x, O.y, O.z, 0F, 0F);
        p.fallDistance = 0;
        p.setHealth(p.getMaxHealth());
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 1200, 10, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 1200, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 1200, 0, false, false));
        p.server.setDifficulty(Difficulty.NORMAL, true);
        p.serverLevel().setDayTime(6000);
    }

    /** Standing with every raiding faction friendly, the Sky Patrol neutral. */
    static void friends(ServerPlayer p) {
        for (Faction f : Faction.values()) Reputation.set(p, f, f == Faction.SKY ? 0 : 300);
    }

    static void endAll() {
        for (Raid r : new ArrayList<>(FactionRaids.active())) FactionRaids.end(r, false, "test");
    }

    static int tagged(ServerPlayer p, double range) {
        return p.serverLevel().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(range, 64, range), FactionRaids::isRaider).size();
    }

    static void killWave(ServerPlayer p) {
        Raid r = FactionRaids.raidOn(p);
        if (r == null) return;
        for (LivingEntity e : r.alive()) e.kill();
    }

    /** Turns the camera (client) to look at the nearest entity matching. */
    static void lookAt(Minecraft mc, Vec3 at) {
        if (mc.player == null || at == null) return;
        Vec3 d = at.subtract(mc.player.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        mc.player.setYRot(yaw); mc.player.setXRot(Math.max(-60F, Math.min(40F, pitch)));
        mc.player.yRotO = yaw; mc.player.xRotO = mc.player.getXRot();
        UiTest.server(p -> { p.setYRot(yaw); p.setYHeadRot(yaw); });
    }

    static volatile Vec3 look;

    private static Vec3 nearest(ServerPlayer p, java.util.function.Predicate<Entity> what) {
        Entity best = null;
        double bd = 1e9;
        for (Entity e : p.serverLevel().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(80, 64, 80), what::test)) {
            double d = e.distanceToSqr(p);
            if (d < bd && d > 9) { bd = d; best = e; }
        }
        return best == null ? null : best.position().add(0, best.getBbHeight() * 0.5, 0);
    }

    private static Object goals(Mob m) {
        try {
            var fld = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(Mob.class, "f_21345_");
            return fld.get(m);
        } catch (Throwable t) { return null; }
    }

    /** Does this mob still have a goal of this class (by simple name)? */
    private static boolean hasGoal(Mob m, String simpleName) {
        Object gs = goals(m);
        if (!(gs instanceof net.minecraft.world.entity.ai.goal.GoalSelector sel)) return true;
        for (var w : sel.getAvailableGoals()) if (w.getGoal().getClass().getSimpleName().equals(simpleName)) return true;
        return false;
    }

    /** The boss bars the client is showing right now (names and ids). */
    static String clientBars(Minecraft mc) {
        try {
            Object overlay = mc.gui.getBossOverlay();
            for (var fld : overlay.getClass().getDeclaredFields()) {
                if (!java.util.Map.class.isAssignableFrom(fld.getType())) continue;
                fld.setAccessible(true);
                StringBuilder sb = new StringBuilder();
                for (var e : ((java.util.Map<?, ?>) fld.get(overlay)).entrySet())
                    sb.append('[').append(e.getKey()).append(' ').append(((net.minecraft.world.BossEvent) e.getValue()).getName().getString()).append(']');
                return sb.toString();
            }
        } catch (Throwable t) { return "? " + t; }
        return "?";
    }

    // ---------------------------------------------------------------- the test

    private static int repBefore;
    private static PartyBotEntity bot;
    private static int floorBroken = -1;

    public static void build(Minecraft mc, List<Step> l, String focus) {
        listen();
        log("start focus=" + focus);
        FactionRaids.testFast = true;
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            UiTest.character(CharacterType.RHALIX); }, 40));
        for (int part = 0; part < 4; part++) { int fp = part; l.add(new Step(null, () -> UiTest.server(p -> stage(p, fp)), 10)); }
        l.add(new Step(null, () -> UiTest.server(p -> {
            endAll();
            for (var b : new ArrayList<>(PartyBots.bots(p))) b.discard();
            ready(p);
            FactionRaids.clearCooldowns(p);
            friends(p);
        }), 40));

        // ---- 1. the roll: good standing never, Sky Patrol never, peaceful never; bad standing + a low roll does
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("chances: rep 0=" + f(FactionRaids.chance(0)) + " -149=" + f(FactionRaids.chance(-149)) + " -151=" + f(FactionRaids.chance(-151))
                    + " -400=" + f(FactionRaids.chance(-400)) + " -700=" + f(FactionRaids.chance(-700)) + " -1000=" + f(FactionRaids.chance(-1000)));
            check("chance curve", FactionRaids.chance(0) == 0 && FactionRaids.chance(-149) == 0 && FactionRaids.chance(-151) > 0.04
                    && FactionRaids.chance(-1000) > FactionRaids.chance(-400) && FactionRaids.chance(-1000) <= 0.18 + 1e-9, "see chances");
            Raid r = FactionRaids.check(p, 0.0);
            check("good standing never raids (forced roll 0)", r == null, "raid=" + (r == null ? "none" : r.kind));
            Reputation.set(p, Faction.SKY, -1000);
            r = FactionRaids.check(p, 0.0);
            String kinds = java.util.Arrays.toString(Kind.values());
            check("Sky Patrol & Skylanders never raid (Hated, forced roll 0)", r == null && !kinds.contains("SKY"), "raid=" + (r == null ? "none" : r.kind) + " kinds=" + kinds);
            Reputation.set(p, Faction.SKY, 0);
            Reputation.set(p, Faction.GOV, -700);
            p.server.setDifficulty(Difficulty.PEACEFUL, true);
            r = FactionRaids.check(p, 0.0);
            check("peaceful never raids", r == null, "raid=" + (r == null ? "none" : r.kind));
            p.server.setDifficulty(Difficulty.NORMAL, true);
            r = FactionRaids.check(p, 0.99);
            check("a high roll doesn't raid", r == null, "chance=" + f(FactionRaids.chance(-700)) + " roll=0.99");
            r = FactionRaids.check(p, 0.0);
            check("Hated by the Government + forced roll starts a Government raid", r != null && r.kind == Kind.GOV && r.severity == 1 && r.waves == 4,
                    "raid=" + (r == null ? "none" : r.kind + " severity=" + r.severity + " waves=" + r.waves));
            Raid again = FactionRaids.check(p, 0.0);
            check("no second raid while one is on", again == null, "second=" + again);
            if (r != null) FactionRaids.end(r, false, "test");
            FactionRaids.clearCooldowns(p);
            Reputation.set(p, Faction.GOV, -300);
        }), 10));

        // ---- 2. a Government raid (Hostile: 3 waves) with a bot along
        l.add(new Step(null, () -> UiTest.server(p -> {
            resetCounters();
            Raid r = FactionRaids.start(p, Kind.GOV, true);
            check("government raid starts with a warning", r != null && r.wave == 0 && r.bar().getPlayers().contains(p),
                    "raid=" + (r != null) + " bar=" + (r != null ? r.bar().getName().getString() : "-"));
        }), 50));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            if (r == null) { check("gov wave 1", false, "no raid"); return; }
            int helis = 0, grounds = 0, aboardHeli = 0, aboardGround = 0, scanners = 0;
            for (VehicleEntity v : r.vehicleList()) {
                if (v.kind.heli()) { helis++; aboardHeli += v.getPassengers().size(); } else { grounds++; aboardGround += v.getPassengers().size(); }
            }
            for (LivingEntity e : r.alive()) if (e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(com.xenowarriors.gov.GovItems.KARMA_SCANNER.get())) scanners++;
            check("gov wave 1: helicopters packed with soldiers + a convoy", r.wave == 1 && helis >= 1 && aboardHeli >= 6 && grounds >= 1 && aboardGround >= 2,
                    "wave=" + r.wave + "/" + r.waves + " helis=" + helis + " (" + aboardHeli + " aboard) ground=" + grounds + " (" + aboardGround + " aboard) soldiers=" + r.alive().size()
                    + " karmaScanners=" + scanners + " bar='" + r.bar().getName().getString() + "'");
            look = nearest(p, e -> e instanceof VehicleEntity v && v.kind.heli() && FactionRaids.isRaider(v));
        }), 2));
        l.add(new Step(null, () -> lookAt(mc, look), 60));
        l.add(new Step(null, () -> UiTest.server(p -> look = nearest(p, e -> e instanceof VehicleEntity v && v.kind.heli() && FactionRaids.isRaider(v))), 2));
        l.add(new Step("raids_gov_helis", () -> lookAt(mc, look), 2));
        l.add(new Step(null, () -> UiTest.server(p -> look = nearest(p, e -> e instanceof VehicleEntity v && !v.kind.heli() && FactionRaids.isRaider(v))), 60));
        l.add(new Step(null, () -> lookAt(mc, look), 3));
        l.add(new Step("raids_gov_convoy", () -> lookAt(mc, look), 2));
        l.add(new Step(null, () -> UiTest.server(p -> look = nearest(p, e -> e instanceof VehicleEntity v && v.kind.heli() && FactionRaids.isRaider(v))), 60));
        l.add(new Step("raids_gov_drop", () -> lookAt(mc, look), 2));
        l.add(new Step(null, () -> UiTest.server(p -> {
            bot = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (bot != null) {
                bot.moveTo(O.x + 2, O.y, O.z + 1, 0, 0);
                bot.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 600, 10, false, false));   // (so it isn't shot down before it can fight)
            }
        }), 160));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            if (r == null) { check("gov shooting", false, "no raid"); return; }
            int dropped = 0, onFoot = 0, phaseHeli = -1;
            for (VehicleEntity v : r.vehicleList()) if (v.kind.heli()) phaseHeli = Math.max(phaseHeli, v.getPersistentData().getInt(RaidPilot.PHASE));
            for (LivingEntity e : r.alive()) if (e.getVehicle() == null) onFoot++;
            log("shots from vehicles: driver=" + shotsDriver + " passengers=" + shotsPassenger + " heli=" + shotsHeli + " ground=" + shotsGround
                    + " | attacks on player=" + ATTACKS[0] + " (seated " + SEATED[0] + ")");
            check("soldiers shoot from their seats (passengers)", shotsPassenger > 0, "passengers=" + shotsPassenger + " (heli " + shotsHeli + ", ground " + shotsGround + ")");
            check("drivers / pilots shoot too", shotsDriver > 0, "driver=" + shotsDriver);
            check("seated soldiers' shots reach the player", SEATED[0] > 0, "seatedAttacks=" + SEATED[0]);
            check("helicopter dropped its squad, soldiers out fighting", phaseHeli >= 1 && onFoot >= 4 && ATTACKS[0] > 0,
                    "heliPhase=" + phaseHeli + " onFoot=" + onFoot + " attacks=" + ATTACKS[0]);
            LivingEntity bt = bot == null ? null : bot.getTarget();
            check("bot fights the raiders", bot != null && bot.isAlive() && (botHits > 0 || (bt != null && FactionRaids.isRaider(bt))),
                    "bot=" + (bot == null ? "none" : "alive=" + bot.isAlive() + " downed=" + bot.downed() + " hp=" + f(bot.getHealth())) + " target=" + (bt == null ? "none" : bt.getType().toShortString()) + " hitsOnRaiders=" + botHits);
            repBefore = Reputation.get(p, Faction.GOV);
            killWave(p);
        }), 2));
        l.add(new Step(null, () -> {}, 80));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            check("waves progress (wave 2 after wave 1 is beaten)", r != null && r.wave == 2, "wave=" + (r == null ? "-" : r.wave) + " bar='" + (r == null ? "-" : r.bar().getName().getString()) + "'");
            check("killing raiders doesn't cost standing", Reputation.get(p, Faction.GOV) == repBefore, "gov " + repBefore + " -> " + Reputation.get(p, Faction.GOV));
            killWave(p);
        }), 2));
        l.add(new Step(null, () -> {}, 80));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            boolean commander = false;
            if (r != null) for (LivingEntity e : r.alive()) if (e.hasCustomName() && e.getCustomName().getString().contains("Commander")) commander = true;
            check("last wave: the Commander", r != null && r.wave == 3 && commander, "wave=" + (r == null ? "-" : r.wave) + " commander=" + commander);
            killWave(p);
        }), 2));
        l.add(new Step(null, () -> {}, 60));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            int rep = Reputation.get(p, Faction.GOV);
            check("winning ends it: standing back, everything cleared", r == null && rep == repBefore + FactionRaids.WIN_REP && tagged(p, 128) == 0,
                    "raidOn=" + (r != null) + " gov " + repBefore + " -> " + rep + " leftover raid entities=" + tagged(p, 128));
            if (bot != null) bot.discard();
            p.getInventory().clearContent();
        }), 2));

        // ---- 3. a Sky Rebellion raid
        l.add(new Step(null, () -> UiTest.server(p -> {
            friends(p); FactionRaids.clearCooldowns(p); resetCounters(); ready(p);
            Reputation.set(p, Faction.SR, -300);
            Raid r = FactionRaids.start(p, Kind.SR, true);
            check("sky rebellion raid starts", r != null && r.kind == Kind.SR, "raid=" + (r != null));
        }), 60));
        l.add(new Step(null, () -> log("client bars after the Government raid: " + clientBars(mc)), 2));
        l.add(new Step(null, () -> UiTest.server(p -> look = nearest(p, e -> e instanceof SrMemberEntity && FactionRaids.isRaider(e))), 15));
        l.add(new Step(null, () -> lookAt(mc, look), 3));
        l.add(new Step("raids_sr", () -> lookAt(mc, look), 2));
        l.add(new Step(null, () -> {}, 140));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            if (r == null) { check("sr wave", false, "no raid"); return; }
            int n = 0, winged = 0, targeting = 0;
            for (LivingEntity e : r.alive()) if (e instanceof SrMemberEntity m) { n++; if (m.wings()) winged++; if (m.getTarget() == p) targeting++; }
            check("rebels dive in on wings and go for the player", n >= 4 && targeting >= n / 2 && ATTACKS[1] > 0,
                    "rebels=" + n + " wingsOut=" + winged + " targeting=" + targeting + " attacks=" + ATTACKS[1]);
            killWave(p);
        }), 2));
        l.add(new Step(null, () -> {}, 80));
        l.add(new Step(null, () -> UiTest.server(p -> killWave(p)), 80));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            boolean named = false;
            if (r != null) for (LivingEntity e : r.alive()) if (e instanceof SrMemberEntity m && m.isNamed()) named = true;
            check("sr last wave led by a named member", r != null && r.wave == r.waves && named, "wave=" + (r == null ? "-" : r.wave) + " named=" + named);
            killWave(p);
        }), 2));
        l.add(new Step(null, () -> {}, 60));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("raids still listed: " + FactionRaids.active().size());
            check("sr raid won and cleared", FactionRaids.raidOn(p) == null && tagged(p, 128) == 0 && Reputation.get(p, Faction.SR) == -300 + FactionRaids.WIN_REP,
                    "leftover=" + tagged(p, 128) + " sr=" + Reputation.get(p, Faction.SR));
            p.getInventory().clearContent();
        }), 2));

        // ---- 4. a Townsfolk raid
        l.add(new Step(null, () -> UiTest.server(p -> {
            friends(p); FactionRaids.clearCooldowns(p); resetCounters(); ready(p);
            Reputation.set(p, Faction.TOWN, -300);
            Raid r = FactionRaids.start(p, Kind.TOWN, true);
            check("townsfolk raid starts", r != null && r.kind == Kind.TOWN, "raid=" + (r != null));
        }), 60));
        l.add(new Step(null, () -> UiTest.server(p -> look = nearest(p, e -> e instanceof HumanNpcEntity && FactionRaids.isRaider(e))), 60));
        l.add(new Step(null, () -> lookAt(mc, look), 3));
        l.add(new Step("raids_town", () -> lookAt(mc, look), 2));
        l.add(new Step(null, () -> {}, 160));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            if (r == null) { check("town wave", false, "no raid"); return; }
            int n = 0, armed = 0, torches = 0, working = 0, targeting = 0;
            for (LivingEntity e : r.alive()) if (e instanceof HumanNpcEntity h) {
                n++;
                if (h.fighter() && !h.getMainHandItem().isEmpty()) armed++;
                if (h.getOffhandItem().is(net.minecraft.world.item.Items.TORCH)) torches++;
                if (hasGoal(h, "HumanWorkGoal") || hasGoal(h, "RebuildGoal")) working++;
                if (h.getTarget() == p) targeting++;
            }
            check("angry militia: armed, torches, no work/rebuild goals, after the player", n >= 5 && armed == n && torches > 0 && working == 0 && targeting >= n / 2 && ATTACKS[2] > 0,
                    "militia=" + n + " armed=" + armed + " torches=" + torches + " withWorkGoals=" + working + " targeting=" + targeting + " attacks=" + ATTACKS[2]);
            killWave(p);
        }), 2));
        l.add(new Step(null, () -> {}, 80));
        l.add(new Step(null, () -> UiTest.server(p -> killWave(p)), 80));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            boolean captain = false;
            if (r != null) for (LivingEntity e : r.alive()) if (e.hasCustomName() && e.getCustomName().getString().contains("Captain")) captain = true;
            check("town last wave led by the Militia Captain", r != null && r.wave == r.waves && captain, "wave=" + (r == null ? "-" : r.wave) + " captain=" + captain);
            killWave(p);
        }), 2));
        l.add(new Step(null, () -> {}, 60));
        l.add(new Step(null, () -> UiTest.server(p -> {
            check("town raid won and cleared", FactionRaids.raidOn(p) == null && tagged(p, 128) == 0 && Reputation.get(p, Faction.TOWN) == -300 + FactionRaids.WIN_REP,
                    "leftover=" + tagged(p, 128) + " town=" + Reputation.get(p, Faction.TOWN));
            p.getInventory().clearContent();
        }), 2));

        // ---- 5. losing: run away - they give up, and nothing is left behind (even in chunks loaded later)
        l.add(new Step(null, () -> UiTest.server(p -> {
            friends(p); FactionRaids.clearCooldowns(p); ready(p);
            Reputation.set(p, Faction.GOV, -300);
            FactionRaids.start(p, Kind.GOV, true);
        }), 70));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            log("flee: raid wave=" + (r == null ? "-" : r.wave) + " raiders=" + (r == null ? 0 : r.raiders.size()));
            p.connection.teleport(O.x + 200, O.y + 40, O.z, 0F, 0F);
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, false, false));
        }), 40));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            check("fleeing far ends the raid", r == null, "raidOn=" + (r != null));
            p.connection.teleport(O.x, O.y, O.z, 0F, 0F);
        }), 80));
        l.add(new Step(null, () -> UiTest.server(p -> {
            check("nothing left behind after a lost raid", tagged(p, 128) == 0, "leftover raid entities=" + tagged(p, 128));
            // a raider from a raid that's gone (as if loaded from disk) is refused
            GovAgentEntity stray = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(p.serverLevel());
            boolean refused = true;
            if (stray != null) {
                stray.moveTo(O.x + 3, O.y, O.z + 3);
                stray.getPersistentData().putUUID(FactionRaids.TAG, UUID.randomUUID());
                refused = !p.serverLevel().addFreshEntity(stray) && p.serverLevel().getEntity(stray.getUUID()) == null;
            }
            check("stray raider from an ended raid never joins the world", refused, "refused=" + refused);
            // raiders never break blocks: the stone field is whole and the air above it empty
            BlockPos b = BlockPos.containing(O);
            int broken = 0, placed = 0;
            for (int dx = -36; dx <= 36; dx++) for (int dz = -36; dz <= 36; dz++) {
                if (!p.serverLevel().getBlockState(b.offset(dx, -1, dz)).is(Blocks.SMOOTH_STONE)) broken++;
                for (int dy = 0; dy <= 4; dy++) { var s = p.serverLevel().getBlockState(b.offset(dx, dy, dz)); if (!s.isAir() && !s.is(Blocks.FIRE)) placed++; }
            }
            check("no blocks broken or placed by raiders", broken == 0 && placed == 0, "floor broken=" + broken + " blocks placed=" + placed);
        }), 2));
        l.add(new Step(null, () -> {
            String bars = clientBars(mc);
            check("no raid bar left on screen", !bars.contains("Raid"), "client bars=" + bars);
        }, 2));
        l.add(new Step(null, () -> UiTest.server(p -> {
            endAll();
            FactionRaids.testFast = false;
            friends(p);
            FactionRaids.clearCooldowns(p);
            log("summary pass=" + pass + " fail=" + fail + (FAILED.isEmpty() ? "" : " failed=" + FAILED));
        }), 5));
    }

    // ------------------------------------------------------------------ inside the real-pack test

    public static void pack(Minecraft mc, List<Step> l) {
        listen();
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.clear(p);
            FactionRaids.testFast = true;
            endAll();
            p.connection.teleport(PackTests.HOME[0] + 300, 200, PackTests.HOME[2], 0F, 0F);
        }), 60));
        for (int part = 0; part < 4; part++) { int fp = part; l.add(new Step(null, () -> PackTests.server(p -> stage(p, fp)), 10)); }
        l.add(new Step(null, () -> PackTests.server(p -> {
            ready(p);
            friends(p);
            FactionRaids.clearCooldowns(p);
            resetCounters();
            Reputation.set(p, Faction.GOV, -300);
            Raid r = FactionRaids.check(p, 0.0);
            PackTests.check("raids: bad standing + roll starts a Government raid", r != null && r.kind == Kind.GOV, "raid=" + (r == null ? "none" : r.kind));
        }), 260));
        l.add(new Step(null, () -> PackTests.server(p -> look = nearest(p, e -> e instanceof VehicleEntity v && FactionRaids.isRaider(v))), 2));
        l.add(new Step("pack_raids_gov", () -> lookAt(mc, look), 2));
        l.add(new Step(null, () -> PackTests.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            int helis = 0, aboard = 0, guns = 0;
            if (r != null) for (VehicleEntity v : r.vehicleList()) { if (v.kind.heli()) helis++; aboard += v.getPassengers().size(); }
            if (r != null) for (LivingEntity e : r.alive()) if (e instanceof GovAgentEntity g && g.hasGun()) guns++;
            PackTests.check("raids: gov helicopters + convoy, soldiers shoot from vehicles", r != null && helis >= 1 && (shotsDriver + shotsPassenger + SEATED[0]) > 0 && ATTACKS[0] > 0,
                    "helis=" + helis + " stillAboard=" + aboard + " withTaczGuns=" + guns + " shots driver/passenger=" + shotsDriver + "/" + shotsPassenger
                            + " seatedAttacks=" + SEATED[0] + " attacks=" + ATTACKS[0]);
            if (r != null) FactionRaids.end(r, false, "test");
            PackTests.check("raids: ended raid cleared", tagged(p, 128) == 0, "leftover=" + tagged(p, 128));
            friends(p); FactionRaids.clearCooldowns(p);
            Reputation.set(p, Faction.SR, -300);
            FactionRaids.start(p, Kind.SR, true);
        }), 80));
        l.add(new Step(null, () -> PackTests.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            PackTests.check("raids: sky rebellion raid spawns rebels", r != null && r.wave >= 1 && r.alive().size() >= 4, "alive=" + (r == null ? 0 : r.alive().size()));
            if (r != null) FactionRaids.end(r, false, "test");
            friends(p); FactionRaids.clearCooldowns(p);
            Reputation.set(p, Faction.TOWN, -300);
            FactionRaids.start(p, Kind.TOWN, true);
        }), 80));
        l.add(new Step(null, () -> PackTests.server(p -> {
            Raid r = FactionRaids.raidOn(p);
            PackTests.check("raids: townsfolk raid spawns militia", r != null && r.wave >= 1 && r.alive().size() >= 5, "alive=" + (r == null ? 0 : r.alive().size()));
            endAll();
            PackTests.check("raids: all cleared", tagged(p, 128) == 0, "leftover=" + tagged(p, 128));
            friends(p); FactionRaids.clearCooldowns(p);
            FactionRaids.testFast = false;
            PackTests.arena(p);
        }), 40));
    }
}
