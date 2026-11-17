package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (focus "packmoves") Do bots and NPCs move right? Bots follow you round a course (you hop to a new spot every few
 * seconds), townsfolk, Government soldiers and Sky Rebels wander. Every tick, for everyone (as your client sees
 * them): turning (snaps of 50+ degrees in a tick), twitching (the body turning back and forth tick after tick),
 * sliding (moving along without the walk animation) and standing stuck far from you (bots). Pictures along the way.
 */
final class PackMoveTests {
    private PackMoveTests() {}

    private static final class Track {
        String kind; double x, z; float yaw; int lastSign; boolean init;
        int ticks, moving, sliding, snaps, flips, stuck, far;
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static final List<String> SNAPLOG = new java.util.ArrayList<>();

    static void build(Minecraft mc, List<Step> l) {
        double[] home = new double[3];
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            for (PartyBotEntity b : PartyBots.bots(p)) b.discard();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getAbilities().flying = false; p.onUpdateAbilities();
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 120, 4, false, false));
            home[0] = p.getX(); home[1] = p.getY(); home[2] = p.getZ();
            int i = 0;
            for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.KYRIE, CharacterType.MAX}) {
                PartyBotEntity b = PartyBots.spawn(p, t, null);
                if (b == null) continue;
                b.setMode(PartyBotEntity.NEUTRAL);
                b.moveTo(p.getX() - 10 + i * 3, p.getY(), p.getZ() - 8, 0F, 0F);
                i++;
            }
            var lvl = p.serverLevel();
            Mob[] npcs = {
                    com.xenowarriors.npc.ModNpcs.HUMAN.get().create(lvl), com.xenowarriors.npc.ModNpcs.HUMAN.get().create(lvl),
                    com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(lvl), com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(lvl)};
            int k = 0;
            for (Mob m : npcs) {
                if (m == null) continue;
                if (m instanceof com.xenowarriors.gov.GovAgentEntity g) g.setup(false, 1, p.blockPosition());
                if (m instanceof com.xenowarriors.sr.SrMemberEntity s) s.setup(1, p.blockPosition());
                m.moveTo(p.getX() + 6 + k * 2, p.getY(), p.getZ() + 6, k * 90F, 0F);
                m.addTag("xw_pack"); m.setPersistenceRequired();
                lvl.addFreshEntity(m);
                k++;
            }
        }), 40));
        l.add(new Step(null, () -> { TRACKS.clear(); SNAPLOG.clear(); mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 2));
        // the course: you hop to a new spot every 3 seconds, the bots follow; NPCs go about their business
        double[][] course = {{0, 0}, {14, 0}, {14, 14}, {0, 14}, {-12, 6}, {0, 0}};
        for (int leg = 0; leg < course.length; leg++) {
            double[] at = course[leg];
            int lg = leg;
            l.add(new Step(null, () -> PackTests.server(p -> p.teleportTo(home[0] + at[0], home[1], home[2] + at[1])), 1));
            for (int t = 0; t < 60; t++) {
                int tt = t;
                l.add(new Step(tt == 30 ? "moves_leg" + lg : null, () -> { sample(mc); if (tt % 20 == 19) PackTests.server(PackMoveTests::botState); }, 1));
            }
        }
        // flying: you take off and fly the course a few blocks up - the winged bots fly along
        l.add(new Step(null, () -> PackTests.server(p -> { p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); p.getAbilities().flying = true; p.onUpdateAbilities(); }), 5));
        for (int leg = 0; leg < 3; leg++) {
            double[] at = course[leg + 1];
            int lg = leg;
            l.add(new Step(null, () -> PackTests.server(p -> p.teleportTo(home[0] + at[0], home[1] + 7, home[2] + at[1])), 1));
            for (int t = 0; t < 60; t++) {
                int tt = t;
                l.add(new Step(tt == 30 ? "moves_fly_leg" + lg : null, () -> {
                    if (mc.player != null && !mc.player.getAbilities().flying) { mc.player.getAbilities().flying = true; mc.player.onUpdateAbilities(); }   // (stay up)
                    sample(mc); if (tt % 20 == 19) PackTests.server(PackMoveTests::botState);
                }, 1));
            }
            // end of the leg: you're still up there, and every bot has kept up (within 16 blocks)
            l.add(new Step(null, () -> PackTests.server(p -> {
                StringBuilder far = new StringBuilder();
                for (PartyBotEntity b : PartyBots.bots(p)) if (b.distanceTo(p) > 16) far.append(b.charKey()).append(' ').append(String.format(java.util.Locale.ROOT, "%.1f", b.distanceTo(p))).append("; ");
                double up = p.getY() - home[1];
                PackTests.check("moves fly leg " + lg + ": you stay in the air and the bots keep up", up > 4 && far.length() == 0,
                        String.format(java.util.Locale.ROOT, "you %.1f up, flying %s; far: %s", up, p.getAbilities().flying, far));
            }), 1));
        }
        l.add(new Step(null, () -> PackTests.server(p -> { p.getAbilities().flying = false; p.onUpdateAbilities(); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.teleportTo(home[0], home[1], home[2]); }), 40));
        // in battle mode (Epic Fight draws the bots) the same again
        l.add(new Step(null, () -> {
            try { var pp = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch(); if (pp != null) pp.toEpicFightMode(true); } catch (Throwable ignored) {}
        }, 5));
        for (int leg = 0; leg < 3; leg++) {
            double[] at = course[leg + 1];
            int lg = leg;
            l.add(new Step(null, () -> PackTests.server(p -> p.teleportTo(home[0] + at[0], home[1], home[2] + at[1])), 1));
            for (int t = 0; t < 60; t++) {
                int tt = t;
                l.add(new Step(tt == 30 ? "moves_ef_leg" + lg : null, () -> sample(mc), 1));
            }
        }
        l.add(new Step(null, () -> {
            Map<String, int[]> sum = new java.util.TreeMap<>();
            for (Track t : TRACKS.values()) {
                int[] s = sum.computeIfAbsent(t.kind, k -> new int[8]);
                s[0] += t.ticks; s[1] += t.moving; s[2] += t.sliding; s[3] += t.snaps; s[4] += t.flips; s[5] += t.stuck; s[6]++; s[7] += t.far;
            }
            StringBuilder bad = new StringBuilder();
            for (var e : sum.entrySet()) {
                int[] s = e.getValue();
                PackTests.log(String.format(java.util.Locale.ROOT, "moves %s (x%d): ticks %d moving %d sliding %d snaps %d twitches %d stuck %d far %d", e.getKey(), s[6], s[0], s[1], s[2], s[3], s[4], s[5], s[7]));
                if (s[1] > 0 && s[2] > s[1] / 5) bad.append(e.getKey()).append(" slides ").append(s[2]).append("/").append(s[1]).append("; ");
                if (s[3] > 3 * s[6]) bad.append(e.getKey()).append(" snaps ").append(s[3]).append("; ");
                if (s[4] > 6 * s[6]) bad.append(e.getKey()).append(" twitches ").append(s[4]).append("; ");
                if (s[5] > 40 * s[6]) bad.append(e.getKey()).append(" stuck ").append(s[5]).append("; ");
            }
            for (String sl : SNAPLOG) PackTests.log("moves snap: " + sl);
            PackTests.check("moves: bots and NPCs move without sliding, snapping, twitching or getting stuck", bad.length() == 0, bad.toString());
            try { var pp = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch(); if (pp != null) pp.toVanillaMode(true); } catch (Throwable ignored) {}
            PackTests.server(p -> { for (PartyBotEntity b : PartyBots.bots(p)) b.discard(); PackTests.clear(p); });
            mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
        }, 5));
    }

    /** (server) where each bot is and what it's up to. */
    private static void botState(net.minecraft.server.level.ServerPlayer p) {
        for (PartyBotEntity b : PartyBots.bots(p)) {
            var g = b.goalSelector.getRunningGoals().map(w -> w.getGoal().getClass().getSimpleName()).toList();
            PackTests.log(String.format(java.util.Locale.ROOT, "moves state %s: dist %.1f dy %.1f ground %s noGravity %s nav %s target %s goals %s",
                    b.character().getId(), b.distanceTo(p), b.getY() - p.getY(), b.onGround(), b.isNoGravity(), b.getNavigation().isDone() ? "done" : "moving",
                    b.getTarget() == null ? "-" : b.getTarget().getType().toShortString(), g));
        }
    }

    private static void sample(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || e.distanceToSqr(mc.player) > 48 * 48) continue;
            String kind = e instanceof PartyBotEntity b ? "bot " + b.character().getId()
                    : e instanceof com.xenowarriors.gov.GovAgentEntity ? "gov soldier"
                    : e instanceof com.xenowarriors.sr.SrMemberEntity ? "sky rebel"
                    : e.getType() == com.xenowarriors.npc.ModNpcs.HUMAN.get() ? "townsfolk" : null;
            if (kind == null) continue;
            Track t = TRACKS.computeIfAbsent(e.getId(), k -> new Track());
            t.kind = kind;
            double dx = e.getX() - t.x, dz = e.getZ() - t.z;
            float yaw = le.yBodyRot;
            if (t.init) {
                t.ticks++;
                double v = Math.sqrt(dx * dx + dz * dz);
                boolean moving = v > 0.04 && v < 2.0;                          // (not a teleport)
                if (moving) t.moving++;
                if (moving && le.walkAnimation.speed() < 0.05F && !le.isPassenger() && le.onGround() && !le.isNoGravity()) t.sliding++;
                float d = Mth.wrapDegrees(yaw - t.yaw);
                if (Math.abs(d) > 50F && v < 2.0) {
                    t.snaps++;
                    if (SNAPLOG.size() < 40) SNAPLOG.add(String.format(java.util.Locale.ROOT, "%s %.0f deg %s head %.0f yRot %.0f target %s",
                            kind, d, moving ? "walking" : "standing", Mth.wrapDegrees(le.yHeadRot - yaw), Mth.wrapDegrees(le.getYRot() - yaw),
                            e instanceof Mob mob && mob.getTarget() != null ? "yes" : "-"));
                }
                int sign = Math.abs(d) > 6F ? (d > 0 ? 1 : -1) : 0;
                if (sign != 0 && t.lastSign != 0 && sign != t.lastSign) t.flips++;
                if (sign != 0) t.lastSign = sign;
                if (e instanceof PartyBotEntity && !moving && e.distanceTo(mc.player) > 17F) t.stuck++;
                if (e instanceof PartyBotEntity && e.distanceTo(mc.player) > 24F) t.far++;
            }
            t.x = e.getX(); t.z = e.getZ(); t.yaw = yaw; t.init = true;
        }
    }

    /** (focus "packbothold") Talking to a bot: it stands still until you're done or you're kicked out. */
    static void hold(Minecraft mc, List<Step> l) {
        int[] bid = {-1}; double[] at = new double[3];
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            for (PartyBotEntity b : PartyBots.bots(p)) b.discard();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.MAX, null);
            if (b != null) { b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0F, 0F); bid[0] = b.getId(); }
        }), 40));
        // you talk to it, then step 4 blocks back (6 from it) (it would normally follow / wander): it stays put
        l.add(new Step(null, () -> PackTests.server(p -> {
            if (p.serverLevel().getEntity(bid[0]) instanceof PartyBotEntity b) { PartyBots.interact(b, p); at[0] = b.getX(); at[1] = b.getY(); at[2] = b.getZ(); p.teleportTo(p.getX() - 4, p.getY(), p.getZ()); }
        }), 100));
        l.add(new Step("bothold_held", () -> PackTests.server(p -> {
            var b = p.serverLevel().getEntity(bid[0]) instanceof PartyBotEntity x ? x : null;
            double moved = b == null ? -1 : Math.sqrt(b.distanceToSqr(at[0], at[1], at[2]));
            PackTests.check("bothold: it stands still while you talk to it", b != null && b.held() && moved < 0.5, "held " + (b != null && b.held()) + " moved " + String.format("%.2f", moved));
            if (b != null) PartyBots.action(p, b.getId(), PartyBots.ACT_HOLD, 0);   // (menu closed)
        }), 100));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var b = p.serverLevel().getEntity(bid[0]) instanceof PartyBotEntity x ? x : null;
            double moved = b == null ? -1 : Math.sqrt(b.distanceToSqr(at[0], at[1], at[2]));
            PackTests.check("bothold: closing its menu lets it go", b != null && !b.held(), "held " + (b != null && b.held()) + " moved " + String.format("%.2f", moved));
            if (b != null) PartyBots.openBag(p, b);                                 // its bag now, then walk off
            p.teleportTo(p.getX() - 14, p.getY(), p.getZ());
        }), 10));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var b = p.serverLevel().getEntity(bid[0]) instanceof PartyBotEntity x ? x : null;
            PackTests.check("bothold: walking off kicks you out of its bag and lets it go", b != null && !b.held() && !(p.containerMenu instanceof com.xenowarriors.bot.BotMenu),
                    "held " + (b != null && b.held()) + " menu " + p.containerMenu.getClass().getSimpleName());
            p.teleportTo(b.getX() - 2, b.getY(), b.getZ());
            PartyBots.interact(b, p);
        }), 10));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var b = p.serverLevel().getEntity(bid[0]) instanceof PartyBotEntity x ? x : null;
            if (b != null) b.hurt(p.serverLevel().damageSources().generic(), 1F);
        }), 5));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var b = p.serverLevel().getEntity(bid[0]) instanceof PartyBotEntity x ? x : null;
            PackTests.check("bothold: getting hit puts it back in the fight", b != null && !b.held(), "held " + (b != null && b.held()));
            for (PartyBotEntity x : PartyBots.bots(p)) x.discard();
            mc.setScreen(null);
        }), 5));
    }
}
