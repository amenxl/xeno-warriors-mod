package com.xenowarriors.you;

import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import com.xenowarriors.character.Race;
import com.xenowarriors.ka.Element;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.YouActionPacket;
import com.xenowarriors.network.YouSlotsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The server side of the You slots screen: listing, playing, creating and deleting a player's You characters. */
public final class YouSlots {
    private YouSlots() {}

    public static void send(ServerPlayer p, boolean open) {
        PlayerCharacter d = p.getCapability(ModCapabilities.PLAYER_CHARACTER).orElse(null);
        if (d == null) return;
        int[] lv = new int[YouSlot.SLOTS];
        for (int i = 0; i < lv.length; i++) {
            if (!d.youSlots[i].exists()) continue;
            String k = PlayerCharacter.key(CharacterType.YOU, i);
            int[] s = d.getType() == CharacterType.YOU && d.youSlot == i ? d.statLevel : d.charStats.getCompound(k).getIntArray("lv");
            int sum = 0; for (int v : s) sum += v;
            lv[i] = com.xenowarriors.stats.Stats.fromStats(sum);
        }
        ModNetwork.sendToPlayer(new YouSlotsPacket(d.youSlots, lv, d.getType() == CharacterType.YOU ? d.youSlot : -1, open), p);
    }

    public static void act(ServerPlayer p, int action, int slot, YouSlot data) {
        PlayerCharacter d = p.getCapability(ModCapabilities.PLAYER_CHARACTER).orElse(null);
        if (d == null) return;
        if (action == YouActionPacket.OPEN) { send(p, true); return; }
        if (slot < 0 || slot >= YouSlot.SLOTS) return;
        if (com.xenowarriors.journey.Journey.inRun(p)) {
            p.displayClientMessage(Component.literal("You can't switch characters on a Journey").withStyle(ChatFormatting.RED), true);
            return;
        }
        switch (action) {
            case YouActionPacket.PLAY -> {
                if (!CharacterManager.setYou(p, slot)) p.displayClientMessage(Component.literal("That slot is empty").withStyle(ChatFormatting.RED), true);
                else send(p, false);
            }
            case YouActionPacket.CREATE -> {
                if (data == null) return;
                String why = check(p, d, slot, data);
                if (why != null) { p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), false); send(p, true); return; }
                YouSlot s = d.youSlots[slot];
                s.name = data.name.trim(); s.race = data.race; s.element = data.element;
                s.form = data.race == Race.SKYLANDER ? (data.form == YouSlot.FORM_SOLAR ? YouSlot.FORM_SOLAR : YouSlot.FORM_LUNAR) : YouSlot.FORM_NONE;
                s.humanForm = false;
                d.charStats.remove(PlayerCharacter.key(CharacterType.YOU, slot));
                d.loadouts.remove(PlayerCharacter.key(CharacterType.YOU, slot));
                p.sendSystemMessage(Component.literal("Created " + s.name + ", a " + s.race.title + " of " + s.element.title() + ".").withStyle(st -> st.withColor(s.color())));
                CharacterManager.setYou(p, slot);
                send(p, false);
            }
            case YouActionPacket.DELETE -> {
                if (!d.youSlots[slot].exists()) return;
                if (d.getType() == CharacterType.YOU && d.youSlot == slot) {
                    p.displayClientMessage(Component.literal("Switch to another character before deleting this one").withStyle(ChatFormatting.RED), true);
                    return;
                }
                String name = d.youSlots[slot].name;
                d.youSlots[slot] = new YouSlot();
                d.charStats.remove(PlayerCharacter.key(CharacterType.YOU, slot));
                d.loadouts.remove(PlayerCharacter.key(CharacterType.YOU, slot));
                p.sendSystemMessage(Component.literal(name + " was deleted.").withStyle(ChatFormatting.GRAY));
                send(p, true);
            }
            default -> {}
        }
    }

    /** Why a creation isn't allowed, or null. */
    private static String check(ServerPlayer p, PlayerCharacter d, int slot, YouSlot data) {
        if (d.youSlots[slot].exists()) return "That slot already has a character - delete it first";
        if (!YouSlot.validName(data.name.trim())) return "Names are 2-16 letters, numbers, spaces, ' - or _";
        boolean ok = false; for (Race r : Race.CREATABLE) if (r == data.race) ok = true;
        if (!ok) return "That isn't a race you can create";
        if (data.race == Race.ANGEL && !p.isCreative()) return "Angels are creative mode only for now";
        boolean el = false; for (Element e : data.race.elements()) if (e == data.element) el = true;
        if (!el) return "A " + data.race.title + " can't have the " + data.element.title() + " element";
        for (YouSlot s : d.youSlots) if (s.exists() && s.name.equalsIgnoreCase(data.name.trim())) return "You already have a character called " + s.name;
        return null;
    }
}
