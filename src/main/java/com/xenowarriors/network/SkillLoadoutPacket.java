package com.xenowarriors.network;

import com.xenowarriors.client.ClientSkillState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the 4 equipped skills (pool indexes) and the Character Level (for what's unlocked). */
public record SkillLoadoutPacket(int[] loadout, int level, String ka, int season) {
    public static void encode(SkillLoadoutPacket m, FriendlyByteBuf buf) {
        buf.writeVarIntArray(m.loadout);
        buf.writeVarInt(m.level);
        buf.writeUtf(m.ka);
        buf.writeVarInt(m.season);
    }

    public static SkillLoadoutPacket decode(FriendlyByteBuf buf) {
        return new SkillLoadoutPacket(buf.readVarIntArray(), buf.readVarInt(), buf.readUtf(), buf.readVarInt());
    }

    public static void handle(SkillLoadoutPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> { ClientSkillState.setLoadout(m.loadout, m.level); ClientSkillState.setKa(m.ka); ClientSkillState.setSeason(m.season); });
        ctx.get().setPacketHandled(true);
    }
}
