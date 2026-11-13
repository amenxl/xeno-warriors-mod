package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * (Pack test, focus "packefclothes", and the full pack run) Clothes, wings, tails and held weapons in Epic Fight's
 * battle mode - the user's videos: Yuri's and Technica's lab coats as boxes, Kyrie's sleeves and wings floating,
 * the Karma Hoodie puffed up, Mikiko's tail gone, Max's wings and knife, Lexy's claws. For each: from the front out of
 * battle mode (our renderer), then in battle mode standing and in the middle of a swing.
 */
final class PackEfClothesTests {
    private PackEfClothesTests() {}

    private static void battle(boolean on) {
        try {
            var p = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
            if (p == null) return;
            if (on && !p.isEpicFightMode()) p.toEpicFightMode(true);
            if (!on && p.isEpicFightMode()) p.toVanillaMode(true);
        } catch (Throwable t) { PackTests.log("efclothes: couldn't switch battle mode " + t); }
    }

    @SuppressWarnings("unchecked")
    private static void swing(net.minecraft.server.level.ServerPlayer p) {
        var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(p);
        if (patch == null) return;
        var c = patch.getHoldingItemCapability(InteractionHand.MAIN_HAND).getAutoAttackMotion(patch);
        if (c == null || c.isEmpty()) return;
        patch.playAnimationSynchronized((yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.StaticAnimation>) c.get(0), 0F);
    }

    private record Case(String name, CharacterType who, int outfit, String held, boolean hoodie, boolean summon) {}

    static void build(Minecraft mc, List<Step> l) {
        Case[] cases = {
                new Case("yuri", CharacterType.YURI, 0, "minecraft:iron_sword", false, false),
                new Case("technica", CharacterType.TECHNICA, 0, "minecraft:iron_sword", false, false),
                new Case("kyrie_s2", CharacterType.KYRIE, 1, null, false, true),
                new Case("keita_hoodie", CharacterType.KEITA, 0, "minecraft:iron_sword", true, false),
                new Case("max", CharacterType.MAX, 0, null, false, true),
                new Case("lexy", CharacterType.LEXY, 0, null, false, true),
                new Case("mikiko_tail", CharacterType.MIKIKO, 1, "minecraft:iron_sword", false, false),
                new Case("rhalix", CharacterType.RHALIX, 0, "minecraft:iron_sword", false, false),
                new Case("sam", CharacterType.SAM, 0, null, false, true),
                new Case("logan", CharacterType.LOGAN, 0, null, false, true),
                new Case("kaeru", CharacterType.KAERU, 0, "minecraft:iron_sword", false, false),
                new Case("kaeru_fists", CharacterType.KAERU, 0, null, false, false)};
        for (Case c : cases) one(mc, l, c);
        // the old way (baked into one skinned mesh), for comparison
        l.add(new Step(null, () -> com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.RIGID = false, 1));
        one(mc, l, new Case("yuri_oldbake", CharacterType.YURI, 0, "minecraft:iron_sword", false, false));
        l.add(new Step(null, () -> com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.RIGID = true, 1));
        l.add(new Step(null, () -> PackTests.check("efclothes: clothes drawn as rigid pieces on Epic Fight's bones, tail on its hip bone",
                com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.rigDraws > 0 && com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.tailDraws > 0,
                "rig draws " + com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.rigDraws + ", tail draws " + com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.tailDraws
                        + ", armor pieces " + com.xenowarriors.client.compat.epicfight.ArmorPatchedLayer.drawn), 2));
        l.add(new Step(null, () -> { battle(false); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.SetOutfitPacket(0));
            PackTests.server(p -> { p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); PackTests.clear(p); }); }, 10));
    }

    private static void one(Minecraft mc, List<Step> l, Case c) {
        l.add(new Step(null, () -> { battle(false); UiTest.character(c.who); }, 30));
        l.add(new Step(null, () -> {
            com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.SetOutfitPacket(c.outfit));
            PackTests.server(p -> {
                PackTests.arena(p); PackTests.clear(p);
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.getInventory().selected = 0;
                var it = c.held == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(c.held));
                p.getInventory().setItem(0, it == null ? ItemStack.EMPTY : new ItemStack(it));
                p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                p.setItemSlot(EquipmentSlot.CHEST, c.hoodie ? new ItemStack(com.xenowarriors.item.ModItems.KARMA_HOODIE.get()) : ItemStack.EMPTY);
                if (c.summon) com.xenowarriors.forms.SignatureWeapons.toggle(p);
                p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(0);
            });
        }, 25));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 15));
        l.add(new Step("pack_efclothes_" + c.name + "_vanilla", () -> PackTests.log("efclothes " + c.name + " vanilla: held " + mc.player.getMainHandItem()
                + " / " + mc.player.getOffhandItem() + ", " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)), 3));
        l.add(new Step(null, () -> battle(true), 30));
        l.add(new Step(null, () -> { com.xenowarriors.client.render.CharacterDetailLayer.renders = 0;
            com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.DEBUG_PIECES = new StringBuilder(); }, 3));
        l.add(new Step("pack_efclothes_" + c.name + "_battle", () -> PackTests.log("efclothes " + c.name + " battle: "
                + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player) + " wings " + com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.lastWings
                + " rig " + com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.lastRig
                + " vanilla detail layer runs " + com.xenowarriors.client.render.CharacterDetailLayer.renders
                + " pieces " + com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.DEBUG_PIECES), 3));
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK), 12));
        l.add(new Step("pack_efclothes_" + c.name + "_back", () -> {}, 3));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); PackTests.server(PackEfClothesTests::swing); }, 6));
        l.add(new Step("pack_efclothes_" + c.name + "_swing", () -> PackTests.log("efclothes " + c.name + " swing: "
                + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)), 20));
        if (c.summon) l.add(new Step(null, () -> PackTests.server(p -> com.xenowarriors.forms.SignatureWeapons.toggle(p)), 5));
    }

    /**
     * (focus "packefhit", and the full pack run) Do the characters' battle-mode punches land? (the user: Mikiko couldn't
     * hit anybody.) A still zombie stands in front; the first moves of each combo are played; its health is read.
     */
    static void hits(Minecraft mc, List<Step> l) {
        for (CharacterType who : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.KAERU}) {
            float[] hp = {0F, 0F};
            int[] zid = {-1};
            l.add(new Step(null, () -> { UiTest.character(who); }, 30));
            l.add(new Step(null, () -> PackTests.server(p -> {
                PackTests.arena(p); PackTests.clear(p);
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.getInventory().selected = 0;
                p.getInventory().setItem(0, ItemStack.EMPTY);
                p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(0);
                var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                if (z != null) {
                    z.moveTo(p.getX(), p.getY(), p.getZ() + 1.4, 180F, 0F);
                    z.setNoAi(true);
                    z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
                    z.setHealth(200F);
                    z.addTag("xw_efhit");
                    p.serverLevel().addFreshEntity(z);
                    zid[0] = z.getId(); hp[0] = z.getHealth();
                }
            }), 15));
            // out of battle mode first: real clicks (as the attack key does)
            l.add(new Step(null, () -> { battle(false); mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON); }, 25));
            for (int k = 0; k < 3; k++) l.add(new Step(null, () -> net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey()), 14));
            l.add(new Step(null, () -> PackTests.server(p -> {
                var z = p.serverLevel().getEntity(zid[0]);
                float now = z instanceof net.minecraft.world.entity.LivingEntity le ? le.getHealth() : -1F;
                PackTests.check("efhit " + who.getId() + ": clicks out of battle mode land", now >= 0F && now < hp[0],
                        "zombie hp " + hp[0] + " -> " + now + ", aiming at " + mc.hitResult);
                hp[0] = now;
            }), 2));
            l.add(new Step(null, () -> battle(true), 30));
            for (int k = 0; k < 3; k++) l.add(new Step(null, () -> net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey()), 14));
            l.add(new Step(null, () -> PackTests.server(p -> {
                var z = p.serverLevel().getEntity(zid[0]);
                float now = z instanceof net.minecraft.world.entity.LivingEntity le ? le.getHealth() : -1F;
                PackTests.check("efhit " + who.getId() + ": clicks in battle mode land", now >= 0F && now < hp[0],
                        "zombie hp " + hp[0] + " -> " + now);
                hp[0] = now;
            }), 2));
            for (int k = 0; k < 3; k++) {
                int kk = k;
                l.add(new Step(null, () -> PackTests.server(p -> {
                    var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(p);
                    if (patch == null) return;
                    var c = patch.getHoldingItemCapability(InteractionHand.MAIN_HAND).getAutoAttackMotion(patch);
                    if (c == null || c.size() <= kk) return;
                    p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0;
                    patch.playAnimationSynchronized((yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.StaticAnimation>) c.get(kk), 0F);
                }), 16));
            }
            l.add(new Step("pack_efhit_" + who.getId(), () -> PackTests.server(p -> {
                var z = p.serverLevel().getEntity(zid[0]);
                float now = z instanceof net.minecraft.world.entity.LivingEntity le ? le.getHealth() : -1F;
                var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(p);
                Object first = null;
                try { first = patch.getHoldingItemCapability(InteractionHand.MAIN_HAND).getAutoAttackMotion(patch).get(0); } catch (Throwable ignored) {}
                PackTests.check("efhit " + who.getId() + ": battle-mode punches land", now >= 0F && now < hp[0],
                        "zombie hp " + hp[0] + " -> " + now + " (combo starts " + first + ")");
                if (z != null) z.discard();
            }), 5));
        }
        l.add(new Step(null, () -> { battle(false); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 10));
    }
}
