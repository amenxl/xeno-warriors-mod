package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.network.YouActionPacket;
import com.xenowarriors.you.YouSlot;
import com.xenowarriors.you.YouSlots;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * (focus "packyoucolour") A made character picks its colour: made with one in the creator (it's kept, synced, and the
 * menus and HUD use it), changed from the slots screen, and back to its element's. Pictures of the picker, the creator,
 * the slots screen and the main menu in it.
 */
final class PackYouColourTests {
    private PackYouColourTests() {}

    static final int PICK = 0x30D0A0, PICK2 = 0xF050D0;

    private static int local() { return ClientCharacterData.screenColor(CharacterType.YOU) & 0xFFFFFF; }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p);
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            d.youSlots[7] = new YouSlot();                                          // (an empty slot to make it in)
            YouSlot s = new YouSlot(); s.name = "Corvin"; s.race = com.xenowarriors.character.Race.HUMAN; s.element = com.xenowarriors.ka.Element.FIRE; s.colour = PICK;
            YouSlots.act(p, YouActionPacket.CREATE, 7, s);                           // (what the creator sends)
        }), 40));
        l.add(new Step(null, () -> {
            PackTests.server(p -> {
                var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
                PackTests.check("youcolour: made with its colour (server)", d.youSlots[7].colour == PICK && (com.xenowarriors.you.You.color(p) & 0xFFFFFF) == PICK, "colour " + Integer.toHexString(d.youSlots[7].colour));
                // (saved and loaded back)
                PackTests.check("youcolour: kept when saved", YouSlot.load(d.youSlots[7].save()).colour == PICK, "");
            });
            PackTests.check("youcolour: your client has it (menus, HUD)", ClientCharacterData.getLocal() == CharacterType.YOU && local() == PICK && com.xenowarriors.client.menu.Flat.col() == PICK,
                    "type " + ClientCharacterData.getLocal() + " screen " + Integer.toHexString(local()) + " flat " + Integer.toHexString(com.xenowarriors.client.menu.Flat.col()));
            mc.setScreen(new com.xenowarriors.client.menu.MainMenuScreen());
        }, 20));
        l.add(new Step("youcolour_mainmenu", () -> mc.setScreen(new com.xenowarriors.client.you.YouColourScreen("Corvin", PICK, com.xenowarriors.ka.Element.FIRE.color, null, c -> {})), 20));
        l.add(new Step("youcolour_picker", () -> mc.setScreen(new com.xenowarriors.client.you.YouCreatorScreen(8)), 20));
        l.add(new Step("youcolour_creator", () -> { mc.setScreen(null); PackTests.server(p -> {
            YouSlot d = new YouSlot(); d.name = "Corvin"; d.colour = PICK2;
            YouSlots.act(p, YouActionPacket.COLOUR, 7, d);                           // (what the slots screen sends)
        }); }, 20));
        l.add(new Step(null, () -> {
            PackTests.check("youcolour: changed later", local() == PICK2, "now " + Integer.toHexString(local()));
            mc.setScreen(new com.xenowarriors.client.you.YouSlotsScreen());
        }, 20));
        l.add(new Step("youcolour_slots", () -> { mc.setScreen(null); PackTests.server(p -> {
            YouSlot d = new YouSlot(); d.name = "Corvin"; d.colour = -1;
            YouSlots.act(p, YouActionPacket.COLOUR, 7, d);
        }); }, 20));
        l.add(new Step(null, () -> PackTests.check("youcolour: back to its element's", local() == (com.xenowarriors.ka.Element.FIRE.color & 0xFFFFFF), "now " + Integer.toHexString(local())), 5));
        l.add(new Step(null, () -> PackTests.server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.MIKIKO)), 20));
    }
}
