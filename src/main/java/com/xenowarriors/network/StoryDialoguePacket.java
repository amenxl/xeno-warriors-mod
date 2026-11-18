package com.xenowarriors.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: story lines to show (with choices), or open the Story menu ("menu"), or close the dialogue ("close"). */
public record StoryDialoguePacket(CompoundTag tag) {
    public static void encode(StoryDialoguePacket m, FriendlyByteBuf b) { b.writeNbt(m.tag); }
    public static StoryDialoguePacket decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new StoryDialoguePacket(t == null ? new CompoundTag() : t); }
    public static void handle(StoryDialoguePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.story.StoryClient.dialogue(m.tag));
        ctx.get().setPacketHandled(true);
    }
}
