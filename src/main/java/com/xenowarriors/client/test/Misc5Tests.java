package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.compat.InventoryTotem;
import com.xenowarriors.gov.GovAgentEntity;
import com.xenowarriors.gov.GovEntities;
import com.xenowarriors.gov.GovScanning;
import com.xenowarriors.gov.KarmaScanner;
import com.xenowarriors.journey.Journey;
import com.xenowarriors.tower.Tower;
import com.xenowarriors.tower.TowerLevel;
import com.xenowarriors.vr.Vr;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * UI tests for the misc5 batch (focus "misc5"):
 * 1. VR repeat-clear emeralds stop at the daily cap and reset on the next in-game day; first clears aren't limited; the
 *    allowance survives a relog; the VR console shows what's left.
 * 2. A Totem of Undying in the bag (Inventory Totem behaviour) saves the player inside the Tower, a Journey battle and VR.
 * 3. A Gov guard whose scanner breaks in the Tower flees, then vanishes (no drops / XP / Collection credit) and the floor
 *    still completes; outside the modes it still flees and stays.
 * 4. The cheaper Tower XP curve (and old saves over the new per-level need levelling up on login).
 * Logged as "XW-TEST misc5 ... PASS/FAIL"; last line "XW-TEST misc5 summary pass=N fail=N".
 */
public final class Misc5Tests {
    private Misc5Tests() {}

    public static boolean handles(String focus) { return focus != null && focus.startsWith("misc5"); }

    private static int pass, fail;

    private static void log(String s) { UiTest.log("misc5 " + s); }

    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    private static int emeralds(ServerPlayer p) { return p.getInventory().countItem(Items.EMERALD); }

    private static int totems(ServerPlayer p) { return p.getInventory().countItem(Items.TOTEM_OF_UNDYING); }

    private static void clearDone(ServerPlayer p) {
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        root.remove(Vr.DONE);
        root.remove(Vr.DAILY);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
    }

    private static void survival(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 900, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 900, 0, false, false));
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        pass = 0; fail = 0;
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); UiTest.hold(null); UiTest.server(p -> {
            com.xenowarriors.character.Seasons.set(p.server, 3);
            survival(p);
            p.getInventory().clearContent();
        }); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 40));
        towerCurve(mc, l);
        vrCap(mc, l);
        totems(mc, l);
        govVanish(mc, l);
        l.add(new Step(null, () -> { mc.setScreen(null); log("summary pass=" + pass + " fail=" + fail); }, 5));
    }

    // ------------------------------------------------------------------------------------------------ 4. Tower XP curve

    private static void towerCurve(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            long t10 = TowerLevel.totalTo(10), t20 = TowerLevel.totalTo(20), t30 = TowerLevel.totalTo(30), t40 = TowerLevel.totalTo(40);
            long o10 = 2340, o20 = 8740, o30 = 19140;
            check("tower xp curve", t10 == 855 && t20 == 3230 && t30 == 7105 && t40 == 12480,
                    String.format(java.util.Locale.ROOT, "XP to Lv10=%d (was %d, %.2fx faster) Lv20=%d (was %d, %.2fx) Lv30=%d (was %d, %.2fx) Lv40=%d | per level Lv1->2 %d, Lv9->10 %d, Lv19->20 %d, Lv29->30 %d",
                            t10, o10, o10 / (double) t10, t20, o20, o20 / (double) t20, t30, o30, o30 / (double) t30, t40,
                            TowerLevel.needed(1), TowerLevel.needed(9), TowerLevel.needed(19), TowerLevel.needed(29)));
            check("multiplier mapping unchanged", TowerLevel.mult(9) == 1 && TowerLevel.mult(10) == 2 && TowerLevel.mult(20) == 4 && TowerLevel.mult(30) == 8,
                    "Lv9 x" + TowerLevel.mult(9) + " Lv10 x" + TowerLevel.mult(10) + " Lv20 x" + TowerLevel.mult(20) + " Lv30 x" + TowerLevel.mult(30));
            // runs needed, with a solo run's XP: floor f cleared = per floor (2-3 waves x3 + 10+f + kills) - use the guide's estimates
            log(String.format(java.util.Locale.ROOT, "runs needed (solo run to floor 10 ~345 XP, to floor 20 ~985 XP): Lv10 %.1f runs to f10 / %.1f to f20; Lv20 %.1f runs to f20; Lv30 %.1f runs to f20",
                    t10 / 345.0, t10 / 985.0, t20 / 985.0, t30 / 985.0));
            // an old save: Lv5 with 300 XP stored (under the old curve needed(5) was 260) - login levels it up on the new curve
            TowerLevel.set(p, 5, 300);
            TowerLevel.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
            check("old save normalised on login", TowerLevel.level(p) == 7 && TowerLevel.xp(p) == 95,
                    "Lv5 xp300 -> Lv" + TowerLevel.level(p) + " xp " + TowerLevel.xp(p) + " (needed 5->6 " + TowerLevel.needed(5) + ", 6->7 " + TowerLevel.needed(6) + ")");
            TowerLevel.set(p, 1, 0);
        }), 5));
    }

    // ------------------------------------------------------------------------------------------------ 1. VR daily cap

    private static final int[] V = new int[8];

    private static void vrCap(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            clearDone(p);
            p.getInventory().clearContent();
            p.server.overworld().setDayTime(1000);                    // (morning of day 0)
            int e0 = emeralds(p);
            Vr.testReward(p, 1);                                        // first clear of Fox Fighter
            int first = emeralds(p) - e0;
            check("vr first clear unaffected", first >= 12 && first <= 19 && Vr.dailyLeft(p) == Vr.DAILY_EMERALDS,
                    "first clear +" + first + " emeralds, repeat allowance left " + Vr.dailyLeft(p) + "/" + Vr.DAILY_EMERALDS);
            StringBuilder sb = new StringBuilder();
            int total = 0, zeroAfter = 0;
            for (int i = 0; i < 7; i++) {
                int b = emeralds(p);
                Vr.testReward(p, 1);
                int got = emeralds(p) - b;
                total += got;
                if (Vr.dailyLeft(p) == 0 && got == 0) zeroAfter++;
                sb.append('+').append(got).append(' ');
            }
            check("vr repeats stop at the cap", total == Vr.DAILY_EMERALDS && Vr.dailyLeft(p) == 0 && zeroAfter >= 2,
                    "7 repeats: " + sb.toString().trim() + " = " + total + " (cap " + Vr.DAILY_EMERALDS + "), left " + Vr.dailyLeft(p) + ", resets in " + Vr.fmtReset(Vr.ticksToReset(p.server)));
            // a first clear of another challenge still pays in full after the cap
            int b = emeralds(p);
            Vr.testReward(p, 2);
            int f2 = emeralds(p) - b;
            check("vr first clear pays after the cap", f2 >= 12 && f2 <= 19 && Vr.dailyLeft(p) == 0, "King of the Sky first clear +" + f2 + ", left " + Vr.dailyLeft(p));
            V[0] = Vr.dailyLeft(p);
        }), 10));
        l.add(new Step("misc5_vr_cap_chat", () -> {}, 3));
        // the console shows it
        l.add(new Step(null, () -> UiTest.server(Vr::open), 15));
        l.add(new Step("misc5_vr_console_capped", () -> {
            var d = mc.screen instanceof com.xenowarriors.client.vr.VrScreen s ? s.data() : null;
            check("vr console shows the cap", d != null && d.dailyLeft() == 0 && d.dailyCap() == Vr.DAILY_EMERALDS && d.resetTicks() > 0 && d.resetTicks() <= 24000,
                    d == null ? "screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) : "console: left " + d.dailyLeft() + "/" + d.dailyCap() + " resets in " + Vr.fmtReset(d.resetTicks()));
        }, 3));
        // relog: the allowance is in the player's save
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> {
            try {
                CompoundTag saved = p.saveWithoutId(new CompoundTag());
                java.io.File f = new java.io.File(p.server.getServerDirectory(), "misc5_vr_relog.dat");
                net.minecraft.nbt.NbtIo.writeCompressed(saved, f);
                CompoundTag disk = net.minecraft.nbt.NbtIo.readCompressed(f);
                CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
                pers.remove(Vr.DAILY);
                p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
                int wiped = Vr.dailyLeft(p);
                p.getPersistentData().put(Player.PERSISTED_NBT_TAG, disk.getCompound("ForgeData").getCompound(Player.PERSISTED_NBT_TAG).copy());
                check("vr cap persists across relog", wiped == Vr.DAILY_EMERALDS && Vr.dailyLeft(p) == 0, "wiped -> left " + wiped + "; reloaded from disk -> left " + Vr.dailyLeft(p));
            } catch (Exception e) { check("vr cap persists across relog", false, String.valueOf(e)); }
        }); }, 5));
        // the next in-game day: repeats pay again
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.server.overworld().setDayTime(24000 + 1000);
            int left = Vr.dailyLeft(p), b = emeralds(p);
            Vr.testReward(p, 1);
            int got = emeralds(p) - b;
            check("vr cap resets next day", left == Vr.DAILY_EMERALDS && got >= 3 && got <= 5 && Vr.dailyLeft(p) == Vr.DAILY_EMERALDS - got,
                    "day " + Vr.day(p.server) + ": left " + left + ", repeat +" + got + ", now left " + Vr.dailyLeft(p));
        }), 5));
        l.add(new Step(null, () -> UiTest.server(Vr::open), 15));
        l.add(new Step("misc5_vr_console", () -> {
            var d = mc.screen instanceof com.xenowarriors.client.vr.VrScreen s ? s.data() : null;
            log("console next day: " + (d == null ? "no screen" : "left " + d.dailyLeft() + "/" + d.dailyCap() + " resets in " + Vr.fmtReset(d.resetTicks())));
        }, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> { p.server.overworld().setDayTime(1000); p.getInventory().clearContent(); }); }, 5));
    }

    // ------------------------------------------------------------------------------------------------ 2. bag totems in the modes

    private static int before;

    private static void armTotem(ServerPlayer p) {
        InventoryTotem.testForce(true);
        p.removeAllEffects();
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.invulnerableTime = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) p.getInventory().setItem(i, ItemStack.EMPTY);
        p.getInventory().setItem(20, new ItemStack(Items.TOTEM_OF_UNDYING));       // deep in the bag, not a hand
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        before = totems(p);
        p.hurt(p.damageSources().generic(), 1000F);
    }

    private static String state(ServerPlayer p) {
        return "alive=" + p.isAlive() + " hp=" + String.format(java.util.Locale.ROOT, "%.1f", p.getHealth()) + " totems " + before + "->" + totems(p) + " regen=" + p.hasEffect(MobEffects.REGENERATION)
                + " dim=" + p.level().dimension().location().getPath();
    }

    private static boolean saved(ServerPlayer p) {
        return p.isAlive() && p.getHealth() > 0 && p.getHealth() <= 6 && totems(p) == before - 1 && p.hasEffect(MobEffects.REGENERATION);
    }

    private static void totems(Minecraft mc, List<Step> l) {
        // Tower
        l.add(new Step(null, () -> UiTest.server(p -> { survival(p); Tower.enter(p); }), 100));
        l.add(new Step(null, () -> UiTest.server(p -> armTotem(p)), 3));
        l.add(new Step("misc5_totem_tower", () -> UiTest.server(p -> {
            check("bag totem saves in the Tower", saved(p) && Tower.fighting(p) && Tower.is(p.level()), state(p) + " | still in run=" + Tower.fighting(p) + " | " + Tower.testInfo(p));
        }), 3));
        l.add(new Step(null, () -> UiTest.server(p -> { Tower.leave(p, "test"); survival(p); }), 40));
        // VR (free training; checked before VR Ultima turns up)
        l.add(new Step(null, () -> UiTest.server(p -> { survival(p); Vr.action(p, 0, 6); }), 20));
        l.add(new Step(null, () -> UiTest.server(p -> armTotem(p)), 3));
        l.add(new Step("misc5_totem_vr", () -> UiTest.server(p -> {
            check("bag totem saves in VR", saved(p) && Vr.is(p.level()), state(p) + " | still in VR=" + Vr.is(p.level()));
        }), 3));
        l.add(new Step(null, () -> UiTest.server(p -> { Vr.action(p, 2, 0); survival(p); }), 40));
        // Journey battle
        CharacterType[] picks = {CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.KYRIE, CharacterType.LEXY, CharacterType.MAX, CharacterType.TECHNICA, CharacterType.STEVIE, CharacterType.LOGAN};   // (solo: 8)
        l.add(new Step(null, () -> UiTest.server(p -> {
            survival(p);
            Journey.setCleared(p, 2);
            Journey.open(p);
            for (CharacterType t : picks) Journey.action(p, 1, t.ordinal(), 0);
            Journey.action(p, 2, 0, 0);
            Journey.action(p, 11, 0, 0);
            Journey.action(p, 4, 0, 0);
            log("journey started: " + Journey.testPhase(p));
        }), 80));
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> Journey.testJump(p, com.xenowarriors.journey.JourneyRules.BATTLE)); }, 30));
        l.add(new Step(null, () -> UiTest.server(p -> { Journey.action(p, 6, CharacterType.MIKIKO.ordinal(), 0); Journey.action(p, 7, 0, 0); }), 100));
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> { log("journey before hit: " + Journey.testPhase(p)); armTotem(p); }); }, 3));
        l.add(new Step("misc5_totem_journey", () -> UiTest.server(p -> {
            String ph = Journey.testPhase(p);
            check("bag totem saves in a Journey battle", saved(p) && Journey.inRun(p) && ph.equals(Journey.BATTLE + " MIKIKO"),
                    state(p) + " | phase/active=" + ph + " (3 = battle; still Mikiko = not knocked out)");
        }), 3));
        l.add(new Step(null, () -> UiTest.server(p -> { Journey.leave(p, "test over", true); InventoryTotem.testForce(null); p.getInventory().clearContent(); survival(p); }), 60));
    }

    // ------------------------------------------------------------------------------------------------ 3. Gov soldiers vanish in the Tower

    private static GovAgentEntity guard;
    private static final double[] G = new double[8];

    private static GovAgentEntity spawnGuard(ServerPlayer p, double dx, double dz) {
        GovAgentEntity g = GovEntities.GOV_AGENT.get().create(p.serverLevel());
        if (g == null) return null;
        g.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0, 0);
        g.setup(true, 1, g.blockPosition());
        g.makeCommander(40F, 5F);                       // (every modification - SPEED dashes and JUMP leaps included - but not a Tower boss)
        GovScanning.give(g, KarmaScanner.Tier.BASIC, -1);
        p.serverLevel().addFreshEntity(g);
        return g;
    }

    private static void govVanish(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.server(p -> { survival(p); TowerLevel.set(p, 1, 0); Tower.enter(p); }), 100));
        l.add(new Step(null, () -> UiTest.server(p -> Tower.skipTo(p, 3)), 110));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); UiTest.server(p -> {
            survival(p);
            List<Mob> others = Tower.testAlive(p);
            guard = spawnGuard(p, 4, 0);
            if (guard == null) { check("gov guard spawned", false, "no guard"); return; }
            Tower.testAdd(p, guard);
            for (Mob m : others) m.discard();
            guard.setTarget(p);
            G[0] = Tower.testPending(p);
            G[1] = Tower.floorOf(p);
            G[2] = guard.distanceTo(p);
            G[6] = TowerLevel.xp(p);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, guard.getEyePosition());
            GovScanning.overload(guard, p, KarmaScanner.Tier.BASIC, 1_000_000_000L);
            log("tower guard scanner broke: " + Tower.testInfo(p) + " | panicking=" + guard.panicking() + " vanishing=" + guard.vanishing() + " dist=" + String.format("%.1f", G[2]));
        }); }, 40));
        l.add(new Step("misc5_gov_flee", () -> UiTest.server(p -> {
            GovAgentEntity g = guard;
            if (g == null) return;
            double d = g.distanceTo(p);
            check("tower guard flees first", g.isAlive() && !g.isRemoved() && g.panicking() && g.vanishing() && g.getTarget() == null && d > G[2] + 1,
                    "2 s after: alive=" + g.isAlive() + " panicking=" + g.panicking() + " target=" + (g.getTarget() != null) + " dist " + String.format("%.1f -> %.1f", G[2], d));
            G[3] = g.getX(); G[4] = g.getY(); G[5] = g.getZ();
        }), 70));
        l.add(new Step("misc5_gov_vanished", () -> UiTest.server(p -> {
            GovAgentEntity g = guard;
            if (g == null) return;
            Vec3 at = g.position();
            int items = p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(6)).size();
            int orbs = p.serverLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(6)).size();
            boolean inWave = Tower.testAlive(p).contains(g);
            check("tower guard vanishes, no loot", g.isRemoved() && !inWave && items == 0 && orbs == 0 && Tower.testPending(p) == (int) G[0],
                    "5.5 s after: removed=" + g.isRemoved() + " (" + g.getRemovalReason() + ") inWave=" + inWave + " items=" + items + " orbs=" + orbs
                            + " Collection pending " + (int) G[0] + " -> " + Tower.testPending(p) + " | Tower XP " + (long) G[6] + " -> " + TowerLevel.xp(p) + " (wave XP only, no kill XP) | " + Tower.testInfo(p));
        }), 3));
        // the rest of the floor, then the floor should be cleared
        for (int w = 0; w < 4; w++) {
            l.add(new Step(null, () -> UiTest.server(p -> {
                for (Mob m : Tower.testAlive(p)) { m.invulnerableTime = 0; m.hurt(p.damageSources().playerAttack(p), 100000F); if (m.isAlive()) m.discard(); }
                log("tower clearing: " + Tower.testInfo(p));
            }), 70));
        }
        l.add(new Step("misc5_gov_floor_done", () -> UiTest.server(p -> {
            String info = Tower.testInfo(p);
            check("floor completes after the vanish", Tower.floorOf(p) > (int) G[1] || info.contains("state=2"), "floor " + (int) G[1] + " -> " + info);
        }), 3));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); UiTest.server(p -> Tower.leave(p, "test over")); }, 40));
        // outside the modes: flees for 15 s and stays
        l.add(new Step(null, () -> UiTest.server(p -> {
            survival(p);
            guard = spawnGuard(p, 4, 0);
            if (guard == null) return;
            G[2] = guard.distanceTo(p);
            GovScanning.overload(guard, p, KarmaScanner.Tier.BASIC, 1_000_000_000L);
        }), 110));
        l.add(new Step(null, () -> UiTest.server(p -> {
            GovAgentEntity g = guard;
            if (g == null) return;
            check("outside: guard flees and stays", g.isAlive() && !g.isRemoved() && g.panicking() && !g.vanishing() && g.distanceTo(p) > G[2] + 1,
                    "5.5 s after (overworld): alive=" + g.isAlive() + " panicking=" + g.panicking() + " vanishing=" + g.vanishing() + " dist " + String.format("%.1f -> %.1f", G[2], g.distanceTo(p)));
            g.discard();
        }), 3));
    }
}
