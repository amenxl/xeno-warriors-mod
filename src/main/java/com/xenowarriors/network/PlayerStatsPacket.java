package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Server -> client: every online player's character, level and stats. */
public record PlayerStatsPacket(List<Entry> players) {
    public record Entry(String name, String type, int level, int speed, int attack, int defense, float karma,
                        float health, float maxHealth, boolean self, boolean party) {}

    public static void encode(PlayerStatsPacket m, FriendlyByteBuf buf) {
        buf.writeCollection(m.players, (b, e) -> {
            b.writeUtf(e.name());
            b.writeUtf(e.type());
            b.writeVarInt(e.level());
            b.writeVarInt(e.speed());
            b.writeVarInt(e.attack());
            b.writeVarInt(e.defense());
            b.writeFloat(e.karma());
            b.writeFloat(e.health());
            b.writeFloat(e.maxHealth());
            b.writeBoolean(e.self());
            b.writeBoolean(e.party());
        });
    }

    public static PlayerStatsPacket decode(FriendlyByteBuf buf) {
        return new PlayerStatsPacket(buf.readList(b -> new Entry(b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean())));
    }

    public static void handle(PlayerStatsPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PlayerStatsScreen.receive(m.players));
    }
}
