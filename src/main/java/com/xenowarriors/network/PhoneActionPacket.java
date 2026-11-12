package com.xenowarriors.network;

import com.xenowarriors.item.PhoneCalls;
import com.xenowarriors.item.PhoneItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server from the phone screen: 0 = call this player, 1 = hang up, 2 = pretend call (talk to nobody),
 * 3 = open the Teammates page, 4 = ring a busy teammate (the character is in the UUID's low bits: its ordinal).
 */
public record PhoneActionPacket(int action, UUID target) {
    public static void encode(PhoneActionPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.action);
        buf.writeUUID(m.target);
    }

    public static PhoneActionPacket decode(FriendlyByteBuf buf) {
        return new PhoneActionPacket(buf.readVarInt(), buf.readUUID());
    }

    public static void handle(PhoneActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p == null) return;
        switch (m.action) {
            case 0 -> PhoneCalls.call(p, m.target);
            case 1 -> PhoneCalls.hangUp(p);
            case 2 -> {
                boolean on = !PhoneItem.onCall(p);
                PhoneCalls.setOnCall(p, on);
                p.displayClientMessage(net.minecraft.network.chat.Component.literal(on ? "📞 On a call... (right-click to hang up)" : "Call ended")
                        .withStyle(on ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GRAY), true);
            }
            case 3 -> com.xenowarriors.bot.BotCalls.open(p);
            case 4 -> {
                long o = m.target.getLeastSignificantBits();
                var all = com.xenowarriors.character.CharacterType.values();
                if (o >= 0 && o < all.length) com.xenowarriors.bot.BotCalls.call(p, all[(int) o]);
            }
            default -> {}
        }
        ctx.get().setPacketHandled(true);
    }
}
