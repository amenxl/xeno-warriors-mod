package com.xenowarriors.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: how many are helping you dig this block (0 = none). */
public record CoMinePacket(BlockPos pos, int helpers) {
    public static void encode(CoMinePacket m, FriendlyByteBuf buf) { buf.writeBlockPos(m.pos); buf.writeVarInt(m.helpers); }

    public static CoMinePacket decode(FriendlyByteBuf buf) { return new CoMinePacket(buf.readBlockPos(), buf.readVarInt()); }

    public static void handle(CoMinePacket m, Supplier<NetworkEvent.Context> ctx) {
        CoMineClientHook.set(m.pos, m.helpers);
        ctx.get().setPacketHandled(true);
    }
}
