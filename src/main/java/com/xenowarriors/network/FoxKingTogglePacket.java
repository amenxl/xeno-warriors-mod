package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Mikiko pressed the Fox King key. */
public record FoxKingTogglePacket() {
    public static void encode(FoxKingTogglePacket m, FriendlyByteBuf buf) {}

    public static FoxKingTogglePacket decode(FriendlyByteBuf buf) {
        return new FoxKingTogglePacket();
    }

    public static void handle(FoxKingTogglePacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) press(p);
    }

    /** Tapping the transform key (or Transform on the toggle wheel). */
    public static void press(net.minecraft.server.level.ServerPlayer p) {
        if (com.xenowarriors.party.Duels.cantAct(p)) return;
        if (com.xenowarriors.character.ModCapabilities.getType(p) == com.xenowarriors.character.CharacterType.YOU && com.xenowarriors.you.You.race(p) == com.xenowarriors.character.Race.DEMON && !p.isShiftKeyDown()) {   // (a Demon You: the key swaps human / demon form)
            com.xenowarriors.you.YouRaces.toggleHumanForm(p);
            return;
        }
        if (!com.xenowarriors.toggle.Toggles.transformAllowed(p)) return;          // (switched off in Character Customization > Passives)
        if (com.xenowarriors.toggle.Passives.livingWeapon(p)) return;             // Ultima: Living Weapon
        if (com.xenowarriors.skill.UltForms.press(p)) return;    // Kill / Lexy / You (Demon, sneaking): their transformations
        com.xenowarriors.mikiko.FoxKing.toggle(p); com.xenowarriors.rhalix.DemonsRemnants.activate(p); com.xenowarriors.you.YouRaces.toggleHumanForm(p);   // each only works for its own character
    }
}
