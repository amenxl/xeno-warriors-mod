package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your bot summoned DragonMineZ's dragon (this entity) - open its wish menu for you. */
public record DmzWishPacket(int dragonId) {
    public static void encode(DmzWishPacket m, FriendlyByteBuf b) { b.writeVarInt(m.dragonId); }
    public static DmzWishPacket decode(FriendlyByteBuf b) { return new DmzWishPacket(b.readVarInt()); }

    public static void handle(DmzWishPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.compat.DmzWishOpen.open(m.dragonId));
        ctx.get().setPacketHandled(true);
    }
}
