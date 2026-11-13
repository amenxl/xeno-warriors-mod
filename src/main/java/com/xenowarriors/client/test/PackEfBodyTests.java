package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * (Pack test, focus "packefbody" and the full pack run) Mikiko in Epic Fight's battle mode holding an axe - the case
 * in the user's video (a dark second body inside him and a black block by his shoulder): shots from behind and the
 * front, standing and walking, and what decided how he's drawn.
 */
final class PackEfBodyTests {
    private PackEfBodyTests() {}

    private static Object patch() {
        try { return yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch(); } catch (Throwable t) { return null; }
    }

    private static void battle(boolean on) {
        try {
            var p = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
            if (p == null) return;
            if (on && !p.isEpicFightMode()) p.toEpicFightMode(true);
            if (!on && p.isEpicFightMode()) p.toVanillaMode(true);
        } catch (Throwable t) { PackTests.log("efbody: couldn't switch battle mode " + t); }
    }

    static void build(Minecraft mc, List<com.xenowarriors.client.UiTest.Step> l) {
        if (!ModList.get().isLoaded("epicfight")) { l.add(new com.xenowarriors.client.UiTest.Step(null, () -> PackTests.log("efbody: no Epic Fight - skipped"), 1)); return; }
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> PackTests.server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, new ItemStack(Items.WOODEN_AXE));
            p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0;
        }), 20));
        // Epic Fight's Stray Hat on Mikiko (the user's picture: a stray piece by his shoulder)
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> PackTests.server(p -> {
            var hat = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("epicfight", "stray_hat"));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, hat == null ? ItemStack.EMPTY : new ItemStack(hat));
            PackTests.log("efbody: stray hat " + (hat != null));
            com.xenowarriors.client.render.CharacterArmorLayer.ALL_RUNS.clear();
            RenderProbe.reset();
        }), 15));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> { mc.options.hideGui = true; mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 15));
        l.add(new com.xenowarriors.client.UiTest.Step("pack_strayhat_front", () -> PackTests.log("efbody: the Stray Hat (64x32 armor) drawn on the plain armor model " + (com.xenowarriors.client.render.CharacterArmorLayer.oldLayoutDrawn + com.xenowarriors.client.compat.epicfight.ArmorPatchedLayer.oldDrawn)
                + " times (layer ran " + com.xenowarriors.client.render.CharacterArmorLayer.layerRuns + ", head " + mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD) + ", self runs " + com.xenowarriors.client.render.CharacterArmorLayer.selfAll + "/" + com.xenowarriors.client.render.CharacterArmorLayer.selfRuns + ", all runs " + com.xenowarriors.client.render.CharacterArmorLayer.ALL_RUNS + ", " + RenderProbe.read() + ", ef " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player) + ", self by type/head " + com.xenowarriors.client.render.CharacterArmorLayer.SELF_TYPES + ", client says " + com.xenowarriors.client.ClientCharacterData.get(mc.player) + " local " + com.xenowarriors.client.ClientCharacterData.getLocal() + ", last self run " + com.xenowarriors.client.render.CharacterArmorLayer.lastRun + ", last foreign armor drawn: " + com.xenowarriors.client.render.CharacterArmorLayer.lastForeign + ") -> " + (com.xenowarriors.client.render.CharacterArmorLayer.oldLayoutDrawn + com.xenowarriors.client.compat.epicfight.ArmorPatchedLayer.oldDrawn > 0 ? "PASS" : "FAIL")), 3));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK), 12));
        l.add(new com.xenowarriors.client.UiTest.Step("pack_strayhat_back", () -> {}, 3));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); }, 15));
        l.add(new com.xenowarriors.client.UiTest.Step("pack_strayhat_inventory", () -> {}, 3));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> { mc.setScreen(null); PackTests.server(p -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY)); }, 10));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> { battle(true); mc.options.hideGui = true; mc.setScreen(null); }, 40));
        for (String side : new String[]{"back", "front"}) {
            l.add(new com.xenowarriors.client.UiTest.Step(null, () -> mc.options.setCameraType(side.equals("back") ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT), 15));
            l.add(new com.xenowarriors.client.UiTest.Step("pack_efbody_" + side, () -> PackTests.log("efbody " + side + ": " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)
                    + " patch " + (patch() != null)), 3));
        }
        // walking (the video: running along a path)
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.options.keyUp.setDown(true); }, 12));
        l.add(new com.xenowarriors.client.UiTest.Step("pack_efbody_walk", () -> PackTests.log("efbody walking: " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)), 3));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> mc.options.keyUp.setDown(false), 10));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> { battle(false); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 10));
    }

    /**
     * (focus "packremote", and the full pack run) Online play: another player as your client sees them - a RemotePlayer
     * playing Mikiko beside you, in and out of Epic Fight's battle mode, with and without the Stray Hat (the user's
     * video: an odd shadow body on the other player, and the hat's cloth over his face).
     */
    static void remote(Minecraft mc, List<com.xenowarriors.client.UiTest.Step> l) {
        if (!ModList.get().isLoaded("epicfight")) return;
        net.minecraft.client.player.RemotePlayer[] rp = new net.minecraft.client.player.RemotePlayer[1];
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> UiTest.character(CharacterType.RHALIX), 30));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> {
            PackTests.server(p -> { p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(10); });
            var prof = new com.mojang.authlib.GameProfile(java.util.UUID.fromString("7d0b5a4e-2222-4c2a-9b1e-00000000f00d"), "Friend");
            var r = new net.minecraft.client.player.RemotePlayer(mc.level, prof);
            r.setId(777123);
            r.moveTo(mc.player.getX(), mc.player.getY(), mc.player.getZ() + 3, 180, 0);
            r.setYHeadRot(180); r.yBodyRot = 180; r.yBodyRotO = 180; r.yHeadRotO = 180;
            r.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
            com.xenowarriors.client.ClientCharacterData.set(prof.getId(), CharacterType.MIKIKO);
            mc.level.addPlayer(r.getId(), r);
            rp[0] = r;
            mc.options.hideGui = true; mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON);
            PackTests.log("remote: added a remote Mikiko (patch " + remotePatch(r) + ")");
        }, 20));
        l.add(new com.xenowarriors.client.UiTest.Step("pack_remote_vanilla", () -> PackTests.log("remote vanilla: " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(rp[0])
                + " (skin model " + rp[0].getModelName() + ", cel outline slim " + com.xenowarriors.client.cel.CelOutline.lastSlim + ", cel " + com.xenowarriors.client.cel.Cel.on() + ")"), 3));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> remoteBattle(rp[0], true), 30));
        l.add(new com.xenowarriors.client.UiTest.Step("pack_remote_battle", () -> PackTests.log("remote battle: " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(rp[0])), 3));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> rp[0].setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY), 20));
        l.add(new com.xenowarriors.client.UiTest.Step("pack_remote_battle_hands", () -> PackTests.log("remote battle, bare hands: " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(rp[0])), 3));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> {
            var hat = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("epicfight", "stray_hat"));
            if (hat != null) rp[0].setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(hat));
        }, 20));
        l.add(new com.xenowarriors.client.UiTest.Step("pack_remote_battle_hat", () -> PackTests.log("remote battle + hat: last run " + com.xenowarriors.client.render.CharacterArmorLayer.lastRun + ", last foreign " + com.xenowarriors.client.render.CharacterArmorLayer.lastForeign), 3));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> remoteBattle(rp[0], false), 30));
        l.add(new com.xenowarriors.client.UiTest.Step("pack_remote_vanilla_hat", () -> PackTests.log("remote vanilla + hat: last run " + com.xenowarriors.client.render.CharacterArmorLayer.lastRun + ", last foreign " + com.xenowarriors.client.render.CharacterArmorLayer.lastForeign), 3));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> { mc.level.removeEntity(rp[0].getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); mc.options.hideGui = false; }, 5));
    }

    private static String rendererInfo(Minecraft mc) {
        try {
            var r = mc.getEntityRenderDispatcher().getRenderer(mc.player);
            StringBuilder b = new StringBuilder(r.getClass().getName());
            if (r instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> lr) {
                var f = net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredFields();
                for (var fl : f) if (java.util.List.class.isAssignableFrom(fl.getType())) {
                    fl.setAccessible(true);
                    var list = (java.util.List<?>) fl.get(lr);
                    b.append(" layers ").append(list.stream().map(o -> o.getClass().getSimpleName()).toList());
                }
            }
            return b.toString();
        } catch (Throwable t) { return "? " + t; }
    }

    private static String remotePatch(net.minecraft.world.entity.Entity e) {
        try {
            var p = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(e, yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch.class);
            return p == null ? "none" : p.getClass().getSimpleName();
        } catch (Throwable t) { return "error " + t; }
    }

    private static void remoteBattle(net.minecraft.world.entity.Entity e, boolean on) {
        try {
            var p = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(e, yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch.class);
            if (p == null) { PackTests.log("remote: no patch"); return; }
            if (on) p.toEpicFightMode(false); else p.toVanillaMode(false);
            PackTests.log("remote: battle mode " + p.isEpicFightMode());
        } catch (Throwable t) { PackTests.log("remote: couldn't switch battle mode " + t); }
    }
}
