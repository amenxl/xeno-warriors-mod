package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> owner: their pets out in the world, with Karma (and DragonMineZ Ki) for the pet HUD. */
public record PetBarsPacket(int[] ids, float[] karma, float[] karmaMax, float[] ki, float[] kiMax) {
    public static void encode(PetBarsPacket m, FriendlyByteBuf b) {
        b.writeVarIntArray(m.ids);
        for (int i = 0; i < m.ids.length; i++) { b.writeFloat(m.karma[i]); b.writeFloat(m.karmaMax[i]); b.writeFloat(m.ki[i]); b.writeFloat(m.kiMax[i]); }
    }
    public static PetBarsPacket decode(FriendlyByteBuf b) {
        int[] ids = b.readVarIntArray(); int n = ids.length;
        float[] k = new float[n], km = new float[n], ki = new float[n], kim = new float[n];
        for (int i = 0; i < n; i++) { k[i] = b.readFloat(); km[i] = b.readFloat(); ki[i] = b.readFloat(); kim[i] = b.readFloat(); }
        return new PetBarsPacket(ids, k, km, ki, kim);
    }
    public static void handle(PetBarsPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PetHud.set(m.ids, m.karma, m.karmaMax, m.ki, m.kiMax));
        ctx.get().setPacketHandled(true);
    }
}
