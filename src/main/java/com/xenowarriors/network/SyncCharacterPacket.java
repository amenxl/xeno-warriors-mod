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
public record SyncCharacterPacket(UUID playerId, CharacterType type, int outfit, com.xenowarriors.you.YouSlot you) {
    public SyncCharacterPacket(UUID playerId, CharacterType type) { this(playerId, type, 0, null); }
    public SyncCharacterPacket(UUID playerId, CharacterType type, int outfit) { this(playerId, type, outfit, null); }

    /** Everything about what this player is playing (their You character too, when that's what they are). */
    public static SyncCharacterPacket of(net.minecraft.server.level.ServerPlayer p) {
        CharacterType t = com.xenowarriors.character.ModCapabilities.getType(p);
        return new SyncCharacterPacket(p.getUUID(), t, com.xenowarriors.character.CharacterManager.outfit(p),
                t == CharacterType.YOU ? p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.you()).orElse(null) : null);
    }

    public static void encode(SyncCharacterPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeEnum(msg.type);
        buf.writeVarInt(msg.outfit);
        buf.writeBoolean(msg.you != null);
        if (msg.you != null) msg.you.write(buf);
    }

    public static SyncCharacterPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID(); CharacterType t = buf.readEnum(CharacterType.class); int o = buf.readVarInt();
        return new SyncCharacterPacket(id, t, o, buf.readBoolean() ? com.xenowarriors.you.YouSlot.read(buf) : null);
    }

    public static void handle(SyncCharacterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientCharacterData.set(msg.playerId, msg.type);
            ClientCharacterData.setOutfit(msg.playerId, msg.outfit);
            ClientCharacterData.setYou(msg.playerId, msg.you);
        });
    }
}
