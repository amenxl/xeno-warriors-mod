package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.render.CharacterDetailLayer;
import com.xenowarriors.forms.SignatureWeapons;
import com.xenowarriors.item.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * (focus "packchase") Chase's summon key: Ash to Ash in his right hand, Dust to Dust in his left, both off his back
 * while they're out; a second tap puts them back. Pictures from the front and the back, and talking (the mouth in his skin).
 */
final class PackChaseTests {
    private PackChaseTests() {}

    private static boolean backShown(Minecraft mc) {
        var m = CharacterDetailLayer.detailModel(mc.player);
        return m != null && m.backSwords != null && m.backSwords.visible;
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> { com.xenowarriors.character.Seasons.set(p.server, 3); PackTests.arena(p); PackTests.clear(p); }), 20));
        l.add(new Step(null, () -> { UiTest.character(CharacterType.CHASE); PackTests.server(p -> { p.getInventory().selected = 0; p.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY); }); }, 40));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setYRot(0F); mc.player.setXRot(10F); }, 15));
        l.add(new Step("chase_back_before", () -> PackTests.check("chase: swords on his back before", backShown(mc), "model " + (CharacterDetailLayer.detailModel(mc.player) == null ? "none" : "back_swords " + CharacterDetailLayer.detailModel(mc.player).backSwords)), 5));
        l.add(new Step(null, () -> PackTests.server(SignatureWeapons::toggle), 15));          // (what the summon key sends)
        l.add(new Step("chase_back_drawn", () -> {
            PackTests.check("chase: Ash to Ash in his right hand", mc.player.getMainHandItem().is(ModItems.ASH_TO_ASH.get()) && SignatureWeapons.isSummoned(mc.player.getMainHandItem()), "main " + mc.player.getMainHandItem());
            PackTests.check("chase: Dust to Dust in his left hand", mc.player.getOffhandItem().is(ModItems.DUST_TO_DUST.get()) && SignatureWeapons.isSummoned(mc.player.getOffhandItem()), "off " + mc.player.getOffhandItem());
            PackTests.check("chase: his back is empty while they're out", com.xenowarriors.client.render.DetailModel.swordsDrawn(mc.player), "");   // (the renderers hide them on this; the shared model's flag is whatever the last view set)
            PackTests.check("chase: the swords' names", mc.player.getMainHandItem().getHoverName().getString().equals("Ash to Ash") && mc.player.getOffhandItem().getHoverName().getString().equals("Dust to Dust"),
                    mc.player.getMainHandItem().getHoverName().getString() + " / " + mc.player.getOffhandItem().getHoverName().getString());
        }, 5));
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT), 10));
        l.add(new Step("chase_front_drawn", () -> {}, 5));
        l.add(new Step(null, () -> PackTests.server(p -> p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true)), 3));
        l.add(new Step("chase_swing", () -> {}, 3));
        // talking (a chat line): the mouth over his face
        l.add(new Step(null, () -> { if (mc.player.connection != null) mc.player.connection.sendChat("Begone."); }, 6));
        l.add(new Step("chase_talking", () -> {}, 3));
        l.add(new Step(null, () -> PackTests.server(SignatureWeapons::toggle), 15));
        l.add(new Step("chase_back_away", () -> {
            PackTests.check("chase: a second tap puts them away", mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().isEmpty(), "main " + mc.player.getMainHandItem() + " off " + mc.player.getOffhandItem());
            PackTests.check("chase: back on his back", backShown(mc), "");
        }, 5));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
    }
}
