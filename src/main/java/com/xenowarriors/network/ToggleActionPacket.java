package com.xenowarriors.network;

import com.xenowarriors.toggle.Toggles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the toggle wheel, the Passives / R+L Click tabs, and the right+left click itself. */
public record ToggleActionPacket(int action, String id) {
    public static final int REQUEST = 0, USE = 1, ENABLE = 2, DISABLE = 3, SET_RL = 4, FIRE_RL = 5, LOCKED_ON = 6, SCENT_PICK = 7;

    public static void encode(ToggleActionPacket m, FriendlyByteBuf b) { b.writeVarInt(m.action); b.writeUtf(m.id, 32); }

    public static ToggleActionPacket decode(FriendlyByteBuf b) { return new ToggleActionPacket(b.readVarInt(), b.readUtf(32)); }

    public static void handle(ToggleActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p == null) return;
        Toggles.Entry e = Toggles.Entry.byId(m.id);
        switch (m.action) {
            case USE -> { if (e != null) Toggles.use(p, e); }
            case ENABLE, DISABLE -> { if (e != null && Toggles.of(p).contains(e)) { Toggles.setEnabled(p, e, m.action == ENABLE); Toggles.send(p); } }
            case SET_RL -> Toggles.setRl(p, m.id);
            case FIRE_RL -> Toggles.useRl(p);
            case LOCKED_ON -> { try { com.xenowarriors.toggle.Passives.lockedOn(p, Integer.parseInt(m.id)); } catch (NumberFormatException ignored) {} }
            case SCENT_PICK -> { try { com.xenowarriors.toggle.Passives.pickScent(p, Integer.parseInt(m.id)); } catch (NumberFormatException ignored) {} }
            default -> Toggles.send(p);
        }
    }
}
