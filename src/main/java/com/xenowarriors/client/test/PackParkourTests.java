package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotParkour;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * (focus "packparkour") Bots doing ParCool-style parkour: a course on the test floor - a low wall to vault, a 2-high wall
 * to climb, a 3-high wall to wall-jump up, a 3-block drop to roll off, a gap to leap - with you standing past each one
 * (up on the walls) so the bot (Stevie: no wings) has to get to you. Each checks the bot got there and the move started;
 * at the end, which of ParCool's poses were put on it.
 */
final class PackParkourTests {
    private PackParkourTests() {}

    private static BlockPos base() { return BlockPos.containing(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2]); }

    private static void fill(ServerPlayer p, int x0, int y0, int z0, int x1, int y1, int z1, boolean solid) {
        var l = p.serverLevel(); BlockPos b = base();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
            l.setBlock(b.offset(x, y, z), solid ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
    }

    private static PartyBotEntity bot(ServerPlayer p, int[] id) {
        return p.serverLevel().getEntity(id[0]) instanceof PartyBotEntity b ? b : null;
    }

    /** One stretch of the course: build it, put the bot and you in place, give it time, check. */
    private static void stage(Minecraft mc, List<Step> l, int[] id, String name, java.util.function.Consumer<ServerPlayer> build, double[] botAt, double[] youAt, float youYaw,
                              int ticks, int move, java.util.function.BiPredicate<ServerPlayer, PartyBotEntity> got) {
        int[] before = new int[1];
        float[] hp = new float[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getAbilities().flying = false; p.onUpdateAbilities();
            build.accept(p);
        }), 4));
        l.add(new Step(null, () -> PackTests.server(p -> {
            BlockPos b = base();
            p.connection.teleport(b.getX() + 0.5 + youAt[0], b.getY() + youAt[1], b.getZ() + 0.5 + youAt[2], youYaw, 20F);
            PartyBotEntity bt = bot(p, id);
            if (bt == null) return;
            bt.setTarget(null);
            bt.getNavigation().stop();
            bt.moveTo(b.getX() + 0.5 + botAt[0], b.getY() + botAt[1], b.getZ() + 0.5 + botAt[2], 0F, 0F);
            bt.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            bt.fallDistance = 0;
            bt.setHealth(bt.getMaxHealth());
            hp[0] = bt.getHealth();
            before[0] = count(move);
        }), 2));
        for (int t = 0; t < ticks; t += 2) {
            int tt = t;
            l.add(new Step(tt == ticks / 3 ? "parkour_" + name : null, () -> {
                if (tt % 10 == 0) PackTests.server(p -> {
                    PartyBotEntity bt = bot(p, id);
                    if (bt != null) PackTests.log(String.format(java.util.Locale.ROOT, "parkour %s t%d: bot at %.1f %.1f %.1f move %s (seen %s)", name, tt,
                            bt.getX() - base().getX() - 0.5, bt.getY() - base().getY(), bt.getZ() - base().getZ() - 0.5,
                            BotParkour.NAMES[BotParkour.codeOf(bt.parkour())], com.xenowarriors.client.compat.ParcoolBots.APPLIED));
                });
            }, 2));
        }
        l.add(new Step(null, () -> PackTests.server(p -> {
            PartyBotEntity bt = bot(p, id);
            int n = count(move) - before[0];
            boolean there = bt != null && got.test(p, bt);
            PackTests.check("parkour " + name + ": the bot " + BotParkour.NAMES[move] + "s and gets there", bt != null && n > 0 && there,
                    String.format(java.util.Locale.ROOT, "%s x%d; bot at %.1f %.1f %.1f; hp %.1f of %.1f", BotParkour.NAMES[move], n,
                            bt == null ? 0 : bt.getX() - base().getX() - 0.5, bt == null ? 0 : bt.getY() - base().getY(), bt == null ? 0 : bt.getZ() - base().getZ() - 0.5,
                            bt == null ? 0 : bt.getHealth(), hp[0]));
            if (move == BotParkour.ROLL && bt != null)
                PackTests.check("parkour roll: no fall damage", bt.getHealth() >= hp[0] - 0.01F, "hp " + bt.getHealth() + " of " + hp[0]);
        }), 2));
    }

    /** Starts of this move (either side, for the vault and the wall jump). */
    private static int count(int move) {
        int n = BotParkour.STARTED[move];
        if (move == BotParkour.VAULT_R) n += BotParkour.STARTED[BotParkour.VAULT_L];
        if (move == BotParkour.WALLJUMP_R) n += BotParkour.STARTED[BotParkour.WALLJUMP_L];
        return n;
    }

    static void build(Minecraft mc, List<Step> l) {
        int[] id = {-1};
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.STEVIE, null);
            if (b != null) {
                b.setMode(PartyBotEntity.NEUTRAL);
                b.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 600, 1, false, false));
                id[0] = b.getId();
            }
            PackTests.log("parkour: ParCool " + BotParkour.on() + ", bot " + (b == null ? "-" : b.character() + " flier " + b.canFly()));
        }), 30));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 2));
        // a low wall it runs at (you're well past it): vault
        stage(mc, l, id, "vault", p -> fill(p, -4, 0, 5, 4, 0, 5, true),
                new double[]{0, 0, -4}, new double[]{0, 0, 14}, 180F, 70, BotParkour.VAULT_R,
                (p, b) -> b.getZ() - base().getZ() > 6);
        // a 2-high wall, you on top of it: grabs the edge and climbs up
        stage(mc, l, id, "climb", p -> fill(p, -5, 0, 4, 5, 1, 8, true),
                new double[]{0, 0, -2}, new double[]{0, 2, 7}, 180F, 90, BotParkour.CLIMB,
                (p, b) -> b.getY() - base().getY() > 1.9);
        // a 3-high wall: jumps, kicks off it, grabs the edge, climbs up
        stage(mc, l, id, "walljump", p -> fill(p, -5, 0, 4, 5, 2, 8, true),
                new double[]{0, 0, -2}, new double[]{0, 3, 7}, 180F, 100, BotParkour.WALLJUMP_R,
                (p, b) -> b.getY() - base().getY() > 2.9);
        // standing on a 3-high block, you down below: drops off and rolls
        stage(mc, l, id, "roll", p -> fill(p, -1, 0, -1, 1, 2, 1, true),
                new double[]{0, 3, 0}, new double[]{0, 0, 9}, 180F, 80, BotParkour.ROLL,
                (p, b) -> b.getY() - base().getY() < 0.5 && b.onGround());
        // a two-block gap in the floor between it and you: leaps across
        stage(mc, l, id, "leap", p -> { fill(p, -16, -1, 3, 16, -1, 4, false); fill(p, -16, -6, 3, 16, -6, 4, true); },
                new double[]{0, 0, -2}, new double[]{0, 0, 10}, 180F, 80, BotParkour.LEAP,
                (p, b) -> b.getZ() - base().getZ() > 5 && b.getY() - base().getY() > -0.5);
        l.add(new Step("parkour_end", () -> {
            var a = com.xenowarriors.client.compat.ParcoolBots.APPLIED;
            boolean all = a.contains("SpeedVaultAnimator") && a.contains("ClingToCliffAnimator") && a.contains("ClimbUpAnimator")
                    && a.contains("WallJumpAnimator") && a.contains("RollAnimator") && a.contains("CatLeapAnimator");
            PackTests.check("parkour: the bot's body shows ParCool's poses (vault, cling, climb up, wall jump, roll, cat leap)", all, "poses " + a);
            PackTests.log("parkour moves started: " + java.util.Arrays.toString(BotParkour.STARTED));
            var ef = com.xenowarriors.client.compat.epicfight.EfBotBodies.PARKOUR;
            PackTests.check("parkour: on Epic Fight's body it plays the Epic Fight x ParCool animations", ef.contains("BIPED_CLIMB_UP") && ef.contains("BIPED_ROLL_FORWARD"), "played " + ef);
            PackTests.server(p -> { PackTests.arena(p); for (PartyBotEntity b : PartyBots.bots(p)) b.discard(); });
            mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
        }, 5));
    }
}
