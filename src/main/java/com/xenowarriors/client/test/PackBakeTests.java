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
        // Rhalix's ground combo (8 hits), its heavy, Gravity Burst and the leap
        l.add(new Step(null, () -> UiTest.character(CharacterType.RHALIX), 30));
        l.add(new Step(null, () -> PackTests.server(p -> { p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(0); }), 20));
        com.xenowarriors.combat.ComboStyle R = com.xenowarriors.combat.ComboStyle.RHALIX;
        java.util.List<Object[]> rh = new java.util.ArrayList<>();
        for (int h = 0; h < 8; h++) rh.add(new Object[]{"rhalix_hit" + h, h, false});
        rh.add(new Object[]{"rhalix_heavy", 0, true});
        rh.add(new Object[]{"rhalix_burst", com.xenowarriors.combat.ComboStyle.BURST, false});
        rh.add(new Object[]{"rhalix_leap", com.xenowarriors.combat.ComboStyle.LEAP, false});
        for (Object[] r : rh) {
            String name = (String) r[0]; int hit = (Integer) r[1]; boolean heavy = (Boolean) r[2];
            int dur = R.duration(hit, heavy);
            l.add(new Step(null, () -> { mc.player.setXRot(0); mc.player.xRotO = 0;
                com.xenowarriors.client.combo.ComboClient.forceForTest(mc.player, R, hit, heavy); PoseRecorder.start(mc.player); }, dur + 4));
            l.add(new Step(null, () -> dump(name, PoseRecorder.stop()), 8));
        }
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            PackTests.log("bake: done"); }, 2));
    }

    /**
     * (focus "packbakecheck") Each rebuilt move side by side with the original: the original in vanilla mode, then the
     * Epic Fight version in battle mode, both shot from the front at the strike (and once a third of the way in).
     */
    static void check(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> { PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p);
            p.getInventory().selected = 0; p.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
            p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(0); });
            mc.options.hideGui = true; mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 30));
        for (String name : new String[]{"jab_r", "jab_l", "chong_chui_r", "ding_zhou_r", "tiao_zhang_l", "shuang_zhang", "tie_shan_kao", "flip_kick", "kick_roundhouse"}) {
            Move m = Move.valueOf(name.toUpperCase(java.util.Locale.ROOT));
            int at = Math.round(m.duration * 0.4F);
            l.add(new Step(null, () -> battle(false), 12));
            // (a named step's shot is taken once its wait is over)
            l.add(new Step("pack_bake_" + name + "_old", () -> { mc.player.setXRot(0); com.xenowarriors.client.mikiko.MikikoAnimations.forceMoveForTest(mc.player, m); }, at));
            l.add(new Step(null, () -> {}, m.duration + 6));
            l.add(new Step(null, () -> battle(true), 15));
            l.add(new Step("pack_bake_" + name + "_ef", () -> PackTests.server(p -> {
                var acc = com.xenowarriors.compat.epicfight.EfAnimations.MIKIKO.get(name);
                var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(p);
                if (acc != null && patch != null) patch.playAnimationSynchronized(acc, 0F);
                else PackTests.log("bake check: no animation " + name);
            }), at));
            l.add(new Step(null, () -> PackTests.log("bake check " + name + ": " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)), m.duration + 6));
        }
        // the real thing: Mikiko's bare-hand combo in battle mode is now his own moves, drawn by Epic Fight
        l.add(new Step(null, () -> PackTests.server(p -> {
            var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(p);
            var c = patch == null ? null : patch.getHoldingItemCapability(net.minecraft.world.InteractionHand.MAIN_HAND).getAutoAttackMotion(patch);
            PackTests.check("bake: Mikiko's battle-mode combo is his own rebuilt moves", c != null && !c.isEmpty()
                    && c.get(0) == com.xenowarriors.compat.epicfight.EfAnimations.MIKIKO.get("jab_r"), "combo " + c);
        }), 3));
        l.add(new Step(null, () -> { battle(false); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
    }
}
