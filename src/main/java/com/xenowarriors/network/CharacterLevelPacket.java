package com.xenowarriors.network;

import com.xenowarriors.client.LevelHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> player: your Character Level (and how close the next one is). */
public record CharacterLevelPacket(int level, float progress) {
    public static void encode(CharacterLevelPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.level);
        buf.writeFloat(m.progress);
    }

    public static CharacterLevelPacket decode(FriendlyByteBuf buf) {
        return new CharacterLevelPacket(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(CharacterLevelPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LevelHud.set(m.level, m.progress));
    }
}
