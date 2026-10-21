package com.xenowarriors.network;

import com.xenowarriors.client.ClientSkillState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: how much total flying a Mikiko has done (his flying gets less clumsy with practice). */
public record FlightSkillPacket(int entityId, int flightTicks) {
    public static void encode(FlightSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.flightTicks);
    }

    public static FlightSkillPacket decode(FriendlyByteBuf buf) {
        return new FlightSkillPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(FlightSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSkillState.setFlightSkill(msg.entityId, msg.flightTicks));
    }
}
