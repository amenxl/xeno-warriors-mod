package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.collection.Collection;
import com.xenowarriors.tower.Tower;
import com.xenowarriors.tower.TowerLevel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * UI tests for the Tower Level (focus "towerlvl"): the multiplier numbers (levels 9/10/19/20/30 = x1/x2/x2/x4/x8,
 * overflow-safe), XP raising the level (and the client getting it), kills outside the Tower (yours and a tamed
 * wolf's) counting x the level multiplier, kills inside counting floor x level, wave/floor XP with a level-up, the
 * Tower Level bar, the level surviving a save + reload (relog), and the stats menu row.
 * Logged as "XW-TEST towerlvl ... PASS/FAIL"; the last line is "XW-TEST towerlvl summary pass=N fail=N".
 */
public final class TowerLevelTests {
    private TowerLevelTests() {}

    public static boolean handles(String focus) { return focus != null && focus.startsWith("towerlvl"); }

    private static int pass, fail;

    private static void log(String s) { UiTest.log("towerlvl " + s); }

    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    private static final String ZOMBIE = "minecraft:zombie", SKELETON = "minecraft:skeleton";

    private static void setCount(ServerPlayer p, String id, int n) {
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag k = root.getCompound(Collection.KEY);
        k.putInt(id, n);
        root.put(Collection.KEY, k);
        root.putBoolean(Collection.SEEN, true);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
    }

    /** Kills a fresh mob next to the player, by the player or (wolf) by their tamed wolf; returns the Collection gain. */
    private static int killOutside(ServerPlayer p, EntityType<? extends Mob> type, boolean byWolf) {
        String id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type).toString();
        int before = Collection.count(p, id);
        Mob m = type.create(p.serverLevel());
        m.moveTo(p.getX() + 3, p.getY(), p.getZ() + 3, 0, 0);
        p.serverLevel().addFreshEntity(m);
        if (byWolf) {
            var w = EntityType.WOLF.create(p.serverLevel());
            w.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0, 0);
            w.tame(p);
            p.serverLevel().addFreshEntity(w);
            m.hurt(p.damageSources().mobAttack(w), 100000F);
            w.discard();
        } else m.hurt(p.damageSources().playerAttack(p), 100000F);
        if (m.isAlive()) m.discard();
        return Collection.count(p, id) - before;
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        pass = 0; fail = 0;
        // ---- 1. the numbers
        l.add(new Step(null, () -> {
            int[] lv = {1, 9, 10, 19, 20, 29, 30, 40, 100};
            long[] want = {1, 1, 2, 2, 4, 4, 8, 16, 1024};
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            for (int i = 0; i < lv.length; i++) { long m = TowerLevel.mult(lv[i]); sb.append("Lv").append(lv[i]).append("=x").append(m).append(' '); ok &= m == want[i]; }
            check("multiplier by level", ok, sb.toString().trim());
            long big = TowerLevel.mult(1000), huge = TowerLevel.times(TowerLevel.mult(300), 100);
            check("overflow-safe", TowerLevel.mult(610) == 1L << 61 && TowerLevel.mult(620) == Long.MAX_VALUE && big == Long.MAX_VALUE && huge == 100L << 30
                    && TowerLevel.times(Long.MAX_VALUE, 3) == Long.MAX_VALUE, "Lv610=x" + TowerLevel.mult(610) + " Lv620=" + TowerLevel.fmt(TowerLevel.mult(620)) + " Lv1000=" + TowerLevel.fmt(big) + " Lv300*floor100=" + huge);
            log("curve: XP to Lv10=" + TowerLevel.totalTo(10) + " Lv20=" + TowerLevel.totalTo(20) + " Lv30=" + TowerLevel.totalTo(30) + " Lv40=" + TowerLevel.totalTo(40)
                    + " | per level: Lv1->2 " + TowerLevel.needed(1) + ", Lv9->10 " + TowerLevel.needed(9) + ", Lv19->20 " + TowerLevel.needed(19)
                    + " | floor XP f1=" + TowerLevel.floorXp(1) + " f9=" + TowerLevel.floorXp(9) + " f19=" + TowerLevel.floorXp(19) + " | boss bonus f10=" + TowerLevel.bossXp(10) + " f20=" + TowerLevel.bossXp(20));
        }, 2));
        // ---- setup
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); UiTest.hold(null); UiTest.server(p -> {
            p.setGameMode(GameType.SURVIVAL);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 900, 4, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 900, 0, false, false));
            setCount(p, ZOMBIE, 0); setCount(p, SKELETON, 0);
            TowerLevel.set(p, 1, 0);
        }); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 30));
        // ---- 2. XP raises the level (and the client hears about it)
        l.add(new Step(null, () -> UiTest.server(p -> {
            TowerLevel.addXp(p, TowerLevel.needed(1) + 5, "test");
            int a = TowerLevel.level(p); long ax = TowerLevel.xp(p);
            TowerLevel.addXp(p, TowerLevel.totalTo(10) - TowerLevel.totalTo(2) - 5, "test");
            check("xp raises level", a == 2 && ax == 5 && TowerLevel.level(p) == 10 && TowerLevel.xp(p) == 0,
                    "+" + (TowerLevel.needed(1) + 5) + " XP: Lv1 -> Lv" + a + " (xp " + ax + "); +" + (TowerLevel.totalTo(10) - TowerLevel.totalTo(2) - 5) + " more -> Lv" + TowerLevel.level(p) + " (xp " + TowerLevel.xp(p) + ")");
        }), 10));
        l.add(new Step("towerlvl_levelup_chat", () -> check("client synced", com.xenowarriors.network.TowerLevelPacket.clientLevel == 10, "client Tower level=" + com.xenowarriors.network.TowerLevelPacket.clientLevel), 3));
        // ---- 3. outside the Tower: every kill counts x the level multiplier (a tamed wolf's kill too)
        l.add(new Step(null, () -> UiTest.server(p -> {
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            for (int lv : new int[]{1, 9, 10, 19, 20, 30}) {
                TowerLevel.set(p, lv, 0);
                setCount(p, ZOMBIE, 0);
                int got = killOutside(p, EntityType.ZOMBIE, false);
                sb.append("Lv").append(lv).append(" +").append(got).append(' ');
                ok &= got == TowerLevel.mult(lv);
            }
            check("outside kill counts x level", ok && !Tower.fighting(p), sb.toString().trim() + " (want 1 1 2 2 4 8)");
            TowerLevel.set(p, 20, 0);
            setCount(p, SKELETON, 0);
            int wolf = killOutside(p, EntityType.SKELETON, true);
            check("pet kill counts for owner x level", wolf == 4, "Lv20 tamed wolf kills a skeleton: owner +" + wolf);
            setCount(p, ZOMBIE, 998);
            int capped = killOutside(p, EntityType.ZOMBIE, false);
            check("cap at 1000", capped == 2 && Collection.count(p, ZOMBIE) == Collection.MAX, "at 998, Lv20 kill: +" + capped + " -> " + Collection.count(p, ZOMBIE));
            TowerLevel.set(p, 1000, 0);
            setCount(p, ZOMBIE, 0);
            int max = killOutside(p, EntityType.ZOMBIE, false);
            check("huge multiplier no overflow", max == Collection.MAX, "Lv1000 (" + TowerLevel.fmt(TowerLevel.mult(1000)) + ") kill: +" + max + " (killValue " + TowerLevel.killValue(p, 100) + ")");
            TowerLevel.set(p, 20, 0);
            setCount(p, ZOMBIE, 0);
        }), 10));
        // ---- 4. inside the Tower: floor x level
        l.add(new Step(null, () -> UiTest.server(Tower::enter), 100));
        l.add(new Step(null, () -> UiTest.server(p -> Tower.skipTo(p, 21)), 110));
        l.add(new Step("towerlvl_hud", () -> UiTest.server(p -> log("in run: " + Tower.testInfo(p) + " | bar: " + TowerLevel.testBar(p))), 3));
        l.add(new Step(null, () -> UiTest.server(p -> {
            int before = Tower.testPending(p);
            long xpBefore = TowerLevel.xp(p);
            Mob m = Tower.testAlive(p).stream().filter(com.xenowarriors.collection.Catalog::hostile).findFirst().orElse(null);
            if (m == null) { check("inside kill floor x level", false, "no enemy to kill"); return; }
            m.invulnerableTime = 0;
            m.hurt(p.damageSources().playerAttack(p), 100000F);
            int after = Tower.testPending(p);
            int fm = Tower.mult(Tower.floorOf(p));
            check("inside kill floor x level", after - before == fm * 4 && fm == 3, "floor " + Tower.floorOf(p) + " (x" + fm + ") x Tower Lv20 (x4): pending " + before + " -> " + after + " (+" + (after - before) + ")");
            check("kill gives Tower XP", TowerLevel.xp(p) - xpBefore == TowerLevel.XP_KILL, "xp " + xpBefore + " -> " + TowerLevel.xp(p));
        }), 10));
        // ---- 5. clearing a floor: wave + floor XP, with a level-up to 10
        long[] xp0 = new long[1];
        l.add(new Step(null, () -> UiTest.server(p -> Tower.skipTo(p, 9)), 110));
        l.add(new Step(null, () -> UiTest.server(p -> { TowerLevel.set(p, 9, TowerLevel.needed(9) - 20); xp0[0] = TowerLevel.totalTo(9) + TowerLevel.needed(9) - 20; log("floor 9 start: " + Tower.testInfo(p) + " Tower Lv9 xp " + TowerLevel.xp(p) + "/" + TowerLevel.needed(9)); }), 3));
        for (int w = 0; w < 2; w++) {
            final int wave = w;
            l.add(new Step(null, () -> UiTest.server(p -> {
                int n = 0;
                for (Mob m : Tower.testAlive(p)) { m.invulnerableTime = 0; m.hurt(p.damageSources().playerAttack(p), 100000F); if (!m.isAlive()) n++; else m.discard(); }
                log("floor 9 wave " + (wave + 1) + ": killed " + n + " | " + Tower.testInfo(p) + " | Tower Lv" + TowerLevel.level(p) + " xp " + TowerLevel.xp(p));
            }), wave == 0 ? 70 : 40));
        }
        l.add(new Step("towerlvl_levelup", () -> UiTest.server(p -> {
            long total = TowerLevel.totalTo(TowerLevel.level(p)) + TowerLevel.xp(p), gained = total - xp0[0];
            check("floor clear XP + level up", TowerLevel.level(p) == 10 && gained >= 2L * TowerLevel.XP_WAVE + TowerLevel.floorXp(9),
                    "gained " + gained + " XP (2 waves x" + TowerLevel.XP_WAVE + " + floor " + TowerLevel.floorXp(9) + " + kills) -> Tower Lv" + TowerLevel.level(p) + " | " + Tower.testInfo(p) + " | bar: " + TowerLevel.testBar(p));
        }), 3));
        l.add(new Step(null, () -> UiTest.server(p -> Tower.leave(p, "test over")), 30));
        // ---- 6. a relog: saved with the player, read back, synced on login
        l.add(new Step(null, () -> UiTest.server(p -> {
            TowerLevel.set(p, 23, 77);
            try {
                CompoundTag saved = p.saveWithoutId(new CompoundTag());
                java.io.File f = new java.io.File(p.server.getServerDirectory(), "towerlvl_relog.dat");
                net.minecraft.nbt.NbtIo.writeCompressed(saved, f);
                CompoundTag disk = net.minecraft.nbt.NbtIo.readCompressed(f);
                // the session ends: what's in memory goes; the next login reads the save
                CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
                pers.remove(TowerLevel.KEY);
                p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
                TowerLevel.onLogout(new PlayerEvent.PlayerLoggedOutEvent(p));
                int gone = TowerLevel.level(p);
                p.getPersistentData().put(Player.PERSISTED_NBT_TAG, disk.getCompound("ForgeData").getCompound(Player.PERSISTED_NBT_TAG).copy());
                com.xenowarriors.network.TowerLevelPacket.clientLevel = 0;
                TowerLevel.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
                check("persists across relog", gone == 1 && TowerLevel.level(p) == 23 && TowerLevel.xp(p) == 77,
                        "saved Lv23 xp77; wiped -> Lv" + gone + "; reloaded from disk -> Lv" + TowerLevel.level(p) + " xp " + TowerLevel.xp(p));
            } catch (Exception e) { check("persists across relog", false, String.valueOf(e)); }
        }), 10));
        l.add(new Step(null, () -> check("relog client sync", com.xenowarriors.network.TowerLevelPacket.clientLevel == 23, "client Tower level after login=" + com.xenowarriors.network.TowerLevelPacket.clientLevel), 3));
        // ---- 7. the stats menu
        l.add(new Step(null, () -> com.xenowarriors.client.PlayerStatsScreen.openTab(null, 1), 30));
        l.add(new Step("towerlvl_stats", () -> log("stats screen: " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + " Tower Lv" + com.xenowarriors.network.TowerLevelPacket.clientLevel
                + " progress " + Math.round(com.xenowarriors.network.TowerLevelPacket.clientProgress * 100) + "%"), 3));
        l.add(new Step(null, () -> { mc.setScreen(null); log("summary pass=" + pass + " fail=" + fail); }, 5));
    }
}
