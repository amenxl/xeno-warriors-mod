package com.xenowarriors.network;

import com.xenowarriors.client.party.PartyClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server -> party members: everyone in the party with their health and Karma (empty = not in a party). */
public record PartySyncPacket(List<Member> members) {
    public record Member(UUID id, String name, float health, float maxHealth, float karma, float maxKarma, int type,
                         boolean knockedOut, boolean leader, double x, double y, double z, String dim, boolean bot) {}

    public static void encode(PartySyncPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.members.size());
        for (Member x : m.members) {
            buf.writeUUID(x.id);
            buf.writeUtf(x.name);
            buf.writeFloat(x.health);
            buf.writeFloat(x.maxHealth);
            buf.writeFloat(x.karma);
            buf.writeFloat(x.maxKarma);
            buf.writeVarInt(x.type);
            buf.writeBoolean(x.knockedOut);
            buf.writeBoolean(x.leader);
            buf.writeDouble(x.x);
            buf.writeDouble(x.y);
            buf.writeDouble(x.z);
            buf.writeUtf(x.dim);
            buf.writeBoolean(x.bot);
        }
    }

    public static PartySyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        java.util.ArrayList<Member> list = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Member(buf.readUUID(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf(), buf.readBoolean()));
        }
        return new PartySyncPacket(list);
    }

    public static void handle(PartySyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PartyClient.setMembers(m.members));
    }
}
