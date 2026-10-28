package com.xenowarriors.network;

import com.xenowarriors.you.YouSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your ten You characters (with each one's Character Level), and which is in use; opens the slots screen. */
public record YouSlotsPacket(YouSlot[] slots, int[] levels, int current, boolean open) {
    public static void encode(YouSlotsPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.slots.length);
        for (int i = 0; i < m.slots.length; i++) { m.slots[i].write(buf); buf.writeVarInt(m.levels[i]); }
        buf.writeVarInt(m.current); buf.writeBoolean(m.open);
    }
    public static YouSlotsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt(); YouSlot[] s = new YouSlot[n]; int[] lv = new int[n];
        for (int i = 0; i < n; i++) { s[i] = YouSlot.read(buf); lv[i] = buf.readVarInt(); }
        return new YouSlotsPacket(s, lv, buf.readVarInt(), buf.readBoolean());
    }
    public static void handle(YouSlotsPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.you.YouClient.slots(m.slots, m.levels, m.current, m.open));
        ctx.get().setPacketHandled(true);
    }
}
