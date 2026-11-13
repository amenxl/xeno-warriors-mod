package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.compat.epicfight.WomMoves;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * (Pack test, focus "packwom", and the full pack run) Weapons of Miracles' moves on our characters: for one character
 * of each bare-hand style and for each of our weapons, Epic Fight's combo is ours (every WOM move found), and two of
 * its swings are played and shot from the front for review. "XW-TEST pack wom ... PASS/FAIL".
 */
final class PackWomTests {
    private PackWomTests() {}

    private static void battle(boolean on) {
        try {
            var p = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
            if (p == null) return;
            if (on && !p.isEpicFightMode()) p.toEpicFightMode(true);
            if (!on && p.isEpicFightMode()) p.toVanillaMode(true);
        } catch (Throwable t) { PackTests.log("wom: couldn't switch battle mode " + t); }
    }

    @SuppressWarnings("rawtypes")
    private static List combo(net.minecraft.server.level.ServerPlayer p) {
        var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(p);
        if (patch == null) return null;
        return patch.getHoldingItemCapability(InteractionHand.MAIN_HAND).getAutoAttackMotion(patch);
    }

    @SuppressWarnings("unchecked")
    private static void swing(net.minecraft.server.level.ServerPlayer p, int i) {
        var c = combo(p);
        var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(p);
        if (c == null || patch == null || c.size() <= i) return;
        patch.playAnimationSynchronized((yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.StaticAnimation>) c.get(i), 0F);
    }

    private static void one(Minecraft mc, List<Step> l, CharacterType who, String item, String[] expect) {
        String name = item == null ? "fists_" + who.name().toLowerCase(java.util.Locale.ROOT) : item;
        l.add(new Step(null, () -> UiTest.character(who), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getInventory().selected = 0;
            var it = item == null ? null : ForgeRegistries.ITEMS.getValue(com.xenowarriors.XenoWarriors.id(item));
            p.getInventory().setItem(0, it == null ? ItemStack.EMPTY : new ItemStack(it));
            p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(0);
        }), 15));
        l.add(new Step(null, () -> { battle(true); mc.options.hideGui = true; mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 25));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var c = combo(p);
            int swaps = WomMoves.swaps;
            boolean ok = c != null && c.size() == expect.length + 2;
            if (ok) for (int i = 0; i < expect.length; i++) {
                Object want = yesman.epicfight.api.animation.AnimationManager.byKey(expect[i]);
                if (want == null || c.get(i) != want) { ok = false; break; }
            }
            PackTests.check("wom " + name + ": " + expect.length + " Weapons of Miracles moves in the combo", ok,
                    "combo size " + (c == null ? "none" : c.size()) + " swaps " + swaps + " missing " + WomMoves.MISSING + " first " + (c == null || c.isEmpty() ? "-" : c.get(0)));
            swing(p, 0);
        }), 6));
        l.add(new Step("pack_wom_" + name + "_1", () -> PackTests.log("wom " + name + " swing 1: " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)), 18));
        l.add(new Step(null, () -> PackTests.server(p -> swing(p, 1)), 6));
        l.add(new Step("pack_wom_" + name + "_2", () -> PackTests.log("wom " + name + " swing 2: " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)), 18));
    }

    static void build(Minecraft mc, List<Step> l) {
        if (!ModList.get().isLoaded("epicfight") || !WomMoves.loaded()) {
            l.add(new Step(null, () -> PackTests.log("wom: Epic Fight or Weapons of Miracles not loaded - skipped"), 1));
            return;
        }
        one(mc, l, CharacterType.KAERU, null, WomMoves.STREET);
        one(mc, l, CharacterType.ULTIMA, null, WomMoves.BEAST);
        one(mc, l, CharacterType.KYRIE, null, WomMoves.SWIFT);
        CharacterType[] holder = {CharacterType.LOGAN, CharacterType.KEITA, CharacterType.KILL, CharacterType.ZANE, CharacterType.SAM, CharacterType.MAX, CharacterType.AIONI};
        int i = 0;
        for (var e : WomMoves.WEAPONS.entrySet()) one(mc, l, holder[i++ % holder.length], e.getKey(), e.getValue());
        // Mikiko keeps his own bare-hand moves
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.getInventory().setItem(0, ItemStack.EMPTY);
            var c = combo(p);
            PackTests.check("wom: Mikiko's bare hands keep his own moves", WomMoves.movesFor(p) == null, "combo size " + (c == null ? "none" : c.size()));
        }), 5));
        l.add(new Step(null, () -> { battle(false); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            PackTests.server(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)); }, 10));
    }
}
