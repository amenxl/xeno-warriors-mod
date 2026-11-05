package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * UI tests for batch 3 (focus "batch3", "efbatch3" for the wings under Epic Fight, "batch3tacz" for guns; extra words
 * after "batch3" pick parts, e.g. "batch3hud"). Every result is an "XW-TEST batch3 ..." line.
 */
public final class Batch3Tests {
    private Batch3Tests() {}

    public static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST batch3 " + s); }

    static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("XW-TEST batch3", t); } });
    }

    static void character(MikikoTests.Steps l, CharacterType t) {
        l.add(null, () -> server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, t)), 40);
    }

    static void hold(ServerPlayer p, ItemStack s) {
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, s);
    }

    static void view(Minecraft mc, CameraType cam, float yaw, float pitch) {
        mc.setScreen(null);
        mc.options.hideGui = true;
        mc.options.setCameraType(cam);
        mc.player.setXRot(pitch); mc.player.setYRot(yaw); mc.player.yBodyRot = yaw; mc.player.setYHeadRot(yaw);
    }

    public static void run(Minecraft mc, String focus, MikikoTests.Steps l) {
        boolean ef = focus.startsWith("ef");
        String f = focus.replaceFirst("^ef", "").replaceFirst("^batch3", "").replace("tacz", "");
        boolean all = f.isEmpty();
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 5);
        if (ef || f.contains("wings")) wings(mc, l, ef);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); log("done"); }, 5);
    }

    // ------------------------------------------------------------------ 6. Rhalix's wings (under Epic Fight)

    private static void wings(Minecraft mc, MikikoTests.Steps l, boolean ef) {
        character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> { hold(p, ItemStack.EMPTY); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); }), 10);
        String[] anims = ef ? new String[]{"none", "ef:FIST_AUTO1", "ef:BIPED_ROLL_FORWARD", "ef:BIPED_STEP_LEFT", "pole1"} : new String[]{"none"};
        for (String a : anims) {
            String n = a.replace("ef:", "").toLowerCase(java.util.Locale.ROOT);
            l.add(null, () -> {
                com.xenowarriors.client.RhalixWingsClient.set(mc.player.getUUID(), true, false);
                view(mc, CameraType.THIRD_PERSON_BACK, 0F, 12F);
                if (ef) com.xenowarriors.compat.epicfight.EfTest.play(mc.player, a);
            }, a.equals("none") ? 20 : 4);
            l.add("batch3_wings_" + n + "_a", () -> { com.xenowarriors.client.RhalixWingsClient.set(mc.player.getUUID(), true, false);
                log("wings " + n + " a grow=" + com.xenowarriors.client.RhalixWingsClient.grow(mc.player, 0F)
                        + (ef ? " " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player) : "")); }, 1);
            l.add(null, () -> {}, 5);
            l.add("batch3_wings_" + n + "_b", () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT), 1);
            l.add(null, () -> { if (ef) com.xenowarriors.compat.epicfight.EfTest.play(mc.player, "none"); }, 20);
        }
    }
}
