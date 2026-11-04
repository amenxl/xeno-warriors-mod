package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Party Swap on/off: client -> server to set it (from the Party menu), server -> client to tell the menu what it is. */
public record PartySwapPacket(boolean on) {
    public static void encode(PartySwapPacket m, FriendlyByteBuf b) { b.writeBoolean(m.on); }
    public static PartySwapPacket decode(FriendlyByteBuf b) { return new PartySwapPacket(b.readBoolean()); }
    public static void handle(PartySwapPacket m, Supplier<NetworkEvent.Context> ctx) {
        var sender = ctx.get().getSender();
        if (sender != null) com.xenowarriors.party.PartySwap.set(sender, m.on);
        else DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.party.PartySetupScreen.swapOn = m.on);
        ctx.get().setPacketHandled(true);
    }
}
