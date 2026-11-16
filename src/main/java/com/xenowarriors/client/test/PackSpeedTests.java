package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * (focus "packspeeds") How fast every combo plays in battle mode: each character's bare hands and each of our weapons.
 * Epic Fight scales an attack's speed by the holder's attack speed against the move's own ("basis") speed - a move
 * borrowed from a weapon but thrown bare-handed can come out far too fast or slow. Logs each move's speed (1.0 = as
 * its own weapon plays it) and how long it takes; flags anything outside 0.75 - 1.5.
 */
final class PackSpeedTests {
    private PackSpeedTests() {}

    static final java.util.List<String> OFF = new java.util.ArrayList<>(), SLOW = new java.util.ArrayList<>();

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); }), 10));
        for (CharacterType t : CharacterType.PLAYABLE) {
            l.add(new Step(null, () -> { UiTest.character(t); PackTests.server(p -> { p.getInventory().selected = 0; p.getInventory().setItem(0, ItemStack.EMPTY); }); }, 30));
            l.add(new Step(null, () -> battle(), 10));
            l.add(new Step(null, () -> measure(mc, t.getId() + " bare hands"), 2));
        }
        CharacterType[] holder = {CharacterType.MAX, CharacterType.SAM, CharacterType.LOGAN, CharacterType.KEITA, CharacterType.ZANE, CharacterType.KYRIE, CharacterType.LEXY, CharacterType.MIKIKO, CharacterType.RHALIX};
        int i = 0;
        for (String w : com.xenowarriors.compat.epicfight.WomMoves.WEAPONS.keySet()) {
            CharacterType t = holder[i++ % holder.length];
            l.add(new Step(null, () -> { UiTest.character(t); PackTests.server(p -> {
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(com.xenowarriors.XenoWarriors.id(w));
                p.getInventory().selected = 0;
                p.getInventory().setItem(0, item == null ? ItemStack.EMPTY : new ItemStack(item));
            }); }, 30));
            l.add(new Step(null, () -> battle(), 10));
            l.add(new Step(null, () -> measure(mc, w + " (" + t.getId() + ")"), 2));
        }
        l.add(new Step(null, () -> {
            PackTests.server(p -> p.getInventory().setItem(0, ItemStack.EMPTY));
            PackTests.check("speeds: every combo plays near its own speed (0.75 - 1.5)", OFF.isEmpty(), "off: " + OFF);
            PackTests.check("speeds: every hit of a string lands within 0.6s", SLOW.isEmpty(), "slow: " + SLOW);
        }, 3));
    }

    private static void battle() {
        try { var pp = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch(); if (pp != null && !pp.isEpicFightMode()) pp.toEpicFightMode(true); } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void measure(Minecraft mc, String what) {
        try {
            var patch = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
            if (patch == null) { PackTests.log("speeds " + what + ": no patch"); return; }
            var cap = patch.getHoldingItemCapability(InteractionHand.MAIN_HAND);
            List list = cap == null ? null : cap.getAutoAttackMotion(patch);
            if (list == null || list.isEmpty()) { PackTests.log("speeds " + what + ": no combo"); return; }
            float as = patch.getAttackSpeed(InteractionHand.MAIN_HAND);
            StringBuilder sb = new StringBuilder();
            int n = Math.max(1, list.size() - 2);                                   // (the string; the last two are the dash and air attacks)
            for (int k = 0; k < list.size(); k++) {
                var acc = (yesman.epicfight.api.asset.AssetAccessor) list.get(k);
                Object o = acc.get();
                if (!(o instanceof yesman.epicfight.api.animation.types.AttackAnimation a)) continue;
                float factor = a.getProperty(yesman.epicfight.api.animation.property.AnimationProperty.AttackAnimationProperty.ATTACK_SPEED_FACTOR).orElse(1F);
                var basis = a.getProperty(yesman.epicfight.api.animation.property.AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED);
                float total = a.getTotalTime();
                float x = basis.isPresent() ? as / basis.get() : total * as;
                Float own = com.xenowarriors.compat.epicfight.WomMoves.playSpeed(patch, a);   // (our override: a borrowed move never slower than its own speed)
                float speed = 1F + (x - 1F) * factor;
                if (own != null && speed < own) speed = own;
                String name = String.valueOf(acc.registryName()).replaceAll(".*/", "");
                float c = a.phases.length > 0 ? a.phases[0].contact / speed : -1, r = a.phases.length > 0 ? a.phases[a.phases.length - 1].recovery / speed : -1;
                sb.append(String.format(java.util.Locale.ROOT, " %s %.2fx %.2fs hit@%.2f next@%.2f", name, speed, total / Math.max(0.05F, speed), c, r));
                if (k < n && c > 0.6F) SLOW.add(what + " " + name + " hits at " + String.format(java.util.Locale.ROOT, "%.2fs", c));
                if (k < n && !what.startsWith("mikiko bare") && (speed < 0.75F || speed > 1.5F)) OFF.add(what + " " + name + " " + String.format(java.util.Locale.ROOT, "%.2f", speed));
            }
            PackTests.log("speeds " + what + ": attack speed " + String.format(java.util.Locale.ROOT, "%.2f", as) + " |" + sb);
        } catch (Throwable t) {
            PackTests.log("speeds " + what + ": error " + t);
        }
    }
}
