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

    /** An invisible armor stand to film from, at this offset from the player, looking at them. */
    static void camera(Minecraft mc, MikikoTests.Steps l, double dx, double dy, double dz) {
        int[] id = {-1};
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz)), pitch = (float) Math.toDegrees(Math.atan2(dy + 0.7, Math.sqrt(dx * dx + dz * dz)));   // (the stand's eyes are 1.8 up)
        l.add(null, () -> server(p -> {
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, p.getBoundingBox().inflate(40), a -> a.getTags().contains("xw_b3_cam")).forEach(a -> a.discard());
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + dx, p.getY() + dy, p.getZ() + dz, yaw, pitch); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(yaw);
            a.addTag("xw_b3_cam");
            p.serverLevel().addFreshEntity(a); id[0] = a.getId();
        }), 15);
        l.add(null, () -> {
            if (mc.level.getEntity(id[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(pitch); a.xRotO = pitch; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a);
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true;
        }, 5);
    }

    static void uncamera(Minecraft mc, MikikoTests.Steps l) {
        l.add(null, () -> { mc.setCameraEntity(mc.player); server(p -> p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, p.getBoundingBox().inflate(40), a -> a.getTags().contains("xw_b3_cam")).forEach(a -> a.discard())); }, 3);
    }

    private static void wings(Minecraft mc, MikikoTests.Steps l, boolean ef) {
        character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> {
            hold(p, ItemStack.EMPTY); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var c = p.blockPosition();
            for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 9; dz++) for (int dy = 0; dy <= 4; dy++) p.serverLevel().setBlock(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            p.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5); p.setYRot(0F); p.setYHeadRot(0F); p.yBodyRot = 0F;
        }), 10);
        boolean wasWhole = com.xenowarriors.client.ClientSettings.efWholeBattleMode();
        if (ef) l.add(null, () -> com.xenowarriors.client.ClientSettings.setEfWholeBattleMode(true), 2);   // (Epic Fight draws him all the time, its own moves too)
        String[] anims = ef ? new String[]{"none", "ef:FIST_AUTO1", "ef:BIPED_ROLL_FORWARD", "ef:BIPED_STEP_LEFT", "pole1"} : new String[]{"none"};
        double[][] cams = {{-2.6, 0.0, -2.6}, {4.2, -0.4, 1.2}};               // behind his left shoulder; his right side
        for (int ci = 0; ci < cams.length; ci++) {
            final int cam = ci;
            camera(mc, l, cams[ci][0], cams[ci][1], cams[ci][2]);
            for (String a : anims) {
                String n = a.replace("ef:", "").toLowerCase(java.util.Locale.ROOT);
                l.add(null, () -> {
                    server(p -> { p.teleportTo(Math.floor(p.getX()) + 0.5, p.getY(), Math.floor(p.getZ()) + 0.5); p.setYRot(0F); p.setYHeadRot(0F); p.yBodyRot = 0F; });
                    mc.player.setYRot(0F); mc.player.yBodyRot = 0F; mc.player.setYHeadRot(0F);
                    com.xenowarriors.client.RhalixWingsClient.set(mc.player.getUUID(), true, false);
                }, 8);
                l.add(null, () -> { if (ef) com.xenowarriors.compat.epicfight.EfTest.play(mc.player, a); }, 2);
                for (int k = 0; k < 3; k++) {
                    String shot = "batch3_wings_c" + cam + "_" + n + "_" + k;
                    l.add(shot, () -> { com.xenowarriors.client.RhalixWingsClient.set(mc.player.getUUID(), true, false);
                        log("wings " + shot + " grow=" + com.xenowarriors.client.RhalixWingsClient.grow(mc.player, 0F)
                                + (ef ? " " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player) + " efWingDraws=" + efWingDraws() : "")); }, 3);
                }
                l.add(null, () -> { if (ef) com.xenowarriors.compat.epicfight.EfTest.play(mc.player, "none"); }, 16);
            }
        }
        uncamera(mc, l);
        if (ef) l.add(null, () -> com.xenowarriors.client.ClientSettings.setEfWholeBattleMode(wasWhole), 2);
    }

    /** Epic Fight's wing draws so far (-1 before the fix / without Epic Fight). */
    private static int efWingDraws() {
        try { return com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.class.getField("wingDraws").getInt(null); } catch (Throwable t) { return -1; }
    }
}
