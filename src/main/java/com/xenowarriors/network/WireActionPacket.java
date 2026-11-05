package com.xenowarriors.network;

import com.xenowarriors.rhalix.Wires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Rhalix's wires - a whip swing, starting / letting go of a bind, or a bound player's mash. */
public record WireActionPacket(int action, int target) {
    public static final int WHIP = 0, BIND = 1, RELEASE = 2, MASH = 3;

    public static void encode(WireActionPacket m, FriendlyByteBuf b) { b.writeVarInt(m.action); b.writeVarInt(m.target); }

    public static WireActionPacket decode(FriendlyByteBuf b) { return new WireActionPacket(b.readVarInt(), b.readVarInt()); }

    public static void handle(WireActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        switch (m.action) {
            case WHIP -> Wires.whip(p);
            case BIND -> Wires.startBind(p, m.target);
            case RELEASE -> Wires.release(p, "let go");
            case MASH -> Wires.mash(p);
            default -> {}
        }
    }
}
