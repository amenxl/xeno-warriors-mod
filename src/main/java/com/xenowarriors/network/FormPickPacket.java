package com.xenowarriors.network;

import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.WeaponForms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the summon key's form wheel - opened (start charging), a form picked, closed with nothing, or a tap. */
public record FormPickPacket(int action, int form) {
    public static final int OPEN = 0, PICK = 1, CANCEL = 2, TAP = 3;

    public static void encode(FormPickPacket m, FriendlyByteBuf b) { b.writeVarInt(m.action); b.writeVarInt(m.form); }

    public static FormPickPacket decode(FriendlyByteBuf b) { return new FormPickPacket(b.readVarInt(), b.readVarInt()); }

    public static void handle(FormPickPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        switch (m.action) {
            case OPEN -> WeaponForms.open(p);
            case PICK -> { Form f = Form.byId(m.form); if (f != null) WeaponForms.pick(p, f); else WeaponForms.close(p); }
            case CANCEL -> WeaponForms.close(p);
            case TAP -> { if (!com.xenowarriors.party.Duels.cantAct(p)) WeaponForms.tap(p); }
            default -> {}
        }
    }
}
