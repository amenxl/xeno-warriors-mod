package com.xenowarriors.network;

import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: wear this outfit (Mikiko: 0 Recarnet 1, 1 Origins 1, 2 Origins 2, 3/4 Recarnet Season 3). */
public record SetOutfitPacket(int outfit) {
    public static void encode(SetOutfitPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.outfit); }

    public static SetOutfitPacket decode(FriendlyByteBuf buf) { return new SetOutfitPacket(buf.readVarInt()); }

    public static void handle(SetOutfitPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        int o = Math.max(0, Math.min(4, m.outfit));
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = o);
        CharacterManager.syncToTrackingAndSelf(p);
    }
}
