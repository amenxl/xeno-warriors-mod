package com.xenowarriors.network;

import com.xenowarriors.apl.Apl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Abilities (APL). Client -> server: REQUEST, BUY (ability index), RESET, POTION (cycle Auto Potion's health %).
 * Server -> client: STATE (APL earned and spent, the reset's cost, Auto Potion's %, every ability's rank).
 */
public record AplPacket(int action, int ability, int earned, int spent, long resetCost, int potionAt, int[] ranks) {
    public static final int REQUEST = 0, BUY = 1, RESET = 2, POTION = 3, STATE = 4;

    public static AplPacket action(int action, int ability) { return new AplPacket(action, ability, 0, 0, 0, 0, new int[0]); }

    public static void encode(AplPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.action); b.writeVarInt(m.ability + 1); b.writeVarInt(m.earned); b.writeVarInt(m.spent);
        b.writeVarLong(m.resetCost); b.writeVarInt(m.potionAt); b.writeVarIntArray(m.ranks);
    }

    public static AplPacket decode(FriendlyByteBuf b) {
        return new AplPacket(b.readVarInt(), b.readVarInt() - 1, b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarInt(), b.readVarIntArray(64));
    }

    /** The client's copy. */
    public static volatile AplPacket CLIENT = new AplPacket(STATE, -1, 0, 0, 0, 35, new int[Apl.Ability.values().length]);

    public static void handle(AplPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        if (m.action == STATE) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CLIENT = m);
            return;
        }
        var p = ctx.get().getSender();
        if (p == null) return;
        Apl.Ability[] all = Apl.Ability.values();
        String why = null;
        switch (m.action) {
            case BUY -> { if (m.ability >= 0 && m.ability < all.length) why = Apl.buy(p, all[m.ability]); }
            case RESET -> why = Apl.reset(p);
            case POTION -> Apl.cyclePotion(p);
            default -> {}
        }
        if (why != null) p.displayClientMessage(Component.literal(why).withStyle(net.minecraft.ChatFormatting.RED), true);
        Apl.sync(p);
    }
}
