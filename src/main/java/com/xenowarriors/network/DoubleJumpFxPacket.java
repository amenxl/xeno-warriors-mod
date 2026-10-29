package com.xenowarriors.network;

import com.xenowarriors.client.mikiko.MikikoAnimations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> nearby clients: play Mikiko's double-jump flip and wind burst. */
public record DoubleJumpFxPacket(int entityId) {
    public static void encode(DoubleJumpFxPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
    }

    public static DoubleJumpFxPacket decode(FriendlyByteBuf buf) {
        return new DoubleJumpFxPacket(buf.readVarInt());
    }

    public static void handle(DoubleJumpFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null && mc.level.getEntity(msg.entityId) instanceof net.minecraft.client.player.AbstractClientPlayer p
                    && com.xenowarriors.client.ClientCharacterData.get(p) == com.xenowarriors.character.CharacterType.KAERU)
                com.xenowarriors.client.kaeru.KaeruClient.onDoubleJump(msg.entityId);
            else MikikoAnimations.onDoubleJump(msg.entityId);
        });
    }
}
