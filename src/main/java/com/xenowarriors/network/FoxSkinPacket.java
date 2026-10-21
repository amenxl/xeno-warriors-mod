package com.xenowarriors.network;

import com.xenowarriors.client.fox.FoxClientFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this fox is someone's pet and wears this skin (0 = its natural coat). */
public record FoxSkinPacket(int entityId, int skin) {
    public static void encode(FoxSkinPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeVarInt(m.skin);
    }

    public static FoxSkinPacket decode(FriendlyByteBuf buf) {
        return new FoxSkinPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(FoxSkinPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FoxClientFx.setSkin(m.entityId, m.skin));
    }
}
