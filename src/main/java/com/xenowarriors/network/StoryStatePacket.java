package com.xenowarriors.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your story mode state (storylines, missions, who you've met, bonds, what's locked, the objective). */
public record StoryStatePacket(CompoundTag tag) {
    public static void encode(StoryStatePacket m, FriendlyByteBuf b) { b.writeNbt(m.tag); }
    public static StoryStatePacket decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new StoryStatePacket(t == null ? new CompoundTag() : t); }
    public static void handle(StoryStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.story.StoryClient.state(m.tag));
        ctx.get().setPacketHandled(true);
    }
}
