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
        }), 15));
        l.add(new com.xenowarriors.client.UiTest.Step(null, () -> { mc.options.hideGui = true; mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 15));
        l.add(new com.xenowarriors.client.UiTest.Step("pack_strayhat_front", () -> PackTests.log("efbody: the Stray Hat (64x32 armor) drawn on the plain armor model " + com.xenowarriors.client.render.CharacterArmorLayer.oldLayoutDrawn
                + " times (layer ran " + com.xenowarriors.client.render.CharacterArmorLayer.layerRuns + ", head " + mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD) + ", last foreign armor drawn: " + com.xenowarriors.client.render.CharacterArmorLayer.lastForeign + ") -> " + (com.xenowarriors.client.render.CharacterArmorLayer.oldLayoutDrawn > 0 ? "PASS" : "FAIL")), 3));
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
}
