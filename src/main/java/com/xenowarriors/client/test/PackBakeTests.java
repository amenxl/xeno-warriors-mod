package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.efbake.PoseRecorder;
import com.xenowarriors.client.mikiko.Move;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * (Pack test, focus "packbake") Records our own animations so they can be rebuilt as Epic Fight animations: each
 * one is played on the player (Epic Fight's vanilla mode, third person) and every frame of the pose is written to the
 * log as "XW-BAKE name frames" (tools/xw/ef/bake.py turns them into Epic Fight json).
 */
final class PackBakeTests {
    private PackBakeTests() {}

    private static void battle(boolean on) {
        try {
            var p = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
            if (p == null) return;
            if (on && !p.isEpicFightMode()) p.toEpicFightMode(true);
            if (!on && p.isEpicFightMode()) p.toVanillaMode(true);
        } catch (Throwable t) { PackTests.log("bake: battle " + t); }
    }

    static void dump(String name, List<float[]> frames) {
        StringBuilder sb = new StringBuilder();
        for (float[] f : frames) {
            sb.append('[');
            for (int i = 0; i < f.length; i++) { if (i > 0) sb.append(','); sb.append(Math.round(f[i] * 10000F) / 10000F); }
            sb.append(']');
        }
        com.xenowarriors.XenoWarriors.LOGGER.info("XW-BAKE {} {} {}", name, frames.size(), sb);
    }

    /** Mikiko's fighting moves (bare hands and blade), each played once from the start. */
    static final Move[] MIKIKO = {Move.JAB_R, Move.JAB_L, Move.HOOK_R, Move.UPPERCUT_L, Move.CHONG_CHUI_R, Move.DING_ZHOU_R, Move.TIAO_ZHANG_L,
            Move.SHUANG_ZHANG, Move.TIE_SHAN_KAO, Move.FLIP_KICK, Move.KICK_FRONT, Move.KICK_ROUNDHOUSE, Move.KICK_SPIN, Move.KICK_SIDE,
            Move.KNEE_FLYING, Move.LUNGE_PUNCH_R, Move.LUNGE_PUNCH_L};

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> { battle(false); PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p);
            p.getInventory().selected = 0; p.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
            p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(0); });
            mc.options.hideGui = true; mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 30));
        for (Move m : MIKIKO) {
            l.add(new Step(null, () -> { mc.player.setXRot(0); mc.player.xRotO = 0;
                com.xenowarriors.client.mikiko.MikikoAnimations.forceMoveForTest(mc.player, m); PoseRecorder.start(mc.player); }, m.duration + 3));
            l.add(new Step(null, () -> dump("mikiko_" + m.name().toLowerCase(java.util.Locale.ROOT), PoseRecorder.stop()), 6));
        }
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            PackTests.log("bake: done"); }, 2));
    }
}
