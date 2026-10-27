package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: someone (a player or a bot) started combo swing {@code hit} of this style - play it. */
public record ComboSwingPacket(int entity, int style, int hit, boolean heavy) {
    public static void encode(ComboSwingPacket m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeVarInt(m.style); b.writeVarInt(m.hit); b.writeBoolean(m.heavy); }
    public static ComboSwingPacket decode(FriendlyByteBuf b) { return new ComboSwingPacket(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()); }
    public static void handle(ComboSwingPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.combo.ComboClient.remote(m.entity, m.style, m.hit, m.heavy));
    }
}
