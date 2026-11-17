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
        int ticks, moving, sliding, snaps, flips, stuck;
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();

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
        l.add(new Step(null, () -> { TRACKS.clear(); mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 2));
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
                int[] s = sum.computeIfAbsent(t.kind, k -> new int[7]);
                s[0] += t.ticks; s[1] += t.moving; s[2] += t.sliding; s[3] += t.snaps; s[4] += t.flips; s[5] += t.stuck; s[6]++;
            }
            StringBuilder bad = new StringBuilder();
            for (var e : sum.entrySet()) {
                int[] s = e.getValue();
                PackTests.log(String.format(java.util.Locale.ROOT, "moves %s (x%d): ticks %d moving %d sliding %d snaps %d twitches %d stuck %d", e.getKey(), s[6], s[0], s[1], s[2], s[3], s[4], s[5]));
                if (s[1] > 0 && s[2] > s[1] / 5) bad.append(e.getKey()).append(" slides ").append(s[2]).append("/").append(s[1]).append("; ");
                if (s[3] > 3 * s[6]) bad.append(e.getKey()).append(" snaps ").append(s[3]).append("; ");
                if (s[4] > 6 * s[6]) bad.append(e.getKey()).append(" twitches ").append(s[4]).append("; ");
                if (s[5] > 40 * s[6]) bad.append(e.getKey()).append(" stuck ").append(s[5]).append("; ");
            }
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
                if (moving && le.walkAnimation.speed() < 0.05F && !le.isPassenger() && le.onGround()) t.sliding++;
                float d = Mth.wrapDegrees(yaw - t.yaw);
                if (Math.abs(d) > 50F && v < 2.0) t.snaps++;
                int sign = Math.abs(d) > 6F ? (d > 0 ? 1 : -1) : 0;
                if (sign != 0 && t.lastSign != 0 && sign != t.lastSign) t.flips++;
                if (sign != 0) t.lastSign = sign;
                if (e instanceof PartyBotEntity && !moving && e.distanceTo(mc.player) > 10F) t.stuck++;
            }
            t.x = e.getX(); t.z = e.getZ(); t.yaw = yaw; t.init = true;
        }
    }
}
