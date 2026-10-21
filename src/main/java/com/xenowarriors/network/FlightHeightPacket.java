package com.xenowarriors.network;

import com.xenowarriors.character.RhalixFlight;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> Rhalix: how high above the ground he can fly right now. */
public record FlightHeightPacket(float height) {
    public static void encode(FlightHeightPacket m, FriendlyByteBuf buf) {
        buf.writeFloat(m.height);
    }

    public static FlightHeightPacket decode(FriendlyByteBuf buf) {
        return new FlightHeightPacket(buf.readFloat());
    }

    public static void handle(FlightHeightPacket m, Supplier<NetworkEvent.Context> ctx) {
        RhalixFlight.clientMax = m.height;
    }
}
