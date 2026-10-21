package com.xenowarriors.network;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Client -> server: Mikiko double-jumped (his client already launched him). */
public record DoubleJumpPacket() {
    public static void encode(DoubleJumpPacket msg, FriendlyByteBuf buf) {}

    public static DoubleJumpPacket decode(FriendlyByteBuf buf) {
        return new DoubleJumpPacket();
    }

    public static void handle(DoubleJumpPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || ModCapabilities.getType(player) != CharacterType.MIKIKO || player.onGround()) return;
        player.fallDistance = 0F;
        // sound for everyone else (his own client already played it)
        player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_FLAP,
                SoundSource.PLAYERS, 0.35F, 1.7F);
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), new DoubleJumpFxPacket(player.getId()));
    }
}
