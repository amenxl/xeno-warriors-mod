package com.xenowarriors.network;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.mikiko.Hekublade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Mikiko's Hekublade Dimension key (open it / leave it) - needs the Hekublade in hand. */
public record RealmKeyPacket() {
    public static void encode(RealmKeyPacket m, FriendlyByteBuf buf) {}

    public static RealmKeyPacket decode(FriendlyByteBuf buf) { return new RealmKeyPacket(); }

    public static void handle(RealmKeyPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
        if (!Hekublade.isBlade(p.getMainHandItem())) {
            p.displayClientMessage(Component.literal("Hold the Hekublade to open its dimension.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        com.xenowarriors.mikiko.HekubladeRealm.toggle(p);
    }
}
