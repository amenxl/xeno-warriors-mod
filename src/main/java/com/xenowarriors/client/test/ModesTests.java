package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.gov.HealingPodBlock;
import com.xenowarriors.gov.HealingPods;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

/**
 * UI tests for the game modes (focus "modes..."):
 * modespod   - the healing pod (empty, someone asleep inside, waking with the doors open), a bot and a Gov worker walking to pods.
 * modestower - Tower arena size at floors 1/10/20, kill multiplier, enemy strength per decade, a neutral mob attacking,
 *              the safety cleanup (shortened timer), a character switch refused mid-wave, /xeno tower resetmap.
 * modesmisc  - Journey's healing pod step, the Continue buttons, /xeno journey resetmap, Party Swap.
 * Results are logged as "XW-TEST modes ...".
 */
public final class ModesTests {
    private ModesTests() {}

    private static void log(String s) { UiTest.log("modes " + s); }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        switch (focus) {
            case "modespod" -> pod(mc, l);
            case "modestower" -> tower(mc, l);
            default -> misc(mc, l);
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static final int[] CAM = {-1};

    /** A camera (an invisible armor stand) at x,y,z looking yaw/pitch: spawned on the server, then looked through. */
    private static void cam(Minecraft mc, List<Step> l, java.util.function.Function<ServerPlayer, double[]> where) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            if (CAM[0] >= 0 && p.serverLevel().getEntity(CAM[0]) != null) p.serverLevel().getEntity(CAM[0]).discard();
            double[] w = where.apply(p);
            ArmorStand a = EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(w[0], w[1], w[2], (float) w[3], (float) w[4]); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot((float) w[3]);
            p.serverLevel().addFreshEntity(a); CAM[0] = a.getId();
        }), 12));
        l.add(new Step(null, () -> { if (mc.level.getEntity(CAM[0]) instanceof ArmorStand a) {
            float y = a.getYRot(), x = a.getXRot();
            a.yRotO = y; a.xRotO = x; a.setYHeadRot(y); a.yHeadRotO = y; mc.setCameraEntity(a); } }, 8));
    }

    private static void me(Minecraft mc, List<Step> l) { l.add(new Step(null, () -> mc.setCameraEntity(mc.player), 5)); }

    private static void placePod(ServerLevel lv, BlockPos at, Direction facing) {
        BlockState s = com.xenowarriors.sky.ModBlocks.HEALING_POD.get().defaultBlockState().setValue(HealingPodBlock.FACING, facing);
        lv.setBlock(at, s, 3);
        lv.setBlock(at.above(), s.setValue(HealingPodBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    private static String podState(ServerLevel lv, BlockPos at) {
        BlockState s = lv.getBlockState(at);
        return s.getBlock() instanceof HealingPodBlock ? "open=" + s.getValue(HealingPodBlock.OPEN) + " occupied=" + s.getValue(HealingPodBlock.OCCUPIED) : "no pod";
    }

    private static void survival(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 900, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 900, 0, false, false));
    }

    // ------------------------------------------------------------------------------------------------ the healing pod

    private static void pod(Minecraft mc, List<Step> l) {
        BlockPos[] pods = new BlockPos[3];
        int[] bot = {-1}, npc = {-1};
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.KYRIE); UiTest.hold(null); mc.options.hideGui = true; UiTest.server(p -> {
            survival(p);
            ServerLevel lv = p.serverLevel();
            BlockPos b = p.blockPosition();
            for (int dx = -14; dx <= 14; dx++) for (int dz = -6; dz <= 18; dz++) {
                for (int dy = 0; dy <= 5; dy++) lv.setBlock(b.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                lv.setBlock(b.offset(dx, -1, dz), Blocks.SMOOTH_QUARTZ.defaultBlockState(), 3);
            }
            pods[0] = b.offset(0, 0, 4); pods[1] = b.offset(4, 0, 4); pods[2] = b.offset(-4, 0, 4);
            for (BlockPos q : pods) placePod(lv, q, Direction.NORTH);
            p.teleportTo(b.getX() + 8.5, b.getY(), b.getZ() - 4.5); p.setYRot(0); p.setXRot(0);
            log("pods placed at " + pods[0].toShortString() + " / " + pods[1].toShortString() + " / " + pods[2].toShortString() + " state " + podState(lv, pods[0]));
        }); }, 40));
        // (an armor stand's eyes are ~1.75 above its feet)
        cam(mc, l, p -> new double[]{pods[0].getX() + .5, pods[0].getY() - 0.4, pods[0].getZ() - 4.2, 0, -6});
        l.add(new Step("modes_pod_empty", () -> {}, 3));
        cam(mc, l, p -> new double[]{pods[0].getX() + 4.5, pods[0].getY() + 2.5, pods[0].getZ() - 4.5, 45, 18});
        l.add(new Step("modes_pod_row", () -> {}, 3));
        // the player resting inside (fixed rest, so the pictures land at the right moments)
        l.add(new Step(null, () -> UiTest.server(p -> {
            HealingPods.WAKE = 100;
            p.setHealth(6F);
            String why = HealingPods.start(p.serverLevel(), pods[0], p, 140, () -> log("pod: player stepped out at hp=" + p.getHealth()));
            log("pod start (player) -> " + (why == null ? "ok" : why) + " state " + podState(p.serverLevel(), pods[0]) + " inPod=" + HealingPods.inPod(p));
            log("pod second user refused: " + HealingPods.start(p.serverLevel(), pods[0], EntityType.ZOMBIE.create(p.serverLevel()), -1, null));
        }), 10));
        cam(mc, l, p -> new double[]{pods[0].getX() + .5, pods[0].getY() - 0.1, pods[0].getZ() - 2.0, 0, 2});
        l.add(new Step("modes_pod_sleeping", () -> UiTest.server(p -> log("pod asleep: " + podState(p.serverLevel(), pods[0]) + " asleep=" + HealingPods.asleep(p) + " face=" + com.xenowarriors.client.pod.PodFace.face(mc.player))), 3));
        cam(mc, l, p -> new double[]{pods[0].getX() + 2.6, pods[0].getY() + 0.2, pods[0].getZ() - 2.6, 45, 8});
        l.add(new Step("modes_pod_sleeping_side", () -> {}, 3));
        cam(mc, l, p -> new double[]{pods[0].getX() + .5, pods[0].getY() - 0.1, pods[0].getZ() - 2.3, 0, 2});
        l.add(new Step(null, () -> UiTest.server(p -> log("pod before waking: asleep=" + HealingPods.asleep(p))), 1));
        // wait for the doors (the rest is 80 ticks): poll, then picture
        for (int i = 0; i < 14; i++) l.add(new Step(null, () -> {}, 5));
        l.add(new Step("modes_pod_waking", () -> UiTest.server(p -> log("pod waking: " + podState(p.serverLevel(), pods[0]) + " asleep=" + HealingPods.asleep(p) + " inPod=" + HealingPods.inPod(p) + " face=" + com.xenowarriors.client.pod.PodFace.face(mc.player))), 3));
        l.add(new Step(null, () -> {}, 130));
        l.add(new Step("modes_pod_out", () -> UiTest.server(p -> log("pod after: " + podState(p.serverLevel(), pods[0]) + " inPod=" + HealingPods.inPod(p) + " player z-offset=" + String.format("%.2f", p.getZ() - (pods[0].getZ() + .5)))), 3));
        // a real heal (natural length): health over time, cooldown afterwards
        l.add(new Step(null, () -> UiTest.server(p -> {
            HealingPods.WAKE = 25;
            p.setHealth(5F);
            log("pod natural start -> " + HealingPods.start(p.serverLevel(), pods[2], p, -1, null) + " hp=" + p.getHealth());
        }), 40));
        l.add(new Step(null, () -> UiTest.server(p -> log("pod natural +40t hp=" + p.getHealth() + " " + podState(p.serverLevel(), pods[2]))), 40));
        l.add(new Step(null, () -> UiTest.server(p -> log("pod natural +80t hp=" + p.getHealth() + " " + podState(p.serverLevel(), pods[2]))), 60));
        l.add(new Step(null, () -> UiTest.server(p -> log("pod natural +140t hp=" + p.getHealth() + " inPod=" + HealingPods.inPod(p) + " " + podState(p.serverLevel(), pods[2])
                + " cooldown refuse: " + HealingPods.start(p.serverLevel(), pods[2], p, -1, null))), 5));
        l.add(new Step(null, () -> UiTest.server(p -> { if (HealingPods.inPod(p)) HealingPods.wake(p); }), 40));
        // a bot low on health walks to a pod by itself
        cam(mc, l, p -> new double[]{pods[1].getX() + 9, pods[1].getY() + 5, pods[1].getZ() - 10, 35, 25});
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.teleportTo(pods[0].getX() + .5, pods[0].getY(), pods[0].getZ() - 9.5);
            var b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.LOGAN, null);
            if (b == null) { log("bot spawn failed"); return; }
            b.teleportTo(pods[1].getX() + 9.5, pods[1].getY(), pods[1].getZ() - 9.5);
            b.setHealth(b.getMaxHealth() * 0.25F);
            bot[0] = b.getId();
            log("bot " + b.character().getId() + " hp=" + b.getHealth() + "/" + b.getMaxHealth() + " distToPod=" + String.format("%.1f", Math.sqrt(b.blockPosition().distSqr(pods[1]))));
        }), 30));
        l.add(new Step("modes_bot_walking", () -> UiTest.server(p -> { if (p.serverLevel().getEntity(bot[0]) instanceof Mob b)
            log("bot walking: pos=" + b.blockPosition().toShortString() + " dist=" + String.format("%.1f", Math.sqrt(b.blockPosition().distSqr(pods[1]))) + " inPod=" + HealingPods.inPod(b)); }), 3));
        for (int i = 0; i < 10; i++) {
            if (i == 3) {
                cam(mc, l, p -> new double[]{pods[1].getX() + .5, pods[1].getY() - 0.1, pods[1].getZ() - 2.0, 0, 2});
                l.add(new Step("modes_bot_in_pod", () -> {}, 3));
            }
            l.add(new Step(null, () -> UiTest.server(p -> { if (p.serverLevel().getEntity(bot[0]) instanceof Mob b)
                log("bot t: dist=" + String.format("%.1f", Math.sqrt(b.blockPosition().distSqr(pods[1]))) + " inPod=" + HealingPods.inPod(b) + " hp=" + String.format("%.1f", b.getHealth()) + " " + podState(p.serverLevel(), pods[1])); }), 15));
        }
        // a Gov worker, hurt, uses the third pod
        l.add(new Step(null, () -> UiTest.server(p -> {
            var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(p.serverLevel());
            if (g == null) return;
            g.moveTo(pods[2].getX() - 5.5, pods[2].getY(), pods[2].getZ() - 6.5, 0, 0);
            g.setup(false, 0, pods[2]);
            p.serverLevel().addFreshEntity(g);
            g.setHealth(g.getMaxHealth() * 0.3F);
            npc[0] = g.getId();
            log("gov worker hp=" + g.getHealth() + "/" + g.getMaxHealth());
        }), 20));
        for (int i = 0; i < 8; i++) {
            if (i == 3) {
                cam(mc, l, p -> new double[]{pods[2].getX() + .5, pods[2].getY() - 0.1, pods[2].getZ() - 2.2, 0, 2});
                l.add(new Step("modes_npc_in_pod", () -> {}, 3));
            }
            l.add(new Step(null, () -> UiTest.server(p -> { if (p.serverLevel().getEntity(npc[0]) instanceof Mob g)
                log("gov t: dist=" + String.format("%.1f", Math.sqrt(g.blockPosition().distSqr(pods[2]))) + " inPod=" + HealingPods.inPod(g) + " hp=" + String.format("%.1f", g.getHealth())); }), 15));
        }
        me(mc, l);
        // an old-style pod (no doors/fluid properties set) still works: the default state is closed + empty
        l.add(new Step(null, () -> UiTest.server(p -> log("default pod state: " + com.xenowarriors.sky.ModBlocks.HEALING_POD.get().defaultBlockState())), 5));
    }

    // ------------------------------------------------------------------------------------------------ the Tower

    private static void tower(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> {
            for (int f : new int[]{1, 9, 10, 19, 20, 35, 50, 99, 100, 500, 990, 1000}) {
                float[] s = com.xenowarriors.tower.Tower.waveScale(f);
                log(String.format("tower floor %d: radius=%d mult=x%d enemyHp=x%.2f enemyDmg=x%.2f (before: hp x%.2f dmg x%.2f)", f, com.xenowarriors.tower.Tower.radius(f), com.xenowarriors.tower.Tower.mult(f),
                        1 + s[0], 1 + s[1], 1 + 0.07F * f, 1 + 0.045F * f));
            }
        }, 2));
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); UiTest.hold(null); UiTest.server(p -> {
            survival(p);
            var t = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            t.putBoolean(com.xenowarriors.collection.Collection.SEEN, true);
            p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, t);
        }); mc.options.hideGui = false; }, 30));
        l.add(new Step(null, () -> UiTest.server(com.xenowarriors.tower.Tower::enter), 100));
        l.add(new Step("modes_tower_hud_f1", () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); UiTest.server(p -> log("tower f1: " + com.xenowarriors.tower.Tower.testInfo(p))); }, 3));
        overview(mc, l, "modes_tower_f1");
        enemies(l, "f1");
        // kill multiplier at floor 1
        kill(l, "f1");
        // switching characters mid-wave is refused
        l.add(new Step(null, () -> { com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.SelectCharacterPacket(CharacterType.LEXY)); }, 10));
        l.add(new Step("modes_switch_refused", () -> UiTest.server(p -> log("switch in a Tower wave: still " + com.xenowarriors.character.ModCapabilities.getType(p).getId() + " - reason: " + com.xenowarriors.character.CharacterManager.switchBlocked(p))), 3));
        // a neutral iron golem and an enderman go for you
        int[] ids = {-1, -1};
        l.add(new Step(null, () -> UiTest.server(p -> {
            BlockPos c = com.xenowarriors.tower.Tower.testCenter(p);
            for (Mob m : com.xenowarriors.tower.Tower.testAlive(p)) m.setNoAi(true);
            var g = EntityType.IRON_GOLEM.create(p.serverLevel());
            g.moveTo(p.getX() + 5, p.getY(), p.getZ() + 2, 0, 0); p.serverLevel().addFreshEntity(g); ids[0] = g.getId();
            var e = EntityType.ENDERMAN.create(p.serverLevel());
            e.moveTo(p.getX() - 5, p.getY(), p.getZ() + 2, 0, 0); p.serverLevel().addFreshEntity(e); ids[1] = e.getId();
            com.xenowarriors.tower.Tower.testAdd(p, g); com.xenowarriors.tower.Tower.testAdd(p, e);
            log("neutral spawned: golem target=" + g.getTarget() + " enderman target=" + e.getTarget() + " center=" + (c == null ? "?" : c.toShortString()));
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            var g = (Mob) p.serverLevel().getEntity(ids[0]); var e = (Mob) p.serverLevel().getEntity(ids[1]);
            log("neutral after 1.5s: golem target=" + (g == null || g.getTarget() == null ? "none" : g.getTarget().getName().getString()) + " anger=" + (g instanceof net.minecraft.world.entity.NeutralMob n ? n.getPersistentAngerTarget() : null)
                    + " | enderman target=" + (e == null || e.getTarget() == null ? "none" : e.getTarget().getName().getString()) + " | player=" + p.getUUID());
        }), 40));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); UiTest.server(p -> { p.setYRot(90); }); }, 5));
        l.add(new Step("modes_tower_neutral", () -> UiTest.server(p -> log("neutral: player hp=" + p.getHealth() + " lastHurtBy=" + (p.getLastHurtByMob() == null ? "none" : p.getLastHurtByMob().getType().toShortString()))), 3));
        // safety protocol: nothing fights for (shortened) 10 seconds -> the wave is cleared
        l.add(new Step(null, () -> UiTest.server(p -> {
            com.xenowarriors.tower.Tower.SAFETY_TICKS = 200;
            for (Mob m : com.xenowarriors.tower.Tower.testAlive(p)) { m.setNoAi(true); m.teleportTo(m.getX(), m.getY(), m.getZ()); }
            log("safety before: " + com.xenowarriors.tower.Tower.testInfo(p));
        }), 120));
        l.add(new Step(null, () -> UiTest.server(p -> log("safety at ~6s: " + com.xenowarriors.tower.Tower.testInfo(p))), 110));
        l.add(new Step("modes_tower_safety", () -> UiTest.server(p -> log("safety after: " + com.xenowarriors.tower.Tower.testInfo(p))), 3));
        l.add(new Step(null, () -> UiTest.server(p -> com.xenowarriors.tower.Tower.SAFETY_TICKS = 2 * 60 * 20), 5));
        // reset map: break some floor, rebuild
        l.add(new Step(null, () -> UiTest.server(p -> {
            BlockPos c = com.xenowarriors.tower.Tower.testCenter(p);
            int broken = 0;
            for (int dx = 2; dx <= 6; dx++) for (int dz = 2; dz <= 6; dz++) { p.serverLevel().setBlock(c.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3); p.serverLevel().setBlock(c.offset(dx, -1, dz), Blocks.AIR.defaultBlockState(), 3); broken += 2; }
            p.serverLevel().setBlock(c.offset(16, 2, 0), Blocks.AIR.defaultBlockState(), 3);
            log("resetmap tower: broke " + (broken + 1) + " blocks; holes now=" + holes(p.serverLevel(), c, 16));
        }), 10));
        overview(mc, l, "modes_tower_broken");
        l.add(new Step(null, () -> UiTest.server(p -> {
            int n = com.xenowarriors.tower.Tower.resetMap(p);
            BlockPos c = com.xenowarriors.tower.Tower.testCenter(p);
            log("resetmap tower: rebuilt " + n + " run(s); holes now=" + holes(p.serverLevel(), c, 16) + " wall=" + p.serverLevel().getBlockState(c.offset(17, 2, 0)).getBlock() + " player y=" + p.getY());
        }), 20));
        // floors 10 and 20: the arena grows
        for (int f : new int[]{10, 20}) {
            l.add(new Step(null, () -> UiTest.server(p -> com.xenowarriors.tower.Tower.skipTo(p, f)), 110));
            l.add(new Step("modes_tower_hud_f" + f, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); UiTest.server(p -> log("tower f" + f + ": " + com.xenowarriors.tower.Tower.testInfo(p) + " edge=" + p.serverLevel().getBlockState(com.xenowarriors.tower.Tower.testCenter(p).offset(com.xenowarriors.tower.Tower.radius(f), 0, 0)).getBlock())); }, 3));
            overview(mc, l, "modes_tower_f" + f);
            enemies(l, "f" + f);
        }
        for (int f : new int[]{15, 25}) {
            l.add(new Step(null, () -> UiTest.server(p -> com.xenowarriors.tower.Tower.skipTo(p, f)), 110));
            enemies(l, "f" + f);
        }
        l.add(new Step(null, () -> UiTest.server(p -> com.xenowarriors.tower.Tower.skipTo(p, 21)), 110));
        kill(l, "f21");
        l.add(new Step(null, () -> UiTest.server(p -> com.xenowarriors.tower.Tower.leave(p, "test over")), 20));
    }

    private static int holes(ServerLevel lv, BlockPos c, int r) {
        int n = 0;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) if (dx * dx + dz * dz <= (r - 1) * (r - 1) && lv.getBlockState(c.offset(dx, 0, dz)).isAir()) n++;
        return n;
    }

    private static void overview(Minecraft mc, List<Step> l, String name) {
        cam(mc, l, p -> { BlockPos c = com.xenowarriors.tower.Tower.testCenter(p); return new double[]{c.getX() + .5, c.getY() + 58, c.getZ() + .5, 0, 90}; });
        l.add(new Step(name, () -> mc.options.hideGui = true, 3));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.setCameraEntity(mc.player); }, 5));
    }

    private static void enemies(List<Step> l, String tag) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (Mob m : com.xenowarriors.tower.Tower.testAlive(p)) {
                if (n++ >= 4) break;
                var mh = m.getAttribute(Attributes.MAX_HEALTH); var md = m.getAttribute(Attributes.ATTACK_DAMAGE);
                sb.append(m.getType().toShortString()).append(String.format(" hp %.0f/%.0f (x%.2f)", m.getMaxHealth(), mh == null ? 0 : mh.getBaseValue(), mh == null ? 0 : m.getMaxHealth() / mh.getBaseValue()));
                if (md != null) sb.append(String.format(" dmg %.1f/%.1f (x%.2f)", md.getValue(), md.getBaseValue(), md.getValue() / Math.max(0.01, md.getBaseValue())));
                sb.append(m.getTarget() == null ? " target=none" : " target=" + m.getTarget().getType().toShortString()).append("; ");
            }
            log("tower enemies " + tag + ": " + sb);
        }), 3));
    }

    private static void kill(List<Step> l, String tag) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            int before = com.xenowarriors.tower.Tower.testPending(p);
            List<Mob> alive = com.xenowarriors.tower.Tower.testAlive(p);
            Mob m = alive.stream().filter(com.xenowarriors.collection.Catalog::hostile).findFirst().orElse(null);
            if (m == null) { log("kill " + tag + ": no enemy to kill"); return; }
            m.invulnerableTime = 0;
            m.hurt(p.damageSources().playerAttack(p), 100000F);
            int after = com.xenowarriors.tower.Tower.testPending(p);
            log("kill " + tag + ": " + m.getType().toShortString() + " dead=" + !m.isAlive() + " pending " + before + " -> " + after + " (+" + (after - before) + ", floor mult x" + com.xenowarriors.tower.Tower.mult(com.xenowarriors.tower.Tower.floorOf(p)) + ")");
        }), 10));
    }

    // ------------------------------------------------------------------------------------------------ Journey and Party Swap

    private static void misc(Minecraft mc, List<Step> l) {
        CharacterType[] eight = {CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.KYRIE, CharacterType.LEXY, CharacterType.MAX, CharacterType.TECHNICA, CharacterType.STEVIE, CharacterType.LOGAN};
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); UiTest.hold(null); UiTest.server(p -> {
            survival(p);
            com.xenowarriors.journey.Journey.setCleared(p, 2);
            com.xenowarriors.journey.Journey.open(p);
            for (CharacterType t : eight) com.xenowarriors.journey.Journey.action(p, 1, t.ordinal(), 0);
            com.xenowarriors.journey.Journey.action(p, 2, 0, 0);
            com.xenowarriors.journey.Journey.action(p, 11, 0, 0);
            com.xenowarriors.journey.Journey.action(p, 4, 0, 0);
        }); mc.options.hideGui = false; }, 80));
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> { com.xenowarriors.journey.Journey.testSet(p, CharacterType.MIKIKO, 0.3F, -1); com.xenowarriors.journey.Journey.testSet(p, CharacterType.KYRIE, 0.5F, -1); }); }, 10));
        // the healing pod step
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); UiTest.server(p -> com.xenowarriors.journey.Journey.testJump(p, com.xenowarriors.journey.JourneyRules.HEAL)); }, 20));
        l.add(new Step("modes_journey_pod", () -> { mc.setScreen(null); UiTest.server(p -> log("journey pod: inPod=" + HealingPods.inPod(p) + " asleep=" + HealingPods.asleep(p) + " pos=" + p.blockPosition().toShortString())); }, 3));
        l.add(new Step(null, () -> {}, 100));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
        l.add(new Step("modes_journey_pod_done", () -> { mc.setScreen(null); UiTest.server(p -> log("journey pod done: inPod=" + HealingPods.inPod(p) + " hp=" + p.getHealth() + "/" + p.getMaxHealth())); }, 3));
        // a battle, then the results with Continue
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> com.xenowarriors.journey.Journey.testJump(p, com.xenowarriors.journey.JourneyRules.BATTLE)); }, 30));
        l.add(new Step(null, () -> UiTest.server(p -> { com.xenowarriors.journey.Journey.action(p, 6, CharacterType.MIKIKO.ordinal(), 0); com.xenowarriors.journey.Journey.action(p, 7, 0, 0); }), 100));
        l.add(new Step(null, () -> UiTest.server(p -> com.xenowarriors.journey.Journey.testEnd(p, true)), 30));
        l.add(new Step("modes_journey_results_continue", () -> log("screen after battle: " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())), 3));
        l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.journey.JourneyMenuScreen(false)), 15));
        l.add(new Step("modes_journey_menu_continue", () -> {}, 3));
        // press Continue (where the menu draws it): back to the path
        l.add(new Step(null, () -> {
            if (mc.screen != null) {
                float s = Math.min(mc.screen.width / 400F, mc.screen.height / 225F);
                double ox = (mc.screen.width - 400 * s) / 2, oy = (mc.screen.height - 225 * s) / 2;
                mc.screen.mouseClicked(ox + (246 + 37) * s, oy + (225 - 22 + 8) * s, 0);
            }
        }, 10));
        l.add(new Step("modes_journey_after_continue", () -> log("after Continue: screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())), 3));
        // reset map: break the platform, rebuild
        l.add(new Step(null, () -> UiTest.server(p -> {
            BlockPos c = com.xenowarriors.journey.Journey.testNode(p, -1);
            int broke = 0;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) if (Math.abs(dx) + Math.abs(dz) <= 3 && !(dx == 0 && dz == 0)) { p.serverLevel().setBlock(c.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3); broke++; }
            log("resetmap journey: broke " + broke + " platform blocks at " + c.toShortString() + "; solid now=" + solid(p.serverLevel(), c));
            p.teleportTo(c.getX() + .5, c.getY() + 3, c.getZ() + .5);
        }), 10));
        l.add(new Step(null, () -> UiTest.server(p -> {
            int n = com.xenowarriors.journey.Journey.resetMap(p);
            BlockPos c = com.xenowarriors.journey.Journey.testNode(p, -1);
            log("resetmap journey: rebuilt " + n + "; solid now=" + solid(p.serverLevel(), c) + " player y-platform=" + String.format("%.1f", p.getY() - c.getY()));
        }), 20));
        l.add(new Step(null, () -> UiTest.server(p -> com.xenowarriors.journey.Journey.leave(p, "test over", true)), 40));
        // party swap: a bot is lost, another character joins
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> {
            p.teleportTo(p.server.overworld(), p.getX(), p.getY(), p.getZ(), 0, 0);
            com.xenowarriors.modes.SwitchRules.clearCombat(p);
            com.xenowarriors.bot.PartyBots.add(p, new String[]{"kyrie", "lexy"});
            com.xenowarriors.party.PartySwap.set(p, true);
        }); }, 40));
        l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.party.PartySetupScreen()), 40));
        l.add(new Step("modes_party_swap_toggle", () -> log("party swap client state=" + com.xenowarriors.client.party.PartySetupScreen.swapOn), 3));
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> {
            var bots = com.xenowarriors.bot.PartyBots.bots(p);
            log("party before: " + bots.stream().map(b -> b.character().getId()).toList() + " next pick=" + com.xenowarriors.party.PartySwap.pick(p, CharacterType.KYRIE));
            for (var b : bots) if (b.character() == CharacterType.KYRIE) b.kill();
        }); }, 20));
        l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.party.PartySetupScreen()), 40));
        l.add(new Step("modes_party_swap_after", () -> UiTest.server(p -> log("party after: " + com.xenowarriors.bot.PartyBots.bots(p).stream().map(b -> b.character().getId() + " lv" + b.botLevel()).toList())), 3));
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> {
            for (var b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.character() == CharacterType.LEXY) b.kill();
        }); }, 20));
        l.add(new Step(null, () -> UiTest.server(p -> log("party after 2nd loss within cooldown: " + com.xenowarriors.bot.PartyBots.bots(p).stream().map(b -> b.character().getId()).toList())), 5));
        // swap off: nobody comes in
        l.add(new Step(null, () -> UiTest.server(p -> {
            com.xenowarriors.party.PartySwap.set(p, false);
            for (var b : com.xenowarriors.bot.PartyBots.bots(p)) b.kill();
        }), 20));
        l.add(new Step(null, () -> UiTest.server(p -> log("party with swap off after a loss: " + com.xenowarriors.bot.PartyBots.bots(p).stream().map(b -> b.character().getId()).toList())), 5));
    }

    private static int solid(ServerLevel lv, BlockPos c) {
        int n = 0;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) if (Math.abs(dx) + Math.abs(dz) <= 4 && !lv.getBlockState(c.offset(dx, 0, dz)).isAir()) n++;
        return n;
    }
}
