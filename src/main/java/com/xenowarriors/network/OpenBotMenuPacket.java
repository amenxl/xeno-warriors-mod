package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> owner: open a bot teammate's menu (its mode, level, pets, whether it can fly you). */
public record OpenBotMenuPacket(int botId, String character, int mode, int level, boolean canFly, String[] petNames, int[] petIds) {
    public static void encode(OpenBotMenuPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.botId); b.writeUtf(m.character); b.writeVarInt(m.mode); b.writeVarInt(m.level); b.writeBoolean(m.canFly);
        b.writeVarInt(m.petNames.length);
        for (int i = 0; i < m.petNames.length; i++) { b.writeUtf(m.petNames[i]); b.writeVarInt(m.petIds[i]); }
    }
    public static OpenBotMenuPacket decode(FriendlyByteBuf b) {
        int id = b.readVarInt(); String c = b.readUtf(); int mode = b.readVarInt(), lv = b.readVarInt(); boolean fly = b.readBoolean();
        int n = Math.min(32, b.readVarInt()); String[] names = new String[n]; int[] ids = new int[n];
        for (int i = 0; i < n; i++) { names[i] = b.readUtf(); ids[i] = b.readVarInt(); }
        return new OpenBotMenuPacket(id, c, mode, lv, fly, names, ids);
    }
    public static void handle(OpenBotMenuPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PacketScreens.openBotMenu(m));
        ctx.get().setPacketHandled(true);
    }
}
