package com.xenowarriors.network;

import com.xenowarriors.forms.FormGunItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server, summoned guns (TACZ-style controls): aiming down the sights on / off, or a shot (with where the
 * player was looking, the spread they fired with, and whether they were aimed).
 */
public record GunActionPacket(int action, float pitch, float yaw, float spread, boolean aimed) {
    public static final int AIM_ON = 0, AIM_OFF = 1, FIRE = 2;

    public static GunActionPacket aim(boolean on) { return new GunActionPacket(on ? AIM_ON : AIM_OFF, 0F, 0F, 0F, on); }

    public static void encode(GunActionPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.action); b.writeFloat(m.pitch); b.writeFloat(m.yaw); b.writeFloat(m.spread); b.writeBoolean(m.aimed);
    }

    public static GunActionPacket decode(FriendlyByteBuf b) {
        return new GunActionPacket(b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean());
    }

    public static void handle(GunActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        switch (m.action) {
            case AIM_ON -> FormGunItem.setAiming(p, true);
            case AIM_OFF -> FormGunItem.setAiming(p, false);
            case FIRE -> {
                var stack = p.getMainHandItem();
                if (stack.getItem() instanceof FormGunItem g && Float.isFinite(m.pitch) && Float.isFinite(m.yaw) && Float.isFinite(m.spread))
                    g.fire(p, stack, m.aimed, m.spread, m.pitch, m.yaw);
            }
            default -> {}
        }
    }
}
