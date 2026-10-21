package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Server -> client: every online player's character, level and stats. */
public record PlayerStatsPacket(List<Entry> players, Me me) {
    public record Entry(String name, String type, int level, int speed, int attack, int defense, float karma,
                        float health, float maxHealth, boolean self, boolean party) {}

    /** Extra detail about yourself for the "My Warrior" tab. */
    public record Me(float speedXp, float attackXp, float defenseXp, int senseRange, int senseSeconds, int senseUses,
                     int sealed, boolean inRealm, boolean prisoner, int pets, int flightMinutes, int mining, float miningXp,
                     int bladeLevel, float bladeXp, int realmLeft, int realmMax, int cooldownLeft) {}

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
        Me me = m.me;
        buf.writeFloat(me.speedXp());
        buf.writeFloat(me.attackXp());
        buf.writeFloat(me.defenseXp());
        buf.writeVarInt(me.senseRange());
        buf.writeVarInt(me.senseSeconds());
        buf.writeVarInt(me.senseUses());
        buf.writeVarInt(me.sealed());
        buf.writeBoolean(me.inRealm());
        buf.writeBoolean(me.prisoner());
        buf.writeVarInt(me.pets());
        buf.writeVarInt(me.flightMinutes());
        buf.writeVarInt(me.mining());
        buf.writeFloat(me.miningXp());
        buf.writeVarInt(me.bladeLevel());
        buf.writeFloat(me.bladeXp());
        buf.writeVarInt(me.realmLeft());
        buf.writeVarInt(me.realmMax());
        buf.writeVarInt(me.cooldownLeft());
    }

    public static PlayerStatsPacket decode(FriendlyByteBuf buf) {
        List<Entry> list = buf.readList(b -> new Entry(b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean()));
        Me me = new Me(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        return new PlayerStatsPacket(list, me);
    }

    public static void handle(PlayerStatsPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PlayerStatsScreen.receive(m.players, m.me));
    }
}
