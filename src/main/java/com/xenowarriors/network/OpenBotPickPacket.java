package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> player: open "pick 2 people to join your party" (who's taken, who's still recovering, how many can join). */
public record OpenBotPickPacket(String[] taken, String[] cooling, int free) {
    public static void encode(OpenBotPickPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.taken.length); for (String s : m.taken) b.writeUtf(s);
        b.writeVarInt(m.cooling.length); for (String s : m.cooling) b.writeUtf(s);
        b.writeVarInt(m.free);
    }
    public static OpenBotPickPacket decode(FriendlyByteBuf b) {
        String[] t = new String[Math.min(16, b.readVarInt())]; for (int i = 0; i < t.length; i++) t[i] = b.readUtf();
        String[] c = new String[Math.min(16, b.readVarInt())]; for (int i = 0; i < c.length; i++) c[i] = b.readUtf();
        return new OpenBotPickPacket(t, c, b.readVarInt());
    }
    public static void handle(OpenBotPickPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PacketScreens.openBotPick(m));
        ctx.get().setPacketHandled(true);
    }
}
