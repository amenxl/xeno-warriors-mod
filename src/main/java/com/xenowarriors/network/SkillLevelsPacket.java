package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Ability levels (skill/SkillLevels). Server -> client: every skill's tier / level / uses for the character you're
 * playing. Client -> server: {@code upgrade} = a skill id to move up a tier (empty = just send me the list).
 */
public record SkillLevelsPacket(String upgrade, List<Entry> entries) {
    public record Entry(String id, int tier, int level, int uses) {}

    public static void encode(SkillLevelsPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.upgrade, 64);
        b.writeVarInt(m.entries.size());
        for (Entry e : m.entries) { b.writeUtf(e.id, 64); b.writeVarInt(e.tier); b.writeVarInt(e.level); b.writeVarInt(e.uses); }
    }

    public static SkillLevelsPacket decode(FriendlyByteBuf b) {
        String up = b.readUtf(64);
        int n = b.readVarInt();
        List<Entry> l = new ArrayList<>(Math.min(n, 256));        // (n is from the wire)
        for (int i = 0; i < n && i < 4096; i++) l.add(new Entry(b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readVarInt()));
        return new SkillLevelsPacket(up, l);
    }

    public static void handle(SkillLevelsPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) {
            if (!m.upgrade.isEmpty()) com.xenowarriors.skill.SkillLevels.upgrade(sender, m.upgrade);
            else com.xenowarriors.skill.SkillLevels.sync(sender);
            return;
        }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.ClientSkillLevels.set(m.entries));
    }
}
