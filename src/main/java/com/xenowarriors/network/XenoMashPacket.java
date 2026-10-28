package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Ultima mashing to push back a Xeno Blast: client -> server is one press; server -> client is the progress. */
public record XenoMashPacket(int caster, int progress) {
    public static void encode(XenoMashPacket m, FriendlyByteBuf b) { b.writeVarInt(m.caster); b.writeVarInt(m.progress); }
    public static XenoMashPacket decode(FriendlyByteBuf b) { return new XenoMashPacket(b.readVarInt(), b.readVarInt()); }
    public static void handle(XenoMashPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) { com.xenowarriors.skill.XenoBlast.mash(p, m.caster); return; }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.XenoCutscene.mashProgress(m.progress));
    }
}
