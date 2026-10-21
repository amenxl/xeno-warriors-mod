package com.xenowarriors.network;

import com.xenowarriors.party.Duels;
import com.xenowarriors.party.Parties;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: something picked from the right-click menu on another player. */
public record PlayerActionPacket(UUID target, int action) {
    public static final int INVITE = 0, DUEL = 1, LEAVE = 2, KICK = 3;

    public static void encode(PlayerActionPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.target);
        buf.writeVarInt(m.action);
    }

    public static PlayerActionPacket decode(FriendlyByteBuf buf) {
        return new PlayerActionPacket(buf.readUUID(), buf.readVarInt());
    }

    public static void handle(PlayerActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || p.getServer() == null) return;
        ServerPlayer target = p.getServer().getPlayerList().getPlayer(m.target);
        if (target == null && m.action != LEAVE) return;
        if (target != null && target.distanceTo(p) > 16 && m.action != LEAVE && m.action != KICK) return;
        switch (m.action) {
            case INVITE -> Parties.invite(p, target);
            case DUEL -> Duels.request(p, target);
            case LEAVE -> Parties.leave(p, true);
            case KICK -> Parties.kick(p, target);
            default -> { }
        }
    }
}
