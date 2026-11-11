package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: an enemy's hit during your cutscene (skill.CutsceneBreak): {@code state} 0 = shrugged off, 1 = counted,
 * 2 = guard broken; {@code hits}/{@code need} counted so far; {@code chance} = % a hit counts at your Defense.
 * Shown by client.CutsceneGuardHud for a moment.
 */
public record CutsceneGuardPacket(int state, int hits, int need, int chance) {
    /** The last one the local player got, and when (System.currentTimeMillis). */
    public static volatile CutsceneGuardPacket last;
    public static volatile long lastAtMs;

    public static void encode(CutsceneGuardPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.state);
        buf.writeVarInt(m.hits);
        buf.writeVarInt(m.need);
        buf.writeVarInt(m.chance);
    }

    public static CutsceneGuardPacket decode(FriendlyByteBuf buf) {
        return new CutsceneGuardPacket(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(CutsceneGuardPacket m, Supplier<NetworkEvent.Context> ctx) {
        last = m;
        lastAtMs = System.currentTimeMillis();
    }
}
