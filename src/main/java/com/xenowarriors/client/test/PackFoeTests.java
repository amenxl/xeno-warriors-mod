package com.xenowarriors.client.test;

import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * (focus "packfoes", and the full pack run) Season 3's foes with Epic Fight: the Cynic Dancer's claws and the
 * Ancient Swordstress's greatsword in hand, drawn on Epic Fight's body, swinging their weapons' moves; the bow is a bow
 * again; corrupted souls / townsfolk with Simply Swords weapons.
 */
final class PackFoeTests {
    private PackFoeTests() {}

    static void build(Minecraft mc, List<Step> l) {
        int[] ids = {-1, -1, -1};
        int[] swings0 = {0};
        l.add(new Step(null, () -> { UiTest.character(com.xenowarriors.character.CharacterType.MIKIKO); mc.setScreen(null); mc.options.hideGui = true; }, 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.setInvulnerable(true);
            p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(10);
            var l2 = p.serverLevel();
            var v = com.xenowarriors.foes.Foes.VESSEL.get().create(l2);
            var s = com.xenowarriors.foes.Foes.SWORDSTRESS.get().create(l2);
            if (v != null) { com.xenowarriors.foes.Foes.placeForTest(l2, v, p.blockPosition().offset(-1, 0, 3)); ids[0] = v.getId(); v.setTarget(p); v.setInvulnerable(true); }   // (the Swordstress hunts Dancers)
            if (s != null) { com.xenowarriors.foes.Foes.placeForTest(l2, s, p.blockPosition().offset(2, 0, 3)); ids[1] = s.getId(); s.setTarget(p); }
            com.xenowarriors.compat.SimplySwordsGear.testRoll = true;
            var soul = com.xenowarriors.corrupted.ModCorrupted.CORRUPTED_SOUL.get().create(l2);
            if (soul != null) { soul.moveTo(p.getX() + 1, p.getY() + 1.5, p.getZ() + 5, 180, 0); soul.setNoAi(true); l2.addFreshEntity(soul); ids[2] = soul.getId(); }
            com.xenowarriors.compat.SimplySwordsGear.testRoll = null;
            ItemStack sh = soul == null ? ItemStack.EMPTY : soul.getMainHandItem();
            var sk = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(sh.getItem());
            PackTests.check("foes: a corrupted soul can carry a Simply Swords weapon", sk != null && sk.getNamespace().equals("simplyswords"), "holds " + sk);
            swings0[0] = com.xenowarriors.foes.FoeAnims.swings;
        }), 20));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); }, 140));
        l.add(new Step("pack_foes_fight", () -> PackTests.server(p -> {
            var l2 = p.serverLevel();
            var v = l2.getEntity(ids[0]); var s = l2.getEntity(ids[1]); var soul = l2.getEntity(ids[2]);
            boolean vClaws = v instanceof net.minecraft.world.entity.LivingEntity le && le.getMainHandItem().is(com.xenowarriors.item.ModItems.CYNIC_TSUME.get());
            boolean sSword = s instanceof net.minecraft.world.entity.LivingEntity le2 && le2.getMainHandItem().is(com.xenowarriors.item.ModItems.SWORDSTRESS_GREATSWORD.get());
            PackTests.check("foes: the Cynic Dancer holds its claws, the Swordstress its greatsword", vClaws && sSword, "dancer " + v + " swordstress " + s);
            Object vp = v == null ? null : yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(v, yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch.class);
            Object sp = s == null ? null : yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(s, yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch.class);
            PackTests.check("foes: both on Epic Fight's body", vp != null && sp != null, "patches " + vp + " / " + sp);
            PackTests.check("foes: they swing their weapons' Epic Fight moves", com.xenowarriors.foes.FoeAnims.swings > swings0[0],
                    "swings " + (com.xenowarriors.foes.FoeAnims.swings - swings0[0]) + " player hurt? health " + p.getHealth());
            var bow = yesman.epicfight.world.capabilities.EpicFightCapabilities.getItemStackCapability(new ItemStack(net.minecraft.world.item.Items.BOW));
            String cat = bow == null ? "none" : String.valueOf(bow.getWeaponCategory());
            PackTests.check("bow: Epic Fight's bow again (not Epic Arsenal's gun)", cat.toLowerCase(java.util.Locale.ROOT).contains("bow"), "bow category " + cat + " " + bow);
            var gs = yesman.epicfight.world.capabilities.EpicFightCapabilities.getItemStackCapability(new ItemStack(com.xenowarriors.item.ModItems.SWORDSTRESS_GREATSWORD.get()));
            var hk = yesman.epicfight.world.capabilities.EpicFightCapabilities.getItemStackCapability(new ItemStack(com.xenowarriors.item.ModItems.HEKUBLADE.get()));
            var ws = yesman.epicfight.world.capabilities.EpicFightCapabilities.getItemStackCapability(new ItemStack(com.xenowarriors.forms.FormItems.WIRE_SWORD.get()));
            var ct = yesman.epicfight.world.capabilities.EpicFightCapabilities.getItemStackCapability(new ItemStack(com.xenowarriors.item.ModItems.CYNIC_TSUME.get()));
            PackTests.log("weapon styles: greatsword " + (gs == null ? null : gs.getWeaponCategory()) + " hekublade " + (hk == null ? null : hk.getWeaponCategory())
                    + " wire sword " + (ws == null ? null : ws.getWeaponCategory()) + " cynic claws " + (ct == null ? null : ct.getWeaponCategory()));
            p.setInvulnerable(false);
        }), 4));
        l.add(new Step(null, () -> PackTests.server(p -> {
            for (int id : ids) { var e = p.serverLevel().getEntity(id); if (e != null) e.discard(); }
        }), 4));
        l.add(new Step(null, () -> { mc.options.hideGui = false; }, 4));
    }
}
