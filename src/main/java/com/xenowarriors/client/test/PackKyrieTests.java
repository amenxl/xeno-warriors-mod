package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * (Pack test, focus "packkyrie", and the full pack run) Kyrie's Season 2 outfit and her summoned rapier: shots from
 * the front and back (plain, and in Epic Fight's battle mode with the rapier out).
 */
final class PackKyrieTests {
    private PackKyrieTests() {}

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.character(CharacterType.KYRIE), 30));
        l.add(new Step(null, () -> { com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.SetOutfitPacket(1));
            PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); p.getInventory().selected = 0; p.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
                p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(0); }); }, 25));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 20));
        l.add(new Step("pack_kyrie_s2_front", () -> PackTests.log("kyrie: outfit " + com.xenowarriors.client.ClientCharacterData.outfit(mc.player)), 3));
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK), 12));
        l.add(new Step("pack_kyrie_s2_back", () -> {}, 3));
        l.add(new Step(null, () -> PackTests.server(p -> com.xenowarriors.forms.SignatureWeapons.toggle(p)), 15));
        l.add(new Step(null, () -> PackTests.server(p -> PackTests.check("kyrie: her rapier is summoned with the summon key",
                p.getMainHandItem().is(com.xenowarriors.item.ModItems.KYRIE_RAPIER.get()) && com.xenowarriors.forms.SignatureWeapons.isSummoned(p.getMainHandItem()),
                "held " + p.getMainHandItem())), 2));
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT), 12));
        l.add(new Step("pack_kyrie_rapier_front", () -> {}, 3));
        l.add(new Step(null, () -> { try { var pp = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch(); if (pp != null) pp.toEpicFightMode(true); } catch (Throwable t) {} }, 30));
        l.add(new Step("pack_kyrie_rapier_battle", () -> {}, 3));
        l.add(new Step(null, () -> { try { var pp = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch(); if (pp != null) pp.toVanillaMode(true); } catch (Throwable t) {}
            PackTests.server(p -> com.xenowarriors.forms.SignatureWeapons.toggle(p));
            com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.SetOutfitPacket(0));
            mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 10));
    }
}
