package com.xenowarriors.network;

import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "I picked this character." */
public record SelectCharacterPacket(CharacterType type) {
    public static void encode(SelectCharacterPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.type);
    }

    public static SelectCharacterPacket decode(FriendlyByteBuf buf) {
        return new SelectCharacterPacket(buf.readEnum(CharacterType.class));
    }

    public static void handle(SelectCharacterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || msg.type == CharacterType.NONE) return;
        if (!com.xenowarriors.character.Seasons.unlocked(player.server, msg.type.season())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Season " + msg.type.season() + " content is locked").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        ServerPlayer other = com.xenowarriors.character.CharacterLock.takenBy(player, msg.type);
        if (other != null && com.xenowarriors.character.ModCapabilities.getType(player) != msg.type) {
            if (com.xenowarriors.character.CharacterLock.anyFree(player)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(other.getGameProfile().getName() + " is already playing " + msg.type.getDisplayName().getString() + " - pick someone else!").withStyle(net.minecraft.ChatFormatting.RED), false);
                ModNetwork.sendToPlayer(new OpenSelectScreenPacket(), player);
                return;
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Every character is taken, so you can double up with " + other.getGameProfile().getName() + ".").withStyle(net.minecraft.ChatFormatting.YELLOW), false);
        }
        CharacterManager.setCharacter(player, msg.type);
    }
}
