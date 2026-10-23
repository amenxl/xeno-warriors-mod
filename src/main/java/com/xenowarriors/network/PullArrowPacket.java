package com.xenowarriors.network;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: pulled one of the arrows stuck in me out. */
public record PullArrowPacket() {
    public static void encode(PullArrowPacket m, FriendlyByteBuf buf) {}

    public static PullArrowPacket decode(FriendlyByteBuf buf) { return new PullArrowPacket(); }

    public static void handle(PullArrowPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || ModCapabilities.getType(p) == CharacterType.NONE || p.getArrowCount() <= 0) return;
        p.setArrowCount(p.getArrowCount() - 1);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.5F, 1.4F);
    }
}
