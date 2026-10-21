package com.xenowarriors.network;

import com.xenowarriors.client.fox.FoxStatsScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> owner: open the stat card for one of your foxes. */
public record FoxStatsPacket(int entityId, UUID fox, String name, int level, float xp, int xpNeeded, float health,
                             float maxHealth, float attack, float speed, int kills, int days, boolean snow, int skin, String weapon) {
    public static void encode(FoxStatsPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeUUID(m.fox);
        buf.writeUtf(m.name);
        buf.writeVarInt(m.level);
        buf.writeFloat(m.xp);
        buf.writeVarInt(m.xpNeeded);
        buf.writeFloat(m.health);
        buf.writeFloat(m.maxHealth);
        buf.writeFloat(m.attack);
        buf.writeFloat(m.speed);
        buf.writeVarInt(m.kills);
        buf.writeVarInt(m.days + 1);
        buf.writeBoolean(m.snow);
        buf.writeVarInt(m.skin);
        buf.writeUtf(m.weapon);
    }

    public static FoxStatsPacket decode(FriendlyByteBuf buf) {
        return new FoxStatsPacket(buf.readVarInt(), buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readFloat(),
                buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt() - 1, buf.readBoolean(), buf.readVarInt(), buf.readUtf());
    }

    public static void handle(FoxStatsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FoxStatsScreen.open(msg));
    }
}
