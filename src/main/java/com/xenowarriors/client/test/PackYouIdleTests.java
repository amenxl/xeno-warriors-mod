package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.Idles;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.network.YouActionPacket;
import com.xenowarriors.you.YouSlot;
import com.xenowarriors.you.YouSlots;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * (focus "packyouidle") A made character borrows idles: refused under bond 100, then Rhalix's book, Mikiko's stretches
 * and Yuri's laptop at bond 100 (the idle takes the body, and only the idle); the Outfits tab's IDLE ANIMATION cards.
 */
final class PackYouIdleTests {
    private PackYouIdleTests() {}

    private static String idle(Minecraft mc) { var s = ClientCharacterData.you(mc.player); return s == null ? "(not a made character)" : s.idle; }

    private static void pick(String id) {
        PackTests.server(p -> { YouSlot d = new YouSlot(); d.idle = id; YouSlots.act(p, YouActionPacket.IDLE, -1, d); });
    }

    /** Borrow {@code id}'s idle at bond 100, stand still {@code wait} ticks, and check {@code holder} has the body. */
    private static void borrow(Minecraft mc, List<Step> l, String id, String holder, int wait) {
        l.add(new Step(null, () -> PackTests.server(p -> {
            com.xenowarriors.party.Bonds.set(p, id, 100, 0F);
            PackTests.arena(p);
            p.connection.teleport(PackTests.HOME[0] + 0.3, PackTests.HOME[1], PackTests.HOME[2], 0F, 0F);     // (moved: standing still starts over)
        }), 10));
        l.add(new Step(null, () -> pick(id), 10));
        l.add(new Step(null, () -> PackTests.check("youidle: borrows " + id + "'s idle at bond 100", id.equals(idle(mc)), "idle " + idle(mc)), 2));
        l.add(new Step(null, () -> {}, wait));
        l.add(new Step("youidle_" + id, () -> PackTests.check("youidle: " + id + "'s idle plays (" + holder + ")", Idles.holder(mc.player).equals(holder), "holder " + Idles.holder(mc.player)), 3));
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p);
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            d.youSlots[6] = new YouSlot();
            YouSlot s = new YouSlot(); s.name = "Idler"; s.race = com.xenowarriors.character.Race.HUMAN; s.element = com.xenowarriors.ka.Element.WATER;
            YouSlots.act(p, YouActionPacket.CREATE, 6, s);
            p.getInventory().selected = 0; p.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
        }), 40));
        // under 100: refused
        l.add(new Step(null, () -> pick("keita"), 10));
        l.add(new Step(null, () -> PackTests.check("youidle: refused under bond 100", ClientCharacterData.getLocal() == CharacterType.YOU && idle(mc).isEmpty(),
                "type " + ClientCharacterData.getLocal() + " idle '" + idle(mc) + "'"), 2));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 5));
        borrow(mc, l, "rhalix", Idles.BOOK, 160);
        borrow(mc, l, "mikiko", Idles.MIKIKO, 80);      // (his first standing idle starts at 80 ticks still)
        l.add(new Step(null, () -> PackTests.check("youidle: Mikiko's idle doesn't bring his flying or fighting", !mc.player.getAbilities().flying, ""), 2));
        borrow(mc, l, "yuri", Idles.LAPTOP, 140);
        // the cards on the Outfits tab
        l.add(new Step(null, () -> { mc.options.hideGui = false; com.xenowarriors.client.menu.CharacterCustomScreen.open(null);
            if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.tab(2); }, 40));
        l.add(new Step("youidle_cards", () -> {}, 5));
        l.add(new Step(null, () -> pick(""), 10));
        l.add(new Step(null, () -> { PackTests.check("youidle: None stops borrowing", idle(mc).isEmpty(), "idle " + idle(mc)); mc.setScreen(null);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            PackTests.server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.MIKIKO)); }, 20));
    }
}
