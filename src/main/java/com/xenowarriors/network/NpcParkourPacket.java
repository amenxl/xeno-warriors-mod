package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients near: an NPC's parkour move (NpcParkour), packed as BotParkour.pack. */
public record NpcParkourPacket(int entity, int move) {
    public static void encode(NpcParkourPacket m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeVarInt(m.move); }
    public static NpcParkourPacket decode(FriendlyByteBuf b) { return new NpcParkourPacket(b.readVarInt(), b.readVarInt()); }
    public static void handle(NpcParkourPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.npc.NpcParkourClient.set(m.entity(), m.move()));
        ctx.get().setPacketHandled(true);
    }
}
