package com.xenowarriors.network;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> client: "This player is playing as this character." */
public record SyncCharacterPacket(UUID playerId, CharacterType type, int outfit) {
    public SyncCharacterPacket(UUID playerId, CharacterType type) { this(playerId, type, 0); }

    public static void encode(SyncCharacterPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeEnum(msg.type);
        buf.writeVarInt(msg.outfit);
    }

    public static SyncCharacterPacket decode(FriendlyByteBuf buf) {
        return new SyncCharacterPacket(buf.readUUID(), buf.readEnum(CharacterType.class), buf.readVarInt());
    }

    public static void handle(SyncCharacterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientCharacterData.set(msg.playerId, msg.type);
            ClientCharacterData.setOutfit(msg.playerId, msg.outfit);
        });
    }
}
