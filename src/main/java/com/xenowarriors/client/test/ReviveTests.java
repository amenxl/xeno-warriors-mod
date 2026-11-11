package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.LockOn;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.UltimateCutscene;
import com.xenowarriors.client.XenoCutscene;
import com.xenowarriors.client.mikiko.FoxKingClient;
import com.xenowarriors.client.party.ReviveWarpClient;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.mikiko.FoxKing;
import com.xenowarriors.party.Duels;
import com.xenowarriors.party.KarmaShield;
import com.xenowarriors.party.ReviveWarp;
import com.xenowarriors.skill.CutsceneBreak;
import com.xenowarriors.skill.SkillManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * UI tests (focus "revive"): Fox King mastery (duration at mastery 0/50/100, mastery gain, HUD timer, stats row), the
 * revive warp (REVIVE lock-on on a downed bot, hold right-click to warp for 30 Karma, refusals, Rhalix's Karma shield
 * blocking an arrow, the Rhalix bot warping to a downed player on its own) and cutscenes cut short by a hit.
 * Results are logged as "XW-TEST revive ...".
 */
public final class ReviveTests {
    private ReviveTests() {}

    private static final String TAG = "xw_revive_test";
    private static int X, Y, Z;

    private static void log(String s) { UiTest.log("revive " + s); }
    private static String f1(double d) { return String.format(Locale.ROOT, "%.2f", d); }
    private static void server(java.util.function.Consumer<ServerPlayer> f) { UiTest.server(f); }

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    static void fill(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (BlockPos q : BlockPos.betweenClosed(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1))) l.setBlock(q, s, 2);
    }

    private static void clearAll(ServerPlayer p) {
        for (PartyBotEntity b : p.serverLevel().getEntitiesOfClass(PartyBotEntity.class, p.getBoundingBox().inflate(90))) { PartyBots.forgetFake(b.getUUID()); b.discard(); }
        for (PartyBotEntity b : PartyBots.bots(p)) { PartyBots.forgetFake(b.getUUID()); b.discard(); }
        p.serverLevel().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(90), e -> e.getTags().contains(TAG) || e instanceof ItemEntity || e instanceof AbstractArrow).forEach(Entity::discard);
        for (String c : new String[]{"kaeru", "rhalix", "ultima", "yuri", "mikiko"}) p.getPersistentData().remove("xw_bot_rejoin_" + c);
    }

    private static void arena(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        X = p.getBlockX(); Z = p.getBlockZ(); Y = Math.min(lv.getMaxBuildHeight() - 30, p.getBlockY() + 40);
        fill(lv, X - 32, Y, Z - 32, X + 32, Y + 12, Z + 32, Blocks.AIR.defaultBlockState());
        fill(lv, X - 32, Y - 1, Z - 32, X + 32, Y - 1, Z + 32, Blocks.SMOOTH_STONE.defaultBlockState());
        p.getAbilities().flying = false; p.onUpdateAbilities();
        p.teleportTo(lv, X + 0.5, Y, Z + 0.5, 0, 10);
        lv.setDayTime(6000);
        clearAll(p);
        log("arena at " + X + " " + Y + " " + Z);
    }

    private static void home(ServerPlayer p, double dx, double dz, float yaw, float pitch) {
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().flying = false; p.onUpdateAbilities();
        p.teleportTo(p.serverLevel(), X + 0.5 + dx, Y, Z + 0.5 + dz, yaw, pitch);
        p.setHealth(p.getMaxHealth());
    }

    private static void karma(ServerPlayer p, float v) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.setKarma(v); Karma.sync(p, d); });
    }

    private static <T extends Mob> T mob(ServerPlayer p, EntityType<T> type, double x, double z, boolean ai, float hp) {
        T m = type.create(p.serverLevel());
        if (m == null) return null;
        m.moveTo(x, Y, z, 180, 0);
        m.setNoAi(!ai);
        m.setPersistenceRequired();
        m.addTag(TAG);
        p.serverLevel().addFreshEntity(m);
        var a = m.getAttribute(Attributes.MAX_HEALTH);
        if (a != null) { a.setBaseValue(hp); m.setHealth(hp); }
        return m;
    }

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t && !b.isRemoved()) return b;
        p.getPersistentData().remove("xw_bot_rejoin_" + t.getId());
        PartyBotEntity b = PartyBots.spawn(p, t, null);
        if (b == null) { log("spawn " + t.getId() + " FAILED"); return null; }
        b.setMode(PartyBotEntity.NEUTRAL);
        var hp = b.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(200);
        b.setHealth(b.getMaxHealth());
        return b;
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false; UiTest.character(CharacterType.MIKIKO); UiTest.hold(null); }, 30));
        l.add(new Step(null, () -> server(ReviveTests::arena), 30));
        boolean all = focus.equals("revive");
        if (all || focus.equals("revive_fox")) fox(mc, l);
        if (all || focus.equals("revive_cut")) cutscenes(mc, l);       // (before the warp tests: a revive leaves you 10 s invincible)
        if (all || focus.equals("revive_warp")) warp(mc, l);
        l.add(new Step(null, () -> log("done"), 5));
    }

    // ------------------------------------------------------------------------------------------------ 1. Fox King mastery

    private static final float[] MK = new float[4];

    private static void fox(Minecraft mc, List<Step> l) {
        for (int m : new int[]{0, 50, 100}) {
            l.add(new Step(null, () -> server(p -> {
                FoxKing.stop(p, true);
                FoxKing.resetCooldown(p);
                home(p, 0, 0, 0, 10);
                FoxKing.setMastery(p, m);
                karma(p, 100);
                float k0 = Karma.get(p);
                FoxKing.toggle(p);
                var atk = p.getAttribute(Attributes.ATTACK_DAMAGE);
                var mod = atk == null ? null : atk.getModifier(java.util.UUID.fromString("7d1f0c2a-5b6e-4f3a-9c8d-2e1f0a9b8c72"));
                log("fox mastery=" + m + " active=" + FoxKing.active(p) + " left=" + FoxKing.left(p) + " ticks (" + FoxKing.left(p) / 20 + " s, expect " + FoxKing.duration(m) / 20
                        + ") karmaSpent=" + f1(k0 + Karma.MAX * FoxKing.KARMA_MAX_BONUS - Karma.get(p)) + " (expect " + FoxKing.cost(m) + ") cooldownAfter=" + FoxKing.cooldown(m) / 20
                        + "s attack+" + (mod == null ? "?" : Math.round(mod.getAmount() * 100)) + "%");
            }), 30));
            l.add(new Step("revive_fox_hud_m" + m, () -> log("fox client mastery=" + f1(FoxKingClient.mastery) + " hudLeft=" + FoxKingClient.left() + " total=" + FoxKingClient.total()), 3));
        }
        // mastery gain: back to 0, 2 seconds in the form, 3 hits and a kill
        l.add(new Step(null, () -> server(p -> {
            FoxKing.stop(p, true); FoxKing.resetCooldown(p); FoxKing.setMastery(p, 0); karma(p, 100); FoxKing.toggle(p);
            MK[0] = FoxKing.mastery(p);
        }), 41));
        l.add(new Step(null, () -> server(p -> {
            MK[1] = FoxKing.mastery(p);
            var z = mob(p, EntityType.ZOMBIE, X + 0.5, Z + 3.5, false, 1000);
            if (z != null) z.addTag("xw_fox_dummy");
        }), 2));
        for (int h = 0; h < 3; h++) l.add(new Step(null, () -> server(p -> {
            for (Mob z : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(10), e -> e.getTags().contains("xw_fox_dummy"))) { z.invulnerableTime = 0; z.hurt(p.damageSources().playerAttack(p), 2F); }
        }), 11));
        l.add(new Step(null, () -> server(p -> {
            MK[2] = FoxKing.mastery(p);
            for (Mob z : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(10), e -> e.getTags().contains("xw_fox_dummy"))) { z.invulnerableTime = 0; z.hurt(p.damageSources().playerAttack(p), 5000F); }
        }), 3));
        l.add(new Step(null, () -> server(p -> {
            MK[3] = FoxKing.mastery(p);
            log("fox gain: start=" + f1(MK[0]) + " after 2s in form=" + f1(MK[1]) + " (+" + f1(MK[1] - MK[0]) + ", expect ~+0.10) after 3 hits=" + f1(MK[2])
                    + " (+" + f1(MK[2] - MK[1]) + ", expect ~+0.30 plus time) after a kill=" + f1(MK[3]) + " (+" + f1(MK[3] - MK[2]) + ", expect +0.5)");
            int saved = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> Math.round(d.save().getFloat("fox_mastery") * 100)).orElse(-1);
            log("fox mastery saved in NBT (x100)=" + saved);
            FoxKing.setMastery(p, 37);
        }), 10));
        l.add(new Step(null, () -> { com.xenowarriors.client.menu.CharacterCustomScreen.open(null); }, 30));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.testTab(0); }, 30));
        l.add(new Step("revive_fox_stats", () -> log("fox stats screen open=" + (mc.screen != null) + " client mastery=" + f1(FoxKingClient.mastery)), 3));
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> { FoxKing.stop(p, true); FoxKing.resetCooldown(p); }); }, 10));
    }

    // ------------------------------------------------------------------------------------------------ 2. revive warp

    private static final float[] KW = new float[4];

    private static PartyBotEntity botNear(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t && !b.isRemoved()) return b;
        return null;
    }

    private static void downBot(ServerPlayer p, CharacterType t, double dx, double dz) {
        PartyBotEntity b = bot(p, t);
        if (b != null && (b.downed() || com.xenowarriors.bot.ReviveAftermath.invulnerable(b))) {     // (just revived: 10 s invincible - a fresh one instead)
            PartyBots.forgetFake(b.getUUID()); b.discard();
            b = bot(p, t);
        }
        if (b == null) return;
        b.moveTo(X + 0.5 + dx, Y, Z + 0.5 + dz, 0, 0);
        b.getNavigation().stop();
        b.invulnerableTime = 0;
        b.hurt(p.damageSources().generic(), 100000F);
        log("down bot " + t.getId() + " downed=" + b.downed() + " at dist " + f1(b.distanceTo(p)));
    }

    private static void lockOnto(Minecraft mc) {
        if (LockOn.target() != null) LockOn.toggle();
        LockOn.toggle();
        var t = LockOn.target();
        log("lock-on target=" + (t == null ? "none" : t.getName().getString()) + " downedAlly=" + ReviveWarpClient.downedAlly(t));
    }

    private static void holdUse(Minecraft mc, List<Step> l, String shotMid, int ticks) {
        l.add(new Step(null, () -> mc.options.keyUse.setDown(true), 10));
        l.add(new Step(shotMid, () -> log("hold progress=" + f1(ReviveWarpClient.progress())), ticks - 10));
        l.add(new Step(null, () -> mc.options.keyUse.setDown(false), 4));
    }

    private static void warp(Minecraft mc, List<Step> l) {
        // a: as Mikiko, a downed Kaeru bot 20 blocks ahead: lock on (REVIVE), hold right-click, warp for 30 Karma
        l.add(new Step(null, () -> { UiTest.character(CharacterType.MIKIKO); mc.options.setCameraType(CameraType.FIRST_PERSON); }, 20));
        l.add(new Step(null, () -> server(p -> { clearAll(p); home(p, 0, 0, 0, 8); karma(p, 100); }), 20));
        l.add(new Step(null, () -> server(p -> downBot(p, CharacterType.KAERU, 0, 18)), 15));
        l.add(new Step(null, () -> lockOnto(mc), 10));
        l.add(new Step("revive_marker_bot", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> KW[0] = Karma.get(p)), 3));
        holdUse(mc, l, "revive_hold", 26);
        l.add(new Step("revive_warped", () -> server(p -> {
            PartyBotEntity b = botNear(p, CharacterType.KAERU);
            log("warp mikiko: result=" + ReviveWarp.last + " karma " + f1(KW[0]) + " -> " + f1(Karma.get(p)) + " (expect -30) dist to bot now=" + (b == null ? "?" : f1(b.distanceTo(p)))
                    + " shield=" + KarmaShield.active(p));
        }), 3));
        l.add(new Step(null, () -> {}, 50));
        l.add(new Step(null, () -> server(p -> { PartyBotEntity b = botNear(p, CharacterType.KAERU); log("after 2.5s in the circle: bot downed=" + (b != null && b.downed())); }), 3));
        // b: refused with 20 Karma
        l.add(new Step(null, () -> server(p -> { home(p, 0, 0, 0, 8); karma(p, 20); downBot(p, CharacterType.KAERU, 0, 18); }), 15));
        l.add(new Step(null, () -> lockOnto(mc), 5));
        holdUse(mc, l, null, 26);
        l.add(new Step("revive_refused", () -> server(p -> {
            PartyBotEntity b = botNear(p, CharacterType.KAERU);
            log("warp with 20 karma: result=" + ReviveWarp.last + " karma=" + f1(Karma.get(p)) + " (expect 20) dist to bot=" + (b == null ? "?" : f1(b.distanceTo(p))));
            // too far: the same bot 60 blocks off (the server check; lock-on wouldn't reach that far anyway)
            karma(p, 100);
            if (b != null) { b.moveTo(X + 0.5, Y, Z + 0.5 + 60, 0, 0); boolean ok = ReviveWarp.warp(p, b); log("warp 60 blocks: ok=" + ok + " result=" + ReviveWarp.last + " karma=" + f1(Karma.get(p))); b.moveTo(X + 0.5, Y, Z + 18.5, 0, 0); }
        }), 3));
        // c: as Rhalix: the shield dome goes up around them both and stops a skeleton's arrow
        l.add(new Step(null, () -> { if (LockOn.target() != null) LockOn.toggle(); UiTest.character(CharacterType.RHALIX); }, 25));
        l.add(new Step(null, () -> server(p -> { home(p, 0, 0, 0, 8); karma(p, 100); downBot(p, CharacterType.KAERU, 0, 18); }), 15));
        l.add(new Step(null, () -> lockOnto(mc), 5));
        l.add(new Step(null, () -> server(p -> KW[1] = Karma.get(p)), 2));
        holdUse(mc, l, null, 24);
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(p -> {
            log("warp rhalix: result=" + ReviveWarp.last + " karma " + f1(KW[1]) + " -> " + f1(Karma.get(p)) + " shield=" + KarmaShield.active(p));
            KW[2] = KarmaShield.blockedProjectiles;
            var sk = mob(p, EntityType.SKELETON, p.getX() + 9, p.getZ() + 2, false, 40);
            if (sk == null) return;
            Arrow a = new Arrow(p.serverLevel(), sk);
            a.setPos(sk.getX(), sk.getEyeY() - 0.1, sk.getZ());
            a.addTag(TAG);
            double dx = p.getX() - sk.getX(), dz = p.getZ() - sk.getZ(), dy = p.getY(0.5) - a.getY();
            a.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.1, dz, 1.4F, 0F);
            p.serverLevel().addFreshEntity(a);
            KW[3] = p.getHealth();
            log("skeleton arrow fired at the shield from " + f1(sk.distanceTo(p)) + " blocks");
        }); }, 3));
        l.add(new Step("revive_shield", () -> log("client shields=" + ReviveWarpClient.shields()), 12));
        l.add(new Step("revive_shield_after", () -> server(p -> log("shield after the arrow: blocked projectiles +" + (KarmaShield.blockedProjectiles - KW[2]) + " arrows left="
                + p.serverLevel().getEntitiesOfClass(Arrow.class, p.getBoundingBox().inflate(20)).size() + " health " + f1(KW[3]) + " -> " + f1(p.getHealth()) + " shield=" + KarmaShield.active(p))), 3));
        l.add(new Step(null, () -> {}, 40));
        l.add(new Step(null, () -> server(p -> { PartyBotEntity b = botNear(p, CharacterType.KAERU);
            log("after the revive: bot downed=" + (b != null && b.downed()) + " shield still up=" + KarmaShield.active(p)); }), 5));
        l.add(new Step(null, () -> log("client shields after the revive=" + ReviveWarpClient.shields()), 3));
        // d: the Rhalix bot warps to its downed owner by itself
        l.add(new Step(null, () -> { if (LockOn.target() != null) LockOn.toggle(); UiTest.character(CharacterType.MIKIKO); }, 25));
        l.add(new Step(null, () -> server(p -> {
            clearAll(p);
            home(p, 0, 0, 180, 20);
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            r.moveTo(X + 0.5, Y, Z + 0.5 + 22, 180, 0);
            r.getNavigation().stop();
            r.karma = 100F;
        }), 30));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity r = botNear(p, CharacterType.RHALIX);
            if (r != null) { r.moveTo(X + 0.5, Y, Z + 0.5 + 22, 180, 0); r.getNavigation().stop(); }
            p.hurt(p.damageSources().generic(), 1000F);
            log("player downed=" + Duels.isDowned(p) + " rhalix bot at " + (r == null ? "?" : f1(r.distanceTo(p))) + " karma=" + (r == null ? "?" : f1(r.karma)));
        }), 12));
        l.add(new Step("revive_bot_warp", () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(p -> {
            PartyBotEntity r = botNear(p, CharacterType.RHALIX);
            log("rhalix bot: dist to downed player=" + (r == null ? "?" : f1(r.distanceTo(p))) + " karma=" + (r == null ? "?" : f1(r.karma)) + " shield=" + (r != null && KarmaShield.active(r))
                    + " last=" + ReviveWarp.last);
        }); }, 3));
        l.add(new Step("revive_bot_shield_wide", () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT), 15));
        l.add(new Step(null, () -> {}, 40));
        l.add(new Step(null, () -> server(p -> log("after: player downed=" + Duels.isDowned(p) + " shield=" + KarmaShield.blockedHits)), 5));
        l.add(new Step(null, () -> log("client shields after=" + ReviveWarpClient.shields()), 3));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); server(ReviveTests::clearAll); }, 10));
    }

    // ------------------------------------------------------------------------------------------------ 3. cutscenes cut short by a hit

    private static final long[] HIT = new long[2];
    private static final float[] HP = new float[3];

    private static Mob attacker(ServerPlayer p) {
        for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(60), e -> e.getTags().contains("xw_cut_attacker"))) return m;
        Mob z = mob(p, EntityType.ZOMBIE, X + 0.5 - 30, Z + 0.5 - 30, false, 100);     // (far away: out of a Xeno Blast's freeze)
        if (z != null) z.addTag("xw_cut_attacker");
        return z;
    }

    private static void cut(Minecraft mc, List<Step> l, CharacterType t, int slot, String name, int hitAt) {
        l.add(new Step(null, () -> { UiTest.character(t); mc.options.setCameraType(CameraType.FIRST_PERSON); }, 25));
        l.add(new Step(null, () -> server(p -> {
            home(p, 0, 0, 0, 0);
            p.getPersistentData().remove("xw_karma_lock_until");
            SkillManager.resetCooldowns(p);
            karma(p, 100);
            p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40), e -> e.getTags().contains("xw_cut_target")).forEach(Entity::discard);
            for (int k = 0; k < 2; k++) { var z = mob(p, EntityType.ZOMBIE, X + 0.5 - 2 + k * 4, Z + 0.5 + 7, false, 300); if (z != null) z.addTag("xw_cut_target"); }
            attacker(p);
        }), 10));
        l.add(new Step(null, () -> server(p -> { SkillManager.useSkill(p, slot); HP[0] = 0; for (Mob z : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40), e -> e.getTags().contains("xw_cut_target"))) HP[0] += z.getHealth(); }), hitAt));
        l.add(new Step("revive_cut_" + name + "_before", () -> log("cut " + name + ": in cutscene before the hit=" + UltimateCutscene.active() + " xeno=" + XenoCutscene.active() + " camera=" + mc.options.getCameraType()), 1));
        l.add(new Step(null, () -> server(p -> {
            HP[1] = p.getHealth();
            HIT[0] = p.level().getGameTime();
            Mob a = attacker(p);
            p.invulnerableTime = 0;
            if (a != null) com.xenowarriors.combat.Frozen.release(a);          // (caught in the Xeno Blast's freeze it couldn't attack at all)
            if (a != null) for (int k = 0; k < CutsceneBreak.HITS; k++) { p.invulnerableTime = 0; p.hurt(p.damageSources().mobAttack(a), 0.5F); }   // (the guard: 3 hits)
            log("cut " + name + ": hit at " + HIT[0] + " -> interrupted=" + (CutsceneBreak.lastAt == HIT[0]) + " what=" + CutsceneBreak.lastWhat + " health " + f1(HP[1]) + " -> " + f1(p.getHealth()));
        }), 0));
        final int[] seen = {-1};
        for (int k = 1; k <= 8; k++) {
            int kk = k;
            l.add(new Step(kk == 4 ? "revive_cut_" + name + "_after" : null, () -> {
                boolean on = UltimateCutscene.active();
                if (!on && seen[0] < 0) { seen[0] = kk; log("cut " + name + ": camera control back after " + kk + " client ticks (camera=" + mc.options.getCameraType() + ")"); }
                if (kk == 8 && seen[0] < 0) log("cut " + name + ": STILL in cutscene after 8 ticks");
            }, 1));
        }
        l.add(new Step(null, () -> server(p -> {
            float now = 0; int n = 0;
            for (Mob z : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(60), e -> e.getTags().contains("xw_cut_target"))) { now += z.getHealth(); n++; }
            log("cut " + name + ": targets' total health " + f1(HP[0]) + " -> " + f1(now) + " (" + n + " alive) ultimate entities left: barrage="
                    + p.serverLevel().getEntitiesOfClass(com.xenowarriors.entity.GravityBarrageEntity.class, p.getBoundingBox().inflate(60)).size()
                    + " tornado=" + p.serverLevel().getEntitiesOfClass(com.xenowarriors.entity.BlazingWindEntity.class, p.getBoundingBox().inflate(60)).size()
                    + " xenoCasting=" + com.xenowarriors.skill.XenoBlast.casting(p) + " frozen=" + com.xenowarriors.combat.Frozen.is(p));
        }), name.equals("xeno") ? 200 : 40));
        if (name.equals("xeno")) l.add(new Step(null, () -> server(p -> {
            float now = 0;
            for (Mob z : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(60), e -> e.getTags().contains("xw_cut_target"))) now += z.getHealth();
            log("cut xeno: after its release the blast still fired: targets' total health " + f1(HP[0]) + " -> " + f1(now));
        }), 20));
    }

    private static void cutscenes(Minecraft mc, List<Step> l) {
        cut(mc, l, CharacterType.MIKIKO, SkillManager.ULTIMATE_SLOT, "blazing", 30);
        cut(mc, l, CharacterType.RHALIX, SkillManager.ULTIMATE_SLOT, "barrage", 45);
        cut(mc, l, CharacterType.MIKIKO, SkillManager.SUPER_SLOT, "xeno", 60);
        l.add(new Step(null, () -> server(ReviveTests::clearAll), 10));
    }
}
